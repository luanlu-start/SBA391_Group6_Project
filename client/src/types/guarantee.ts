import type { ComplaintPerson } from './complaint';

export type CompensationStatus = 'PENDING_REVIEW' | 'QUEUED' | 'PAID' | 'REJECTED';
export type CompensationFilter = CompensationStatus | 'ALL';
export type FundMovementType = 'GUARANTEE_FUND_CONTRIBUTION' | 'GUARANTEE_FUND_PAYOUT';

export interface Compensation {
  id: string;
  complaintId: string | null;
  complaintCode: string | null;
  orderCode: string | null;
  declaredValue: number | null;
  beneficiary: ComplaintPerson | null;
  requestedAmount: number;
  approvedAmount: number | null;
  status: CompensationStatus;
  queuePosition: number | null;
  note: string | null;
  ruledAt: string;
  reviewedBy: ComplaintPerson | null;
  reviewedAt: string | null;
  queuedAt: string | null;
  paidAt: string | null;
}

export interface GuaranteeFund {
  balance: number;
  pendingCount: number;
  pendingAmount: number;
  queuedCount: number;
  queuedAmount: number;
  paidCount: number;
  paidAmount: number;
  movements: { transactionCode: string; type: FundMovementType; amount: number; description: string; createdAt: string }[];
}
