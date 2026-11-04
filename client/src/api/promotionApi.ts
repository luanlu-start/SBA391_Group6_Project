import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type { Promotion, PromotionQuery, PromotionTargetOptions, PromotionUpdate, PromotionWrite } from '../types/promotion';

export const promotionApi = {
  list: (query: PromotionQuery, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<Promotion>>>('/admin/promotions', { params: query, signal }),
  get: (id: string, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<Promotion>>(`/admin/promotions/${encodeURIComponent(id)}`, { signal }),
  create: (request: PromotionWrite) => axiosClient.post<unknown, ApiResponse<Promotion>>('/admin/promotions', request),
  update: (id: string, request: PromotionUpdate) =>
    axiosClient.put<unknown, ApiResponse<Promotion>>(`/admin/promotions/${encodeURIComponent(id)}`, request),
  targetOptions: (q: string, signal?: AbortSignal) => axiosClient.get<unknown, ApiResponse<PromotionTargetOptions>>(
    '/admin/promotions/target-options', { params: { q: q || undefined, limit: 30 }, signal }),
};
