import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';
import { Bell, ShieldCheck } from 'lucide-react';
import { useAuthentication } from '../auth/useAuthentication';
import LoadingSpinner from '../components/common/LoadingSpinner';
import EmptyState from '../components/ui/EmptyState';
import WorkspaceLayout from './WorkspaceLayout';
import NotificationBell from '../features/notifications/NotificationBell';

export default function ModeratorLayout() {
  const { user, initializing } = useAuthentication();
  const location = useLocation();
  if (initializing) return <main id="main-content"><LoadingSpinner text="Đang kiểm tra quyền kiểm duyệt…" /></main>;
  if (!user) return <Navigate to="/login" replace state={{ returnTo: `${location.pathname}${location.search}` }} />;
  if (!user.roles.some(role => role === 'ADMIN' || role === 'MODERATOR')) return <main id="main-content">
    <EmptyState title="Bạn chưa có quyền kiểm duyệt" description="Khu vực này dành cho kiểm duyệt viên và quản trị viên."
      action={<Link to="/account" className="ui-button ui-button-secondary">Về tài khoản</Link>} />
  </main>;

  return <WorkspaceLayout title="Kiểm duyệt FPost" navigation={[
    { label: 'Duyệt hồ sơ xác minh', to: '/mod/kyc', icon: <ShieldCheck size={17} /> },
    { label: 'Thông báo', to: '/notifications', icon: <Bell size={17} /> },
  ]} headerActions={<><NotificationBell /><Link to="/account" className="ui-button ui-button-ghost ui-button-sm">{user.fullName}</Link></>}>
    <Outlet />
  </WorkspaceLayout>;
}
