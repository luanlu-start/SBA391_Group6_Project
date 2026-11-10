import axiosClient from '../api/axiosClient';
import type { ApiResponse } from '../types/api';
import type { BadgesResponse, ReferralDetail, ReferralResponse } from '../types/rewards';

export const rewardsService = {
  badges: () => axiosClient.get<unknown, ApiResponse<BadgesResponse>>('/me/badges'),
  referral: (page = 0) => axiosClient.get<unknown, ApiResponse<ReferralResponse>>('/me/referral', { params: { page } }),
  applyReferral: (referralCode: string) =>
    axiosClient.post<unknown, ApiResponse<ReferralDetail>>('/me/referral', { referralCode }),
};
