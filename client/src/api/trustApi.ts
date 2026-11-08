import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type { CourierTrust, MyTrust, TrustFilter } from '../types/trust';

export const trustApi = {
  mine: (signal?: AbortSignal) => axiosClient.get<unknown, ApiResponse<MyTrust>>('/me/trust', { signal }),
  couriers: (filter: TrustFilter, page: number, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<CourierTrust>>>('/admin/trust', { params: { filter, page, size: 20 }, signal }),
  restrict: (courierId: string, days: number, reason: string) =>
    axiosClient.post<unknown, ApiResponse<CourierTrust>>(`/admin/trust/${encodeURIComponent(courierId)}/restrict`, { days, reason }),
  recalculate: () => axiosClient.post<unknown, ApiResponse<{ couriers: number }>>('/admin/trust/recalculate'),
};
