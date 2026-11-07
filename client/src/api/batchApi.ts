import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type {
  DeliveryBatch,
  RouteOptimizationResult,
  EligibleBatchOrder,
  BatchCreateRequest,
  RouteOptimizeRequest,
  DeliveryBatchStatus,
} from '../types/batch';
import type { PageResponse } from '../types/trip';

export const batchApi = {
  // Lấy danh sách các đơn hàng đủ điều kiện để gộp
  getEligibleOrders: () => {
    return axiosClient.get<unknown, ApiResponse<EligibleBatchOrder[]>>('/batches/eligible-orders');
  },

  // Tính toán và gợi ý tối ưu hóa thứ tự các điểm dừng (BR-F-058)
  optimizeRoute: (data: RouteOptimizeRequest) => {
    return axiosClient.post<unknown, ApiResponse<RouteOptimizationResult>>('/batches/optimize-route', data);
  },

  // Gộp đơn thành lượt giao (API-080, BR-F-057, BR-R-046)
  create: (data: BatchCreateRequest) => {
    return axiosClient.post<unknown, ApiResponse<DeliveryBatch>>('/batches', data);
  },

  // Lấy thứ tự điểm dừng gợi ý của lượt giao (API-081)
  getRoute: (id: number | string) => {
    return axiosClient.get<unknown, ApiResponse<RouteOptimizationResult>>(`/batches/${id}/route`);
  },

  // Lấy chi tiết lượt giao
  getById: (id: number | string) => {
    return axiosClient.get<unknown, ApiResponse<DeliveryBatch>>(`/batches/${id}`);
  },

  // Lấy danh sách các lượt giao của tài xế
  getMyBatches: (params?: { page?: number; size?: number }) => {
    return axiosClient.get<unknown, ApiResponse<PageResponse<DeliveryBatch>>>('/batches', { params });
  },

  // Đánh dấu hoàn thành một điểm dừng
  completeStop: (batchId: number | string, stopId: number | string) => {
    return axiosClient.patch<unknown, ApiResponse<DeliveryBatch>>(`/batches/${batchId}/stops/${stopId}/complete`);
  },

  // Cập nhật trạng thái lượt giao
  updateStatus: (id: number | string, status: DeliveryBatchStatus) => {
    return axiosClient.patch<unknown, ApiResponse<DeliveryBatch>>(`/batches/${id}/status`, null, {
      params: { status },
    });
  },
};
