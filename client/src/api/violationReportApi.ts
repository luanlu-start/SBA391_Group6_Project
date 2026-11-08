import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type {
  MyViolationReport, ViolationAction, ViolationCase, ViolationCaseStatus, ViolationReason, ViolationTargetType,
} from '../types/violationReport';

const casePath = (id: string) => `/mod/reports/${encodeURIComponent(id)}`;

export const violationReportApi = {
  submit: (input: { targetType: ViolationTargetType; targetId: string; reason: ViolationReason; description?: string }, files: File[]) => {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify(input)], { type: 'application/json' }));
    files.forEach(file => form.append('files', file));
    return axiosClient.post<unknown, ApiResponse<MyViolationReport>>('/reports', form);
  },
  mine: (page = 0) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<MyViolationReport>>>('/me/reports', { params: { page, size: 20 } }),
  queue: (status: ViolationCaseStatus, targetType: ViolationTargetType | undefined, page: number, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<ViolationCase>>>('/mod/reports', { params: { status, targetType, page, size: 20 }, signal }),
  get: (id: string, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<ViolationCase>>(casePath(id), { signal }),
  evidence: (id: string, evidenceId: string, signal?: AbortSignal) =>
    axiosClient.get<unknown, Blob>(`${casePath(id)}/evidence/${encodeURIComponent(evidenceId)}`, { responseType: 'blob', signal }),
  resolve: (id: string, action: ViolationAction, note: string) =>
    axiosClient.post<unknown, ApiResponse<ViolationCase>>(`${casePath(id)}/resolve`, { action, note }),
};
