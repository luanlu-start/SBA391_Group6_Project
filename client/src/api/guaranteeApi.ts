import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type { Compensation, CompensationFilter, GuaranteeFund } from '../types/guarantee';

export const guaranteeApi = {
  overview: (signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<GuaranteeFund>>('/admin/guarantee-fund', { signal }),
  compensations: (status: CompensationFilter, page: number, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<Compensation>>>('/admin/guarantee-fund/compensations',
      { params: { status, page, size: 20 }, signal }),
  contribute: (amount: number, note: string) =>
    axiosClient.post<unknown, ApiResponse<GuaranteeFund>>('/admin/guarantee-fund/contributions', { amount, note }),
  decide: (complaintId: string, input: { decision: 'APPROVE' | 'REJECT'; amount?: number; note?: string }) =>
    axiosClient.post<unknown, ApiResponse<Compensation>>(`/admin/complaints/${encodeURIComponent(complaintId)}/compensation`, input),
};
