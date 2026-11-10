package com.fptpost.modules.rewards.repository;

import com.fptpost.modules.rewards.model.Referral;
import com.fptpost.modules.identity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<Referral, Long> {
    @Query("""
            select invitee.publicId as userId, profile.fullName as fullName, invitee.email as email,
                   inviter.publicId as inviterPublicId, invitee.status as inviteeStatus,
                   r.createdAt as registeredAt, r.rewardedAt as rewardedAt, r.rewardPerPerson as rewardAmount
            from Referral r join r.invitee invitee join r.inviter inviter
            left join UserProfile profile on profile.userId = invitee.id
            where inviter.id = :userId
            order by r.createdAt desc, r.id desc
            """)
    Page<ReferralEntry> findInvitees(Long userId, Pageable page);

    @Query("""
            select inviter.publicId as userId, profile.fullName as fullName, inviter.email as email,
                   inviter.publicId as inviterPublicId, invitee.status as inviteeStatus,
                   r.createdAt as registeredAt, r.rewardedAt as rewardedAt, r.rewardPerPerson as rewardAmount
            from Referral r join r.invitee invitee join r.inviter inviter
            left join UserProfile profile on profile.userId = inviter.id
            where invitee.id = :userId
            """)
    Optional<ReferralEntry> findInviter(Long userId);

    @Query("""
            select coalesce(sum(case when r.inviter.id = :userId then 1L else 0L end), 0L) as invited,
                   coalesce(sum(case when r.inviter.id = :userId and r.rewardedAt is not null
                                     then 1L else 0L end), 0L) as rewarded,
                   coalesce(sum(r.rewardPerPerson), 0L) as earnedAmount
            from Referral r
            where r.inviter.id = :userId or r.invitee.id = :userId
            """)
    ReferralSummary summarizeFor(Long userId);

    interface ReferralEntry {
        UUID getUserId();
        String getFullName();
        String getEmail();
        UUID getInviterPublicId();
        UserStatus getInviteeStatus();
        Instant getRegisteredAt();
        Instant getRewardedAt();
        Long getRewardAmount();
    }

    interface ReferralSummary {
        long getInvited();
        long getRewarded();
        long getEarnedAmount();
    }
}
