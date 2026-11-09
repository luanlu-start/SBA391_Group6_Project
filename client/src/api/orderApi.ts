import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type { Order, OrderLeg, OrderRequest, PricingEstimateRequest, PricingEstimateResponse, RelayOrderRequest, RelayOrderResponse, RelayPricingEstimateRequest, RelayPricingEstimateResponse } from '../types/order';
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

  estimateRelayPricing: (data: RelayPricingEstimateRequest) => {
    return axiosClient.post<unknown, ApiResponse<RelayPricingEstimateResponse>>('/orders/relay/estimate', data);
  },

  createRelay: (data: RelayOrderRequest) => {
    return axiosClient.post<unknown, ApiResponse<RelayOrderResponse>>('/orders/relay', data);
  },

  getOrderLegs: (orderId: number | string) => {
    return axiosClient.get<unknown, ApiResponse<OrderLeg[]>>(`/orders/${orderId}/legs`);
  },

  releaseEscrow: (orderId: number | string) => {
    return axiosClient.post<unknown, ApiResponse<EscrowReleaseResponse>>(`/admin/escrow/${orderId}/release`);
  },
};

