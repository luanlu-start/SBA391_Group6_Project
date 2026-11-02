import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';
import { Bell, Boxes, History, Settings2, ShieldCheck, TicketPercent } from 'lucide-react';
import NotificationBell from '../features/notifications/NotificationBell';
import { useAuthentication } from '../auth/useAuthentication';
import LoadingSpinner from '../components/common/LoadingSpinner';
import EmptyState from '../components/ui/EmptyState';
import WorkspaceLayout from './WorkspaceLayout';

export default function AdminLayout() {
  const { user, initializing } = useAuthentication();
  const location = useLocation();
  if (initializing) return <main id="main-content"><LoadingSpinner text="Đang kiểm tra phiên đăng nhập…" /></main>;
  if (!user) return <Navigate to="/login" replace state={{ returnTo: `${location.pathname}${location.search}` }} />;
  if (!user.roles.includes('ADMIN')) return <main id="main-content"><EmptyState title="Bạn chưa có quyền quản trị"
    description="Khu vực này chỉ dành cho quản trị viên."
    action={<Link to="/account" className="ui-button ui-button-secondary">Về tài khoản</Link>} /></main>;

  return <WorkspaceLayout title="Quản trị FPost"
    navigation={[
      { label: 'Tham số và biểu phí', to: '/admin/params', icon: <Settings2 size={17} /> },
      { label: 'Nhật ký kiểm toán', to: '/admin/audit', icon: <History size={17} /> },
      { label: 'Quản lý khuyến mãi', to: '/admin/promotions', icon: <TicketPercent size={17} /> },
      { label: 'Loại hàng hóa', to: '/admin/categories', icon: <Boxes size={17} /> },
      { label: 'Duyệt hồ sơ KYC', to: '/mod/kyc', icon: <ShieldCheck size={17} /> },
      { label: 'Thông báo', to: '/notifications', icon: <Bell size={17} /> },
    ]}
    headerActions={<><NotificationBell /><Link to="/account" className="ui-button ui-button-ghost ui-button-sm">{user.fullName}</Link></>}>
    <Outlet />
  </WorkspaceLayout>;
}
