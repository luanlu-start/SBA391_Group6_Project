import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';

export const INCIDENT_TYPES = {
  TRAFFIC_JAM: 'Kẹt xe',
  DAMAGED_GOODS: 'Hư hỏng hàng',
  SCHEDULE_CHANGE: 'Thay đổi thời gian giao',
  RECIPIENT_UNREACHABLE: 'Không liên lạc được người nhận',
} as const;
export type IncidentType = keyof typeof INCIDENT_TYPES;
export interface DeliveryIncident {
  id: number;
  orderId: number;
  reporterId: number;
  type: IncidentType;
  description: string;
  hasEvidence: boolean;
  createdAt: string;
}

export const incidentApi = {
  report: (orderId: number, type: IncidentType, description: string, photo: File | null) => {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify({ type, description })], { type: 'application/json' }));
    if (photo) form.append('photo', photo);
    return axiosClient.post<unknown, ApiResponse<DeliveryIncident>>(`/orders/${orderId}/incidents`, form,
      { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  history: (orderId: number, page: number) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<DeliveryIncident>>>(`/orders/${orderId}/incidents`,
      { params: { page, size: 10 } }),
  evidence: (orderId: number, incidentId: number) =>
    axiosClient.get<unknown, Blob>(`/orders/${orderId}/incidents/${incidentId}/evidence`, { responseType: 'blob' }),
};
