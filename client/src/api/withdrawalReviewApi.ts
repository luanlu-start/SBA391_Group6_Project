import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type { AdminWithdrawal, WithdrawalImportResult, WithdrawalQueueQuery } from '../types/withdrawalReview';

const root = '/admin/withdrawals';
const itemPath = (id: number) => `${root}/${encodeURIComponent(String(id))}`;

export const withdrawalReviewApi = {
  list: (query: WithdrawalQueueQuery, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<AdminWithdrawal>>>(root, { params: query, signal }),
  get: (id: number, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<AdminWithdrawal>>(itemPath(id), { signal }),
  approve: (id: number) =>
    axiosClient.post<unknown, ApiResponse<AdminWithdrawal>>(`${itemPath(id)}/approve`),
  reject: (id: number, reason: string) =>
    axiosClient.post<unknown, ApiResponse<AdminWithdrawal>>(`${itemPath(id)}/reject`, { reason }),
  markPaid: (id: number, reference: string) =>
    axiosClient.post<unknown, ApiResponse<AdminWithdrawal>>(`${itemPath(id)}/mark-paid`, { reference }),
  markFailed: (id: number, reason: string) =>
    axiosClient.post<unknown, ApiResponse<AdminWithdrawal>>(`${itemPath(id)}/mark-failed`, { reason }),
  exportApproved: () =>
    axiosClient.post<unknown, Blob>(`${root}/export`, {}, { responseType: 'blob' }),
  importResult: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return axiosClient.post<unknown, ApiResponse<WithdrawalImportResult>>(`${root}/import-result`, form);
  },
};
