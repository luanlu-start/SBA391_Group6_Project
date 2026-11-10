package com.fptpost.modules.rewards.service;

import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.jobs.service.BackgroundJob;
import com.fptpost.modules.jobs.service.JobResult;
import com.fptpost.modules.payment.model.LedgerTransactionType;
import com.fptpost.modules.payment.service.LedgerService;
import com.fptpost.modules.rewards.model.UserBadge;
import com.fptpost.modules.rewards.repository.RewardOrderQueries;
import com.fptpost.modules.rewards.repository.UserBadgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class RewardsJob implements BackgroundJob {
    private static final PageRequest BATCH = PageRequest.of(0, 50);

    private final RewardOrderQueries orders;
    private final UserBadgeRepository badges;
    private final UserRepository users;
    private final ParameterService parameters;
    private final LedgerService ledger;

    @Override
    public String name() {
        return "gamification-rewards";
    }

    @Override
    public String title() {
        return "Cấp huy hiệu và thưởng";
    }

    @Override
    public String description() {
        return "Xử lý thưởng giới thiệu sau đơn đầu tiên và huy hiệu giao hàng theo tuần";
    }

    @Override
    public Duration interval() {
        return Duration.ofMinutes(1);
    }

    @Override
    public JobResult execute(Instant now) {
        var policy = parameters.getSnapshot();
        int processed = rewardReferrals(now, policy.integer("REF_REWARD"));
        long target = policy.integer("WEEKLY_DELIVERY_TARGET");
        long amount = policy.integer("WEEKLY_DELIVERY_REWARD");
        LocalDate week = RewardsService.weekStart(now);
        // Include the preceding week so a run just after midnight Monday can close it out.
        processed += awardWeekly(week.minusWeeks(1), now, target, amount);
        processed += awardWeekly(week, now, target, amount);
        return new JobResult(processed, "Đã xử lý " + processed + " lượt giới thiệu hoặc huy hiệu");
    }

    private int rewardReferrals(Instant now, long amount) {
        if (amount == 0) return 0;
        var ready = orders.findReadyReferrals(BATCH);
        for (var referral : ready) {
            long inviter = referral.getInviter().getId();
            long invitee = referral.getInvitee().getId();
            String reference = referral.getId().toString();
            ledger.creditReward(Math.min(inviter, invitee), amount, LedgerTransactionType.REFERRAL_REWARD,
                    reference, "Thưởng giới thiệu bạn bè");
            ledger.creditReward(Math.max(inviter, invitee), amount, LedgerTransactionType.REFERRAL_REWARD,
                    reference, "Thưởng giới thiệu bạn bè");
            referral.setRewardPerPerson(amount);
            referral.setRewardedAt(now);
        }
        return ready.size();
    }

    private int awardWeekly(LocalDate week, Instant now, long target, long amount) {
        String code = UserBadge.WEEKLY_DELIVERIES;
        var recipients = orders.findWeeklyRecipients(RewardsService.startOf(week),
                RewardsService.startOf(week.plusWeeks(1)), code, week, target, BATCH);
        for (Long userId : recipients) {
            badges.save(UserBadge.builder().user(users.getReferenceById(userId)).code(code).weekStart(week)
                    .awardedAt(now).rewardAmount(amount).build());
            ledger.creditReward(userId, amount, LedgerTransactionType.ACHIEVEMENT_REWARD,
                    week.toString(), "Thưởng giao hàng tích cực tuần " + week);
        }
        return recipients.size();
    }
}
