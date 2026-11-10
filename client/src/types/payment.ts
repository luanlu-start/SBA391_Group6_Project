export type EscrowPaymentMethod = 'WALLET' | 'VNPAY';
export type EscrowStatus = 'HELD' | 'RELEASED' | 'REFUNDED' | 'PARTIALLY_REFUNDED';
export type RefundKind = 'FULL' | 'PARTIAL';
export type RefundStatus = 'PENDING' | 'SUCCESS' | 'FAILED';

export interface RefundToSourceRequest {
  reason?: string;
  simulateProviderRejection?: boolean;
}

export interface RefundToSourceResponse {
  id: number;
  orderId: number;
  orderCode: string;
  paymentTxnId?: number;
  amount: number;
  cancellationFee: number;
  kind: RefundKind;
  status: RefundStatus;
  vnpayRefundRef?: string;
  reason?: string;
  message: string;
  createdAt: string;
}

export interface WalletResponse {
  id: number;
  userId: number;
  available: number;
  escrowHeld: number;
  pendingWithdraw: number;
  totalBalance: number;
  updatedAt: string;
}

export interface WalletTopupRequest {
  amount: number;
  bankCode?: string;
  language?: string;
}

export interface PaymentCreationResponse {
  code: string;
  message: string;
  paymentUrl: string;
  txnRef: string;
}

export interface OrderPaymentRequest {
  paymentMethod: EscrowPaymentMethod;
  bankCode?: string;
  language?: string;
}

export interface OrderPaymentResponse {
  orderId: number;
  orderCode: string;
  paymentMethod: EscrowPaymentMethod;
  amount: number;
  status: 'SUCCESS' | 'PENDING_PAYMENT';
  paymentUrl?: string;
  txnRef?: string;
  message: string;
}

export interface VNPayReturnResponse {
  orderCode: string;
  amount?: number;
  bankCode?: string;
  bankTranNo?: string;
  cardType?: string;
  orderInfo?: string;
  payDate?: string;
  responseCode: string;
  transactionNo?: string;
  transactionStatus?: string;
  secureHash?: string;
  validSignature: boolean;
  success: boolean;
  message: string;
}

