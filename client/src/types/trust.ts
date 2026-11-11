import type { ComplaintPerson } from './complaint';

export type CourierTierCode = 'NEW' | 'BRONZE' | 'SILVER' | 'GOLD' | 'DIAMOND';
export type TrustComponentKey = 'ONTIME' | 'RATING' | 'COMPLETION' | 'COMPLAINT';
export type TrustScoreReason = 'NIGHTLY' | 'COMPLAINT_CLOSED' | 'RATING' | 'ORDER_COMPLETED' | 'CANCELLATION' | 'ADMIN';
export type TrustFilter = 'REVIEW' | 'RESTRICTED' | 'ALL';

export interface MyTrust {
  score: number;
  tier: CourierTierCode;
  tierName: string;
  completedOrders: number;
  components: { key: TrustComponentKey; weight: number; value: number; hasData: boolean }[];
  recentCancellations: number;
  cancelLimit: number;
  restrictedUntil: string | null;
  belowRestrictThreshold: boolean;
  nextTier: { tier: CourierTierCode; name: string; minScore: number; minOrders: number } | null;
  benefits: { maxConcurrentOrders: number; maxTotalDeclaredValue: number; canAcceptHighValue: boolean; commissionRate: number };
  calculatedAt: string;
  history: {
    score: number;
    previousScore: number | null;
    tier: CourierTierCode;
    previousTier: CourierTierCode | null;
    reason: TrustScoreReason;
    note: string | null;
    calculatedAt: string;
  }[];
}

export interface CourierTrust {
  courier: ComplaintPerson | null;
  score: number;
  tier: CourierTierCode;
  completedOrders: number;
  lostComplaints: number;
  recentCancellations: number;
  flaggedForReview: boolean;
  restrictedUntil: string | null;
  calculatedAt: string;
}
