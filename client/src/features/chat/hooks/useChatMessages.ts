import { useCallback, useEffect, useRef, useState } from 'react';
import { chatApi } from '../../../api/chatApi';
import type {
  ChatMessage,
  SendChatMessageRequest,
} from '../../../types/chat';

function mergeMessages(
  current: ChatMessage[],
  incoming: ChatMessage[],
): ChatMessage[] {
  const messages = new Map(
    current.map((message) => [message.id, message]),
  );

  incoming.forEach((message) => {
    messages.set(message.id, message);
  });

  return [...messages.values()].sort((left, right) => {
    const timeDifference =
      Date.parse(left.createdAt) - Date.parse(right.createdAt);

    return timeDifference || left.id.localeCompare(right.id);
  });
}

function errorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : 'Không thể xử lý yêu cầu. Vui lòng thử lại.';
}

export function useChatMessages(roomId: string | null) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [sendError, setSendError] = useState<string | null>(null);
  const [failedMessage, setFailedMessage] =
    useState<SendChatMessageRequest | null>(null);

  const generation = useRef(0);
  const historyVersion = useRef(0);
  const nextPage = useRef(0);
  const historyBusy = useRef(false);
  const sendBusy = useRef(false);
  const pendingMessage = useRef<SendChatMessageRequest | null>(null);
  const liveMessages = useRef<ChatMessage[]>([]);

  useEffect(() => {
    generation.current += 1;
    historyVersion.current += 1;
    nextPage.current = 0;
    historyBusy.current = false;
    sendBusy.current = false;
    pendingMessage.current = null;
    liveMessages.current = [];

    setMessages([]);
    setLoading(false);
    setSending(false);
    setHasMore(false);
    setHistoryError(null);
    setSendError(null);
    setFailedMessage(null);

    return () => {
      generation.current += 1;
      historyVersion.current += 1;
    };
  }, [roomId]);

  const receiveMessage = useCallback((message: ChatMessage) => {
    if (message.roomId !== roomId) return;

    liveMessages.current = mergeMessages(
      liveMessages.current,
      [message],
    );

    setMessages((current) => mergeMessages(current, [message]));
  }, [roomId]);

  const loadHistory = useCallback(async (reset: boolean) => {
    if (!roomId || (!reset && historyBusy.current)) return;

    const currentGeneration = generation.current;
    const requestVersion = ++historyVersion.current;
    const page = reset ? 0 : nextPage.current;

    historyBusy.current = true;
    setLoading(true);
    setHistoryError(null);

    if (reset) {
      liveMessages.current = [];
    }

    try {
      const response = await chatApi.getMessages(roomId, page, 50);

      if (currentGeneration !== generation.current
        || requestVersion !== historyVersion.current) {
        return;
      }

      const history = response.data;

      if (reset) {
        setMessages(
          mergeMessages(history.content, liveMessages.current),
        );
      } else {
        setMessages((current) =>
          mergeMessages(current, history.content),
        );
      }

      nextPage.current = page + 1;
      setHasMore(!history.last);
    } catch (error) {
      if (currentGeneration === generation.current
        && requestVersion === historyVersion.current) {
        setHistoryError(errorMessage(error));
      }
    } finally {
      if (currentGeneration === generation.current
        && requestVersion === historyVersion.current) {
        historyBusy.current = false;
        setLoading(false);
      }
    }
  }, [roomId]);

  const refreshHistory = useCallback(
    () => loadHistory(true),
    [loadHistory],
  );

  const loadOlder = useCallback(
    () => loadHistory(false),
    [loadHistory],
  );

  useEffect(() => {
    void refreshHistory();
  }, [refreshHistory]);

  const sendMessage = useCallback(async (
    content: string,
  ): Promise<boolean> => {
    if (!roomId || sendBusy.current) return false;

    const normalizedContent = content.trim();

    if (!normalizedContent || content.length > 2000) {
      setSendError('Tin nhắn phải có nội dung và tối đa 2.000 ký tự.');
      return false;
    }

    const pending = pendingMessage.current;

    // Giữ nội dung tin lỗi để thử lại với cùng clientMessageId.
    if (pending && pending.content !== normalizedContent) {
      setSendError('Hãy gửi lại tin chưa được xác nhận trước.');
      return false;
    }

    const request: SendChatMessageRequest = pending ?? {
      clientMessageId: crypto.randomUUID(),
      content: normalizedContent,
    };

    const currentGeneration = generation.current;

    pendingMessage.current = request;
    sendBusy.current = true;
    setSending(true);
    setSendError(null);

    try {
      const response = await chatApi.sendMessage(roomId, request);

      if (currentGeneration !== generation.current) return false;

      receiveMessage(response.data);
      pendingMessage.current = null;
      setFailedMessage(null);

      return true;
    } catch (error) {
      if (currentGeneration === generation.current) {
        setFailedMessage(request);
        setSendError(errorMessage(error));
      }

      return false;
    } finally {
      if (currentGeneration === generation.current) {
        sendBusy.current = false;
        setSending(false);
      }
    }
  }, [roomId, receiveMessage]);

  const retryMessage = useCallback(async (): Promise<boolean> => {
    const pending = pendingMessage.current;

    return pending ? sendMessage(pending.content) : false;
  }, [sendMessage]);

  return {
    messages,
    loading,
    sending,
    hasMore,
    historyError,
    sendError,
    failedMessage,
    receiveMessage,
    refreshHistory,
    loadOlder,
    sendMessage,
    retryMessage,
  };
}