package com.fptpost.modules.rewards.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.exception.IdentityErrorCode;
import com.fptpost.modules.identity.mapper.UserMapper;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.rewards.dto.BadgesResponse;
import com.fptpost.modules.rewards.dto.ReferralResponse;
import com.fptpost.modules.rewards.dto.ReferralDetailResponse;
import com.fptpost.modules.rewards.enums.ReferralEligibility;
import com.fptpost.modules.rewards.model.Referral;
import com.fptpost.modules.rewards.repository.ReferralRepository;
import com.fptpost.modules.rewards.repository.RewardOrderQueries;
import com.fptpost.modules.rewards.repository.UserBadgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RewardsService {
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserRepository users;
    private final ReferralRepository referrals;
    private final UserBadgeRepository badges;
    private final RewardOrderQueries orders;
    private final ParameterService parameters;
    private final Clock identityClock;
    private final UserMapper userMapper;
    private final PlatformTransactionManager transactions;

    @Transactional
    public String attachReferral(String code, User invitee, Instant now) {
        if (code == null || code.isBlank()) return null;

        if (!code.matches("[A-Fa-f0-9]{32}")) {
            throw new AppException(IdentityErrorCode.REFERRAL_CODE_INVALID);
        }
        UUID publicId = UUID.fromString(code.substring(0, 8) + "-" + code.substring(8, 12) + "-"
                + code.substring(12, 16) + "-" + code.substring(16, 20) + "-" + code.substring(20));
        User inviter = users.findByPublicId(publicId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getEmailVerifiedAt() != null)
                .filter(user -> !user.getId().equals(invitee.getId()))
                .orElseThrow(() -> new AppException(IdentityErrorCode.REFERRAL_CODE_INVALID));
        referrals.save(Referral.builder().inviter(inviter).invitee(invitee).createdAt(now).build());
        return referralCode(inviter.getPublicId());
    }

    public ReferralDetailResponse applyReferral(UUID publicId, String code) {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        try {
            return transaction.execute(status -> applyLockedReferral(publicId, code));
        } catch (DataIntegrityViolationException | TransientDataAccessException exception) {
            // Retry in a fresh transaction so a concurrent claim becomes visible after rollback.
            return transaction.execute(status -> applyLockedReferral(publicId, code));
        }
    }

    private ReferralDetailResponse applyLockedReferral(UUID publicId, String code) {
        User invitee = users.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
        if (invitee.getStatus() != UserStatus.ACTIVE || invitee.getEmailVerifiedAt() == null) {
            throw new AppException(IdentityErrorCode.ACCOUNT_DISABLED);
        }
        var existing = referrals.findInviter(invitee.getId());
        if (existing.isPresent()) {
            if (referralCode(existing.get().getInviterPublicId()).equalsIgnoreCase(code)) {
                return referralDetail(existing.get());
            }
            throw new AppException(IdentityErrorCode.REFERRAL_ALREADY_RECORDED);
        }
        // Serializable isolation keeps a first completion from committing between this check and insertion.
        if (orders.countCompletedOrders(invitee.getId()) > 0) {
            throw new AppException(IdentityErrorCode.REFERRAL_TOO_LATE);
        }
        attachReferral(code, invitee, identityClock.instant());
        referrals.flush();
        return referralDetail(referrals.findInviter(invitee.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public BadgesResponse badges(UUID publicId) {
        User user = user(publicId);
        boolean courier = user.getRoles().stream().anyMatch(role -> "COURIER".equals(role.getCode()));
        LocalDate week = weekStart(identityClock.instant());
        var policy = parameters.getSnapshot();
        var result = new ArrayList<BadgesResponse.Badge>();
        if (user.isFptBadge()) {
            result.add(new BadgesResponse.Badge("FPT_COMMUNITY", "Cộng đồng FPT", null, null, 0));
        }
        badges.findByUserIdOrderByAwardedAtDescIdDesc(user.getId(), PageRequest.of(0, 52)).stream()
                .map(badge -> new BadgesResponse.Badge(badge.getCode(), "Giao hàng tích cực",
                        badge.getAwardedAt(), badge.getWeekStart(), badge.getRewardAmount()))
                .forEach(result::add);

        long deliveries = courier ? orders.countWeeklyDeliveries(user.getId(), startOf(week), startOf(week.plusWeeks(1))) : 0;
        return new BadgesResponse(result, courier, week, deliveries, policy.integer("WEEKLY_DELIVERY_TARGET"),
                policy.integer("WEEKLY_DELIVERY_REWARD"));
    }

    @Transactional(readOnly = true)
    public ReferralResponse referral(UUID publicId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new AppException(GlobalErrorCode.INVALID_REQUEST);
        User user = user(publicId);
        var counts = referrals.summarizeFor(user.getId());
        long invited = counts.getInvited();
        long rewarded = counts.getRewarded();
        var invitees = referrals.findInvitees(user.getId(), PageRequest.of(page, size)).map(this::referralDetail);
        var referredBy = referrals.findInviter(user.getId()).map(this::referralDetail).orElse(null);
        return new ReferralResponse(referralCode(publicId), parameters.getSnapshot().integer("REF_REWARD"), invited,
                invited - rewarded, rewarded, counts.getEarnedAmount(), referredBy, PageResponse.from(invitees),
                eligibility(user, referredBy));
    }

    private ReferralEligibility eligibility(User user, ReferralDetailResponse referredBy) {
        if (referredBy != null) return ReferralEligibility.ALREADY_RECORDED;
        if (user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null) {
            return ReferralEligibility.ACCOUNT_INACTIVE;
        }
        return orders.countCompletedOrders(user.getId()) == 0
                ? ReferralEligibility.ELIGIBLE : ReferralEligibility.FIRST_ORDER_COMPLETED;
    }

    private ReferralDetailResponse referralDetail(ReferralRepository.ReferralEntry entry) {
        String status;
        if (entry.getRewardedAt() != null) {
            status = "REWARDED";
        } else if (entry.getInviteeStatus() == UserStatus.PENDING_VERIFICATION) {
            status = "WAITING_VERIFICATION";
        } else if (entry.getInviteeStatus() == UserStatus.ACTIVE) {
            status = "WAITING_FIRST_ORDER";
        } else {
            status = "ACCOUNT_INACTIVE";
        }
        String fullName = entry.getFullName();
        return new ReferralDetailResponse(entry.getUserId(),
                fullName == null || fullName.isBlank() ? "Người dùng FPost" : fullName,
                userMapper.maskEmail(entry.getEmail()), referralCode(entry.getInviterPublicId()), status,
                entry.getRegisteredAt(), entry.getRewardedAt(), entry.getRewardAmount());
    }

    private String referralCode(UUID publicId) {
        return publicId.toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private User user(UUID publicId) {
        return users.findByPublicId(publicId).orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
    }

    static LocalDate weekStart(Instant now) {
        return now.atZone(BUSINESS_ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    static Instant startOf(LocalDate week) {
        return week.atStartOfDay(BUSINESS_ZONE).toInstant();
    }
}
