import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type { CourierEarningsResponse, CourierStatsResponse } from '../types/payment';

export const earningsApi = {
  getMyEarnings: (params?: { period?: string; from?: string; to?: string }) => {
    return axiosClient.get<unknown, ApiResponse<CourierEarningsResponse>>(
      '/me/earnings',
      { params }
    );
  },

  getMyStats: () => {
    return axiosClient.get<unknown, ApiResponse<CourierStatsResponse>>(
      '/me/stats'
    );
  },
};
