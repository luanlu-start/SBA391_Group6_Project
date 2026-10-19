import { useEffect } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import Alert from '../components/ui/Alert';
import { useAuthentication } from '../auth/useAuthentication';
import AuthLayout from '../layouts/AuthLayout';
import LoginForm from '../features/auth/components/LoginForm';

export default function LoginPage() {
  const { user } = useAuthentication();
  const state = useLocation().state as { passwordReset?: boolean; passwordChanged?: boolean } | null;
  useEffect(() => { window.scrollTo(0, 0); }, []);

  if (user) return <Navigate to="/account" replace />;

  return (
    <AuthLayout>
      <header className="login-heading">
        <p>Chào mừng trở lại</p>
        <h1>Đăng nhập</h1>
        <span>Dùng email hoặc số điện thoại đã đăng ký.</span>
      </header>
      {state?.passwordReset && <div className="recovery-success"><Alert tone="success">
        Đặt lại mật khẩu thành công. Các phiên cũ đã được đăng xuất. Vui lòng đăng nhập bằng mật khẩu mới.
      </Alert></div>}
      {state?.passwordChanged && <div className="recovery-success"><Alert tone="success">
        Đổi mật khẩu thành công. Các phiên cũ đã được đăng xuất. Vui lòng đăng nhập bằng mật khẩu mới.
      </Alert></div>}
      <LoginForm />
    </AuthLayout>
  );
}
