import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type { OrderResponse } from '../types/order';
import type { OrderPaymentRequest, OrderPaymentResponse } from '../types/payment';

export const orderApi = {
  getById: (id: number | string) => {
    return axiosClient.get<unknown, ApiResponse<OrderResponse>>(`/orders/${id}`);
  },

  getAll: () => {
    return axiosClient.get<unknown, ApiResponse<OrderResponse[]>>('/orders');
  },

  payOrder: (id: number | string, data: OrderPaymentRequest) => {
    return axiosClient.post<unknown, ApiResponse<OrderPaymentResponse>>(`/orders/${id}/payment`, data);
  },
};
