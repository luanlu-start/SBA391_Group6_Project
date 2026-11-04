package com.fptpost.modules.catalog.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.catalog.dto.internal.ParameterSnapshot;
import com.fptpost.modules.catalog.dto.request.PricingQuoteRequest;
import com.fptpost.modules.catalog.dto.response.PricingSimulationResponse;
import com.fptpost.modules.catalog.exception.PricingSimulationErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Development reference for Excel sheet 07. Replace this adapter with the owner's Pricing facade. */
public class ReferencePricingSimulation implements PricingSimulationPort {
    @Override
    public PricingSimulationResponse simulate(PricingQuoteRequest q, ParameterSnapshot p) {
        try {
            if (q.getDistanceKm().signum() < 0 || q.getWeightKg().signum() <= 0 || q.getLongestSideCm().signum() <= 0
                    || q.getWeightKg().compareTo(p.decimal("MAX_WEIGHT")) > 0
                    || q.getLongestSideCm().compareTo(p.decimal("MAX_SIDE")) > 0
                    || q.getDeclaredValue() < 0 || q.getDeclaredValue() > p.integer("MAX_DECLARED")
                    || q.getPromotionDiscount() < 0
                    || q.getAgreedFareRatio().compareTo(p.decimal("BID_MIN")) < 0
                    || q.getAgreedFareRatio().compareTo(p.decimal("BID_MAX")) > 0) throw invalid();
            BigDecimal distance = q.getDistanceKm();
            BigDecimal baseKm = p.decimal("BASE_KM"), longKm = p.decimal("LONG_KM");
            BigDecimal distanceFee = distance.min(longKm).subtract(baseKm).max(BigDecimal.ZERO)
                    .multiply(p.decimal("PER_KM"))
                    .add(distance.subtract(longKm).max(BigDecimal.ZERO).multiply(p.decimal("PER_KM_LONG")));
            long base = p.integer("BASE_FARE");
            long weight = q.getWeightKg().compareTo(BigDecimal.valueOf(2)) <= 0 ? 0
                    : q.getWeightKg().compareTo(BigDecimal.valueOf(5)) <= 0 ? p.integer("W_FEE_2")
                    : q.getWeightKg().compareTo(BigDecimal.valueOf(10)) <= 0 ? p.integer("W_FEE_3") : p.integer("W_FEE_4");
            long size = q.getLongestSideCm().compareTo(p.decimal("SIZE_S")) <= 0 ? 0
                    : q.getLongestSideCm().compareTo(p.decimal("SIZE_M")) <= 0 ? p.integer("SIZE_FEE_M") : p.integer("SIZE_FEE_L");
            BigDecimal urgency = switch (q.getUrgency()) {
                case NORMAL -> BigDecimal.ONE;
                case SAMEDAY -> p.decimal("SAMEDAY_MULT");
                case URGENT -> p.decimal("URGENT_MULT");
            };
            BigDecimal unrounded = BigDecimal.valueOf(Math.addExact(base, Math.addExact(weight, size)))
                    .add(distanceFee).multiply(urgency).setScale(2, RoundingMode.HALF_UP);
            long step = p.integer("ROUND_FEE");
            long suggested = Math.max(p.integer("MIN_FARE"), ceilingStep(unrounded, step));
            long bidMin = ceilingStep(BigDecimal.valueOf(suggested).multiply(p.decimal("BID_MIN")), step);
            long bidMax = ceilingStep(BigDecimal.valueOf(suggested).multiply(p.decimal("BID_MAX")), step);
            long agreed = ceilingStep(BigDecimal.valueOf(suggested).multiply(q.getAgreedFareRatio())
                    .setScale(2, RoundingMode.HALF_UP), step);
            BigDecimal rate = p.decimal(switch (q.getCourierTier()) {
                case NEW -> "FEE_NEW";
                case BRONZE -> "FEE_BRONZE";
                case SILVER -> "FEE_SILVER";
                case GOLD -> "FEE_GOLD";
                case DIAMOND -> "FEE_DIAMOND";
            });
            long commission = floorRate(agreed, rate);
            long withholding = floorRate(agreed, p.decimal("WITHHOLD"));
            long insurance = Boolean.TRUE.equals(q.getInsured()) ? Math.max(p.integer("INS_MIN"),
                    BigDecimal.valueOf(q.getDeclaredValue()).multiply(p.decimal("INS_RATE"))
                            .setScale(0, RoundingMode.HALF_UP).longValueExact()) : 0;
            long discount = Math.min(q.getPromotionDiscount(), agreed);
            long fundShare = floorRate(commission, p.decimal("FUND_SHARE"));
            long fund = Math.addExact(insurance, fundShare);
            long net = Math.subtractExact(Math.subtractExact(agreed, commission), withholding);
            long platformBeforeDiscount = Math.subtractExact(commission, fundShare);
            long platformNet = Math.subtractExact(platformBeforeDiscount, discount);
            long sender = Math.subtractExact(Math.addExact(agreed, insurance), discount);
            // Discount is already reflected in platformNet; compare actual incoming/outgoing amounts once.
            long difference = Math.subtractExact(sender,
                    Math.addExact(Math.addExact(net, withholding), Math.addExact(platformNet, fund)));
            return PricingSimulationResponse.builder().suggestedFare(suggested).baseFare(base).distanceFee(distanceFee)
                    .weightFee(weight).sizeFee(size).urgencyMultiplier(urgency).bidMinimum(bidMin).bidMaximum(bidMax)
                    .agreedFare(agreed).commissionRate(rate).commissionAmount(commission).withholdingAmount(withholding)
                    .insuranceFee(insurance).promotionDiscount(discount).guaranteeFundAmount(fund).courierNet(net)
                    .platformNet(platformNet).senderTotal(sender).balanceDifference(difference)
                    .parameterVersions(p.versions()).mode("REFERENCE").build();
        } catch (ArithmeticException exception) {
            throw invalid();
        }
    }

    private static long ceilingStep(BigDecimal value, long step) {
        return value.divide(BigDecimal.valueOf(step), 0, RoundingMode.CEILING)
                .multiply(BigDecimal.valueOf(step)).longValueExact();
    }
    private static long floorRate(long amount, BigDecimal rate) {
        return BigDecimal.valueOf(amount).multiply(rate).setScale(0, RoundingMode.DOWN).longValueExact();
    }
    private static AppException invalid() { return new AppException(PricingSimulationErrorCode.QUOTE_INVALID); }
}
