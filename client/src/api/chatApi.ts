import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type {
  ChatMessage,
  ChatRoom,
  SendChatMessageRequest,
} from '../types/chat';

export const chatApi = {
  openRoom: (orderId: number | string) => {
    return axiosClient.post<unknown, ApiResponse<ChatRoom>>(
      `/chat-rooms/orders/${encodeURIComponent(String(orderId))}`,
    );
  },

  getRoom: (roomId: string) => {
    return axiosClient.get<unknown, ApiResponse<ChatRoom>>(
      `/chat-rooms/${encodeURIComponent(roomId)}`,
    );
  },

  getMessages: (
    roomId: string,
    page = 0,
    size = 20,
    signal?: AbortSignal,
  ) => {
    return axiosClient.get<
      unknown,
      ApiResponse<PageResponse<ChatMessage>>
    >(
      `/chat-rooms/${encodeURIComponent(roomId)}/messages`,
      {
        params: { page, size },
        signal,
      },
    );
  },

  sendMessage: (
    roomId: string,
    request: SendChatMessageRequest,
  ) => {
    return axiosClient.post<unknown, ApiResponse<ChatMessage>>(
      `/chat-rooms/${encodeURIComponent(roomId)}/messages`,
      request,
    );
  },
};