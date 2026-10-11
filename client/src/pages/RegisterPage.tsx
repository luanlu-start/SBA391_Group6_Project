import { useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthentication } from '../auth/useAuthentication';
import AuthLayout from '../layouts/AuthLayout';
import RegisterForm from '../features/auth/components/RegisterForm';

export default function RegisterPage() {
  const { user } = useAuthentication();
  useEffect(() => { window.scrollTo(0, 0); }, []);

  if (user) return <Navigate to="/account" replace />;

  return (
    <AuthLayout variant="register">
      <header className="login-heading">
        <p>Bắt đầu hành trình cùng FPost</p>
        <h1>Tạo tài khoản</h1>
        <span>Điền thông tin của bạn để nhận mã xác thực qua email.</span>
      </header>
      <RegisterForm />
    </AuthLayout>
  );
}
