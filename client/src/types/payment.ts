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
