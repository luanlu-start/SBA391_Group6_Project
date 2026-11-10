import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import {
  CreditCard,
  Flag,
  LayoutDashboard,
  Menu,
  Navigation,
  Package,
  PackageSearch,
  Settings2,
  ShieldCheck,
  TrendingUp,
  Wallet,
  X,
} from 'lucide-react';
import NotificationBell from '../../features/notifications/NotificationBell';
import ThemeToggleButton from './ThemeToggleButton';
import UserAvatar from './UserAvatar';
import { useAuthentication } from '../../auth/useAuthentication';
import { useAccountMode } from '../../auth/useAccountMode';
import Button from '../ui/Button';
import copy from '../../i18n/home.vi.json';
import '../../styles/navigation.css';

export default function Navbar() {
  const { user } = useAuthentication();
  const { mode, canSwitch, selectMode } = useAccountMode();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const closeMenu = () => setMenuOpen(false);

  const roles = user?.roles ?? [];
  const isAdmin = roles.includes('ADMIN');
  const isModerator = roles.includes('MODERATOR');
  const isCourierRole = roles.includes('COURIER');
  const isSenderRole = roles.includes('SENDER');
  const isStaff = isAdmin || isModerator;
  const isCourierActive = !isStaff && isCourierRole && (!isSenderRole || mode === 'COURIER');
  const isSenderActive = !isStaff && isSenderRole && !isCourierActive;
  const inAccountArea = pathname === '/account' || pathname.startsWith('/me/')
    || pathname === '/notifications' || pathname === '/courier' || pathname.startsWith('/complaints/');

  return (
    <header className="navbar-container" onKeyDown={event => { if (event.key === 'Escape') closeMenu(); }}>
      <div className="navbar-content">
        <Link to="/" className="navbar-brand" aria-label={`FPost — ${copy.navigation.home}`} onClick={closeMenu}>
          <span className="brand-logo"><Package size={21} aria-hidden="true" /></span>
          <span className="brand-title">F<span className="brand-accent">Post</span></span>
        </Link>

        <nav id="main-navigation" className={`navbar-links${menuOpen ? ' is-open' : ''}`} aria-label="Điều hướng chính">
          <NavLink to="/" end className="nav-link" onClick={closeMenu}>
            {user ? 'Tổng quan' : copy.navigation.home}
          </NavLink>

          {!user && (
            <>
              <Link to="/#how-it-works" className="nav-link" onClick={closeMenu}>{copy.navigation.how}</Link>
              <Link to="/#shipping-prices" className="nav-link" onClick={closeMenu}>{copy.navigation.pricing}</Link>
              <Link to="/#become-courier" className="nav-link" onClick={closeMenu}>{copy.navigation.courier}</Link>
            </>
          )}

          {isCourierActive && (
            <>
              <NavLink to="/courier/market" className="nav-link" onClick={closeMenu}>
                <Package size={15} aria-hidden="true" /> Sàn đơn
              </NavLink>
              <NavLink to="/courier/trips" className="nav-link" onClick={closeMenu}>
                <Navigation size={15} aria-hidden="true" /> Chuyến đi
              </NavLink>
              <NavLink to="/courier/orders" className="nav-link" onClick={closeMenu}>
                <PackageSearch size={15} aria-hidden="true" /> Đơn nhận
              </NavLink>
              <NavLink to="/courier/earnings" className="nav-link" onClick={closeMenu}>
                <TrendingUp size={15} aria-hidden="true" /> Thu nhập
              </NavLink>
            </>
          )}

          {isSenderActive && (
            <>
              <NavLink to="/sender/orders" className="nav-link" onClick={closeMenu}>
                Đơn của tôi
              </NavLink>
              <NavLink to="/orders/payment" className="nav-link" onClick={closeMenu}>
                <CreditCard size={15} aria-hidden="true" /> Ký quỹ
              </NavLink>
              <NavLink to="/wallet" className="nav-link" onClick={closeMenu}>
                <Wallet size={15} aria-hidden="true" /> Ví tiền
              </NavLink>
            </>
          )}

          {isAdmin && (
            <>
              <NavLink to="/admin/dashboard" className="nav-link" onClick={closeMenu}>
                <LayoutDashboard size={15} aria-hidden="true" /> Quản trị
              </NavLink>
              <NavLink to="/sender/orders" className="nav-link" onClick={closeMenu}>
                <PackageSearch size={15} aria-hidden="true" /> Đơn &amp; Giải ngân
              </NavLink>
              <NavLink to="/mod/kyc" className="nav-link" onClick={closeMenu}>
                <ShieldCheck size={15} aria-hidden="true" /> Kiểm duyệt
              </NavLink>
            </>
          )}

          {isModerator && !isAdmin && (
            <>
              <NavLink to="/mod/kyc" className="nav-link" onClick={closeMenu}>
                <ShieldCheck size={15} aria-hidden="true" /> Duyệt KYC
              </NavLink>
              <NavLink to="/mod/orders" className="nav-link" onClick={closeMenu}>
                <PackageSearch size={15} aria-hidden="true" /> Kiểm duyệt đơn
              </NavLink>
              <NavLink to="/mod/reports" className="nav-link" onClick={closeMenu}>
                <Flag size={15} aria-hidden="true" /> Vi phạm &amp; Khiếu nại
              </NavLink>
            </>
          )}

          {isAdmin && (
            <NavLink to="/admin/dashboard" className="nav-link navbar-mobile-role" onClick={closeMenu}>
              <Settings2 size={17} aria-hidden="true" /> Bảng quản trị
            </NavLink>
          )}

          {canSwitch && !isStaff && (
            <label className="navbar-mode">
              <span className="sr-only">Chế độ sử dụng</span>
              <select
                className="ui-control ui-select account-mode-select"
                aria-label="Chế độ sử dụng"
                value={mode}
                onChange={event => {
                  const next = event.target.value === 'COURIER' ? 'COURIER' : 'SENDER';
                  selectMode(next);
                  closeMenu();
                  navigate(next === 'COURIER' ? '/courier' : '/sender/orders');
                }}
              >
                <option value="SENDER">Người gửi</option>
                <option value="COURIER">Người giao</option>
              </select>
            </label>
          )}
        </nav>

        <div className="navbar-actions">
          <ThemeToggleButton />
          {user && <NotificationBell />}
          {user ? (
            <Link
              to="/account"
              className={`nav-link navbar-account${inAccountArea ? ' active' : ''}`}
              aria-current={inAccountArea ? 'location' : undefined}
              aria-label={copy.navigation.account}
              onClick={closeMenu}
            >
              <UserAvatar size="sm" />
              <span className="navbar-account-label">{copy.navigation.account}</span>
            </Link>
          ) : (
            <Link to="/login" className="ui-button ui-button-primary ui-button-sm" onClick={closeMenu}>
              {copy.navigation.login}
            </Link>
          )}
          <Button
            className="navbar-toggle"
            variant="ghost"
            size="sm"
            aria-expanded={menuOpen}
            aria-controls="main-navigation"
            aria-label={menuOpen ? copy.navigation.closeMenu : copy.navigation.menu}
            onClick={() => setMenuOpen(!menuOpen)}
            icon={menuOpen ? <X size={20} /> : <Menu size={20} />}
          />
        </div>
      </div>
    </header>
  );
}
