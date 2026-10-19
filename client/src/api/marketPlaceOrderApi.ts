import axiosClient from './axiosClient';
import type {
  MarketPlaceOrder,
  MarketplaceOrderDetail,
  MarketOrderAcceptResponse,
  MarketplaceOrderParams,
  SpringPageResponse,
} from '../types/order';
import type { ApiResponse } from '../types/api';

export const marketPlaceOrderApi = {
  getAll: (params: MarketplaceOrderParams = {}) => {
    return axiosClient.get<unknown, ApiResponse<SpringPageResponse<MarketPlaceOrder>>>('/market/orders', { params });
  },

  getById: (id: number | string) => {
    return axiosClient.get<unknown, ApiResponse<MarketplaceOrderDetail>>(`/market/orders/${id}`);
  },

  acceptOrder: (id: number | string) => {
    return axiosClient.post<unknown, ApiResponse<MarketOrderAcceptResponse>>(`/market/orders/${id}/accept`);
  },
};
