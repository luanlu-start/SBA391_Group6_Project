import { NavLink } from 'react-router-dom';

export default function AccountNavigation() {
  return <nav className="account-navigation" aria-label="Quản lý tài khoản">
    <NavLink to="/me/profile">Hồ sơ cá nhân</NavLink><NavLink to="/me/addresses">Sổ địa chỉ</NavLink>
    <NavLink to="/me/security">Bảo mật và thiết bị</NavLink>
    <NavLink to="/me/kyc">Xác minh danh tính</NavLink>
    <NavLink to="/me/courier-register">Người giao</NavLink>
  </nav>;
}
