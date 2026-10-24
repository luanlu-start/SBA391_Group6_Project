import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type {
  WalletResponse,
  WalletTopupRequest,
  PaymentCreationResponse,
  VNPayReturnResponse,
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
};
