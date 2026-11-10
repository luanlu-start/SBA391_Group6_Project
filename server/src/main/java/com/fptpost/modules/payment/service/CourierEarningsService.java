package com.fptpost.modules.payment.service;

import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.repository.CourierProfileRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.dto.CourierEarningPointDto;
import com.fptpost.modules.payment.dto.CourierEarningReceiptDto;
import com.fptpost.modules.payment.dto.CourierEarningsResponse;
import com.fptpost.modules.payment.dto.CourierStatsResponse;
import com.fptpost.modules.payment.model.Escrow;
import com.fptpost.modules.payment.model.EscrowStatus;
import com.fptpost.modules.payment.repository.EscrowRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CourierEarningsService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");

    private final OrderRepository orderRepository;
    private final EscrowRepository escrowRepository;
    private final CourierProfileRepository courierProfileRepository;

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

        List<Order> courierOrders = orderRepository.findByCourierIdOrderByCreatedAtDesc(userId);

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
            long gross = resolveOrderGrossAmount(order, escrowOpt.orElse(null));

            boolean isCompleted = order.getStatus() == OrderStatus.COMPLETED
                    || (escrowOpt.isPresent() && escrowOpt.get().getStatus() == EscrowStatus.RELEASED);

            boolean isPendingDelivery = !isCompleted && order.getStatus() != OrderStatus.CANCELLED;

            if (isPendingDelivery) {
                pendingEscrowAmount += gross;
            }

            if (!isCompleted || gross <= 0) {
                continue;
            }

            Instant completedInstant = order.getEndedAt() != null
                    ? order.getEndedAt()
                    : (order.getCreatedAt() != null ? order.getCreatedAt() : Instant.now());
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
                        .productName(order.getProductName())
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

        List<Order> courierOrders = orderRepository.findByCourierIdOrderByCreatedAtDesc(userId);
        int totalAssigned = courierOrders.size();
        int completedFromOrders = (int) courierOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED)
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

        double scoreVal = trustScore.doubleValue();
        double onTimeRate = effectiveCompleted > 0
                ? roundOneDecimal(Math.min(100.0, Math.max(80.0, 75.0 + scoreVal * 0.25)))
                : 100.0;
        double ratingAvg = effectiveCompleted > 0
                ? roundOneDecimal(Math.min(5.0, Math.max(3.8, 3.5 + (scoreVal / 100.0) * 1.5)))
                : 5.0;
        double complaintRate = effectiveTotal > 0
                ? roundOneDecimal(Math.max(0.0, (cancelledOrders * 50.0) / effectiveTotal))
                : 0.0;

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

    private TierRequirement resolveNextTierRequirement(CourierTier currentTier) {
        return switch (currentTier) {
            case NEW -> new TierRequirement(CourierTier.BRONZE, 10, BigDecimal.valueOf(65.0));
            case BRONZE -> new TierRequirement(CourierTier.SILVER, 30, BigDecimal.valueOf(75.0));
            case SILVER -> new TierRequirement(CourierTier.GOLD, 80, BigDecimal.valueOf(85.0));
            case GOLD, DIAMOND -> new TierRequirement(CourierTier.DIAMOND, 200, BigDecimal.valueOf(92.0));
        };
    }

    private record TierRequirement(CourierTier tier, int minOrders, BigDecimal minScore) {
    }
}
