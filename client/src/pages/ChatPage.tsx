import { useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { chatApi } from '../api/chatApi';
import { useAuthentication } from '../auth/useAuthentication';
import Alert from '../components/ui/Alert';
import Button from '../components/ui/Button';
import EmptyState from '../components/ui/EmptyState';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Textarea from '../components/ui/Textarea';
import { useChatMessages } from '../features/chat/hooks/useChatMessages';
import { useChatSocket } from '../features/chat/hooks/useChatSocket';
import {
  chatMessageSchema,
  type ChatMessageFormValues,
} from '../features/chat/schemas/chatMessageSchema';
import type { ChatRoom } from '../types/chat';
import '../styles/chat.css';

export default function ChatPage() {
  const { orderId } = useParams();
  const { user, initializing } = useAuthentication();

  if (initializing) {
    return <Alert>Đang kiểm tra phiên đăng nhập…</Alert>;
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (!orderId) {
    return <Alert tone="danger">Không xác định được đơn hàng.</Alert>;
  }

  return (
    <OrderConversation
      key={`${user.id}:${orderId}`}
      orderId={orderId}
    />
  );
}

function OrderConversation({ orderId }: { orderId: string }) {
  const [room, setRoom] = useState<ChatRoom | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;

    setLoading(true);
    setError(null);
    setRoom(null);

    chatApi.openRoom(orderId)
      .then((response) => {
        if (!cancelled) setRoom(response.data);
      })
      .catch((cause: unknown) => {
        if (!cancelled) {
          setError(
            cause instanceof Error
              ? cause.message
              : 'Không thể mở cuộc trò chuyện.',
          );
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [orderId, attempt]);

  return (
    <div className="chat-page">
      <PageHeader
        eyebrow="Trao đổi giao nhận"
        title={`Trò chuyện đơn #${orderId}`}
        description="Trao đổi thông tin lấy hàng và giao hàng trong cùng đơn."
        actions={<Link to="/">Về trang chủ</Link>}
      />

      {loading && <Alert>Đang mở cuộc trò chuyện…</Alert>}

      {error && (
        <Alert
          tone="danger"
          action={
            <Button
              variant="secondary"
              onClick={() => setAttempt((value) => value + 1)}
            >
              Thử lại
            </Button>
          }
        >
          {error}
        </Alert>
      )}

      {room && <ConversationPanel key={room.id} room={room} />}
    </div>
  );
}

function ConversationPanel({ room }: { room: ChatRoom }) {
    const [currentTime, setCurrentTime] = useState(() => Date.now());

    useEffect(() => {
      if (!room.readOnlyAt || room.readOnly) return;

      const timer = window.setInterval(() => {
        setCurrentTime(Date.now());
      }, 1000);

      return () => window.clearInterval(timer);
    }, [room.readOnlyAt, room.readOnly]);
  const chat = useChatMessages(room.id);

  const socket = useChatSocket({
    roomId: room.id,
    onMessage: chat.receiveMessage,
    onConnected: chat.refreshHistory,
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ChatMessageFormValues>({
    resolver: zodResolver(chatMessageSchema),
    defaultValues: { content: '' },
  });

  const statusLabel = {
    disconnected: 'Mất kết nối thời gian thực',
    connecting: 'Đang kết nối…',
    connected: 'Đã kết nối thời gian thực',
    error: 'Kết nối gặp lỗi',
  }[socket.status];

  const deadline = room.readOnlyAt
    ? Date.parse(room.readOnlyAt)
    : null;

  const readOnly =
    room.readOnly ||
    (deadline !== null
      && Number.isFinite(deadline)
      && currentTime >= deadline);

  async function submit(values: ChatMessageFormValues) {
    if (readOnly) return;

    if (await chat.sendMessage(values.content)) {
      reset({ content: '' });
    }
  }

  async function retry() {
    if (readOnly) return;

    if (await chat.retryMessage()) {
      reset({ content: '' });
    }
  }

  return (
    <SectionCard title="Tin nhắn" description={statusLabel}>
      <div className="chat-panel">
        {socket.status !== 'connected' && (
          <Alert
            tone="warning"
            action={
              <Button variant="secondary" onClick={socket.reconnect}>
                Kết nối lại
              </Button>
            }
          >
            {socket.error
              || 'Tin mới sẽ được đồng bộ khi kết nối được khôi phục.'}
          </Alert>
        )}

        {chat.historyError && (
          <Alert
            tone="danger"
            action={
              <Button
                variant="secondary"
                onClick={() => void chat.refreshHistory()}
              >
                Tải lại lịch sử
              </Button>
            }
          >
            {chat.historyError}
          </Alert>
        )}

        {chat.hasMore && (
          <Button
            variant="secondary"
            loading={chat.loading}
            onClick={() => void chat.loadOlder()}
          >
            Xem tin nhắn cũ
          </Button>
        )}

        {chat.loading && <p role="status">Đang tải tin nhắn…</p>}

        <div
          className="chat-history"
          role="region"
          aria-label="Lịch sử trò chuyện"
          tabIndex={0}
        >
          {!chat.loading && !chat.historyError
            && chat.messages.length === 0 && (
              <EmptyState
                title="Chưa có tin nhắn"
                description="Gửi lời chào để bắt đầu trao đổi về đơn hàng."
              />
            )}

          {chat.messages.map((message) => {
            const fromSender = message.authorId === room.senderId;

            return (
              <article
                key={message.id}
                className={`chat-message ${
                  fromSender ? 'chat-message-sender' : 'chat-message-courier'
                }`}
              >
                <strong className="chat-message-author">
                  {fromSender ? 'Người gửi hàng' : 'Người giao hàng'}
                </strong>

                <p className="chat-message-content">{message.content}</p>

                <time dateTime={message.createdAt}>
                  {new Date(message.createdAt).toLocaleString('vi-VN')}
                </time>
              </article>
            );
          })}
        </div>

        {readOnly && (
          <Alert>Cuộc trò chuyện đã chuyển sang chế độ chỉ đọc.</Alert>
        )}

        {chat.sendError && (
          <Alert tone="danger">{chat.sendError}</Alert>
        )}

        {chat.failedMessage && (
          <Alert
            tone="warning"
            action={
              <Button
                loading={chat.sending}
                disabled={readOnly}
                onClick={() => void retry()}
              >
                Gửi lại tin nhắn
              </Button>
            }
          >
            Tin chưa được xác nhận:
            <span className="chat-pending-content">
              {chat.failedMessage.content}
            </span>
          </Alert>
        )}

        <form
          className="chat-composer"
          onSubmit={handleSubmit(submit)}
          noValidate
        >
          <Textarea
            label="Nội dung tin nhắn"
            placeholder="Nhập thông tin cần trao đổi…"
            hint="Tối đa 2.000 ký tự."
            rows={3}
            maxLength={2000}
            disabled={readOnly || chat.sending || !!chat.failedMessage}
            error={errors.content?.message}
            {...register('content')}
          />

          <div className="chat-composer-actions">
            <Button
              type="submit"
              loading={chat.sending}
              loadingText="Đang gửi…"
              disabled={readOnly || !!chat.failedMessage}
            >
              Gửi tin nhắn
            </Button>
          </div>
        </form>
      </div>
    </SectionCard>
  );
}