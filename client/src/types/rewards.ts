import type { PageResponse } from './api';

export interface BadgeAward {
  code: string;
  title: string;
  awardedAt: string | null;
  weekStart: string | null;
  rewardAmount: number;
}

export interface BadgesResponse {
  badges: BadgeAward[];
  courier: boolean;
  weekStart: string;
  weeklyDeliveries: number;
  weeklyTarget: number;
  weeklyReward: number;
}

export interface ReferralResponse {
  code: string;
  rewardPerPerson: number;
  invited: number;
  pending: number;
  rewarded: number;
  earnedAmount: number;
  referredBy: ReferralDetail | null;
  invitees: PageResponse<ReferralDetail>;
  eligibility: 'ELIGIBLE' | 'ALREADY_RECORDED' | 'FIRST_ORDER_COMPLETED' | 'ACCOUNT_INACTIVE';
}
export interface ReferralDetail {
  userId: string;
  fullName: string;
  maskedEmail: string;
  referralCode: string;
  status: 'WAITING_VERIFICATION' | 'WAITING_FIRST_ORDER' | 'REWARDED' | 'ACCOUNT_INACTIVE';
  registeredAt: string;
  rewardedAt: string | null;
  rewardAmount: number | null;
}
