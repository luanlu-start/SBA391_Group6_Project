package com.fptpost.modules.reputation.service;

import com.fptpost.modules.catalog.dto.internal.ParameterSnapshot;
import com.fptpost.modules.identity.enums.CourierTier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Công thức điểm uy tín BR-R-072 và điều kiện hạng BR-R-073, đọc từ tham số PRM-074..090.
 * <p>
 * Mỗi chỉ số được làm mượt về TRUST_INIT/100 với {@value #PRIOR_WEIGHT} mẫu ảo, nên người giao mới hoặc chỉ số chưa có
 * dữ liệu bắt đầu từ điểm khởi tạo và một đơn đơn lẻ không làm điểm nhảy quá mạnh.
 */
public record TrustScorePolicy(BigDecimal initial, BigDecimal ontimeWeight, BigDecimal ratingWeight,
                               BigDecimal completionWeight, BigDecimal complaintWeight, BigDecimal restrictBelow,
                               BigDecimal reviewBelow, BigDecimal cancelPenalty, long cancelLimit, long cancelBanDays,
                               List<TierRule> tiers) {

    static final int PRIOR_WEIGHT = 5;
    private static final Pattern TIER = Pattern.compile("(?:≥|>=)\\s*([0-9]+(?:\\.[0-9]+)?)\\s+điểm\\s*&\\s*(?:≥|>=)\\s*([0-9]+)\\s+đơn");
    private static final Map<CourierTier, String> TIER_KEYS = Map.of(CourierTier.BRONZE, "TIER_BRONZE",
            CourierTier.SILVER, "TIER_SILVER", CourierTier.GOLD, "TIER_GOLD", CourierTier.DIAMOND, "TIER_DIAMOND");

    public record TierRule(CourierTier tier, BigDecimal minScore, long minOrders) {
    }

    /** Dữ liệu đầu vào của một người giao; {@code ratingAverage} và {@code delivered} có thể chưa có. */
    public record Signals(long completed, long cancellations, long lostComplaints, long ratingCount, BigDecimal ratingAverage,
                          long delivered, long onTime) {
    }

    public record Result(BigDecimal score, CourierTier tier, BigDecimal ontimeRate, BigDecimal ratingRate,
                         BigDecimal completionRate, BigDecimal cleanRate) {
    }

    public static TrustScorePolicy from(ParameterSnapshot parameters) {
        List<TierRule> tiers = new ArrayList<>();
        for (CourierTier tier : List.of(CourierTier.DIAMOND, CourierTier.GOLD, CourierTier.SILVER, CourierTier.BRONZE)) {
            Matcher matcher = TIER.matcher(parameters.value(TIER_KEYS.get(tier)));
            if (!matcher.find()) throw new IllegalStateException("Invalid tier condition " + TIER_KEYS.get(tier));
            tiers.add(new TierRule(tier, new BigDecimal(matcher.group(1)), Long.parseLong(matcher.group(2))));
        }
        return new TrustScorePolicy(parameters.decimal("TRUST_INIT"), parameters.decimal("W_ONTIME"), parameters.decimal("W_RATING"),
                parameters.decimal("W_COMPLETE"), parameters.decimal("W_COMPLAINT"), parameters.decimal("TRUST_RESTRICT"),
                parameters.decimal("TRUST_REVIEW"), parameters.decimal("CANCEL_PENALTY"), parameters.integer("CANCEL_LIMIT"),
                parameters.integer("CANCEL_BAN"), List.copyOf(tiers));
    }

    public Result evaluate(Signals signals) {
        double prior = initial.doubleValue() / 100;
        double ontime = signals.delivered() > 0 ? smooth(signals.onTime(), signals.delivered(), prior) : prior;
        double rating = signals.ratingCount() > 0 && signals.ratingAverage() != null
                ? smooth((signals.ratingAverage().doubleValue() - 1) / 4 * signals.ratingCount(), signals.ratingCount(), prior) : prior;
        double completion = smooth(signals.completed(), signals.completed() + signals.cancellations(), prior);
        long handled = signals.completed() + signals.lostComplaints();
        double clean = smooth(Math.max(0, handled - signals.lostComplaints()), handled, prior);
        double raw = 100 * (ontimeWeight.doubleValue() * ontime + ratingWeight.doubleValue() * rating
                + completionWeight.doubleValue() * completion + complaintWeight.doubleValue() * clean)
                - cancelPenalty.doubleValue() * signals.cancellations();
        BigDecimal score = BigDecimal.valueOf(Math.max(0, Math.min(100, raw))).setScale(2, RoundingMode.HALF_UP);
        return new Result(score, tierFor(score, signals.completed()),
                signals.delivered() > 0 ? rate(ontime) : null, signals.ratingCount() > 0 ? rate(rating) : null,
                rate(completion), rate(clean));
    }

    public CourierTier tierFor(BigDecimal score, long completed) {
        for (TierRule rule : tiers) {
            if (score.compareTo(rule.minScore()) >= 0 && completed >= rule.minOrders()) return rule.tier();
        }
        return CourierTier.NEW;
    }

    /** Hạng kế tiếp và điều kiện cần đạt; null khi đã ở hạng cao nhất. */
    public TierRule next(CourierTier current) {
        TierRule next = null;
        for (TierRule rule : tiers) {
            if (rule.tier().ordinal() > current.ordinal()) next = rule;
        }
        return next;
    }

    private static double smooth(double successes, double total, double prior) {
        return (successes + prior * PRIOR_WEIGHT) / (total + PRIOR_WEIGHT);
    }

    private static BigDecimal rate(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
