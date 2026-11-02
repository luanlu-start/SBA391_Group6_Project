
import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthentication } from '../auth/useAuthentication';
import LoadingSpinner from '../components/common/LoadingSpinner';
import EmptyState from '../components/ui/EmptyState';

type RequireRoleProps = {
  allowedRoles: string[];
};

export default function RequireRole({ allowedRoles }: RequireRoleProps) {
  const { user, initializing } = useAuthentication();
  const location = useLocation();

  // Cho khoi tao phien dang nhap hoan tat.
  if (initializing) {
    return (
      <main id="role-loading">
        <LoadingSpinner text="Đang kiểm tra quyền truy cập…" />
      </main>
    );
  }

  // Chua dang nhap: chuyen den trang Login.
  if (!user) {
    return (
      <Navigate
        to="/login"
        replace
        state={{
          returnTo: `${location.pathname}${location.search}`,
        }}
      />
    );
  }

  // Da dang nhap nhung khong co vai tro duoc phep.
  const hasPermission = user.roles.some(role =>
    allowedRoles.includes(role)
  );

  if (!hasPermission) {
    return (
      <EmptyState
        title="Bạn không có quyền truy cập"
        description="Trang này không dành cho vai trò tài khoản hiện tại."
        action={
          <Link to="/account" className="ui-button ui-button-secondary">
            Về tài khoản
          </Link>
        }
      />
    );
  }

  // Chi mount component con khi da duoc cap quyen.
  return <Outlet />;
}
