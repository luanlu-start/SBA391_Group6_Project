package com.fptpost.modules.rewards.dto;

import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.rewards.enums.ReferralEligibility;

public record ReferralResponse(String code, long rewardPerPerson, long invited, long pending,
                               long rewarded, long earnedAmount, ReferralDetailResponse referredBy,
                               PageResponse<ReferralDetailResponse> invitees, ReferralEligibility eligibility) {
}
