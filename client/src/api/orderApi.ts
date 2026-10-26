import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type { Order, OrderRequest } from '../types/order';
import type { OrderPaymentRequest, OrderPaymentResponse } from '../types/payment';

export const orderApi = {
  create: (data: OrderRequest) => {
    return axiosClient.post<unknown, ApiResponse<Order>>('/orders', data);
  },

  update: (id: number | string, data: OrderRequest) => {
    return axiosClient.put<unknown, ApiResponse<Order>>(`/orders/${id}`, data);
  },

  cancel: (id: number | string, reason?: string) => {
    return axiosClient.post<unknown, ApiResponse<Order>>(`/orders/${id}/cancel`, { reason });
  },

  getAll: () => {
    return axiosClient.get<unknown, ApiResponse<Order[]>>('/orders');
  },

  getMyOrders: () => {
    return axiosClient.get<unknown, ApiResponse<Order[]>>('/orders/my-orders');
  },

  getById: (id: number | string) => {
    return axiosClient.get<unknown, ApiResponse<Order>>(`/orders/${id}`);
  },

  uploadImages: (files: File[]) => {
    const formData = new FormData();
    files.forEach(file => {
      formData.append('files', file);
    });
    return axiosClient.post<unknown, ApiResponse<string[]>>('/orders/upload-images', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },

  payOrder: (id: number | string, data: OrderPaymentRequest) => {
    return axiosClient.post<unknown, ApiResponse<OrderPaymentResponse>>(`/orders/${id}/payment`, data);
  },
};

