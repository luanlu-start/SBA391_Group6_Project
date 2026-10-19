import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type { AuditFilterOptions, AuditFilterOptionsQuery, AuditLog, AuditQuery } from '../types/audit';

export const auditApi = {
  getLogs: (query: AuditQuery, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<AuditLog>>>('/admin/audit-logs', { params: query, signal }),
  getFilterOptions: (query: AuditFilterOptionsQuery, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<AuditFilterOptions>>('/admin/audit-logs/filter-options', { params: query, signal }),
};