export interface EscrowResponse {
  id: number;
  orderId: number;
  orderCode: string;
  senderId: number;
  courierId?: number;
  amount: number;
  status: EscrowStatus;
  paymentMethod: EscrowPaymentMethod;
  releaseAt?: string;
  frozen: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface EscrowReleaseResponse {
  orderId: number;
  orderCode: string;
  courierId: number;
  amountReleased: number;
  status: EscrowStatus;
  message: string;
  releasedAt: string;
}

export type LedgerTransactionType =
  | 'WALLET_TOPUP'
  | 'ORDER_ESCROW_HOLD'
  | 'ESCROW_RELEASE'
  | 'ESCROW_REFUND'
  | 'WITHDRAWAL_REQUEST'
  | 'WITHDRAWAL_CANCEL'
  | 'WITHDRAWAL_REFUND'
  | 'WITHDRAWAL_PAYOUT'
  | 'RECONCILIATION_ADJUSTMENT';

export type LedgerTransactionStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REVERSED';

export interface WalletTransactionFilterRequest {
  page?: number;
  size?: number;
  type?: LedgerTransactionType;
  fromDate?: string;
  toDate?: string;
}

export interface WalletTransactionResponse {
  transactionCode: string;
  type: LedgerTransactionType;
  amount: number;
  status: LedgerTransactionStatus;
  balanceDirection: 'IN' | 'OUT';
  description: string;
  orderId?: number;
  refType?: string;
  refId?: string;
  createdAt: string;
}

export interface WalletStatementResponse {
  userId: number;
  period: string;
  startingBalance: number;
  endingBalance: number;
  totalInflow: number;
  totalOutflow: number;
  netChange: number;
  transactionCount: number;
  transactions: WalletTransactionResponse[];
}

export type WithdrawalStatus =
  | 'PENDING_REVIEW'
  | 'APPROVED_L1'
  | 'APPROVED'
  | 'PROCESSING'
  | 'PAID'
  | 'REJECTED'
  | 'FAILED'
  | 'CANCELLED';

export interface WithdrawalCreationRequest {
  amount: number;
  bankAccountId?: number;
  bankName: string;
  accountNumber: string;
  accountHolderName: string;
}

export interface BankAccountCreationRequest {
  bankCode: string;
  bankName?: string;
  accountNumber: string;
  holderName: string;
}

export interface BankAccountResponse {
  id: number;
  userId: number;
  bankCode: string;
  bankName: string;
  maskedAccountNumber: string;
  accountLast4: string;
  holderName: string;
  verifiedAt?: string;
  cooldownUntil: string;
  readyForWithdrawal: boolean;
  createdAt: string;
}

export type ReconciliationReportStatus = 'MATCHED' | 'MISMATCH_FOUND' | 'RESOLVED';
export type ReconciliationItemStatus = 'MATCHED' | 'MISMATCH' | 'ADJUSTED';
export type ReconciliationDiscrepancyType =
  | 'NONE'
  | 'MISSING_LEDGER'
  | 'MISSING_PAYMENT'
  | 'AMOUNT_MISMATCH'
  | 'STALE_PENDING';

export interface ReconciliationItemResponse {
  id: number;
  reportId: number;
  txnRef: string;
  kind: string;
  internalAmount: number;
  vnpayAmount: number;
  differenceAmount: number;
  status: ReconciliationItemStatus;
  discrepancyType: ReconciliationDiscrepancyType;
  note?: string;
  adjustmentTxnCode?: string;
  adjustedBy?: number;
  adjustedAt?: string;
  createdAt: string;
}

export interface ReconciliationReportResponse {
  id: number;
  reportDate: string;
  totalCount: number;
  matchedCount: number;
  mismatchCount: number;
  status: ReconciliationReportStatus;
  createdBy?: number;
  createdAt: string;
  updatedAt: string;
  items: ReconciliationItemResponse[];
}

export interface ReconciliationAdjustRequest {
  debitAccount: string;
  creditAccount: string;
  amount: number;
  note: string;
}

export interface LedgerIntegrityResponse {
  balanced: boolean;
  totalTransactions: number;
  totalEntries: number;
  totalDebit: number;
  totalCredit: number;
  discrepancyAmount: number;
  unbalancedTransactionCount: number;
  unbalancedTransactionCodes: string[];
  checkedAt: string;
  message: string;
}

export interface WithdrawalResponse {
  id: number;
  requestCode: string;
  userId: number;
  amount: number;
  feeAmount: number;
  netAmount: number;
  bankName: string;
  accountNumber: string;
  accountHolderName: string;
  status: WithdrawalStatus;
  rejectionReason?: string;
  failureReason?: string;
  payoutReference?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CourierEarningPoint {
  label: string;
  date: string;
  orderCount: number;
  grossAmount: number;
  commissionAmount: number;
  netAmount: number;
}

export interface CourierEarningReceipt {
  orderId: number;
  orderCode: string;
  productName: string;
  grossAmount: number;
  commissionRate: number;
  commissionAmount: number;
  netAmount: number;
  status: string;
  completedAt: string;
}

export interface CourierEarningsResponse {
  period: 'DAY' | 'WEEK' | 'MONTH';
  fromDate: string;
  toDate: string;
  completedOrdersCount: number;
  grossEarnings: number;
  commissionRate: number;
  commissionAmount: number;
  deductionAmount: number;
  netEarnings: number;
  todayNetEarnings: number;
  pendingEscrowAmount: number;
  chartPoints: CourierEarningPoint[];
  receipts: CourierEarningReceipt[];
}

export interface CourierStatsResponse {
  userId: number;
  currentTier: string;
  currentTierDisplayName: string;
  commissionRate: number;
  trustScore: number;
  totalAssignedOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  inDeliveryOrders: number;
  completionRate: number;
  onTimeRate: number;
  ratingAvg: number;
  complaintRate: number;
  nextTier: string;
  nextTierDisplayName: string;
  nextTierCommissionRate: number;
  nextTierMinOrders: number;
  nextTierMinScore: number;
  ordersNeededForNextTier: number;
  scoreNeededForNextTier: number;
  ordersProgressPercent: number;
  scoreProgressPercent: number;
}
