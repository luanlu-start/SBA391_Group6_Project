import { useState } from 'react';
import { ChevronDown, LogOut } from 'lucide-react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthentication } from '../auth/useAuthentication';
import { authService } from '../services/authService';
import { getApiError, type ApiError } from '../api/ApiError';
import AccountNavigation from '../features/profile/components/AccountNavigation';
import ApiErrorNotice from '../components/ApiErrorNotice';
import Button from '../components/ui/Button';
import UserAvatar from '../components/common/UserAvatar';
import '../styles/account.css';

export default function AccountLayout() {
  const { user, initializing } = useAuthentication();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError>();

  async function logout() {
    setBusy(true);
    setError(undefined);
    try {
      await authService.logout();
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setBusy(false);
    }
  }

  if (initializing) return <p className="ui-muted" role="status">Đang kiểm tra phiên đăng nhập…</p>;
  if (!user) return <Navigate to="/login" replace state={{ returnTo: location.pathname + location.search }} />;

  return <div className="account-layout">
    <aside className="account-sidebar" aria-label="Khu vực tài khoản">
      <div className="account-sidebar-user">
        <UserAvatar />
        <div><strong>{user.fullName || 'Tài khoản của tôi'}</strong><span>Không gian cá nhân</span></div>
      </div>
      <button type="button" className="account-menu-toggle" aria-expanded={menuOpen}
        aria-controls="account-navigation-panel" onClick={() => setMenuOpen(!menuOpen)}>
        Menu tài khoản <ChevronDown size={18} aria-hidden="true" />
      </button>
      <div id="account-navigation-panel" className={`account-sidebar-panel${menuOpen ? ' is-open' : ''}`}
        onKeyDown={event => { if (event.key === 'Escape') setMenuOpen(false); }}>
        <AccountNavigation onNavigate={() => setMenuOpen(false)} />
        <div className="account-sidebar-footer">
          <Button variant="ghost" icon={<LogOut size={17} />} onClick={() => void logout()}
            loading={busy} loadingText="Đang đăng xuất…" fullWidth>Đăng xuất</Button>
        </div>
      </div>
    </aside>
    <div className="account-content">
      {error && <ApiErrorNotice error={error} />}
      <Outlet />
    </div>
  </div>;
}
