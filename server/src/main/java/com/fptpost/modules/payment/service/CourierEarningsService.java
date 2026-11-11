package com.fptpost.modules.payment.service;

import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.repository.CourierProfileRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderLeg;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.dto.CourierEarningPointDto;
import com.fptpost.modules.payment.dto.CourierEarningReceiptDto;
import com.fptpost.modules.payment.dto.CourierEarningsResponse;
import com.fptpost.modules.payment.dto.CourierStatsResponse;
import com.fptpost.modules.payment.model.Escrow;
import com.fptpost.modules.payment.model.EscrowStatus;
import com.fptpost.modules.payment.repository.EscrowRepository;
import com.fptpost.modules.reputation.model.TrustScore;
import com.fptpost.modules.reputation.repository.TrustScoreRepository;
import com.fptpost.modules.reputation.service.TrustScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CourierEarningsService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");

    private final OrderRepository orderRepository;
    private final EscrowRepository escrowRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final ParameterService parameterService;
    private final TrustScoreRepository trustScoreRepository;

    @Transactional(readOnly = true)
    public CourierEarningsResponse getMyEarnings(Long userId, String period, LocalDate from, LocalDate to) {
        String normalizedPeriod = normalizePeriod(period);
        LocalDate today = LocalDate.now(VN_ZONE);

        LocalDate resolvedTo = to != null ? to : today;
        LocalDate resolvedFrom;
        if (from != null) {
            resolvedFrom = from;
        } else if ("DAY".equals(normalizedPeriod)) {
            resolvedFrom = resolvedTo.minusDays(6);
        } else if ("WEEK".equals(normalizedPeriod)) {
            resolvedFrom = resolvedTo.minusDays(27);
        } else {
            resolvedFrom = resolvedTo.withDayOfMonth(1);
        }

        if (resolvedFrom.isAfter(resolvedTo)) {
            LocalDate tmp = resolvedFrom;
            resolvedFrom = resolvedTo;
            resolvedTo = tmp;
        }

        CourierProfile profile = courierProfileRepository.findByUserId(userId).orElse(null);
        CourierTier tier = profile != null && profile.getTier() != null ? profile.getTier() : CourierTier.NEW;
        BigDecimal commissionRate = tier.getCommissionRate();

        List<Order> courierOrders = orderRepository.findByCourierIdOrLegCourierIdOrderByCreatedAtDesc(userId);

        long grossEarnings = 0L;
        long totalCommission = 0L;
        long deductionAmount = 0L;
        long todayNetEarnings = 0L;
        long pendingEscrowAmount = 0L;
        int completedInPeriodCount = 0;

        List<CourierEarningReceiptDto> receipts = new ArrayList<>();
        Map<LocalDate, CourierEarningPointDto> dailyBuckets = new LinkedHashMap<>();

        LocalDate cursor = resolvedFrom;
        while (!cursor.isAfter(resolvedTo)) {
            dailyBuckets.put(cursor, CourierEarningPointDto.builder()
                    .label(cursor.format(SHORT_DATE_FMT))
                    .date(cursor)
                    .orderCount(0)
                    .grossAmount(0L)
                    .commissionAmount(0L)
                    .netAmount(0L)
                    .build());
            cursor = cursor.plusDays(1);
        }

        for (Order order : courierOrders) {
            Optional<Escrow> escrowOpt = escrowRepository.findByOrderId(order.getId());

            boolean isRelay = Boolean.TRUE.equals(order.getIsRelay()) || (order.getLegs() != null && !order.getLegs().isEmpty());
            List<OrderLeg> myLegs = (isRelay && order.getLegs() != null)
                    ? order.getLegs().stream().filter(l -> Objects.equals(l.getCourierId(), userId)).toList()
                    : List.of();

            long gross;
            boolean isCompleted;
            Instant completedInstant = null;
            String receiptProductName = order.getProductName();

            if (isRelay && !myLegs.isEmpty()) {
                // Với đơn tiếp sức: tính thu nhập theo từng chặng mà tài xế này đảm nhận (BR-R-047, BR-R-048)
                gross = myLegs.stream()
                        .map(l -> l.getLegFee() != null ? l.getLegFee().longValue() : 0L)
                        .reduce(0L, Long::sum);

                // Nếu tất cả các chặng của tài xế này đã COMPLETED thì coi như hoàn tất phần việc của tài xế
                isCompleted = myLegs.stream().allMatch(l -> l.getStatus() == OrderStatus.COMPLETED);
                if (isCompleted) {
                    completedInstant = myLegs.stream()
                            .map(OrderLeg::getHandoverAt)
                            .filter(Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .orElse(order.getEndedAt() != null ? order.getEndedAt() : order.getCreatedAt());
                }
                String legStr = myLegs.stream().map(l -> "Chặng " + l.getLegNo()).reduce((a, b) -> a + ", " + b).orElse("");
                receiptProductName = (order.getProductName() != null ? order.getProductName() : "Đơn hàng tiếp sức")
                        + (legStr.isEmpty() ? "" : " (" + legStr + ")");
            } else {
                gross = resolveOrderGrossAmount(order, escrowOpt.orElse(null));
                isCompleted = order.getStatus() == OrderStatus.COMPLETED
                        || (escrowOpt.isPresent() && escrowOpt.get().getStatus() == EscrowStatus.RELEASED);
                if (isCompleted) {
                    completedInstant = order.getEndedAt() != null
                            ? order.getEndedAt()
                            : (order.getCreatedAt() != null ? order.getCreatedAt() : Instant.now());
                }
            }

            boolean isPendingDelivery = !isCompleted && order.getStatus() != OrderStatus.CANCELLED;

            if (isPendingDelivery) {
                pendingEscrowAmount += gross;
            }

            if (!isCompleted || gross <= 0) {
                continue;
            }

            if (completedInstant == null) {
                completedInstant = Instant.now();
            }
            LocalDate completedDate = completedInstant.atZone(VN_ZONE).toLocalDate();

            long commission = BigDecimal.valueOf(gross)
                    .multiply(commissionRate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
            long net = Math.max(0L, gross - commission);

            if (completedDate.equals(today)) {
                todayNetEarnings += net;
            }

            if (!completedDate.isBefore(resolvedFrom) && !completedDate.isAfter(resolvedTo)) {
                completedInPeriodCount++;
                grossEarnings += gross;
                totalCommission += commission;

                receipts.add(CourierEarningReceiptDto.builder()
                        .orderId(order.getId())
                        .orderCode(order.getOrderCode())
                        .productName(receiptProductName)
                        .grossAmount(gross)
                        .commissionRate(commissionRate)
                        .commissionAmount(commission)
                        .netAmount(net)
                        .status("SETTLED")
                        .completedAt(completedInstant)
                        .build());

                CourierEarningPointDto bucket = dailyBuckets.get(completedDate);
                if (bucket != null) {
                    bucket.setOrderCount(bucket.getOrderCount() + 1);
                    bucket.setGrossAmount(bucket.getGrossAmount() + gross);
                    bucket.setCommissionAmount(bucket.getCommissionAmount() + commission);
                    bucket.setNetAmount(bucket.getNetAmount() + net);
                }
            }
        }

        long netEarnings = Math.max(0L, grossEarnings - totalCommission - deductionAmount);

        return CourierEarningsResponse.builder()
                .period(normalizedPeriod)
                .fromDate(resolvedFrom)
                .toDate(resolvedTo)
                .completedOrdersCount(completedInPeriodCount)
                .grossEarnings(grossEarnings)
                .commissionRate(commissionRate)
                .commissionAmount(totalCommission)
                .deductionAmount(deductionAmount)
                .netEarnings(netEarnings)
                .todayNetEarnings(todayNetEarnings)
                .pendingEscrowAmount(pendingEscrowAmount)
                .chartPoints(new ArrayList<>(dailyBuckets.values()))
                .receipts(receipts)
                .build();
    }

    @Transactional(readOnly = true)
    public CourierStatsResponse getMyStats(Long userId) {
        CourierProfile profile = courierProfileRepository.findByUserId(userId).orElse(null);
        CourierTier currentTier = profile != null && profile.getTier() != null ? profile.getTier() : CourierTier.NEW;
        BigDecimal trustScore = profile != null && profile.getTrustScore() != null
                ? profile.getTrustScore()
                : BigDecimal.valueOf(60.0);

        List<Order> courierOrders = orderRepository.findByCourierIdOrLegCourierIdOrderByCreatedAtDesc(userId);
        int totalAssigned = courierOrders.size();
        int completedFromOrders = (int) courierOrders.stream()
                .filter(o -> {
                    if (o.getLegs() != null && !o.getLegs().isEmpty()) {
                        return o.getLegs().stream().anyMatch(l -> Objects.equals(l.getCourierId(), userId) && l.getStatus() == OrderStatus.COMPLETED);
                    }
                    return o.getStatus() == OrderStatus.COMPLETED;
                })
                .count();
        int cancelledOrders = (int) courierOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED)
                .count();
        int inDeliveryOrders = Math.max(0, totalAssigned - completedFromOrders - cancelledOrders);

        int profileCompleted = profile != null ? profile.getCompletedOrders() : 0;
        int effectiveCompleted = Math.max(profileCompleted, completedFromOrders);
        int effectiveTotal = Math.max(totalAssigned, effectiveCompleted + cancelledOrders);

        double completionRate = effectiveTotal > 0
                ? roundOneDecimal((effectiveCompleted * 100.0) / effectiveTotal)
                : 100.0;

        // Chỉ số hiệu suất lấy từ điểm uy tín đã tính (UC-49); chưa có dữ liệu thì để trống thay vì ước lượng.
        TrustScore score = trustScoreRepository.findById(userId).orElse(null);
        Double onTimeRate = score != null && score.getOntimeRate() != null
                ? roundOneDecimal(score.getOntimeRate().doubleValue() * 100) : null;
        Double ratingAvg = score != null && score.getRatingCount() > 0 && score.getRatingAverage() != null
                ? roundOneDecimal(score.getRatingAverage().doubleValue()) : null;
        long lostComplaints = score == null ? 0 : score.getLostComplaints();
        long handled = effectiveCompleted + lostComplaints;
        double complaintRate = handled > 0 ? roundOneDecimal(lostComplaints * 100.0 / handled) : 0.0;

        TierRequirement nextReq = resolveNextTierRequirement(currentTier);

        int ordersNeeded = Math.max(0, nextReq.minOrders - effectiveCompleted);
        BigDecimal scoreNeeded = nextReq.minScore.subtract(trustScore).max(BigDecimal.ZERO);

        int ordersProgressPercent = nextReq.minOrders > 0
                ? Math.min(100, (int) Math.round((effectiveCompleted * 100.0) / nextReq.minOrders))
                : 100;
        int scoreProgressPercent = nextReq.minScore.compareTo(BigDecimal.ZERO) > 0
                ? Math.min(100, trustScore.multiply(BigDecimal.valueOf(100))
                        .divide(nextReq.minScore, 0, RoundingMode.HALF_UP)
                        .intValue())
                : 100;

        return CourierStatsResponse.builder()
                .userId(userId)
                .currentTier(currentTier.name())
                .currentTierDisplayName(currentTier.getDisplayName())
                .commissionRate(currentTier.getCommissionRate())
                .trustScore(trustScore)
                .totalAssignedOrders(effectiveTotal)
                .completedOrders(effectiveCompleted)
                .cancelledOrders(cancelledOrders)
                .inDeliveryOrders(inDeliveryOrders)
                .completionRate(completionRate)
                .onTimeRate(onTimeRate)
                .ratingAvg(ratingAvg)
                .complaintRate(complaintRate)
                .nextTier(nextReq.tier.name())
                .nextTierDisplayName(nextReq.tier.getDisplayName())
                .nextTierCommissionRate(nextReq.tier.getCommissionRate())
                .nextTierMinOrders(nextReq.minOrders)
                .nextTierMinScore(nextReq.minScore)
                .ordersNeededForNextTier(ordersNeeded)
                .scoreNeededForNextTier(scoreNeeded)
                .ordersProgressPercent(ordersProgressPercent)
                .scoreProgressPercent(scoreProgressPercent)
                .build();
    }

    private long resolveOrderGrossAmount(Order order, Escrow escrow) {
        if (escrow != null && escrow.getAmount() != null && escrow.getAmount() > 0) {
            return escrow.getAmount();
        }
        if (order.getPriceSuggested() != null && order.getPriceSuggested().compareTo(BigDecimal.ZERO) > 0) {
            return order.getPriceSuggested().setScale(0, RoundingMode.HALF_UP).longValue();
        }
        return 0L;
    }

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return "MONTH";
        }
        String upper = period.trim().toUpperCase(Locale.ROOT);
        if ("DAY".equals(upper) || "WEEK".equals(upper) || "MONTH".equals(upper)) {
            return upper;
        }
        return "MONTH";
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** Điều kiện hạng kế tiếp theo tham số TIER_* (PRM-079..082), cùng nguồn với job tính hạng UC-49. */
    private TierRequirement resolveNextTierRequirement(CourierTier currentTier) {
        TrustScorePolicy policy = TrustScorePolicy.from(parameterService.getSnapshot());
        TrustScorePolicy.TierRule rule = Optional.ofNullable(policy.next(currentTier))
                .orElseGet(() -> policy.tiers().getFirst());
        return new TierRequirement(rule.tier(), (int) rule.minOrders(), rule.minScore());
    }

    private record TierRequirement(CourierTier tier, int minOrders, BigDecimal minScore) {
    }
}
