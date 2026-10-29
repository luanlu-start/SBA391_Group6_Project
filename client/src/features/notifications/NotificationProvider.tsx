import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Client } from '@stomp/stompjs';
import axiosClient from '../../api/axiosClient';
import { notificationApi } from '../../api/notificationApi';
import { getAccessToken } from '../../auth/authSession';
import { useAuthentication } from '../../auth/useAuthentication';
import { NotificationContext } from './notificationContext';
import type { NotificationState } from './notificationContext';

function socketUrl() {
  const url = new URL('/ws/chat', new URL(axiosClient.defaults.baseURL || '/api/v1', window.location.origin));
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  return import.meta.env.VITE_CHAT_WS_URL || url.toString();
}

export default function NotificationProvider({ children }: { children: ReactNode }) {
  const { user, initializing } = useAuthentication();
  const owner = user?.id;
  const token = getAccessToken();
  const [countState, setCountState] = useState<{ owner: string | undefined; count: number | null }>({ owner, count: null });
  const [connectionState, setConnectionState] = useState<{ owner: string | undefined; status: NotificationState['connection'] }>({ owner, status: 'offline' });
  const [revision, setRevision] = useState(0);
  const requests = useRef<AbortController | null>(null);
  const generation = useRef(0);
  const currentUserId = useRef(owner);
  currentUserId.current = owner;

  const refresh = useCallback(async () => {
    if (!owner || initializing || currentUserId.current !== owner) return;
    requests.current?.abort();
    const currentGeneration = generation.current;
    const controller = new AbortController(); requests.current = controller;
    try {
      const response = await notificationApi.unreadCount(controller.signal);
      if (!controller.signal.aborted && generation.current === currentGeneration && currentUserId.current === owner)
        setCountState({ owner, count: response.data.count });
    } catch {
      if (!controller.signal.aborted && generation.current === currentGeneration && currentUserId.current === owner)
        setCountState({ owner, count: null });
    }
  }, [owner, initializing]);

  useEffect(() => {
    generation.current += 1;
    const currentGeneration = generation.current;
    setCountState({ owner, count: null });
    if (!owner || initializing || !token) { setConnectionState({ owner, status: 'offline' }); return; }
    let active = true;
    const current = () => active && currentUserId.current === owner && generation.current === currentGeneration;
    let debounce: ReturnType<typeof setTimeout> | undefined;
    setConnectionState({ owner, status: 'connecting' });
    void refresh();
    const client = new Client({
      brokerURL: socketUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000, connectionTimeout: 10000,
      heartbeatIncoming: 0, heartbeatOutgoing: 0,
      debug: () => {},
    });
    client.onConnect = () => {
      if (!current()) return;
      setConnectionState({ owner, status: 'connected' });
      client.subscribe('/user/queue/notifications', frame => {
        if (!current()) return;
        try {
          const event: unknown = JSON.parse(frame.body);
          if (typeof event !== 'object' || event === null || !('id' in event) || typeof event.id !== 'string') return;
        } catch { return; }
        if (debounce === undefined) debounce = setTimeout(() => {
          debounce = undefined;
          if (current()) { setRevision(value => value + 1); void refresh(); }
        }, 120);
      });
      setRevision(value => value + 1);
      void refresh();
    };
    client.onWebSocketClose = () => { if (current()) setConnectionState({ owner, status: 'offline' }); };
    client.onWebSocketError = () => { if (current()) setConnectionState({ owner, status: 'offline' }); };
    client.onStompError = () => { if (current()) setConnectionState({ owner, status: 'offline' }); void client.deactivate(); };
    client.activate();
    const onFocus = () => { if (current()) { void refresh(); setRevision(value => value + 1); } };
    window.addEventListener('focus', onFocus);
    window.addEventListener('online', onFocus);
    const fallback = setInterval(() => { if (current() && document.visibilityState === 'visible') void refresh(); }, 60000);
    return () => {
      active = false; generation.current += 1; requests.current?.abort();
      clearTimeout(debounce); clearInterval(fallback);
      window.removeEventListener('focus', onFocus); window.removeEventListener('online', onFocus);
      void client.deactivate();
    };
  }, [owner, initializing, token, refresh]);

  const unreadCount = !initializing && owner && countState.owner === owner ? countState.count : null;
  const connection = !initializing && owner && connectionState.owner === owner ? connectionState.status : 'offline';
  return <NotificationContext.Provider value={{ unreadCount, connection, revision, refresh }}>{children}</NotificationContext.Provider>;
}
