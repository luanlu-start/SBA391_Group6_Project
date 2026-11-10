import axiosClient from './axiosClient';
import { ApiError } from './ApiError';
import { apiErrorMessages } from '../i18n/apiErrors';
import type { ApiResponse } from '../types/api';

export interface DailyPoint {
  date: string;
  ordersCreated: number;
  escrowReleasedAmount: number;
}

export interface Dashboard {
  from: string;
  to: string;
  ordersCreated: number;
  ordersByStatus: Record<string, number>;
  cancellationRate: number;
  newUsers: number;
  newCouriers: number;
  activeCouriers: number;
  escrowReleasedAmount: number;
  escrowReleasedCount: number;
  estimatedCommission: number;
  topUpAmount: number;
  withdrawalRequestedAmount: number;
  daily: DailyPoint[];
}

export interface CourierRevenue {
  courierPublicId: string | null;
  fullName: string | null;
  email: string | null;
  releasedOrders: number;
  grossAmount: number;
  commission: number;
  withholding: number;
  netIncome: number;
}

export type ReportType = 'ORDERS' | 'DASHBOARD' | 'COURIER_REVENUE';

export interface ReportFile {
  blob: Blob;
  fileName: string;
}

interface ExportParams {
  from?: string;
  to?: string;
  month?: string;
}

/** Lỗi của API tải file trả về dạng Blob nên cần đọc lại problem JSON để hiện thông điệp tiếng Việt. */
async function blobError(cause: unknown): Promise<unknown> {
  if (!(cause instanceof ApiError)) return cause;
  const data = (cause.cause as { response?: { data?: unknown } } | undefined)?.response?.data;
  if (!(data instanceof Blob)) return cause;
  try {
    const problem = JSON.parse(await data.text()) as { code?: string; detail?: string; title?: string; correlationId?: string };
    const message = (problem.code && apiErrorMessages[problem.code]) || problem.detail || problem.title || cause.message;
    return new ApiError(message, cause.status, problem.code, [], problem.correlationId ?? cause.correlationId, cause.cause);
  } catch {
    return cause;
  }
}

/** Cùng quy ước tên file với máy chủ; interceptor chỉ trả phần thân nên không đọc được Content-Disposition. */
function fileName(type: ReportType, { from, to, month }: ExportParams) {
  if (type === 'COURIER_REVENUE') return `doanh-thu-nguoi-giao-${month}.csv`;
  return `${type === 'ORDERS' ? 'don-hang' : 'tong-quan'}-${from}-${to}.csv`;
}

export const reportApi = {
  dashboard: (from: string, to: string, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<Dashboard>>('/admin/dashboard', { params: { from, to }, signal }),
  courierRevenue: (month: string, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<CourierRevenue[]>>('/admin/reports/courier-revenue', { params: { month }, signal }),
  async export(type: ReportType, params: ExportParams): Promise<ReportFile> {
    try {
      const response = await axiosClient.get<unknown, Blob>('/admin/reports/export', {
        params: { type, ...params }, responseType: 'blob', timeout: 60000,
        headers: { Accept: 'text/csv, application/problem+json' },
      });
      return { blob: response, fileName: fileName(type, params) };
    } catch (cause) {
      throw await blobError(cause);
    }
  },
};

export function saveReport(file: ReportFile) {
  const url = URL.createObjectURL(file.blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = file.fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
