import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type { ParameterChange, ParameterHistory, PricingSimulation, SimulationQuote, SystemParameter } from '../types/parameter';

const resource = '/admin/params';

export const parameterApi = {
  getAll: () => axiosClient.get<unknown, ApiResponse<SystemParameter[]>>(resource),
  update: (key: string, change: ParameterChange) =>
    axiosClient.put<unknown, ApiResponse<SystemParameter>>(`${resource}/${encodeURIComponent(key)}`, change),
  updateBatch: (changes: Record<string, ParameterChange>) =>
    axiosClient.put<unknown, ApiResponse<SystemParameter[]>>(`${resource}/batch`, { changes }),
  getHistory: (key: string, page = 0, size = 10) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<ParameterHistory>>>(`${resource}/${encodeURIComponent(key)}/history`, { params: { page, size } }),
  simulate: (quote: SimulationQuote, overrides: Record<string, string> = {}) =>
    axiosClient.post<unknown, ApiResponse<PricingSimulation>>(`${resource}/simulate`, { quote, overrides }),
};
