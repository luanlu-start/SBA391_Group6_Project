import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type { AuditLog, AuditQuery } from '../types/audit';

export const auditApi = {
  getLogs: (query: AuditQuery, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<AuditLog>>>('/admin/audit-logs', { params: query, signal }),
};
