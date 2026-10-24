import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type {
  WalletResponse,
  WalletTopupRequest,
  PaymentCreationResponse,
  VNPayReturnResponse,
  WalletTransactionFilterRequest,
  WalletTransactionResponse,
  WalletStatementResponse,
} from '../types/payment';

export const walletApi = {
  getWallet: () => {
    return axiosClient.get<unknown, ApiResponse<WalletResponse>>('/wallet');
  },

  topup: (data: WalletTopupRequest) => {
    return axiosClient.post<unknown, ApiResponse<PaymentCreationResponse>>('/wallet/topups', data);
  },

  verifyVNPayReturn: (params: Record<string, string>) => {
    return axiosClient.get<unknown, ApiResponse<VNPayReturnResponse>>('/payment/vnpay-return', { params });
  },

  getTransactions: (params?: WalletTransactionFilterRequest) => {
    return axiosClient.get<unknown, ApiResponse<PageResponse<WalletTransactionResponse>>>('/wallet/transactions', { params });
  },

  getStatement: (month?: number, year?: number) => {
    return axiosClient.get<unknown, ApiResponse<WalletStatementResponse>>('/wallet/statement', {
      params: { month, year },
    });
  },
};

