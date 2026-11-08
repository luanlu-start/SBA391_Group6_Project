import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuthentication } from '../../../auth/useAuthentication';
import LoadingSpinner from '../../../components/common/LoadingSpinner';

/** Chờ khôi phục phiên trước khi gọi API khiếu nại trên các trang không nằm trong khu vực kiểm duyệt. */
export default function SignedIn({ children }: { children: ReactNode }) {
  const { user, initializing } = useAuthentication();
  const location = useLocation();
  if (initializing) return <LoadingSpinner text="Đang kiểm tra phiên đăng nhập…" />;
  if (!user) return <Navigate to="/login" replace state={{ returnTo: `${location.pathname}${location.search}` }} />;
  return children;
}
