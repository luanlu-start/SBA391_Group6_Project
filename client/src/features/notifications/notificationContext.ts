import { createContext, useContext } from 'react';

export interface NotificationState {
  unreadCount: number | null;
  connection: 'connecting' | 'connected' | 'offline';
  revision: number;
  refresh: () => Promise<void>;
}

export const NotificationContext = createContext<NotificationState | null>(null);

export function useNotifications() {
  const context = useContext(NotificationContext);
  if (!context) throw new Error('NotificationProvider is required');
  return context;
}
