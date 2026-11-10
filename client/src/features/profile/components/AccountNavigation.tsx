import { Link, useLocation } from 'react-router-dom';
import { Gift, House, MapPin, MessageSquare, Settings2, ShieldCheck, Truck, UserRound } from 'lucide-react';
import { useAuthentication } from '../../../auth/useAuthentication';

export default function AccountNavigation({ onNavigate }: { onNavigate: () => void }) {
  const { user } = useAuthentication();
  const { pathname } = useLocation();
  const isCourier = user?.roles.includes('COURIER');
  const groups = [
    { title: 'Tài khoản', items: [
      { to: '/account', label: 'Tổng quan', icon: House },
      { to: '/me/profile', label: 'Hồ sơ cá nhân', icon: UserRound },
      { to: '/me/addresses', label: 'Sổ địa chỉ', icon: MapPin },
    ] },
    { title: 'Bảo mật & xác minh', items: [
      { to: '/me/security', label: 'Bảo mật và thiết bị', icon: ShieldCheck },
      { to: '/me/kyc', label: 'Xác minh danh tính', icon: ShieldCheck },
      { to: isCourier ? '/courier' : '/me/courier-register', label: isCourier ? 'Hồ sơ người giao' : 'Đăng ký người giao', icon: Truck },
    ] },
    { title: 'Tiện ích & hỗ trợ', items: [
      { to: '/me/rewards', label: 'Huy hiệu và giới thiệu', icon: Gift },
      { to: '/me/complaints', label: 'Khiếu nại của tôi', icon: MessageSquare },
    ] },
  ];
  if (user?.roles.some(role => role === 'ADMIN' || role === 'MODERATOR')) {
    groups.push({ title: 'Quản lý hệ thống', items: [
      ...(user.roles.includes('ADMIN') ? [{ to: '/admin', label: 'Trang quản trị', icon: Settings2 }] : []),
      { to: '/mod', label: 'Trang kiểm duyệt', icon: ShieldCheck },
    ] });
  }

  return <nav className="account-navigation" aria-label="Quản lý tài khoản">
    {groups.map(group => <div className="account-nav-group" key={group.title}>
      <p>{group.title}</p>
      {group.items.map(({ to, label, icon: Icon }) => {
        const active = pathname === to || (to === '/me/complaints' && pathname.startsWith('/complaints/'));
        return <Link key={to} to={to} className={active ? 'active' : undefined}
          aria-current={active ? 'page' : undefined} onClick={onNavigate}>
          <Icon size={17} aria-hidden="true" /><span>{label}</span>
        </Link>;
      })}
    </div>)}
  </nav>;
}
