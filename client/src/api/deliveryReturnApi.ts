import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type { OrderStatus } from '../types/order';

export interface DeliveryReturnDetail {
  orderId: number;
  orderStatus: OrderStatus;
  canReport: boolean;
  canConfirm: boolean;
  canResume: boolean;
  requiredCalls: number;
  contactWindowMinutes: number;
  contacts: { id: number; note: string; createdAt: string }[];
  latestCase: {
    id: number; state: 'RETURNING' | 'RESUMED' | 'RETURNED'; reason: 'RECIPIENT_UNREACHABLE'; description: string;
    createdAt: string; resolvedAt: string | null; deliveryFee: number; returnFee: number; returnRate: number;
    financialSettlementPending: boolean;
  } | null;
  history: { id: number; returnCaseId: number | null; action: string; fromStatus: OrderStatus; toStatus: OrderStatus;
    note: string; createdAt: string }[];
}
const path = (id: number) => `/orders/${id}`;
export const deliveryReturnApi = {
  detail: (id: number) => axiosClient.get<unknown, ApiResponse<DeliveryReturnDetail>>(`${path(id)}/return`),
  contact: (id: number, requestId: string, note: string) =>
    axiosClient.post<unknown, ApiResponse<DeliveryReturnDetail>>(`${path(id)}/delivery-contact-attempts`, { requestId, note }),
  report: (id: number, description: string, photo: File) => {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify({ reason: 'RECIPIENT_UNREACHABLE', description })], { type: 'application/json' }));
    form.append('photo', photo);
    return axiosClient.post<unknown, ApiResponse<DeliveryReturnDetail>>(`${path(id)}/delivery-failed`, form,
      { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  resume: (id: number, description: string) =>
    axiosClient.post<unknown, ApiResponse<DeliveryReturnDetail>>(`${path(id)}/return/resume`, { description }),
  issueCode: (id: number) =>
    axiosClient.post<unknown, ApiResponse<{ code: string; expiresAt: string }>>(`${path(id)}/return/code`),
  confirm: (id: number, code: string, received: boolean) =>
    axiosClient.post<unknown, ApiResponse<DeliveryReturnDetail>>(`${path(id)}/return/confirm`, { code, received }),
  evidence: (id: number, caseId: number) =>
    axiosClient.get<unknown, Blob>(`${path(id)}/return/${caseId}/evidence`, { responseType: 'blob' }),
};
