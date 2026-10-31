import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type { ItemCategory, CategoryRequest } from '../types/category';

export const categoryApi = {
  /**
   * Lấy danh sách loại hàng hóa đang hoạt động (dành cho người gửi tạo đơn).
   */
  getActiveList: () =>
    axiosClient.get<unknown, ApiResponse<ItemCategory[]>>('/categories'),

  /**
   * Lấy toàn bộ danh sách loại hàng hóa quản trị (API-148, Admin only).
   */
  adminListAll: () =>
    axiosClient.get<unknown, ApiResponse<ItemCategory[]>>('/admin/categories'),

  /**
   * Lấy thông tin chi tiết một loại hàng hóa (Admin only).
   */
  adminGetById: (id: number) =>
    axiosClient.get<unknown, ApiResponse<ItemCategory>>(`/admin/categories/${id}`),

  /**
   * Tạo mới loại hàng hóa (Admin only).
   */
  adminCreate: (request: CategoryRequest) =>
    axiosClient.post<unknown, ApiResponse<ItemCategory>>('/admin/categories', request),

  /**
   * Cập nhật loại hàng hóa (API-149, Admin only).
   */
  adminUpdate: (id: number, request: CategoryRequest) =>
    axiosClient.put<unknown, ApiResponse<ItemCategory>>(`/admin/categories/${id}`, request),

  /**
   * Xóa hoặc ẩn loại hàng hóa theo quy tắc A1 (Admin only).
   */
  adminDelete: (id: number) =>
    axiosClient.delete<unknown, ApiResponse<{ hardDeleted: boolean; message: string }>>(`/admin/categories/${id}`),

  /**
   * Bật / Tắt nhanh trạng thái hoạt động (Admin only).
   */
  adminToggleActive: (id: number) =>
    axiosClient.patch<unknown, ApiResponse<ItemCategory>>(`/admin/categories/${id}/toggle-active`),
};
