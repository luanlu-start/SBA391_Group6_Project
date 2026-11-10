export type EscrowPaymentMethod = 'WALLET' | 'VNPAY';
export type EscrowStatus = 'HELD' | 'RELEASED' | 'REFUNDED';

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
  | 'REFERRAL_REWARD'
  | 'ACHIEVEMENT_REWARD';

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
