import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type {
  TripOrderMatch,
  OrderCourierMatch,
  QuickBidRequest,
} from '../types/matching';
import type { MarketOrderAcceptResponse } from '../types/order';
import type { Offer } from '../types/offer';

export const matchingApi = {
  /**
   * API-053: Lấy danh sách các đơn hàng tiện đường gợi ý cho chuyến đi (COURIER)
   */
  getOrdersForTrip: (tripId: number | string) => {
    return axiosClient.get<unknown, ApiResponse<TripOrderMatch[]>>(
      `/matching/orders-for-trip/${tripId}`
    );
  },

  /**
   * API-054: Lấy danh sách các chuyến đi và tài xế tiện đường gợi ý cho đơn hàng (SENDER)
   */
  getCouriersForOrder: (orderId: number | string) => {
    return axiosClient.get<unknown, ApiResponse<OrderCourierMatch[]>>(
      `/matching/couriers-for-order/${orderId}`
    );
  },

  /**
   * Nhận đơn nhanh trực tiếp từ gợi ý chuyến đi (Flow bước 5 & BR-F-038)
   */
  quickAcceptOrder: (tripId: number | string, orderId: number | string) => {
    return axiosClient.post<unknown, ApiResponse<MarketOrderAcceptResponse>>(
      `/matching/trips/${tripId}/orders/${orderId}/quick-accept`
    );
  },

  /**
   * Chào giá nhanh trực tiếp từ gợi ý chuyến đi (Flow bước 5 & BR-F-038)
   */
  quickBidOrder: (
    tripId: number | string,
    orderId: number | string,
    data: QuickBidRequest
  ) => {
    return axiosClient.post<unknown, ApiResponse<Offer>>(
      `/matching/trips/${tripId}/orders/${orderId}/quick-bid`,
      data
    );
  },
};
