import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';
import { Banknote, Bell, Boxes, ClipboardList, FileSpreadsheet, Flag, History, LayoutDashboard, PackageSearch, Scale, Settings2, ShieldCheck, TicketPercent, Timer, UserCog, Users } from 'lucide-react';
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
      { label: 'Tổng quan', to: '/admin/dashboard', icon: <LayoutDashboard size={17} /> },
      { label: 'Đơn hàng & Giải ngân', to: '/sender/orders', icon: <ClipboardList size={17} /> },
      { label: 'Duyệt rút tiền', to: '/admin/withdrawals', icon: <Banknote size={17} /> },
      { label: 'Đối soát giao dịch', to: '/admin/reconcile', icon: <Scale size={17} /> },
      { label: 'Báo cáo thống kê', to: '/admin/reports', icon: <FileSpreadsheet size={17} /> },
      { label: 'Người dùng', to: '/admin/users', icon: <Users size={17} /> },
      { label: 'Nhân sự và phân quyền', to: '/admin/staff', icon: <UserCog size={17} /> },
      { label: 'Tham số và biểu phí', to: '/admin/params', icon: <Settings2 size={17} /> },
      { label: 'Loại hàng hóa', to: '/admin/categories', icon: <Boxes size={17} /> },
      { label: 'Quản lý khuyến mãi', to: '/admin/promotions', icon: <TicketPercent size={17} /> },
      { label: 'Tác vụ định kỳ', to: '/admin/jobs', icon: <Timer size={17} /> },
      { label: 'Nhật ký kiểm toán', to: '/admin/audit', icon: <History size={17} /> },
      { label: 'Duyệt hồ sơ KYC', to: '/mod/kyc', icon: <ShieldCheck size={17} /> },
      { label: 'Kiểm duyệt đơn hàng', to: '/mod/orders', icon: <PackageSearch size={17} /> },
      { label: 'Báo cáo vi phạm', to: '/mod/reports', icon: <Flag size={17} /> },
      { label: 'Khiếu nại và kháng nghị', to: '/mod/complaints', icon: <Scale size={17} /> },
      { label: 'Thông báo', to: '/notifications', icon: <Bell size={17} /> },
    ]}
    headerActions={<><NotificationBell /><Link to="/account" className="ui-button ui-button-ghost ui-button-sm">{user.fullName}</Link></>}>
    <Outlet />
  </WorkspaceLayout>;
}
