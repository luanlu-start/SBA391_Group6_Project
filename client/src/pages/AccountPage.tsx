import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useAuthentication } from '../auth/useAuthentication';
import { authService } from '../services/authService';
import { getApiError } from '../api/ApiError';
import ApiErrorNotice from '../components/ApiErrorNotice';
import '../styles/registration.css';

export default function AccountPage() {
  const { user, initializing } = useAuthentication();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [correlationId, setCorrelationId] = useState<string>();
  useEffect(() => { window.scrollTo(0, 0); }, []);
  async function logout() {
    setBusy(true); setError('');
    try { await authService.logout(); }
    catch (cause) { const failure = getApiError(cause); setError(failure.message); setCorrelationId(failure.correlationId); }
    finally { setBusy(false); }
  }
  if (initializing) return <section className="registration-page" role="status">Đang kiểm tra phiên đăng nhập…</section>;
  if (!user) return <Navigate to="/login" replace />;
  return <section className="registration-page"><div className="registration-card registration-verify">
    <h1>Xin chào, {user.fullName}</h1>
    <p>Tài khoản đã xác thực và đang đăng nhập.</p>
    {error && <ApiErrorNotice message={error} correlationId={correlationId} />}
    <dl className="account-details"><dt>Email</dt><dd>{user.email}</dd><dt>Số điện thoại</dt><dd>{user.phone}</dd>
      <dt>Vai trò</dt><dd>{user.roles.map(role => ({ SENDER: 'Người gửi', COURIER: 'Người giao',
        ADMIN: 'Quản trị viên', MODERATOR: 'Kiểm duyệt viên' }[role] || role)).join(', ')}</dd></dl>
    <Link className="registration-primary" to="/">Về trang chủ</Link>
    <button className="registration-secondary" onClick={logout} disabled={busy}>{busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
  </div></section>;
}
