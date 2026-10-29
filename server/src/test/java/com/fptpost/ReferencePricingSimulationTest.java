package com.fptpost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.AppException;
import com.fptpost.modules.catalog.dto.internal.ParameterSnapshot;
import com.fptpost.modules.catalog.dto.request.PricingQuoteRequest;
import com.fptpost.modules.catalog.enums.CourierTier;
import com.fptpost.modules.catalog.enums.QuoteUrgency;
import com.fptpost.modules.catalog.service.ReferencePricingSimulation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ReferencePricingSimulationTest {
    private final ReferencePricingSimulation reference = new ReferencePricingSimulation();

    private ParameterSnapshot snapshot() throws Exception {
        var json = new ObjectMapper();
        var values = new LinkedHashMap<String, String>();
        try (var source = getClass().getResourceAsStream("/catalog/parameters.json")) {
            var rows = json.readTree(source);
            for (var row : rows) {
                // Registry metadata is internal; the source value is exported without floating point arithmetic.
                String value = row.has("defaultValue") ? row.path("defaultValue").asText() : row.path("value").asText();
                values.put(row.path("key").asText(), value);
            }
        }
        Map<String, Long> versions = new LinkedHashMap<>();
        values.keySet().forEach(key -> versions.put(key, 0L));
        return new ParameterSnapshot(values, versions, Instant.EPOCH);
    }

    private PricingQuoteRequest quote(String distance, String kg, String cm, QuoteUrgency urgency) {
        return PricingQuoteRequest.builder().distanceKm(new BigDecimal(distance)).weightKg(new BigDecimal(kg))
                .longestSideCm(new BigDecimal(cm)).urgency(urgency).declaredValue(500000L).build();
    }

    @ParameterizedTest(name = "{0}: expected {5} VND")
    @CsvFileSource(resources = "/pricing-cases.csv", numLinesToSkip = 1)
    void matchesAllTwentyAuthoritativeExcelCases(String id, String distance, String weight, String side,
                                                QuoteUrgency urgency, long expected) throws Exception {
        var result = reference.simulate(quote(distance, weight, side, urgency), snapshot());
        assertThat(result.getSuggestedFare()).isEqualTo(expected);
        assertThat(result.getBalanceDifference()).isZero();
    }

    @Test void matchesWorkbookFinancialExampleIncludingInsuranceAndCommissionRounding() throws Exception {
        var quote = quote("8", "3", "40", QuoteUrgency.SAMEDAY);
        quote.setInsured(true); quote.setCourierTier(CourierTier.BRONZE);
        var result = reference.simulate(quote, snapshot());
        assertThat(result.getSuggestedFare()).isEqualTo(49500);
        assertThat(result.getBidMinimum()).isEqualTo(35000);
        assertThat(result.getBidMaximum()).isEqualTo(74500);
        assertThat(result.getInsuranceFee()).isEqualTo(5000);
        assertThat(result.getCommissionAmount()).isEqualTo(3465);
        assertThat(result.getCourierNet()).isEqualTo(46035);
        assertThat(result.getGuaranteeFundAmount()).isEqualTo(5346);
        assertThat(result.getPlatformNet()).isEqualTo(3119);
        assertThat(result.getSenderTotal()).isEqualTo(54500);
        assertThat(result.getBalanceDifference()).isZero();
    }

    @Test void proposalChangesPreviewWithoutMutatingTheOriginalSnapshot() throws Exception {
        var original = snapshot();
        var values = new LinkedHashMap<>(original.values()); values.put("PER_KM", "4000");
        var proposed = new ParameterSnapshot(values, original.versions(), original.capturedAt());
        var input = quote("8", "3", "40", QuoteUrgency.SAMEDAY);
        assertThat(reference.simulate(input, proposed).getSuggestedFare()).isEqualTo(53000);
        assertThat(reference.simulate(input, original).getSuggestedFare()).isEqualTo(49500);
        assertThat(original.value("PER_KM")).isEqualTo("3500");
    }

    @Test void platformPaysDiscountAndWithholdingDoesNotReduceTheFund() throws Exception {
        var original = snapshot();
        var values = new LinkedHashMap<>(original.values()); values.put("WITHHOLD", "0.01");
        var input = quote("8", "3", "40", QuoteUrgency.SAMEDAY);
        input.setInsured(true); input.setCourierTier(CourierTier.BRONZE); input.setPromotionDiscount(10000L);
        var result = reference.simulate(input, new ParameterSnapshot(values, original.versions(), original.capturedAt()));
        assertThat(result.getWithholdingAmount()).isEqualTo(495);
        assertThat(result.getCourierNet()).isEqualTo(45540);
        assertThat(result.getGuaranteeFundAmount()).isEqualTo(5346);
        assertThat(result.getPlatformNet()).isEqualTo(-6881);
        assertThat(result.getSenderTotal()).isEqualTo(44500);
        assertThat(result.getBalanceDifference()).isZero();
    }

    @Test void rejectsOversizedGoodsAndBidOutsideConfiguredBounds() throws Exception {
        var input = quote("1", "20.001", "20", QuoteUrgency.NORMAL);
        assertThatThrownBy(() -> reference.simulate(input, snapshot())).isInstanceOf(AppException.class);
        input.setWeightKg(BigDecimal.ONE); input.setAgreedFareRatio(new BigDecimal("0.6999"));
        assertThatThrownBy(() -> reference.simulate(input, snapshot())).isInstanceOf(AppException.class);
    }
}
