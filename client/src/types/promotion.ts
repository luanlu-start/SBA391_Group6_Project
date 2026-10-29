export type PromotionType = 'FIXED_AMOUNT' | 'PERCENTAGE';
export type PromotionTarget = 'ALL' | 'SELECTED_USERS';
export type PromotionStatus = 'ACTIVE' | 'PAUSED';
export type PromotionEffectiveStatus = PromotionStatus | 'SCHEDULED' | 'EXPIRED' | 'EXHAUSTED';

export interface PromotionTargetOption {
  value: string;
  label: string;
  description?: string;
}

export interface PromotionTargetOptions {
  options: PromotionTargetOption[];
  hasMore: boolean;
}

export interface Promotion {
  id: string;
  code: string;
  type: PromotionType;
  value: string;
  validFrom: string;
  validTo: string;
  usageLimit: number;
  usedCount: number;
  target: PromotionTarget;
  targetUserIds: string[];
  targetUsers: PromotionTargetOption[];
  status: PromotionStatus;
  effectiveStatus: PromotionEffectiveStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface PromotionWrite {
  code: string;
  type: PromotionType;
  value: string;
  validFrom: string;
  validTo: string;
  usageLimit: number;
  target: PromotionTarget;
  targetUserIds: string[];
  status: PromotionStatus;
}

export interface PromotionUpdate extends PromotionWrite {
  expectedVersion: number;
}

export interface PromotionQuery {
  q?: string;
  status?: PromotionEffectiveStatus;
  page: number;
  size: number;
}
