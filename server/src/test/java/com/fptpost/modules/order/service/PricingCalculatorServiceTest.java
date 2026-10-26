package com.fptpost.modules.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.modules.order.dto.PricingEstimateRequest;
import com.fptpost.modules.order.dto.PricingEstimateResponse;
import com.fptpost.modules.order.model.DeliveryUrgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingCalculatorServiceTest {

    private PricingCalculatorService service;

    @BeforeEach
    void setUp() {
        service = new PricingCalculatorService(new ObjectMapper());
    }

    @Test
    @DisplayName("Cự ly ≤ 2km: Cước cơ bản 10.000đ, không phụ phí cân nặng, không bảo hiểm")
    void estimatePrice_within2Km_shouldReturnBasePrice() {
        var req = PricingEstimateRequest.builder()
                .distanceKm(new BigDecimal("1.5"))
                .weightKg(new BigDecimal("1.0"))
                .urgency(DeliveryUrgency.STANDARD)
                .build();

        PricingEstimateResponse res = service.estimatePrice(req);

        assertThat(res.getBaseDistanceFee()).isEqualByComparingTo("10000");
        assertThat(res.getWeightSurcharge()).isEqualByComparingTo("0");
        assertThat(res.getInsuranceFee()).isEqualByComparingTo("0");
        assertThat(res.getTotalEstimatedPrice()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("Cự ly 10km (2km - 50km): Cước cơ bản 10.000 + 8 * 3.500 = 38.000đ")
    void estimatePrice_between2And50Km_shouldCalculateCorrectly() {
        var req = PricingEstimateRequest.builder()
                .distanceKm(new BigDecimal("10.0"))
                .weightKg(new BigDecimal("1.5"))
                .urgency(DeliveryUrgency.STANDARD)
                .build();

        PricingEstimateResponse res = service.estimatePrice(req);

        // 10000 + (10 - 2) * 3500 = 38000
        assertThat(res.getBaseDistanceFee()).isEqualByComparingTo("38000");
        assertThat(res.getTotalEstimatedPrice()).isEqualByComparingTo("38000");
    }

    @Test
    @DisplayName("Cự ly 60km (>50km): 10.000 + 48*3.500 + 10*2.000 = 198.000đ")
    void estimatePrice_over50Km_shouldCalculateCorrectly() {
        var req = PricingEstimateRequest.builder()
                .distanceKm(new BigDecimal("60.0"))
                .weightKg(new BigDecimal("1.0"))
                .urgency(DeliveryUrgency.STANDARD)
                .build();

        PricingEstimateResponse res = service.estimatePrice(req);

        // 10000 + 48 * 3500 + 10 * 2000 = 10000 + 168000 + 20000 = 198000
        assertThat(res.getBaseDistanceFee()).isEqualByComparingTo("198000");
        assertThat(res.getTotalEstimatedPrice()).isEqualByComparingTo("198000");
    }

    @Test
    @DisplayName("Khối lượng 3.5kg: Phụ phí 5.000đ; Khai báo 2.000.000đ: Bảo hiểm 0.5% = 10.000đ")
    void estimatePrice_withWeightAndInsurance_shouldSumCorrectly() {
        var req = PricingEstimateRequest.builder()
                .distanceKm(new BigDecimal("2.0")) // 10000
                .weightKg(new BigDecimal("3.5"))   // +5000 (2 - 5kg)
                .declaredValue(new BigDecimal("2000000")) // +10000 (0.5%)
                .urgency(DeliveryUrgency.STANDARD)
                .build();

        PricingEstimateResponse res = service.estimatePrice(req);

        assertThat(res.getBaseDistanceFee()).isEqualByComparingTo("10000");
        assertThat(res.getWeightSurcharge()).isEqualByComparingTo("5000");
        assertThat(res.getInsuranceFee()).isEqualByComparingTo("10000");
        assertThat(res.getSubtotalBeforeUrgency()).isEqualByComparingTo("25000");
        assertThat(res.getTotalEstimatedPrice()).isEqualByComparingTo("25000");
    }

    @Test
    @DisplayName("Phí bảo hiểm vượt mức trần 50.000đ: Phải được giới hạn ở 50.000đ")
    void estimatePrice_insuranceCap_shouldBe50kMax() {
        var req = PricingEstimateRequest.builder()
                .distanceKm(new BigDecimal("2.0"))
                .weightKg(new BigDecimal("1.0"))
                .declaredValue(new BigDecimal("20000000")) // 0.5% = 100,000 > 50,000
                .urgency(DeliveryUrgency.STANDARD)
                .build();

        PricingEstimateResponse res = service.estimatePrice(req);

        assertThat(res.getInsuranceFee()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("Gói hỏa tốc 2 giờ: Hệ số x1.5 và làm tròn lên 500đ")
    void estimatePrice_express2H_shouldApplyMultiplierAndRounding() {
        var req = PricingEstimateRequest.builder()
                .distanceKm(new BigDecimal("3.0")) // 10000 + 1 * 3500 = 13500
                .weightKg(new BigDecimal("1.0"))   // 0
                .urgency(DeliveryUrgency.EXPRESS_2H) // x1.5 -> 13500 * 1.5 = 20250 -> round 500 -> 20500
                .build();

        PricingEstimateResponse res = service.estimatePrice(req);

        assertThat(res.getBaseDistanceFee()).isEqualByComparingTo("13500");
        assertThat(res.getUrgencyMultiplier()).isEqualByComparingTo("1.5");
        // 13500 * 1.5 = 20250. Ceil to nearest 500 is 20500
        assertThat(res.getTotalEstimatedPrice()).isEqualByComparingTo("20500");
    }

    @Test
    @DisplayName("Tính khoảng cách theo tọa độ (Da Nang -> Hoi An ~25km thẳng, x1.3 đường bộ ~32km)")
    void estimatePrice_withCoordinates_shouldCalculateDistance() {
        var req = PricingEstimateRequest.builder()
                .pickupLat(new BigDecimal("16.0544"))
                .pickupLng(new BigDecimal("108.2022"))
                .deliveryLat(new BigDecimal("15.8801"))
                .deliveryLng(new BigDecimal("108.3380"))
                .weightKg(new BigDecimal("1.0"))
                .urgency(DeliveryUrgency.STANDARD)
                .build();

        PricingEstimateResponse res = service.estimatePrice(req);

        assertThat(res.getDistanceKm()).isNotNull();
        assertThat(res.getDistanceKm().doubleValue()).isGreaterThan(20.0).isLessThan(45.0);
        assertThat(res.getTotalEstimatedPrice().doubleValue()).isGreaterThan(50000.0);
        assertThat(res.getCalculationMethod()).isIn("OSRM_ROUTING", "HAVERSINE_FALLBACK");
    }
}
