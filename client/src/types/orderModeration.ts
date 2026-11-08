import type { OrderStatus } from './order';

export type OrderModerationStatus = 'AUTO_APPROVED' | 'PENDING' | 'APPROVED' | 'REJECTED';
export type ModerationKeywordLevel = 'PROHIBITED' | 'SUSPICIOUS';

export interface ModerationParty {
  publicId: string;
  fullName: string | null;
  email: string;
}

export interface OrderModerationItem {
  orderId: number;
  orderCode: string | null;
  status: OrderModerationStatus;
  orderStatus: OrderStatus | null;
  productName: string | null;
  productDescription: string | null;
  deliveryNote: string | null;
  quantity: number | null;
  weightKg: number | null;
  declaredValue: number | null;
  categoryName: string | null;
  categoryFlag: 'PROHIBITED' | 'REQUIRES_REVIEW' | null;
  matchedTerms: string[];
  imageUrls: string[];
  sender: ModerationParty | null;
  senderRecentRejections: number;
  submittedAt: string;
  reviewer: ModerationParty | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  canDecide: boolean;
}

export interface OrderModerationQuery {
  q?: string;
  page: number;
  size: number;
}

export interface OrderModerationOutcome {
  status: OrderModerationStatus;
  submittedAt: string;
  reviewedAt: string | null;
  rejectionReason: string | null;
}

export interface ModerationKeyword {
  id: number;
  term: string;
  level: ModerationKeywordLevel;
  createdAt: string;
}
