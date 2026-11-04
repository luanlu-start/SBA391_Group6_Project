import { Client } from '@stomp/stompjs';
import axiosClient from '../../../api/axiosClient';
import type { ChatMessage } from '../../../types/chat';

interface ChatSocketOptions {
  roomId: string;
  accessToken: string;
  onMessage: (message: ChatMessage) => void;
  onConnected: () => void;
  onDisconnected: () => void;
  onError: (message: string) => void;
}

function getWebSocketUrl(): string {
  const configuredUrl = import.meta.env.VITE_CHAT_WS_URL;

  if (configuredUrl) {
    return new URL(configuredUrl, window.location.origin).toString();
  }

  const apiUrl = new URL(
    axiosClient.defaults.baseURL || '/api/v1',
    window.location.origin,
  );

  const socketUrl = new URL('/ws/chat', apiUrl);

  socketUrl.protocol = socketUrl.protocol === 'https:' ? 'wss:' : 'ws:';

  return socketUrl.toString();
}

function isChatMessage(value: unknown): value is ChatMessage {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const message = value as Record<string, unknown>;

  return typeof message.id === 'string'
    && typeof message.roomId === 'string'
    && typeof message.authorId === 'number'
    && typeof message.clientMessageId === 'string'
    && typeof message.content === 'string'
    && typeof message.createdAt === 'string';
}

export function createChatSocket(options: ChatSocketOptions): Client {
  const client = new Client({
    brokerURL: getWebSocketUrl(),

    connectHeaders: {
      Authorization: `Bearer ${options.accessToken}`,
    },

    reconnectDelay: 5000,
    connectionTimeout: 10000,

    // Backend chưa cấu hình heartbeat cho simple broker.
    heartbeatIncoming: 0,
    heartbeatOutgoing: 0,

    // Không ghi frame CONNECT chứa token ra console.
    debug: () => {},
  });

  client.onConnect = () => {
    client.subscribe(
      `/topic/chat-rooms/${options.roomId}`,
      (frame) => {
        let payload: unknown;

        try {
          payload = JSON.parse(frame.body);
        } catch {
          options.onError('Không đọc được dữ liệu tin nhắn.');
          return;
        }

        if (!isChatMessage(payload)
          || payload.roomId !== options.roomId) {
          options.onError('Dữ liệu tin nhắn không hợp lệ.');
          return;
        }

        options.onMessage(payload);
      },
    );

    options.onConnected();
  };

  client.onWebSocketClose = () => {
    options.onDisconnected();
  };

  client.onWebSocketError = () => {
    options.onError('Không thể kết nối trò chuyện thời gian thực.');
  };

  client.onStompError = () => {
    options.onError(
      'Máy chủ từ chối kết nối hoặc quyền nhận tin của cuộc trò chuyện.',
    );

    // Không lặp kết nối vô hạn khi server từ chối quyền.
    void client.deactivate();
  };

  return client;
}