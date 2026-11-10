import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import {
  Banknote,
  CreditCard,
  Flag,
  Layers,
  LayoutDashboard,
  Menu,
  Navigation,
  Package,
  PackageSearch,
  PanelsTopLeft,
  Scale,
  Settings2,
  ShieldCheck,
  TrendingUp,
  UserRound,
  Wallet,
  X,
} from 'lucide-react';
import NotificationBell from '../../features/notifications/NotificationBell';
import { useAuthentication } from '../../auth/useAuthentication';
import { useAccountMode } from '../../auth/useAccountMode';
import Button from '../ui/Button';
import copy from '../../i18n/home.vi.json';

export default function Navbar() {
  const { user } = useAuthentication();
  const { mode, canSwitch, selectMode } = useAccountMode();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const closeMenu = () => setMenuOpen(false);

  const roles = user?.roles ?? [];
  const isAdmin = roles.includes('ADMIN');
  const isModerator = roles.includes('MODERATOR');
  const isCourierRole = roles.includes('COURIER');
  const isSenderRole = roles.includes('SENDER');
  const isStaffOnly = (isAdmin || isModerator) && !isSenderRole && !isCourierRole;
  const isCourierActive = isCourierRole && (!isSenderRole || mode === 'COURIER');
  const isSenderActive = isSenderRole && !isCourierActive;
  const canBecomeCourier = !user || (isSenderRole && !isCourierRole && !isAdmin && !isModerator);

  return (
    <header className="navbar-container">
      <div className="navbar-content">
        <Link to="/" className="navbar-brand" aria-label={`FPost — ${copy.navigation.home}`} onClick={closeMenu}>
          <span className="brand-logo"><Package size={21} aria-hidden="true" /></span>
          <span className="brand-title">F<span className="brand-accent">Post</span></span>
        </Link>

        <nav id="main-navigation" className={`navbar-links${menuOpen ? ' is-open' : ''}`} aria-label="Điều hướng chính">
          <NavLink to="/" end className="nav-link" onClick={closeMenu}>
            {copy.navigation.home}
          </NavLink>

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
              <NavLink to="/courier/batch" className="nav-link" onClick={closeMenu}>
                <Layers size={15} aria-hidden="true" /> Gộp đơn
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
            </>
          )}

          {(isSenderActive || isCourierActive) && (
            <NavLink to="/wallet" className="nav-link" onClick={closeMenu}>
              <Wallet size={15} aria-hidden="true" /> Ví tiền
            </NavLink>
          )}

          {isAdmin && (
            <>
              <NavLink to="/admin/dashboard" className="nav-link" onClick={closeMenu}>
                <LayoutDashboard size={15} aria-hidden="true" /> Tổng quan
              </NavLink>
              <NavLink to="/sender/orders" className="nav-link" onClick={closeMenu}>
                <PackageSearch size={15} aria-hidden="true" /> Đơn &amp; Giải ngân
              </NavLink>
              <NavLink to="/admin/withdrawals" className="nav-link" onClick={closeMenu}>
                <Banknote size={15} aria-hidden="true" /> Rút tiền
              </NavLink>
              <NavLink to="/admin/reconcile" className="nav-link" onClick={closeMenu}>
                <Scale size={15} aria-hidden="true" /> Đối soát
              </NavLink>
              <NavLink to="/mod/complaints" className="nav-link" onClick={closeMenu}>
                <Flag size={15} aria-hidden="true" /> Khiếu nại
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
                <Flag size={15} aria-hidden="true" /> Báo cáo vi phạm
              </NavLink>
              <NavLink to="/mod/complaints" className="nav-link" onClick={closeMenu}>
                <Scale size={15} aria-hidden="true" /> Khiếu nại
              </NavLink>
            </>
          )}

          {!isStaffOnly && !isCourierActive && (
            <>
              <Link to="/#how-it-works" className="nav-link" onClick={closeMenu}>{copy.navigation.how}</Link>
              <Link to="/#shipping-prices" className="nav-link" onClick={closeMenu}>{copy.navigation.pricing}</Link>
            </>
          )}

          {canBecomeCourier && (
            <Link to={user ? '/me/courier-register' : '/#become-courier'} className="nav-link" onClick={closeMenu}>
              {copy.navigation.courier}
            </Link>
          )}

          {(isAdmin || isModerator) && (
            <NavLink to="/mod/kyc" className="nav-link navbar-mobile-role" onClick={closeMenu}>
              <ShieldCheck size={17} aria-hidden="true" /> Khu kiểm duyệt
            </NavLink>
          )}
          {isAdmin && (
            <NavLink to="/admin/dashboard" className="nav-link navbar-mobile-role" onClick={closeMenu}>
              <Settings2 size={17} aria-hidden="true" /> Quản trị
            </NavLink>
          )}
        </nav>

        <div className="navbar-actions ui-row">
          {user && <NotificationBell />}
          {isModerator && !isAdmin && (
            <NavLink to="/mod/kyc" className="nav-link navbar-role-action" onClick={closeMenu}>
              <ShieldCheck size={17} aria-hidden="true" /> Kiểm duyệt
            </NavLink>
          )}
          {isAdmin && (
            <NavLink to="/admin/dashboard" className="nav-link navbar-role-action" onClick={closeMenu}>
              <Settings2 size={17} aria-hidden="true" /> Quản trị
            </NavLink>
          )}
          {canSwitch && (
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
          )}
          {import.meta.env.DEV && !isStaffOnly && (
            <Link to="/ui-kit" className="nav-link navbar-dev-link" onClick={closeMenu}>
              <PanelsTopLeft size={15} aria-hidden="true" /> {copy.navigation.uiKit}
            </Link>
          )}
          {user ? (
            <NavLink to="/account" className="nav-link" aria-label={copy.navigation.account} onClick={closeMenu}>
              <UserRound size={17} aria-hidden="true" />
              <span className="navbar-account-label">{copy.navigation.account}</span>
            </NavLink>
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
