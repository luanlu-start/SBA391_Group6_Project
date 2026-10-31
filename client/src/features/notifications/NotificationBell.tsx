import { Bell } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { useNotifications } from './notificationContext';

export default function NotificationBell() {
  const { unreadCount } = useNotifications();
  return <NavLink to="/notifications" className="nav-link notification-bell"
    aria-label={unreadCount === null ? 'Thông báo' : `Thông báo, ${unreadCount} chưa đọc`}>
    <Bell size={18} aria-hidden="true" />
    <span>Thông báo</span>
    {unreadCount !== null && unreadCount > 0 && <span className="notification-count" aria-hidden="true">{unreadCount > 99 ? '99+' : unreadCount}</span>}
  </NavLink>;
}
