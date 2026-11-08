package com.fptpost.modules.reputation.service;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.catalog.dto.internal.ParameterSnapshot;
import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.dispute.dto.response.ComplaintPerson;
import com.fptpost.modules.dispute.enums.ComplaintOutcome;
import com.fptpost.modules.dispute.repository.ComplaintDirectory;
import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.CourierProfileRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.reputation.dto.request.RestrictCourierRequest;
import com.fptpost.modules.reputation.dto.response.CourierTrustResponse;
import com.fptpost.modules.reputation.dto.response.MyTrustResponse;
import com.fptpost.modules.reputation.enums.TrustScoreReason;
import com.fptpost.modules.reputation.exception.TrustErrorCode;
import com.fptpost.modules.reputation.model.CourierCancellation;
import com.fptpost.modules.reputation.model.TrustScore;
import com.fptpost.modules.reputation.model.TrustScoreHistory;
import com.fptpost.modules.reputation.port.CourierRatingSource;
import com.fptpost.modules.reputation.port.DeliveryPunctualitySource;
import com.fptpost.modules.reputation.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UC-49: tính điểm uy tín và hạng người giao. Điểm được tính lại sau mỗi khiếu nại kết thúc, mỗi lần hủy đơn,
 * khi phân hệ đánh giá hoặc giao hàng gọi {@link #recalculate}, và toàn bộ hằng đêm.
 */
@Service
@RequiredArgsConstructor
public class TrustScoreService {

    /** Số lần hủy trong cửa sổ này bị trừ điểm và tính vào tỷ lệ hoàn thành. */
    static final Duration SCORE_WINDOW = Duration.ofDays(90);
    /** Cửa sổ đếm số lần hủy để tạm khóa nhận đơn (BR-R-075). */
    static final Duration BAN_WINDOW = Duration.ofDays(30);
    private static final int BATCH = 200;
    private static final List<ComplaintOutcome> LOST = List.of(ComplaintOutcome.SENDER_FAVOR, ComplaintOutcome.SPLIT);
    private static final Map<CourierTier, String> FEE_KEYS = Map.of(CourierTier.NEW, "FEE_NEW", CourierTier.BRONZE, "FEE_BRONZE",
            CourierTier.SILVER, "FEE_SILVER", CourierTier.GOLD, "FEE_GOLD", CourierTier.DIAMOND, "FEE_DIAMOND");

    private final CourierProfileRepository profiles;
    private final TrustScoreRepository scores;
    private final TrustScoreHistoryRepository history;
    private final CourierCancellationRepository cancellations;
    private final ReputationSignalRepository signals;
    private final ObjectProvider<CourierRatingSource> ratingSource;
    private final ObjectProvider<DeliveryPunctualitySource> punctualitySource;
    private final ParameterService parameterService;
    private final ComplaintDirectory directory;
    private final NotificationPublisher notifications;
    private final UserRepository users;
    private final IdentityUserAccess userAccess;
    private final AuditOutboxService audit;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    /** Tính lại điểm một người giao; phân hệ đánh giá (UC-47) và xác nhận giao hàng (UC-28) gọi sau khi ghi dữ liệu. */
    @Transactional
    public void recalculate(Long courierId, TrustScoreReason reason) {
        recalculate(List.of(courierId), reason, null);
    }

    /** Dùng khi được gọi sau khi giao dịch khác đã commit, ví dụ từ sự kiện đóng khiếu nại. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recalculateSeparately(Long courierId, TrustScoreReason reason) {
        recalculate(List.of(courierId), reason, null);
    }

    /** Ghi nhận người giao hủy đơn sau khi đã nhận (BR-R-075); gọi lặp cho cùng đơn không trừ điểm hai lần. */
    @Transactional
    public void recordCourierCancellation(Long courierId, Long orderId) {
        if (!cancellations.existsByCourierIdAndOrderId(courierId, orderId)) {
            cancellations.saveAndFlush(CourierCancellation.builder().courierId(courierId).orderId(orderId).cancelledAt(clock.instant()).build());
        }
        recalculate(List.of(courierId), TrustScoreReason.CANCELLATION, "Hủy đơn #" + orderId + " sau khi đã nhận");
    }

    /** Tính lại toàn bộ người giao theo lô, mỗi lô một giao dịch. */
    public int recalculateAll(TrustScoreReason reason) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        int total = 0;
        int page = 0;
        while (true) {
            List<Long> ids = profiles.findAll(PageRequest.of(page++, BATCH, Sort.by("userId"))).map(CourierProfile::getUserId).getContent();
            if (ids.isEmpty()) return total;
            transaction.executeWithoutResult(status -> recalculate(ids, reason, null));
            total += ids.size();
        }
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public int recalculateAllNow() {
        int total = recalculateAll(TrustScoreReason.ADMIN);
        audit.enqueue("TRUST_SCORES_RECALCULATED", "TRUST_SCORE", "ALL", Map.of(), Map.of("couriers", total));
        return total;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public MyTrustResponse mine(UUID actorId) {
        User actor = userAccess.read(actorId);
        CourierProfile profile = profiles.findByUserId(actor.getId())
                .orElseThrow(() -> new AppException(TrustErrorCode.COURIER_PROFILE_MISSING));
        ParameterSnapshot parameters = parameterService.getSnapshot();
        TrustScorePolicy policy = TrustScorePolicy.from(parameters);
        Instant now = clock.instant();
        Inputs inputs = inputs(List.of(profile.getUserId()), now).get(profile.getUserId());
        TrustScorePolicy.Result result = policy.evaluate(inputs.signals());
        TrustScore stored = scores.findById(profile.getUserId()).orElse(null);
        BigDecimal score = stored == null ? result.score() : stored.getScore();
        CourierTier tier = stored == null ? result.tier() : stored.getTier();
        TrustScorePolicy.TierRule next = policy.next(tier);
        List<MyTrustResponse.Component> components = List.of(
                new MyTrustResponse.Component("ONTIME", policy.ontimeWeight(), rateOrInitial(result.ontimeRate(), policy), result.ontimeRate() != null),
                new MyTrustResponse.Component("RATING", policy.ratingWeight(), rateOrInitial(result.ratingRate(), policy), result.ratingRate() != null),
                new MyTrustResponse.Component("COMPLETION", policy.completionWeight(), result.completionRate(), true),
                new MyTrustResponse.Component("COMPLAINT", policy.complaintWeight(), result.cleanRate(), true));
        return new MyTrustResponse(score, tier, tier.getDisplayName(), inputs.signals().completed(), components,
                inputs.recentCancellations(), policy.cancelLimit(), profile.getRestrictedUntil(),
                score.compareTo(policy.restrictBelow()) < 0,
                next == null ? null : new MyTrustResponse.NextTier(next.tier(), next.tier().getDisplayName(), next.minScore(), next.minOrders()),
                new MyTrustResponse.Benefits(tier.getMaxConcurrentOrders(), tier.getMaxTotalDeclaredValue(), tier.isCanAcceptHighValue(),
                        parameters.decimal(FEE_KEYS.get(tier))),
                stored == null ? now : stored.getCalculatedAt(),
                history.findTop20ByCourierIdOrderByCalculatedAtDescIdDesc(profile.getUserId()).stream()
                        .map(item -> new MyTrustResponse.HistoryItem(item.getScore(), item.getPreviousScore(), item.getTier(),
                                item.getPreviousTier(), item.getReason(), item.getNote(), item.getCalculatedAt())).toList());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<CourierTrustResponse> couriers(String filter, int page, int size) {
        TrustScorePolicy policy = TrustScorePolicy.from(parameterService.getSnapshot());
        BigDecimal below = switch (filter == null ? "REVIEW" : filter.trim().toUpperCase(Locale.ROOT)) {
            case "REVIEW" -> policy.reviewBelow();
            case "RESTRICTED" -> policy.restrictBelow();
            case "ALL" -> null;
            default -> throw new AppException(TrustErrorCode.FILTER_INVALID);
        };
        Page<TrustScore> result = scores.ranked(below, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        List<Long> ids = result.getContent().stream().map(TrustScore::getCourierId).toList();
        Map<Long, ComplaintPerson> people = ids.isEmpty() ? Map.of() : directory.people(ids).stream()
                .collect(Collectors.toMap(ComplaintPerson::id, Function.identity()));
        Map<Long, CourierProfile> byId = profiles.findAllById(ids).stream()
                .collect(Collectors.toMap(CourierProfile::getUserId, Function.identity()));
        return PageResponse.from(result.map(item -> new CourierTrustResponse(people.get(item.getCourierId()), item.getScore(),
                item.getTier(), item.getCompletedOrders(), item.getLostComplaints(), item.getRecentCancellations(),
                item.getScore().compareTo(policy.reviewBelow()) < 0,
                Optional.ofNullable(byId.get(item.getCourierId())).map(CourierProfile::getRestrictedUntil).orElse(null),
                item.getCalculatedAt())));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CourierTrustResponse restrict(UUID courierPublicId, RestrictCourierRequest request) {
        User courier = users.findByPublicId(courierPublicId).orElseThrow(() -> new AppException(TrustErrorCode.COURIER_PROFILE_MISSING));
        CourierProfile profile = profiles.findByUserId(courier.getId())
                .orElseThrow(() -> new AppException(TrustErrorCode.COURIER_PROFILE_MISSING));
        Instant now = clock.instant();
        Instant until = now.plus(Duration.ofDays(request.getDays()));
        Instant before = profile.getRestrictedUntil();
        if (before == null || before.isBefore(until)) profile.setRestrictedUntil(until);
        String reason = request.getReason().strip();
        TrustScore current = scores.findById(courier.getId()).orElse(null);
        BigDecimal score = current == null ? profile.getTrustScore() : current.getScore();
        CourierTier tier = current == null ? profile.getTier() : current.getTier();
        history.save(TrustScoreHistory.builder().courierId(courier.getId()).score(score).previousScore(score).tier(tier).previousTier(tier)
                .reason(TrustScoreReason.ADMIN).note("Tạm khóa nhận đơn " + request.getDays() + " ngày: " + reason).calculatedAt(now).build());
        notifyCourier(courier.getId(), "restricted:" + now.toEpochMilli(), "Tài khoản tạm khóa nhận đơn",
                "Quản trị viên tạm khóa nhận đơn đến hết " + request.getDays() + " ngày tới. Lý do: " + reason);
        audit.enqueue("COURIER_RESTRICTED", "COURIER", courierPublicId.toString(),
                before == null ? Map.of() : Map.of("restrictedUntil", before.toString()),
                Map.of("restrictedUntil", profile.getRestrictedUntil().toString(), "reason", reason));
        Map<Long, ComplaintPerson> people = directory.people(List.of(courier.getId())).stream()
                .collect(Collectors.toMap(ComplaintPerson::id, Function.identity()));
        return new CourierTrustResponse(people.get(courier.getId()), score, tier,
                current == null ? profile.getCompletedOrders() : current.getCompletedOrders(),
                current == null ? 0 : current.getLostComplaints(), current == null ? 0 : current.getRecentCancellations(),
                true, profile.getRestrictedUntil(), current == null ? now : current.getCalculatedAt());
    }

    private void recalculate(Collection<Long> courierIds, TrustScoreReason reason, String note) {
        if (courierIds.isEmpty()) return;
        TrustScorePolicy policy = TrustScorePolicy.from(parameterService.getSnapshot());
        Instant now = clock.instant();
        Map<Long, Inputs> inputs = inputs(courierIds, now);
        for (CourierProfile profile : profiles.findAllById(courierIds)) {
            Inputs input = inputs.get(profile.getUserId());
            TrustScorePolicy.Result result = policy.evaluate(input.signals());
            TrustScore current = scores.findById(profile.getUserId()).orElse(null);
            BigDecimal previousScore = current == null ? profile.getTrustScore() : current.getScore();
            CourierTier previousTier = current == null ? profile.getTier() : current.getTier();
            if (current == null) current = TrustScore.builder().courierId(profile.getUserId()).build();

            current.setScore(result.score());
            current.setTier(result.tier());
            current.setOntimeRate(result.ontimeRate());
            current.setRatingAverage(input.signals().ratingAverage());
            current.setRatingCount(input.signals().ratingCount());
            current.setCompletionRate(result.completionRate());
            current.setComplaintRate(BigDecimal.ONE.subtract(result.cleanRate()));
            current.setCompletedOrders(input.signals().completed());
            current.setLostComplaints(input.signals().lostComplaints());
            current.setRecentCancellations(input.signals().cancellations());
            current.setCalculatedAt(now);

            profile.setTrustScore(result.score());
            profile.setTier(result.tier());
            profile.setCompletedOrders((int) Math.min(Integer.MAX_VALUE, input.signals().completed()));
            if (input.recentCancellations() > policy.cancelLimit() && input.lastCancellation() != null) {
                Instant until = input.lastCancellation().plus(Duration.ofDays(policy.cancelBanDays()));
                if (until.isAfter(now) && (profile.getRestrictedUntil() == null || profile.getRestrictedUntil().isBefore(until))) {
                    profile.setRestrictedUntil(until);
                    notifyCourier(profile.getUserId(), "cancel-ban:" + until.toEpochMilli(), "Tạm khóa nhận đơn do hủy nhiều lần",
                            "Bạn đã hủy " + input.recentCancellations() + " đơn sau khi nhận trong 30 ngày nên bị tạm khóa nhận đơn "
                                    + policy.cancelBanDays() + " ngày.");
                }
            }

            boolean changed = previousScore == null || previousScore.compareTo(result.score()) != 0 || previousTier != result.tier();
            if (changed) {
                history.save(TrustScoreHistory.builder().courierId(profile.getUserId()).score(result.score()).previousScore(previousScore)
                        .tier(result.tier()).previousTier(previousTier).reason(reason).note(note).calculatedAt(now).build());
            }
            if (previousTier != null && previousTier != result.tier()) {
                boolean up = result.tier().ordinal() > previousTier.ordinal();
                notifyCourier(profile.getUserId(), "tier:" + result.tier() + ":" + now.truncatedTo(ChronoUnit.DAYS).toEpochMilli(),
                        up ? "Chúc mừng bạn lên hạng " + result.tier().getDisplayName() : "Hạng người giao thay đổi",
                        up ? "Bạn được hưởng quyền lợi hạng " + result.tier().getDisplayName() + "."
                                : "Hạng của bạn chuyển xuống " + result.tier().getDisplayName() + " vì chưa giữ đủ điều kiện điểm và số đơn.");
            }
            flagThresholds(policy, profile, current, now);
            scores.save(current);
        }
    }

    private void flagThresholds(TrustScorePolicy policy, CourierProfile profile, TrustScore current, Instant now) {
        boolean restricted = current.getScore().compareTo(policy.restrictBelow()) < 0;
        if (restricted && current.getRestrictionNoticedAt() == null) {
            current.setRestrictionNoticedAt(now);
            notifyCourier(profile.getUserId(), "restrict:" + now.toEpochMilli(), "Điểm uy tín dưới ngưỡng",
                    "Điểm uy tín của bạn dưới " + policy.restrictBelow().stripTrailingZeros().toPlainString()
                            + " nên tạm thời chỉ nhận được đơn giá trị thấp.");
        } else if (!restricted) {
            current.setRestrictionNoticedAt(null);
        }
        boolean review = current.getScore().compareTo(policy.reviewBelow()) < 0;
        if (review && current.getReviewFlaggedAt() == null) {
            current.setReviewFlaggedAt(now);
            String name = directory.people(List.of(profile.getUserId())).stream().findFirst()
                    .map(person -> person.fullName() == null ? person.email() : person.fullName()).orElse("Người giao");
            for (UUID admin : directory.activeStaff("ADMIN")) {
                notifications.publish(admin, NotificationCategory.SECURITY, "trust:" + profile.getUserId() + ":review:" + now.toEpochMilli(),
                        "Người giao cần xem xét khóa nhận đơn", name + " có điểm uy tín " + current.getScore().toPlainString()
                                + ", dưới ngưỡng xem xét.", "/admin/trust");
            }
        } else if (!review) {
            current.setReviewFlaggedAt(null);
        }
    }

    private Map<Long, Inputs> inputs(Collection<Long> ids, Instant now) {
        Map<Long, Long> completed = counts(signals.completedOrders(ids));
        Map<Long, Long> lost = counts(signals.lostComplaints(ids, LOST));
        Map<Long, Long> windowCancels = counts(cancellations.since(ids, now.minus(SCORE_WINDOW)));
        Map<Long, Object[]> banCancels = cancellations.since(ids, now.minus(BAN_WINDOW)).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], Function.identity()));
        CourierRatingSource ratings = ratingSource.getIfAvailable();
        DeliveryPunctualitySource punctuality = punctualitySource.getIfAvailable();
        Map<Long, CourierRatingSource.RatingSummary> rated = ratings == null ? Map.of() : ratings.summarize(ids);
        Map<Long, DeliveryPunctualitySource.Punctuality> delivered = punctuality == null ? Map.of() : punctuality.summarize(ids);
        Map<Long, Inputs> result = new HashMap<>();
        for (Long id : ids) {
            CourierRatingSource.RatingSummary rating = rated.get(id);
            DeliveryPunctualitySource.Punctuality onTime = delivered.get(id);
            Object[] ban = banCancels.get(id);
            result.put(id, new Inputs(new TrustScorePolicy.Signals(completed.getOrDefault(id, 0L), windowCancels.getOrDefault(id, 0L),
                    lost.getOrDefault(id, 0L), rating == null ? 0 : rating.count(), rating == null ? null : rating.average(),
                    onTime == null ? 0 : onTime.delivered(), onTime == null ? 0 : onTime.onTime()),
                    ban == null ? 0 : ((Number) ban[1]).longValue(), ban == null ? null : (Instant) ban[2]));
        }
        return result;
    }

    private void notifyCourier(Long courierId, String step, String title, String body) {
        directory.reachable(courierId).ifPresent(recipient -> notifications.publish(recipient, NotificationCategory.ORDER,
                "trust:" + step, title, body, "/courier/trust"));
    }

    private static BigDecimal rateOrInitial(BigDecimal rate, TrustScorePolicy policy) {
        return rate != null ? rate : policy.initial().movePointLeft(2);
    }

    private static Map<Long, Long> counts(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
    }

    private record Inputs(TrustScorePolicy.Signals signals, long recentCancellations, Instant lastCancellation) {
    }
}
