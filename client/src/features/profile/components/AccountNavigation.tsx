import { Link, useLocation } from 'react-router-dom';
import { ClipboardList, Gift, House, MapPin, MessageSquare, Settings2, ShieldCheck, Truck, UserRound } from 'lucide-react';
import { useAuthentication } from '../../../auth/useAuthentication';

export default function AccountNavigation({ onNavigate }: { onNavigate: () => void }) {
  const { user } = useAuthentication();
  const { pathname } = useLocation();
  const roles = user?.roles ?? [];
  const isAdmin = roles.includes('ADMIN');
  const isModerator = roles.includes('MODERATOR');
  const isCourier = roles.includes('COURIER');
  const isSender = roles.includes('SENDER');
  const isStaffOnly = (isAdmin || isModerator) && !isSender && !isCourier;

  const groups = [
    {
      title: 'Tài khoản',
      items: [
        { to: '/account', label: 'Tổng quan', icon: House },
        { to: '/me/profile', label: 'Hồ sơ cá nhân', icon: UserRound },
        ...(!isStaffOnly ? [{ to: '/me/addresses', label: 'Sổ địa chỉ', icon: MapPin }] : []),
      ],
    },
    {
      title: 'Bảo mật & xác minh',
      items: [
        { to: '/me/security', label: 'Bảo mật và thiết bị', icon: ShieldCheck },
        ...(!isStaffOnly ? [{ to: '/me/kyc', label: 'Xác minh danh tính', icon: ShieldCheck }] : []),
        ...(isCourier ? [{ to: '/courier', label: 'Hồ sơ người giao', icon: Truck }]
          : (isSender && !isAdmin && !isModerator ? [{ to: '/me/courier-register', label: 'Đăng ký người giao', icon: Truck }] : [])),
      ],
    },
  ];

  if (!isStaffOnly) {
    groups.push({
      title: 'Tiện ích & hỗ trợ',
      items: [
        { to: '/me/rewards', label: 'Huy hiệu và giới thiệu', icon: Gift },
        { to: '/me/complaints', label: 'Khiếu nại của tôi', icon: MessageSquare },
      ],
    });
  }

  if (isAdmin || isModerator) {
    groups.push({
      title: 'Quản lý hệ thống',
      items: [
        ...(isAdmin ? [
          { to: '/admin/dashboard', label: 'Trang quản trị', icon: Settings2 },
          { to: '/sender/orders', label: 'Đơn hàng & Giải ngân', icon: ClipboardList },
        ] : []),
        { to: '/mod/kyc', label: 'Trang kiểm duyệt', icon: ShieldCheck },
      ],
    });
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
