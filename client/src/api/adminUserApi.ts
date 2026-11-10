import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type {
  AccountRequestItem, AccountRequestStatus, AccountRole, AdminUserDetail, AdminUserQuery, AdminUserSummary, BlacklistKind,
  StaffCreationInput,
} from '../types/adminUsers';

const userPath = (publicId: string) => `/admin/users/${encodeURIComponent(publicId)}`;
const requestPath = (id: string) => `/admin/account-requests/${encodeURIComponent(id)}`;

export const adminUserApi = {
  search: (query: AdminUserQuery, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<AdminUserSummary>>>('/admin/users', { params: query, signal }),
  get: (publicId: string, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<AdminUserDetail>>(userPath(publicId), { signal }),
  updateRoles: (publicId: string, roles: AccountRole[]) =>
    axiosClient.put<unknown, ApiResponse<AdminUserDetail>>(`${userPath(publicId)}/roles`, { roles }),
  staff: (page = 0, size = 50, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<AdminUserSummary>>>('/admin/staff', { params: { page, size }, signal }),
  createStaff: (input: StaffCreationInput) =>
    axiosClient.post<unknown, ApiResponse<AdminUserSummary>>('/admin/staff', input),
  accountRequests: (status: AccountRequestStatus | 'ALL', page = 0, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<AccountRequestItem>>>('/admin/account-requests', { params: { status, page, size: 20 }, signal }),
  completeRequest: (id: string) =>
    axiosClient.post<unknown, ApiResponse<AccountRequestItem>>(`${requestPath(id)}/complete`),
  rejectRequest: (id: string, reason: string) =>
    axiosClient.post<unknown, ApiResponse<AccountRequestItem>>(`${requestPath(id)}/reject`, { reason }),
  previewExport: (id: string) =>
    axiosClient.get<unknown, Blob>(`${requestPath(id)}/export`, { responseType: 'blob' }),
  lock: (publicId: string, reason: string, durationHours: number | null, blacklistPhone: boolean) =>
    axiosClient.post<unknown, ApiResponse<AdminUserDetail>>(`${userPath(publicId)}/lock`, { reason, durationHours, blacklistPhone }),
  unlock: (publicId: string, reason: string) =>
    axiosClient.post<unknown, ApiResponse<AdminUserDetail>>(`${userPath(publicId)}/unlock`, { reason }),
  blacklist: (kind: BlacklistKind, value: string, reason: string) =>
    axiosClient.post<unknown, void>('/admin/blacklist', { kind, value, reason }),
};
