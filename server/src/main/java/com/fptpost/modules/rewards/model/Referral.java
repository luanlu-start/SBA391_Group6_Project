package com.fptpost.modules.rewards.model;

import com.fptpost.modules.identity.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "referrals", indexes = @Index(name = "idx_referrals_inviter", columnList = "inviter_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Referral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inviter_id", nullable = false)
    private User inviter;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitee_id", nullable = false, unique = true)
    private User invitee;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant rewardedAt;
    private Long rewardPerPerson;
}
