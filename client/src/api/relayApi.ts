import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type { RelayOrderResponse } from '../types/order';
import type {
  RelayMeetingPoint,
  DetailedOrderLeg,
  HandoverQrData,
  RelayHandoverResult,
} from '../types/relay';

export const relayApi = {
  /**
   * Lấy danh sách các đơn tiếp sức và trạng thái các chặng
   */
  getRelayOrders: (filter: 'available' | 'my' | 'all' = 'all') => {
    return axiosClient.get<unknown, ApiResponse<RelayOrderResponse[]>>(
      `/orders/relay?filter=${filter}`
    );
  },

  /**
   * Lấy danh sách điểm hẹn bàn giao an toàn công cộng (BR-F-061)
   */
  getSafeMeetingPoints: () => {
    return axiosClient.get<unknown, ApiResponse<RelayMeetingPoint[]>>(
      '/orders/relay/meeting-points'
    );
  },

  /**
   * Xem danh sách chi tiết các chặng của đơn tiếp sức
   */
  getOrderLegsDetailed: (orderId: number | string) => {
    return axiosClient.get<unknown, ApiResponse<DetailedOrderLeg[]>>(
      `/orders/${orderId}/legs-detailed`
    );
  },

  /**
   * Xem chi tiết một chặng
   */
  getLegDetail: (orderId: number | string, legNo: number) => {
    return axiosClient.get<unknown, ApiResponse<DetailedOrderLeg>>(
      `/orders/${orderId}/legs/${legNo}`
    );
  },

  /**
   * Sinh mã QR và OTP bàn giao cho chặng tiếp sức (PRM-097)
   */
  generateHandoverQr: (orderId: number | string, legNo: number) => {
    return axiosClient.post<unknown, ApiResponse<HandoverQrData>>(
      `/orders/${orderId}/legs/${legNo}/handover-token`
    );
  },

  /**
   * Lấy mã QR bàn giao hiện tại
   */
  getHandoverQr: (orderId: number | string, legNo: number) => {
    return axiosClient.get<unknown, ApiResponse<HandoverQrData>>(
      `/orders/${orderId}/legs/${legNo}/handover-token`
    );
  },

  /**
   * Xác nhận bàn giao giữa các chặng bằng QR/OTP và ảnh hàng (API-083, BR-F-060, BR-R-048)
   */
  confirmHandover: (
    orderId: number | string,
    legNo: number,
    formData: FormData
  ) => {
    return axiosClient.post<unknown, ApiResponse<RelayHandoverResult>>(
      `/orders/${orderId}/legs/${legNo}/handover`,
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    );
  },

  /**
   * Tài xế tiếp nhận phụ trách một chặng tiếp sức
   */
  acceptLeg: (orderId: number | string, legNo: number) => {
    return axiosClient.post<unknown, ApiResponse<DetailedOrderLeg>>(
      `/orders/${orderId}/legs/${legNo}/accept`
    );
  },
};
