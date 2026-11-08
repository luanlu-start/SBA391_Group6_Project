import type { WithdrawalStatus } from './payment';

export type WithdrawalQueueFilter = 'OPEN' | 'ALL' | WithdrawalStatus;

export interface WithdrawalParty {
  publicId: string;
  fullName: string | null;
  email: string;
}

export interface WithdrawalReconciliation {
  walletAvailable: number;
  walletPendingWithdraw: number;
  walletEscrowHeld: number;
  paidCount: number;
  paidTotal: number;
  unsuccessfulCount: number;
  lastPaidAt: string | null;
}

export interface AdminWithdrawal {
  id: number;
  requestCode: string;
  status: WithdrawalStatus;
  amount: number;
  feeAmount: number;
  netAmount: number;
  bankName: string;
  accountNumber: string;
  accountHolderName: string;
  applicant: WithdrawalParty | null;
  dualApprovalRequired: boolean;
  firstApprover: WithdrawalParty | null;
  firstApprovedAt: string | null;
  approver: WithdrawalParty | null;
  approvedAt: string | null;
  rejectedBy: WithdrawalParty | null;
  rejectedAt: string | null;
  rejectionReason: string | null;
  payoutProvider: string | null;
  payoutReference: string | null;
  payoutSubmittedAt: string | null;
  settledBy: WithdrawalParty | null;
  settledAt: string | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
  dueAt: string;
  overdue: boolean;
  canApprove: boolean;
  canReject: boolean;
  canSettle: boolean;
  reconciliation: WithdrawalReconciliation | null;
}

export interface WithdrawalQueueQuery {
  status: WithdrawalQueueFilter;
  q?: string;
  page: number;
  size: number;
}

export interface WithdrawalImportError {
  line: number;
  requestCode: string | null;
  code: string;
}

export interface WithdrawalImportResult {
  processed: number;
  paid: number;
  failed: number;
  errors: WithdrawalImportError[];
}
