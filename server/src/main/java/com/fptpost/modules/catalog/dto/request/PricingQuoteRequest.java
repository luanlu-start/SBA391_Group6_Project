package com.fptpost.modules.catalog.dto.request;

import com.fptpost.modules.catalog.enums.CourierTier;
import com.fptpost.modules.catalog.enums.QuoteUrgency;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PricingQuoteRequest {
    @NotNull @DecimalMin("0") @Digits(integer = 9, fraction = 3)
    private BigDecimal distanceKm;
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 3)
    private BigDecimal weightKg;
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 3)
    private BigDecimal longestSideCm;
    @NotNull @Builder.Default
    private QuoteUrgency urgency = QuoteUrgency.NORMAL;
    @NotNull @PositiveOrZero
    private Long declaredValue;
    @NotNull @Builder.Default
    private Boolean insured = false;
    @NotNull @Builder.Default
    private CourierTier courierTier = CourierTier.NEW;
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 2, fraction = 4)
    @Builder.Default
    private BigDecimal agreedFareRatio = BigDecimal.ONE;
    @NotNull @PositiveOrZero @Builder.Default
    private Long promotionDiscount = 0L;
}
