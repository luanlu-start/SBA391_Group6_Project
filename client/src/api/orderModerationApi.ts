import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type {
  ModerationKeyword, ModerationKeywordLevel, OrderModerationItem, OrderModerationOutcome, OrderModerationQuery,
} from '../types/orderModeration';

const queue = '/mod/orders';
const keywords = '/admin/moderation-keywords';
const orderPath = (id: number) => `${queue}/${encodeURIComponent(String(id))}`;

export const orderModerationApi = {
  pending: (query: OrderModerationQuery, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<OrderModerationItem>>>(`${queue}/pending`, { params: query, signal }),
  get: (orderId: number, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<OrderModerationItem>>(orderPath(orderId), { signal }),
  approve: (orderId: number) =>
    axiosClient.post<unknown, ApiResponse<OrderModerationItem>>(`${orderPath(orderId)}/approve`),
  reject: (orderId: number, reason: string) =>
    axiosClient.post<unknown, ApiResponse<OrderModerationItem>>(`${orderPath(orderId)}/reject`, { reason }),
  outcome: (orderId: number, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<OrderModerationOutcome>>(`/orders/${encodeURIComponent(String(orderId))}/moderation`, { signal }),
  keywords: (signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<ModerationKeyword[]>>(keywords, { signal }),
  addKeyword: (term: string, level: ModerationKeywordLevel) =>
    axiosClient.post<unknown, ApiResponse<ModerationKeyword>>(keywords, { term, level }),
  removeKeyword: (id: number) =>
    axiosClient.delete<unknown, void>(`${keywords}/${encodeURIComponent(String(id))}`),
};
