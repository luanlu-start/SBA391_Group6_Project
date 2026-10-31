import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type { Trip, TripCreateRequest, TripStatus, PageResponse } from '../types/trip';

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

  cancel: (id: number | string) => {
    return axiosClient.post<unknown, ApiResponse<Trip>>(`/trips/${id}/cancel`);
  },
};
