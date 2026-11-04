import AuthLayout from '../layouts/AuthLayout';
import ForgotPasswordForm from '../features/auth/components/ForgotPasswordForm';

export default function ForgotPasswordPage() {
  return <AuthLayout variant="recovery">
    <header className="login-heading">
      <p>Khôi phục tài khoản</p><h1>Quên mật khẩu?</h1>
      <span>Nhập email hoặc số điện thoại đã đăng ký. Chúng tôi sẽ gửi mã OTP đến email của tài khoản.</span>
    </header>
    <ForgotPasswordForm />
  </AuthLayout>;
}
