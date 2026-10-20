import { Link, NavLink } from 'react-router-dom';
import { useState } from 'react';
import { Menu, Package, PanelsTopLeft, UserRound, Wallet, X } from 'lucide-react';
import { useAuthentication } from '../../auth/useAuthentication';
import Button from '../ui/Button';
import copy from '../../i18n/home.vi.json';

export default function Navbar() {
  const { user } = useAuthentication();
  const [menuOpen, setMenuOpen] = useState(false);
  const closeMenu = () => setMenuOpen(false);

  const isSender = user?.roles?.includes('SENDER') || user?.roles?.includes('ADMIN');

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
        <Link to="/#become-courier" className="nav-link" onClick={closeMenu}>{copy.navigation.courier}</Link>
        {user && (
          <>
            {isSender && (
              <NavLink to="/sender/orders" className="nav-link" onClick={closeMenu}>
                <Package size={16} aria-hidden="true" />
                Đơn của tôi
              </NavLink>
            )}
            <NavLink to="/wallet" className="nav-link" onClick={closeMenu}>
              <Wallet size={16} aria-hidden="true" />
              Ví tiền
            </NavLink>
          </>
        )}
      </nav>
      <div className="navbar-actions ui-row">
        {import.meta.env.DEV && <Link to="/ui-kit" className="nav-link navbar-dev-link" onClick={closeMenu}><PanelsTopLeft size={15} aria-hidden="true" />{copy.navigation.uiKit}</Link>}
        {user ? (
          <>
            <NavLink to="/account" className="nav-link" onClick={closeMenu}><UserRound size={17} aria-hidden="true" />{copy.navigation.account}</NavLink>
          </>
        ) : <Link to="/login" className="ui-button ui-button-primary ui-button-sm" onClick={closeMenu}>{copy.navigation.login}</Link>}
        <Button className="navbar-toggle" variant="ghost" size="sm" aria-expanded={menuOpen} aria-controls="main-navigation"
          aria-label={menuOpen ? copy.navigation.closeMenu : copy.navigation.menu} onClick={() => setMenuOpen(!menuOpen)}
          icon={menuOpen ? <X size={20} /> : <Menu size={20} />} />
      </div>
    </div>
  </header>;
}
