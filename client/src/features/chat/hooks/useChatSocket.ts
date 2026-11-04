import {
  useCallback,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
} from 'react';
import {
  getAccessToken,
  getAccessTokenExpiresAt,
  subscribeSession,
} from '../../../auth/authSession';
import { authService } from '../../../services/authService';
import type { ChatMessage } from '../../../types/chat';
import { createChatSocket } from '../services/chatSocket';

type ConnectionStatus =
  | 'disconnected'
  | 'connecting'
  | 'connected'
  | 'error';

interface UseChatSocketOptions {
  roomId: string | null;
  onMessage: (message: ChatMessage) => void;
  onConnected: () => void;
}

export function useChatSocket({
  roomId,
  onMessage,
  onConnected,
}: UseChatSocketOptions) {
  const accessToken = useSyncExternalStore(
    subscribeSession,
    getAccessToken,
    getAccessToken,
  );

  const [status, setStatus] =
    useState<ConnectionStatus>('disconnected');

  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  const callbacks = useRef({ onMessage, onConnected });

  useEffect(() => {
    callbacks.current = { onMessage, onConnected };
  }, [onMessage, onConnected]);

  const reconnect = useCallback(() => {
    setAttempt((value) => value + 1);
  }, []);

  useEffect(() => {
    if (!roomId || !accessToken) {
      setStatus('disconnected');
      setError(null);
      return;
    }

    const expiresAt = getAccessTokenExpiresAt();
    const expiresAtMs = expiresAt ? Date.parse(expiresAt) : NaN;

    if (!Number.isFinite(expiresAtMs)) {
      setStatus('error');
      setError('Không xác định được thời hạn đăng nhập. Vui lòng đăng nhập lại.');
      return;
    }

    let disposed = false;
    let refreshTimer: ReturnType<typeof setTimeout> | undefined;

    setStatus('connecting');
    setError(null);

    const client = createChatSocket({
      roomId,
      accessToken,

      onMessage: (message) => {
        if (!disposed) {
          callbacks.current.onMessage(message);
        }
      },

      onConnected: () => {
        if (disposed) return;

        setStatus('connected');
        setError(null);
        callbacks.current.onConnected();
      },

      onDisconnected: () => {
        if (disposed) return;

        setStatus((current) =>
          current === 'error' ? current : 'disconnected',
        );
      },

      onError: (message) => {
        if (disposed) return;

        setStatus('error');
        setError(message);
      },
    });

    async function refreshToken() {
      try {
        await authService.refresh();

        // refresh() cập nhật authSession.
        // Token mới làm effect chạy lại và tạo kết nối mới.
      } catch {
        if (disposed) return;

        setStatus('error');
        setError(
          'Không thể làm mới phiên trò chuyện. Hãy thử kết nối lại hoặc đăng nhập lại.',
        );

        void client.deactivate();
      }
    }

    const refreshDelay = expiresAtMs - Date.now() - 30000;

    if (refreshDelay <= 0) {
      // Không kết nối bằng token đã hết hạn hoặc sắp hết hạn.
      void refreshToken();
    } else {
      client.activate();

      refreshTimer = setTimeout(() => {
        if (!disposed) {
          void refreshToken();
        }
      }, refreshDelay);
    }

    return () => {
      disposed = true;

      if (refreshTimer !== undefined) {
        clearTimeout(refreshTimer);
      }

      void client.deactivate();
    };
  }, [roomId, accessToken, attempt]);

  return {
    status,
    error,
    reconnect,
  };
}