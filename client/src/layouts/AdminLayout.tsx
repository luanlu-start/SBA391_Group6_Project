import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';
import { History, Settings2 } from 'lucide-react';
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
    ]}
    headerActions={<Link to="/account" className="ui-button ui-button-ghost ui-button-sm">{user.fullName}</Link>}>
    <Outlet />
  </WorkspaceLayout>;
}
