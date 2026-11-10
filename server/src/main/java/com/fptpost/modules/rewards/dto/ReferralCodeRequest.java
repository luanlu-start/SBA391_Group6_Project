package com.fptpost.modules.rewards.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReferralCodeRequest(@NotBlank @Pattern(regexp = "[A-Fa-f0-9]{32}") String referralCode) {
    public ReferralCodeRequest {
        referralCode = referralCode == null ? null : referralCode.strip();
    }
}
