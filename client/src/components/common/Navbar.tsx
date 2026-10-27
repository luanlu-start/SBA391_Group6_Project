import { Link, NavLink } from 'react-router-dom';
import { useState } from 'react';
import { Menu, Package, PanelsTopLeft, UserRound, Wallet, X } from 'lucide-react';
import { useAuthentication } from '../../auth/useAuthentication';
import { useAccountMode } from '../../auth/useAccountMode';
import { useNavigate } from 'react-router-dom';
import Button from '../ui/Button';
import copy from '../../i18n/home.vi.json';

export default function Navbar() {
  const { user } = useAuthentication();
  const { mode, canSwitch, selectMode } = useAccountMode();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const closeMenu = () => setMenuOpen(false);
  return <header className="navbar-container">
    <div className="navbar-content">
      <Link to="/" className="navbar-brand" aria-label={`FPost — ${copy.navigation.home}`} onClick={closeMenu}>
        <span className="brand-logo"><Package size={21} aria-hidden="true" /></span>
        <span className="brand-title">F<span className="brand-accent">Post</span></span>
      </Link>
      <nav id="main-navigation" className={`navbar-links${menuOpen ? ' is-open' : ''}`} aria-label="Điều hướng chính">
        <NavLink to="/" end className="nav-link" onClick={closeMenu}>{copy.navigation.home}</NavLink>
        <Link to="/#how-it-works" className="nav-link" onClick={closeMenu}>{copy.navigation.how}</Link>
        <Link to="/#shipping-prices" className="nav-link" onClick={closeMenu}>{copy.navigation.pricing}</Link>
        {!canSwitch && <Link to={user ? '/me/courier-register' : '/#become-courier'} className="nav-link" onClick={closeMenu}>{copy.navigation.courier}</Link>}
        {user && (mode === 'SENDER' ? <NavLink to="/sender/orders" className="nav-link" onClick={closeMenu}><Package size={16} aria-hidden="true" />Đơn của tôi</NavLink>
          : <NavLink to="/courier" className="nav-link" onClick={closeMenu}>Người giao</NavLink>)}
        {user && <NavLink to="/wallet" className="nav-link" onClick={closeMenu}><Wallet size={16} aria-hidden="true" />Ví tiền</NavLink>}
      </nav>
      <div className="navbar-actions ui-row">
        {canSwitch && <select className="ui-control ui-select account-mode-select" aria-label="Chế độ sử dụng" value={mode} onChange={event => {
          const next = event.target.value === 'COURIER' ? 'COURIER' : 'SENDER'; selectMode(next); closeMenu(); navigate(next === 'COURIER' ? '/courier' : '/sender/orders');
        }}><option value="SENDER">Người gửi</option><option value="COURIER">Người giao</option></select>}
        {import.meta.env.DEV && <Link to="/ui-kit" className="nav-link navbar-dev-link" onClick={closeMenu}><PanelsTopLeft size={15} aria-hidden="true" />{copy.navigation.uiKit}</Link>}
        {user ? (
          <>
            <NavLink to="/account" className="nav-link" aria-label={copy.navigation.account} onClick={closeMenu}><UserRound size={17} aria-hidden="true" /><span className="navbar-account-label">{copy.navigation.account}</span></NavLink>
          </>
        ) : <Link to="/login" className="ui-button ui-button-primary ui-button-sm" onClick={closeMenu}>{copy.navigation.login}</Link>}
        <Button className="navbar-toggle" variant="ghost" size="sm" aria-expanded={menuOpen} aria-controls="main-navigation"
          aria-label={menuOpen ? copy.navigation.closeMenu : copy.navigation.menu} onClick={() => setMenuOpen(!menuOpen)}
          icon={menuOpen ? <X size={20} /> : <Menu size={20} />} />
      </div>
    </div>
  </header>;
}
