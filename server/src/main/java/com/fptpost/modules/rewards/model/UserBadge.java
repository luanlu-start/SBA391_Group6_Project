package com.fptpost.modules.rewards.model;

import com.fptpost.modules.identity.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_badges", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_badges_code_week", columnNames = {"user_id", "code", "week_start"}))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBadge {
    public static final String WEEKLY_DELIVERIES = "WEEKLY_DELIVERIES";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false)
    private Instant awardedAt;

    @Column(nullable = false)
    private long rewardAmount;
}
