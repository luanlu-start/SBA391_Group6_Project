package com.fptpost.modules.rewards.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BadgesResponse(List<Badge> badges, boolean courier, LocalDate weekStart,
                             long weeklyDeliveries, long weeklyTarget, long weeklyReward) {
    public record Badge(String code, String title, Instant awardedAt, LocalDate weekStart, long rewardAmount) {
    }
}
