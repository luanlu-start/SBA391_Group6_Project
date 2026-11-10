import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type {
  OrderLeg,
  MeetingPoint,
  HandoverToken,
  HandoverConfirmRequest,
  HandoverConfirmResponse,
  HandoverIncidentRequest,
  HandoverIncidentResponse,
  RelayOrderDetail,
} from '../types/relay';

export const relayApi = {
  // Lấy danh sách chặng tiếp sức của tài xế đang đăng nhập
  getCourierRelayLegs: () => {
    return axiosClient.get<unknown, ApiResponse<OrderLeg[]>>('/courier/relay-legs');
  },

  // Đánh dấu đã đến điểm hẹn bàn giao (chuyển sang Sẵn sàng bàn giao)
  markReadyForHandover: (orderId: number | string, legNo: number) => {
    return axiosClient.patch<unknown, ApiResponse<OrderLeg>>(`/orders/${orderId}/legs/${legNo}/ready`);
  },

  // Lấy chi tiết đơn hàng tiếp sức, các chặng và lịch sử bàn giao
  getRelayOrder: (orderId: number | string) => {
    return axiosClient.get<unknown, ApiResponse<RelayOrderDetail>>(`/orders/${orderId}/legs`);
  },

  // Sinh / làm mới mã QR bàn giao (hiệu lực 10 phút)
  generateHandoverToken: (orderId: number | string, legNo: number) => {
    return axiosClient.post<unknown, ApiResponse<HandoverToken>>(`/orders/${orderId}/legs/${legNo}/handover-token`);
  },

  // Lấy mã QR bàn giao còn hiệu lực
  getActiveHandoverToken: (orderId: number | string, legNo: number) => {
    return axiosClient.get<unknown, ApiResponse<HandoverToken | null>>(`/orders/${orderId}/legs/${legNo}/handover-token`);
  },

  // API-083: Xác nhận bàn giao chặng bằng mã QR / mã xác nhận + ảnh hàng
  confirmHandover: (orderId: number | string, legNo: number, data: HandoverConfirmRequest) => {
    return axiosClient.post<unknown, ApiResponse<HandoverConfirmResponse>>(`/orders/${orderId}/legs/${legNo}/handover`, data);
  },

  // Báo cáo sự cố bàn giao tại điểm hẹn
  reportIncident: (orderId: number | string, legNo: number, data: HandoverIncidentRequest) => {
    return axiosClient.post<unknown, ApiResponse<HandoverIncidentResponse>>(`/orders/${orderId}/legs/${legNo}/incident`, data);
  },

  // Lấy danh sách điểm hẹn bàn giao an toàn đã được duyệt
  getSafeMeetingPoints: (campusArea?: string) => {
    return axiosClient.get<unknown, ApiResponse<MeetingPoint[]>>('/relay/meeting-points', {
      params: campusArea ? { campusArea } : undefined,
    });
  },

  // Tải ảnh chụp hàng hóa lúc bàn giao
  uploadHandoverPhoto: async (file: File) => {
    const formData = new FormData();
    formData.append('files', file);
    const response = await axiosClient.post<unknown, ApiResponse<string[]>>('/orders/upload-images', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data?.[0] || '';
  },
};
