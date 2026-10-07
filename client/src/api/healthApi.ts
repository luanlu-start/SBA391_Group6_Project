import axiosClient from './axiosClient';
import type { ApiResponse, HealthInfo } from '../types/api';

export const healthApi = {
  getHealth: () => {
    return axiosClient.get<unknown, ApiResponse<HealthInfo>>('/health');
  },
};
