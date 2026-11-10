import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type {
  ReconciliationReportResponse,
  ReconciliationItemResponse,
  ReconciliationAdjustRequest,
  LedgerIntegrityResponse,
} from '../types/payment';

export const reconciliationApi = {
  getReports: (page = 0, size = 10) => {
    return axiosClient.get<unknown, ApiResponse<PageResponse<ReconciliationReportResponse>>>(
      '/admin/reconciliation',
      { params: { page, size } }
    );
  },

  getReportById: (id: number) => {
    return axiosClient.get<unknown, ApiResponse<ReconciliationReportResponse>>(
      `/admin/reconciliation/${id}`
    );
  },

  runReconciliation: (reportDate?: string) => {
    return axiosClient.post<unknown, ApiResponse<ReconciliationReportResponse>>(
      '/admin/reconciliation/run',
      reportDate ? { reportDate } : {}
    );
  },

  adjustItem: (itemId: number, data: ReconciliationAdjustRequest) => {
    return axiosClient.post<unknown, ApiResponse<ReconciliationItemResponse>>(
      `/admin/reconciliation/items/${itemId}/adjust`,
      data
    );
  },

  checkLedgerIntegrity: () => {
    return axiosClient.get<unknown, ApiResponse<LedgerIntegrityResponse>>(
      '/admin/ledger/integrity'
    );
  },
};
