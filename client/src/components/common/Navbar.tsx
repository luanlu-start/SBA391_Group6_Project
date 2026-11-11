import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { ArrowRightLeft, CreditCard, Layers, Menu, Navigation, Package, Settings2, ShieldCheck, TrendingUp, Wallet, X } from 'lucide-react';
import NotificationBell from '../../features/notifications/NotificationBell';
import { useAuthentication } from '../../auth/useAuthentication';
import { useAccountMode } from '../../auth/useAccountMode';
import Button from '../ui/Button';
import UserAvatar from './UserAvatar';
import copy from '../../i18n/home.vi.json';
import '../../styles/navigation.css';

export default function Navbar() {
  const { user } = useAuthentication();
  const { mode, canSwitch, selectMode } = useAccountMode();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const closeMenu = () => setMenuOpen(false);
  const isCourierAccount = !!user?.roles.includes('COURIER') && mode === 'COURIER';
  const canViewOrders = user?.roles.some(role => role === 'SENDER' || role === 'ADMIN');
  const inAccountArea = pathname === '/account' || pathname.startsWith('/me/')
    || pathname === '/notifications' || pathname === '/courier' || pathname.startsWith('/complaints/');

  const handleModeChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    const next = event.target.value === 'COURIER' ? 'COURIER' : 'SENDER';
    selectMode(next);
    closeMenu();
    navigate(next === 'COURIER' ? '/courier' : canViewOrders ? '/sender/orders' : '/account');
  };

  return <header className="navbar-container" onKeyDown={event => { if (event.key === 'Escape') closeMenu(); }}>
    <div className="navbar-content">
      <Link to="/" className="navbar-brand" aria-label={`FPost — ${copy.navigation.home}`} onClick={closeMenu}>
        <span className="brand-logo"><Package size={21} aria-hidden="true" /></span>
        <span className="brand-title">F<span className="brand-accent">Post</span></span>
      </Link>
      <nav id="main-navigation" className={`navbar-links${menuOpen ? ' is-open' : ''}`} aria-label="Điều hướng chính">
        <NavLink to="/" end className="nav-link" onClick={closeMenu}>{copy.navigation.home}</NavLink>
        {user ? <>
          {isCourierAccount ? <>
            <NavLink to="/courier/market" className="nav-link" onClick={closeMenu}><Package size={16} aria-hidden="true" />Sàn đơn</NavLink>
            <NavLink to="/courier/trips" className="nav-link" onClick={closeMenu}><Navigation size={16} aria-hidden="true" />Chuyến đi</NavLink>
            <NavLink to="/courier/orders" className="nav-link" onClick={closeMenu}><Package size={16} aria-hidden="true" />Đơn nhận</NavLink>
            <NavLink to="/courier/relay" className="nav-link" onClick={closeMenu}><ArrowRightLeft size={16} aria-hidden="true" />Tiếp sức</NavLink>
            <NavLink to="/courier/batch" className="nav-link" onClick={closeMenu}><Layers size={16} aria-hidden="true" />Gộp đơn</NavLink>
            <NavLink to="/courier/earnings" className="nav-link" onClick={closeMenu}><TrendingUp size={16} aria-hidden="true" />Thu nhập</NavLink>
          </> : canViewOrders && <>
            <NavLink to="/sender/orders" className="nav-link" onClick={closeMenu}><Package size={16} aria-hidden="true" />Đơn của tôi</NavLink>
            <NavLink to="/orders/payment" className="nav-link" onClick={closeMenu}><CreditCard size={16} aria-hidden="true" />Ký quỹ</NavLink>
          </>}
          <NavLink to="/wallet" className="nav-link" onClick={closeMenu}><Wallet size={16} aria-hidden="true" />Ví tiền</NavLink>
          {user.roles.includes('ADMIN') ? <NavLink to="/admin" className="nav-link" onClick={closeMenu}>
            <Settings2 size={16} aria-hidden="true" />Quản trị
          </NavLink> : user.roles.includes('MODERATOR') && <NavLink to="/mod" className="nav-link" onClick={closeMenu}>
            <ShieldCheck size={16} aria-hidden="true" />Kiểm duyệt
          </NavLink>}
          {canSwitch && <div className="navbar-mode-mobile">
            <label className="navbar-mode">
              <span className="sr-only">Chế độ sử dụng</span>
              <select className="ui-control ui-select navbar-mode-select" value={mode} onChange={handleModeChange}>
                <option value="SENDER">Người gửi</option>
                <option value="COURIER">Người giao</option>
              </select>
            </label>
          </div>}
        </> : <>
          <Link to="/#how-it-works" className="nav-link" onClick={closeMenu}>{copy.navigation.how}</Link>
          <Link to="/#shipping-prices" className="nav-link" onClick={closeMenu}>{copy.navigation.pricing}</Link>
          <Link to="/#become-courier" className="nav-link" onClick={closeMenu}>{copy.navigation.courier}</Link>
        </>}
      </nav>
      <div className="navbar-actions">
        {user && canSwitch && <label className="navbar-mode navbar-mode-desktop">
          <span className="sr-only">Chế độ sử dụng</span>
          <select className="ui-control ui-select navbar-mode-select" value={mode} onChange={handleModeChange}>
            <option value="SENDER">Người gửi</option>
            <option value="COURIER">Người giao</option>
          </select>
        </label>}
        {user && <NotificationBell />}
        {user ? <Link to="/account" className={`nav-link navbar-account${inAccountArea ? ' active' : ''}`}
          aria-current={inAccountArea ? 'location' : undefined} aria-label={copy.navigation.account} onClick={closeMenu}>
          <UserAvatar size="sm" /><span className="navbar-account-label">{copy.navigation.account}</span>
        </Link> : <Link to="/login" className="ui-button ui-button-primary ui-button-sm" onClick={closeMenu}>{copy.navigation.login}</Link>}
        <Button className="navbar-toggle" variant="ghost" size="sm" aria-expanded={menuOpen}
          aria-controls="main-navigation" aria-label={menuOpen ? copy.navigation.closeMenu : copy.navigation.menu}
          onClick={() => setMenuOpen(!menuOpen)} icon={menuOpen ? <X size={20} /> : <Menu size={20} />} />
      </div>
    </div>
  </header>;
}
