import { useEffect } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { getLoginDestination } from '../auth/loginDestination';
import { useAuthentication } from '../auth/useAuthentication';
import AuthLayout from '../layouts/AuthLayout';
import LoginForm from '../features/auth/components/LoginForm';

export default function LoginPage() {
  const { user } = useAuthentication();
  const location = useLocation();
  useEffect(() => { window.scrollTo(0, 0); }, []);

  if (user) return <Navigate to={getLoginDestination(location.state)} replace />;

  return (
    <AuthLayout>
      <header className="login-heading">
        <p>Chào mừng trở lại</p>
        <h1>Đăng nhập</h1>
        <span>Dùng email hoặc số điện thoại đã đăng ký.</span>
      </header>
      <LoginForm />
    </AuthLayout>
  );
}
