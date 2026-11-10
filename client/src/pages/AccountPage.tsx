import { Link } from 'react-router-dom';
import { ArrowRight, Gauge, Gift, MessageSquare, Package, Pencil, Wallet } from 'lucide-react';
import { useAuthentication } from '../auth/useAuthentication';
import { useAccountMode } from '../auth/useAccountMode';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Badge from '../components/ui/Badge';
import UserAvatar from '../components/common/UserAvatar';

const roleLabels: Record<string, string> = {
  SENDER: 'Người gửi', COURIER: 'Người giao', ADMIN: 'Quản trị viên', MODERATOR: 'Kiểm duyệt viên',
};

export default function AccountPage() {
  const { user } = useAuthentication();
  const { mode } = useAccountMode();
  if (!user) return null;

  const isCourier = user.roles.includes('COURIER') && mode === 'COURIER';
  const canViewOrders = user.roles.some(role => role === 'SENDER' || role === 'ADMIN');
  const shortcuts = [
    ...(isCourier || canViewOrders ? [{
      to: isCourier ? '/courier/market' : '/sender/orders',
      title: isCourier ? 'Tìm đơn giao hàng' : 'Đơn hàng của tôi',
      description: isCourier ? 'Tìm đơn phù hợp với chuyến đi của bạn.' : 'Theo dõi và quản lý các đơn đã gửi.', icon: Package,
    }] : []),
    ...(isCourier ? [{
      to: '/courier/trust', title: 'Uy tín và hạng', description: 'Điểm uy tín, quyền lợi và tiến độ lên hạng.', icon: Gauge,
    }] : []),
    { to: '/wallet', title: 'Ví và giao dịch', description: 'Nạp, rút tiền và xem lịch sử giao dịch.', icon: Wallet },
    { to: '/me/rewards', title: 'Huy hiệu và giới thiệu', description: 'Xem thành tích và mời bạn bè nhận thưởng.', icon: Gift },
    { to: '/me/complaints', title: 'Hỗ trợ khiếu nại', description: 'Theo dõi, trao đổi và bổ sung hồ sơ khiếu nại.', icon: MessageSquare },
  ];

  return <div className="ui-stack account-overview">
    <PageHeader eyebrow="Không gian cá nhân" title="Tài khoản của tôi"
      description="Quản lý thông tin, bảo mật và các tiện ích của bạn tại một nơi." />
    <SectionCard title="Thông tin tài khoản" actions={<Link className="account-edit-link" to="/me/profile">
      <Pencil size={15} aria-hidden="true" />Chỉnh sửa
    </Link>}>
      <div className="account-summary">
        <UserAvatar size="lg" />
        <div><h2>{user.fullName || 'Tài khoản FPost'}</h2><p>Chào mừng bạn đến với FPost</p></div>
        <Badge tone={user.emailVerifiedAt ? 'success' : 'warning'} dot>
          {user.emailVerifiedAt ? 'Đã xác thực email' : 'Chưa xác thực email'}
        </Badge>
      </div>
      <dl className="account-info-grid">
        <div><dt>Email</dt><dd>{user.email}</dd></div>
        <div><dt>Số điện thoại</dt><dd>{user.phone || 'Chưa cập nhật'}</dd></div>
        <div className="account-info-roles"><dt>Vai trò</dt><dd className="ui-row">
          {user.roles.map(role => <Badge key={role}>{roleLabels[role] || role}</Badge>)}
          {user.fptBadge && <Badge tone="success">Cộng đồng FPT</Badge>}
        </dd></div>
      </dl>
    </SectionCard>
    <section aria-labelledby="account-shortcuts-title">
      <div className="account-section-heading"><h2 id="account-shortcuts-title">Truy cập nhanh</h2><p>Các chức năng thường dùng</p></div>
      <div className="account-shortcuts">
        {shortcuts.map(({ to, title, description, icon: Icon }) => <Link key={to} to={to} className="account-shortcut">
          <span className="account-shortcut-icon"><Icon size={22} aria-hidden="true" /></span>
          <span><strong>{title}</strong><span>{description}</span></span>
          <ArrowRight size={18} aria-hidden="true" />
        </Link>)}
      </div>
    </section>
  </div>;
}
