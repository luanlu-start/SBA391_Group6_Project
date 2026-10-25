import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type { Order, OrderRequest } from '../types/order';

export const orderApi = {
  create: (data: OrderRequest) => {
    return axiosClient.post<unknown, ApiResponse<Order>>('/orders', data);
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
};
