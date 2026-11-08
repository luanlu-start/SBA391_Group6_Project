import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type {
  AppealReviewInput, ComplaintChatHistory, ComplaintDetail, ComplaintQueueFilter, ComplaintRulingInput, ComplaintSummary,
} from '../types/complaint';

const complaintPath = (id: string) => `/complaints/${encodeURIComponent(id)}`;
const reviewPath = (id: string) => `/mod/complaints/${encodeURIComponent(id)}`;

export const complaintApi = {
  mine: (page: number, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<ComplaintSummary>>>('/complaints', { params: { page, size: 20 }, signal }),
  get: (id: string, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<ComplaintDetail>>(complaintPath(id), { signal }),
  evidence: (id: string, evidenceId: string, signal?: AbortSignal) =>
    axiosClient.get<unknown, Blob>(`${complaintPath(id)}/evidence/${encodeURIComponent(evidenceId)}`, { responseType: 'blob', signal }),
  message: (id: string, body: string, files: File[]) => {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify({ body })], { type: 'application/json' }));
    files.forEach(file => form.append('files', file));
    return axiosClient.post<unknown, ApiResponse<ComplaintDetail>>(`${complaintPath(id)}/messages`, form);
  },
  appeal: (id: string, reason: string) =>
    axiosClient.post<unknown, ApiResponse<ComplaintDetail>>(`${complaintPath(id)}/appeal`, { reason }),
  queue: (status: ComplaintQueueFilter, mine: boolean, page: number, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<ComplaintSummary>>>('/mod/complaints', { params: { status, mine, page, size: 20 }, signal }),
  chat: (id: string, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<ComplaintChatHistory>>(`${reviewPath(id)}/chat`, { signal, timeout: 40_000 }),
  claim: (id: string) =>
    axiosClient.post<unknown, ApiResponse<ComplaintDetail>>(`${reviewPath(id)}/claim`),
  requestInfo: (id: string, message: string) =>
    axiosClient.post<unknown, ApiResponse<ComplaintDetail>>(`${reviewPath(id)}/request-info`, { message }),
  rule: (id: string, input: ComplaintRulingInput) =>
    axiosClient.post<unknown, ApiResponse<ComplaintDetail>>(`${reviewPath(id)}/ruling`, input),
  reviewAppeal: (id: string, input: AppealReviewInput) =>
    axiosClient.post<unknown, ApiResponse<ComplaintDetail>>(`/admin/complaints/${encodeURIComponent(id)}/appeal-review`, input),
};
