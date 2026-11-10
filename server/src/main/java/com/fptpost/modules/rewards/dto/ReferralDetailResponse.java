package com.fptpost.modules.rewards.dto;

import java.time.Instant;
import java.util.UUID;

public record ReferralDetailResponse(UUID userId, String fullName, String maskedEmail, String referralCode,
                                     String status, Instant registeredAt, Instant rewardedAt, Long rewardAmount) {
}
