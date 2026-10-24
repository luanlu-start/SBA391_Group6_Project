import { useLocation } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout';
import ResetPasswordForm from '../features/auth/components/ResetPasswordForm';

export default function ResetPasswordPage() {
  const state = useLocation().state as { identifier?: unknown; resendAvailableAt?: unknown } | null;
  return <AuthLayout variant="recovery">
    <header className="login-heading">
      <p>Khôi phục tài khoản</p><h1>Đặt lại mật khẩu</h1>
      <span>Nhập mã OTP từ email và tạo mật khẩu mới cho tài khoản.</span>
    </header>
    <ResetPasswordForm initialIdentifier={typeof state?.identifier === 'string' ? state.identifier : undefined}
      resendAvailableAt={typeof state?.resendAvailableAt === 'string' ? state.resendAvailableAt : undefined} />
  </AuthLayout>;
}
