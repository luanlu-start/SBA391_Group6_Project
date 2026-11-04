package com.fptpost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.AppException;
import com.fptpost.modules.catalog.dto.internal.ParameterSnapshot;
import com.fptpost.modules.catalog.enums.ParameterValueType;
import com.fptpost.modules.catalog.exception.ParameterErrorCode;
import com.fptpost.modules.catalog.service.ParameterCatalog;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ParameterCatalogTest {
    private final ParameterCatalog catalog = new ParameterCatalog(new ObjectMapper());

    @Test void containsEverySourceKeyWithUnchangedDefaultsAndUnicodeCompoundValues() {
        assertThat(catalog.definitions()).hasSize(109);
        assertThat(catalog.definitions()).extracting(definition -> definition.prmId()).doesNotHaveDuplicates();
        assertThat(catalog.defaultValues()).containsEntry("BASE_FARE", "10000").containsEntry("FEE_NEW", "0.08")
                .containsEntry("BID_MAX", "1.5").containsEntry("WITHHOLD", "0")
                .containsEntry("TIER_BRONZE", "≥ 55 điểm & ≥ 5 đơn")
                .containsEntry("CONTACT_TRIES", "3 lần gọi trong 30 phút")
                .containsEntry("VALUE_TIER", "Tân binh 3 / Đồng 6 / Bạc 10 / Vàng 15 / Kim cương 20 (triệu đồng)");
        assertThat(catalog.definition("BASE_KM").valueType()).isEqualTo(ParameterValueType.DECIMAL);
        assertThat(catalog.definition("BASE_FARE").valueType()).isEqualTo(ParameterValueType.INTEGER);
        catalog.validateSnapshot(catalog.defaultValues());
    }

    @Test void distinguishesRatiosFromBidMultipliersAndKeepsIntegerCurrency() {
        assertThat(catalog.normalize("FEE_NEW", "0.0800")).isEqualTo("0.08");
        assertThat(catalog.normalize("BID_MAX", "1.75")).isEqualTo("1.75");
        assertThat(catalog.normalize("WITHHOLD", "0")).isEqualTo("0");
        assertThat(catalog.normalize("RECONCILE_AT", "0")).isEqualTo("0");
        assertThat(catalog.normalize("MAX_WEIGHT", "20.5")).isEqualTo("20.5");
        for (String[] invalid : new String[][]{{"FEE_NEW", "8"}, {"BASE_FARE", "12.5"},
                {"OTP_MAX_TRIES", "0"}, {"ROUND_FEE", "0"}, {"RECONCILE_AT", "24"},
                {"FEE_NEW", "-0.01"}, {"BASE_FARE", "9223372036854775808"},
                {"INS_RATE", "NaN"}, {"BASE_KM", "1e100000000"}, {"URGENT_MULT", "0.5"}}) {
            assertThatThrownBy(() -> catalog.normalize(invalid[0], invalid[1]))
                    .isInstanceOf(AppException.class)
                    .extracting(exception -> ((AppException) exception).getErrorCode())
                    .isEqualTo(ParameterErrorCode.PARAMETER_INVALID_VALUE);
        }
    }

    @Test void validatesFinalCrossKeyValuesInsteadOfAllowingInconsistentSizesAmountsOrWeights() {
        for (Map<String, String> changes : java.util.List.of(Map.of("SIZE_S", "60"),
                Map.of("HIGH_VALUE", "4000000"), Map.of("MIN_TOPUP", "11000000"),
                Map.of("BASE_KM", "50"), Map.of("OPEN_DEFAULT", "200"),
                Map.of("W_ONTIME", "0.4"), Map.of("TRUST_REVIEW", "40"), Map.of("WITHHOLD", "0.94"),
                Map.of("TIER_BRONZE", "≥ 80 điểm & ≥ 30 đơn"))) {
            Map<String, String> proposed = new HashMap<>(catalog.defaultValues());
            proposed.putAll(changes);
            assertThatThrownBy(() -> catalog.validateSnapshot(proposed)).isInstanceOf(AppException.class)
                    .extracting(exception -> ((AppException) exception).getErrorCode())
                    .isEqualTo(ParameterErrorCode.PARAMETER_CONSTRAINT_VIOLATION);
        }
        Map<String, String> atomicWeights = new HashMap<>(catalog.defaultValues());
        atomicWeights.put("W_ONTIME", "0.4"); atomicWeights.put("W_RATING", "0.25");
        assertThatCode(() -> catalog.validateSnapshot(atomicWeights)).doesNotThrowAnyException();
        Map<String, String> zeroNet = new HashMap<>(catalog.defaultValues());
        zeroNet.put("WITHHOLD", "0.92");
        assertThatCode(() -> catalog.validateSnapshot(zeroNet)).doesNotThrowAnyException();
    }

    @Test void validatesCompoundGrammarAndNormalizesEquivalentInputWithoutChangingMeaning() {
        assertThat(catalog.normalize("TIER_BRONZE", ">=55 điểm & >=5 đơn")).isEqualTo("≥ 55 điểm & ≥ 5 đơn");
        assertThat(catalog.normalize("CONTACT_TRIES", "4 lần gọi trong 20 phút")).isEqualTo("4 lần gọi trong 20 phút");
        for (String[] invalid : new String[][]{{"CONTACT_TRIES", "call twice"},
                {"TIER_BRONZE", "≥ 101 điểm & ≥ 5 đơn"}, {"CONC_TIER", "3/5/8/12/15"},
                {"CONTACT_TRIES", "0 lần gọi trong 30 phút"},
                {"CONC_TIER", "Tân binh 5 / Đồng 3 / Bạc 8 / Vàng 12 / Kim cương 15"}}) {
            assertThatThrownBy(() -> catalog.normalize(invalid[0], invalid[1])).isInstanceOf(AppException.class);
        }
        assertThatThrownBy(() -> catalog.normalize("UNKNOWN_KEY", "1")).isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ParameterErrorCode.PARAMETER_NOT_FOUND);
    }

    @Test void snapshotRemainsUnchangedWhenItsInputsAndLaterConfigurationChange() {
        Map<String, String> values = new HashMap<>(Map.of("FEE_NEW", "0.08"));
        Map<String, Long> versions = new HashMap<>(Map.of("FEE_NEW", 3L));
        ParameterSnapshot oldOrder = new ParameterSnapshot(values, versions, Instant.EPOCH);
        values.put("FEE_NEW", "0.09"); versions.put("FEE_NEW", 4L);
        assertThat(oldOrder.value("FEE_NEW")).isEqualTo("0.08");
        assertThat(oldOrder.versions()).containsEntry("FEE_NEW", 3L);
        assertThatThrownBy(() -> oldOrder.values().put("FEE_NEW", "0.10")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> oldOrder.versions().put("FEE_NEW", 5L)).isInstanceOf(UnsupportedOperationException.class);
    }
}
