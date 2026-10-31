import { CanceledError } from 'axios';
import type { AxiosRequestTransformer } from 'axios';
import axiosClient from './axiosClient';
import { getSessionState } from '../auth/authSession';
import type { ApiResponse, PageResponse } from '../types/api';
import type { Notification, NotificationSetting, NotificationSettings } from '../types/notification';

function ownerRequest(signal?: AbortSignal) {
  const owner = getSessionState().user?.id;
  const guard: AxiosRequestTransformer = data => {
    if (!owner || getSessionState().user?.id !== owner) throw new CanceledError('Notification request owner changed');
    return data;
  };
  const defaults = axiosClient.defaults.transformRequest;
  return { signal, transformRequest: [guard, ...(Array.isArray(defaults) ? defaults : defaults ? [defaults] : [])] };
}

export const notificationApi = {
  list: (query: { unreadOnly: boolean; page: number; size: number }, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<Notification>>>('/notifications', { ...ownerRequest(signal), params: query }),
  unreadCount: (signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<{ count: number }>>('/notifications/unread-count', ownerRequest(signal)),
  read: (id: string, signal?: AbortSignal) =>
    axiosClient.post<unknown, ApiResponse<Notification>>(`/notifications/${encodeURIComponent(id)}/read`, {}, ownerRequest(signal)),
  readAll: (signal?: AbortSignal) =>
    axiosClient.post<unknown, ApiResponse<{ updatedCount: number }>>('/notifications/read-all', {}, ownerRequest(signal)),
  settings: (signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<NotificationSettings>>('/me/notification-settings', ownerRequest(signal)),
  saveSettings: (settings: NotificationSetting[], signal?: AbortSignal) =>
    axiosClient.put<unknown, ApiResponse<NotificationSettings>>('/me/notification-settings', {
      settings: settings.map(({ category, channels }) => ({ category, channels })),
    }, ownerRequest(signal)),
};
