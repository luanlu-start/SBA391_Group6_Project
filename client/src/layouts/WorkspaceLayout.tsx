import { NavLink, Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, LogOut, Menu, Package, X } from 'lucide-react';
import { useId, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { authService } from '../services/authService';
import { getApiError } from '../api/ApiError';
import { useAuthentication } from '../auth/useAuthentication';
import Button from '../components/ui/Button';
import ThemeToggleButton from '../components/common/ThemeToggleButton';

interface WorkspaceNavigationItem {
  label: string;
  to: string;
  icon: ReactNode;
}

interface WorkspaceLayoutProps {
  title: string;
  navigation: WorkspaceNavigationItem[];
  children: ReactNode;
  headerActions?: ReactNode;
}

export default function WorkspaceLayout({ title, navigation, children, headerActions }: WorkspaceLayoutProps) {
  const navigate = useNavigate();
  const { user } = useAuthentication();
  const workspaceHome = user?.roles.includes('ADMIN') ? '/admin/dashboard'
    : user?.roles.includes('MODERATOR') ? '/mod/kyc' : '/';
  const menuId = useId();
  const [menuOpen, setMenuOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState('');
  const logoutInFlight = useRef(false);

  async function logout() {
    if (logoutInFlight.current) return;
    logoutInFlight.current = true;
    setLoggingOut(true);
    setLogoutError('');
    try {
      await authService.logout();
      navigate('/login', { replace: true });
    } catch (cause) {
      setLogoutError(getApiError(cause).message);
    } finally {
      logoutInFlight.current = false;
      setLoggingOut(false);
    }
  }

  return <div className="ui-workspace">
    <aside className="ui-sidebar" aria-label={title}>
      <div className="ui-sidebar-heading">
        <Link to={workspaceHome} className="ui-workspace-brand" aria-label={`Về ${title}`}>
          <span><Package size={20} aria-hidden="true" /></span><span>F<b>Post</b></span>
        </Link>
        <Button className="ui-sidebar-toggle" variant="ghost" size="sm" aria-controls={menuId}
          aria-expanded={menuOpen} aria-label={menuOpen ? 'Đóng menu làm việc' : 'Mở menu làm việc'}
          onClick={() => setMenuOpen(!menuOpen)} icon={menuOpen ? <X size={20} /> : <Menu size={20} />} />
      </div>
      <div id={menuId} className={`ui-sidebar-menu${menuOpen ? ' is-open' : ''}`}>
        <p className="ui-sidebar-label">KHÔNG GIAN LÀM VIỆC</p>
        <nav aria-label="Điều hướng không gian làm việc">{navigation.map(item =>
          <NavLink key={item.to} to={item.to} end onClick={() => setMenuOpen(false)}
            className={({ isActive }) => `ui-sidebar-link${isActive ? ' active' : ''}`}>
            <span aria-hidden="true">{item.icon}</span>{item.label}
          </NavLink>)}</nav>
      </div>
      <div className="ui-sidebar-footer">
        <Link to="/" className="ui-sidebar-link"><ArrowLeft size={16} />Về trang chủ</Link>
        {logoutError && <p className="ui-sidebar-error" role="alert">{logoutError}</p>}
        <Button className="ui-sidebar-logout" variant="ghost" fullWidth icon={<LogOut size={17} />}
          loading={loggingOut} loadingText="Đang đăng xuất…" onClick={() => void logout()}>Đăng xuất</Button>
      </div>
    </aside>
    <div className="ui-workspace-main">
      <header className="ui-workspace-topbar"><span>{title}</span><div className="ui-row"><ThemeToggleButton />{headerActions}</div></header>
      <main id="main-content" className="ui-workspace-content">{children}</main>
    </div>
  </div>;
}
