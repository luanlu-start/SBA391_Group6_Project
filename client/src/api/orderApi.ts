import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type { Order, OrderRequest, PricingEstimateRequest, PricingEstimateResponse } from '../types/order';
import type { EscrowReleaseResponse, EscrowResponse, OrderPaymentRequest, OrderPaymentResponse } from '../types/payment';

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

  estimatePricing: (data: PricingEstimateRequest) => {
    return axiosClient.post<unknown, ApiResponse<PricingEstimateResponse>>('/orders/pricing/estimate', data);
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

  getAssignedToMe: () => {
    return axiosClient.get<unknown, ApiResponse<Order[]>>(
      '/orders/assigned-to-me'
    );
  },
  getPickupQr: (orderId: number | string) => {
    return axiosClient.get<
      unknown,
      ApiResponse<{ qrToken: string }>
    >(`/orders/${orderId}/pickup-qr`);
  },

  confirmPickup: (
    orderId: number | string,
    qrToken: string,
    photo: File
  ) => {
    const formData = new FormData();
    formData.append('qrToken', qrToken);
    formData.append('photo', photo);

    return axiosClient.post<unknown, ApiResponse<Order>>(
      `/orders/${orderId}/confirm-pickup`,
      formData,
      {
        // Override the JSON default; Axios lets the browser add the multipart boundary.
        headers: { 'Content-Type': 'multipart/form-data' },
      }
    );
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
  getEscrow: (id: number | string) => {
    return axiosClient.get<unknown, ApiResponse<EscrowResponse>>(`/orders/${id}/escrow`);
  },

  releaseEscrow: (orderId: number | string) => {
    return axiosClient.post<unknown, ApiResponse<EscrowReleaseResponse>>(`/admin/escrow/${orderId}/release`);
  },
};

