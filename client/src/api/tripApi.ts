import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type {
  Trip,
  TripCreateRequest,
  TripUpdateRequest,
  TripStatus,
  TripAttachedOrder,
  PageResponse,
} from '../types/trip';

export const tripApi = {
  create: (data: TripCreateRequest) => {
    return axiosClient.post<unknown, ApiResponse<Trip>>('/trips', data);
  },

  getMyTrips: (params?: { status?: TripStatus; page?: number; size?: number }) => {
    return axiosClient.get<unknown, ApiResponse<PageResponse<Trip>>>('/trips', { params });
  },

  getById: (id: number | string) => {
    return axiosClient.get<unknown, ApiResponse<Trip>>(`/trips/${id}`);
  },

  update: (id: number | string, data: TripUpdateRequest) => {
    return axiosClient.put<unknown, ApiResponse<Trip>>(`/trips/${id}`, data);
  },

  togglePause: (id: number | string) => {
    return axiosClient.post<unknown, ApiResponse<Trip>>(`/trips/${id}/pause`);
  },

  cancel: (id: number | string) => {
    return axiosClient.post<unknown, ApiResponse<Trip>>(`/trips/${id}/cancel`);
  },

  complete: (id: number | string) => {
    return axiosClient.post<unknown, ApiResponse<Trip>>(`/trips/${id}/complete`);
  },

  getAttachedOrders: (id: number | string) => {
    return axiosClient.get<unknown, ApiResponse<TripAttachedOrder[]>>(`/trips/${id}/orders`);
  },

  getSuggestedOrders: (id: number | string) => {
    return axiosClient.get<unknown, ApiResponse<TripAttachedOrder[]>>(`/trips/${id}/suggested-orders`);
  },
};
