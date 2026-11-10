import { Link } from 'react-router-dom';
import {
  Award,
  Banknote,
  Bell,
  ChevronRight,
  ClipboardList,
  CreditCard,
  Flag,
  Layers,
  LayoutDashboard,
  Lock,
  MapPin,
  Navigation,
  Package,
  PackagePlus,
  PackageSearch,
  Pencil,
  Scale,
  ShieldCheck,
  TrendingUp,
  UserCheck,
  UserRound,
  Users,
  Wallet,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { useAuthentication } from '../auth/useAuthentication';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Badge from '../components/ui/Badge';
import UserAvatar from '../components/common/UserAvatar';
import '../styles/registration.css';

interface AccountLinkItem {
  label: string;
  description: string;
  to: string;
  icon: ReactNode;
}

interface AccountSectionGroup {
  title: string;
  description: string;
  items: AccountLinkItem[];
}

export default function AccountPage() {
  const { user } = useAuthentication();
  if (!user) return null;

  const isAdmin = user.roles.includes('ADMIN');
  const isModerator = user.roles.includes('MODERATOR');
  const isCourier = user.roles.includes('COURIER');
  const isSender = user.roles.includes('SENDER');
  const isStaff = isAdmin || isModerator;

  const roleLabels = user.roles
    .map(role => ({
      SENDER: 'Người gửi',
      COURIER: 'Người giao',
      ADMIN: 'Quản trị viên',
      MODERATOR: 'Kiểm duyệt viên',
    }[role] || role));

  const groups: AccountSectionGroup[] = [];

  if (isAdmin) {
    groups.push(
      {
        title: 'Quản trị & Tài chính',
        description: 'Vận hành đơn hàng, giải ngân ký quỹ và đối soát sổ cái',
        items: [
          { label: 'Bảng điều khiển quản trị', description: 'Biểu đồ đơn hàng, hoa hồng & báo cáo', to: '/admin/dashboard', icon: <LayoutDashboard size={17} /> },
          { label: 'Quản lý đơn hàng & Giải ngân', description: 'Theo dõi đơn hoàn thành và giải ngân cho người giao', to: '/sender/orders', icon: <ClipboardList size={17} /> },
          { label: 'Duyệt rút tiền', description: 'Duyệt và đối soát lệnh rút tiền ngân hàng', to: '/admin/withdrawals', icon: <Banknote size={17} /> },
          { label: 'Đối soát giao dịch', description: 'Kiểm tra chênh lệch VNPay và sổ cái kép', to: '/admin/reconcile', icon: <Scale size={17} /> },
        ],
      },
      {
        title: 'Kiểm duyệt & Người dùng',
        description: 'Kiểm soát hồ sơ, đơn hàng và tranh chấp trên hệ thống',
        items: [
          { label: 'Quản lý người dùng', description: 'Tra cứu tài khoản, phân quyền và nhân sự', to: '/admin/users', icon: <Users size={17} /> },
          { label: 'Duyệt hồ sơ KYC', description: 'Xem CCCD và xét duyệt danh tính', to: '/mod/kyc', icon: <UserCheck size={17} /> },
          { label: 'Kiểm duyệt đơn hàng', description: 'Xử lý đơn chứa từ khóa nhạy cảm', to: '/mod/orders', icon: <PackageSearch size={17} /> },
          { label: 'Khiếu nại & Vi phạm', description: 'Giải quyết khiếu nại và báo cáo vi phạm', to: '/mod/complaints', icon: <Flag size={17} /> },
        ],
      }
    );
  } else if (isModerator) {
    groups.push({
      title: 'Khu vực kiểm duyệt',
      description: 'Hàng đợi xử lý hồ sơ, đơn hàng và tranh chấp',
      items: [
        { label: 'Duyệt hồ sơ KYC', description: 'Xác minh CCCD của người gửi và người giao', to: '/mod/kyc', icon: <UserCheck size={17} /> },
        { label: 'Kiểm duyệt đơn hàng', description: 'Phê duyệt hoặc từ chối đơn cần rà soát', to: '/mod/orders', icon: <PackageSearch size={17} /> },
        { label: 'Báo cáo vi phạm', description: 'Xử lý vi phạm từ cộng đồng', to: '/mod/reports', icon: <Flag size={17} /> },
        { label: 'Giải quyết khiếu nại', description: 'Trao đổi chứng cứ và ra phán quyết', to: '/mod/complaints', icon: <Scale size={17} /> },
      ],
    });
  }

  if (isCourier) {
    groups.push(
      {
        title: 'Hoạt động người giao',
        description: 'Nhận đơn, quản lý lộ trình và gộp đơn tối ưu',
        items: [
          { label: 'Sàn đơn hàng', description: 'Tìm và nhận các đơn hàng đang mở', to: '/courier/market', icon: <Package size={17} /> },
          { label: 'Chuyến đi của tôi', description: 'Đăng chuyến và ghép đơn tiện đường', to: '/courier/trips', icon: <Navigation size={17} /> },
          { label: 'Đơn được phân công', description: 'Theo dõi đơn đang giao và nhắn tin', to: '/courier/orders', icon: <PackageSearch size={17} /> },
          { label: 'Gộp đơn tối ưu lộ trình', description: 'Tối ưu thứ tự các điểm lấy/giao', to: '/courier/batch', icon: <Layers size={17} /> },
        ],
      },
      {
        title: 'Thu nhập & Hồ sơ bưu tá',
        description: 'Thống kê doanh thu, hạng và không gian người giao',
        items: [
          { label: 'Thu nhập & Thống kê', description: 'Doanh thu ròng, hoa hồng và biểu đồ', to: '/courier/earnings', icon: <TrendingUp size={17} /> },
          { label: 'Hồ sơ người giao', description: 'Xem hạng thành viên và điều kiện nhận đơn', to: '/courier', icon: <Award size={17} /> },
        ],
      }
    );
  }

  if (isSender) {
    groups.push({
      title: 'Dịch vụ người gửi',
      description: 'Tạo đơn giao nhận, ký quỹ và địa chỉ lấy hàng',
      items: [
        { label: 'Đơn hàng của tôi', description: 'Tra cứu vận đơn và hành trình giao hàng', to: '/sender/orders', icon: <ClipboardList size={17} /> },
        { label: 'Tạo đơn hàng mới', description: 'Lên đơn giao hàng thường hoặc tiếp sức', to: '/sender/orders/new', icon: <PackagePlus size={17} /> },
        { label: 'Thanh toán & Ký quỹ', description: 'Ký quỹ đơn chờ qua ví hoặc VNPay', to: '/orders/payment', icon: <CreditCard size={17} /> },
        { label: 'Sổ địa chỉ', description: 'Quản lý địa chỉ gửi và nhận mặc định', to: '/me/addresses', icon: <MapPin size={17} /> },
        ...(!isCourier && !isStaff ? [{
          label: 'Đăng ký Người giao',
          description: 'Mở tính năng nhận đơn và tạo chuyến đi',
          to: '/me/courier-register',
          icon: <Navigation size={17} />,
        }] : []),
      ],
    });
  }

  if (!isStaff || isSender || isCourier) {
    groups.push({
      title: 'Ví tiền, Khiếu nại & KYC',
      description: 'Tài chính cá nhân và xác minh tài khoản',
      items: [
        { label: 'Ví FPTPost & Ngân hàng', description: 'Số dư khả dụng, nạp ví và rút tiền', to: '/wallet', icon: <Wallet size={17} /> },
        { label: 'Khiếu nại của tôi', description: 'Theo dõi tranh chấp và yêu cầu bồi hoàn', to: '/me/complaints', icon: <Scale size={17} /> },
        { label: 'Xác minh danh tính (KYC)', description: 'Cập nhật CCCD để bảo vệ tài khoản', to: '/me/kyc', icon: <ShieldCheck size={17} /> },
        { label: 'Huy hiệu & Giới thiệu', description: 'Xem huy hiệu và mã giới thiệu bạn bè', to: '/me/rewards', icon: <Award size={17} /> },
      ],
    });
  }

  groups.push({
    title: 'Hồ sơ & Bảo mật',
    description: 'Cài đặt tài khoản, thiết bị đăng nhập và thông báo',
    items: [
      { label: 'Hồ sơ cá nhân', description: 'Cập nhật họ tên và thông tin liên hệ', to: '/me/profile', icon: <UserRound size={17} /> },
      { label: 'Bảo mật và thiết bị', description: 'Đổi mật khẩu và quản lý phiên đăng nhập', to: '/me/security', icon: <Lock size={17} /> },
      { label: 'Thông báo & Cài đặt', description: 'Xem thông báo và tùy chỉnh kênh nhận tin', to: '/notifications', icon: <Bell size={17} /> },
    ],
  });

  return (
    <div className="ui-stack account-overview">
      <PageHeader
        eyebrow="Không gian cá nhân"
        title="Tài khoản của tôi"
        description="Quản lý thông tin cá nhân, bảo mật và truy cập nhanh các chức năng theo vai trò."
        actions={
          <Link className="ui-button ui-button-secondary ui-button-sm" to="/me/profile">
            <Pencil size={15} aria-hidden="true" />Chỉnh sửa hồ sơ
          </Link>
        }
      />

      <SectionCard>
        <div className="account-profile-banner">
          <UserAvatar size="lg" />
          <div className="account-profile-main">
            <div className="account-profile-name-row">
              <h2>{user.fullName || user.email}</h2>
              <Badge tone={user.emailVerifiedAt ? 'success' : 'warning'} dot>
                {user.emailVerifiedAt ? 'Đã xác thực email' : 'Chưa xác thực email'}
              </Badge>
              {user.fptBadge && <Badge tone="success">Cộng đồng FPT</Badge>}
              {roleLabels.map(label => (
                <Badge key={label} tone="info">{label}</Badge>
              ))}
            </div>
            <div className="account-profile-meta">
              <span><strong>Email:</strong> {user.email}</span>
              <span><strong>Số điện thoại:</strong> {user.phone || 'Chưa cập nhật'}</span>
            </div>
          </div>
        </div>
      </SectionCard>

      <div className="account-hub-grid">
        {groups.map(group => (
          <SectionCard key={group.title} title={group.title} description={group.description}>
            <div className="account-hub-list">
              {group.items.map(item => (
                <Link key={item.to + item.label} to={item.to} className="account-hub-item">
                  <span className="account-hub-icon">{item.icon}</span>
                  <div className="account-hub-copy">
                    <strong>{item.label}</strong>
                    <span>{item.description}</span>
                  </div>
                  <ChevronRight size={16} className="account-hub-arrow" aria-hidden="true" />
                </Link>
              ))}
            </div>
          </SectionCard>
        ))}
      </div>
    </div>
  );
}
