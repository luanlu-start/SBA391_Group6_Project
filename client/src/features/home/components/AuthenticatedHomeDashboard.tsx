import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  Award,
  Banknote,
  Boxes,
  ChevronRight,
  ClipboardList,
  CreditCard,
  Flag,
  LayoutDashboard,
  Layers,
  MapPin,
  Navigation,
  Package,
  PackagePlus,
  PackageSearch,
  Scale,
  ShieldCheck,
  TrendingUp,
  UserCheck,
  Users,
  Wallet,
} from 'lucide-react';
import type { ReactNode } from 'react';
import type { UserResponse } from '../../../types/identity';
import type { Order } from '../../../types/order';
import { ORDER_STATUS_MAP } from '../../../types/order';
import type { CourierEarningsResponse, CourierStatsResponse, WalletResponse } from '../../../types/payment';
import type { KycReviewItem } from '../../../types/kycReview';
import type { OrderModerationItem } from '../../../types/orderModeration';
import { useAccountMode } from '../../../auth/useAccountMode';
import { orderApi } from '../../../api/orderApi';
import { walletApi } from '../../../api/walletApi';
import { earningsApi } from '../../../api/earningsApi';
import { withdrawalReviewApi } from '../../../api/withdrawalReviewApi';
import { reconciliationApi } from '../../../api/reconciliationApi';
import { kycReviewApi } from '../../../api/kycReviewApi';
import { orderModerationApi } from '../../../api/orderModerationApi';
import { violationReportApi } from '../../../api/violationReportApi';
import { complaintApi } from '../../../api/complaintApi';
import SectionCard from '../../../components/ui/SectionCard';
import Badge from '../../../components/ui/Badge';
import LoadingSpinner from '../../../components/common/LoadingSpinner';

const vnd = new Intl.NumberFormat('vi-VN');

interface KpiItem {
  label: string;
  value: string;
  hint: string;
  to: string;
  tone: 'brand' | 'info' | 'success' | 'warning';
  icon: ReactNode;
}

interface ShortcutItem {
  label: string;
  description: string;
  to: string;
  icon: ReactNode;
}

interface ShortcutGroup {
  title: string;
  subtitle: string;
  items: ShortcutItem[];
}

function formatMoney(amount?: number | null) {
  return `${vnd.format(amount ?? 0)} đ`;
}

function getOrderAmount(order: Order) {
  return order.priceAgreed ?? order.priceSuggested ?? 0;
}

export default function AuthenticatedHomeDashboard({ user }: { user: UserResponse }) {
  const { mode } = useAccountMode();
  const roles = user.roles ?? [];
  const isAdmin = roles.includes('ADMIN');
  const isModerator = roles.includes('MODERATOR');
  const isCourierRole = roles.includes('COURIER');
  const isSenderRole = roles.includes('SENDER');
  const isCourierActive = !isAdmin && !isModerator && isCourierRole && (!isSenderRole || mode === 'COURIER');
  const isModOnly = isModerator && !isAdmin;

  const activeView = isAdmin
    ? 'ADMIN'
    : isModOnly
      ? 'MODERATOR'
      : isCourierActive
        ? 'COURIER'
        : 'SENDER';

  const [loading, setLoading] = useState(true);
  const [orders, setOrders] = useState<Order[]>([]);
  const [wallet, setWallet] = useState<WalletResponse | null>(null);
  const [earnings, setEarnings] = useState<CourierEarningsResponse | null>(null);
  const [stats, setStats] = useState<CourierStatsResponse | null>(null);
  const [openWithdrawalsCount, setOpenWithdrawalsCount] = useState(0);
  const [reconcileMismatchCount, setReconcileMismatchCount] = useState(0);
  const [pendingKycItems, setPendingKycItems] = useState<KycReviewItem[]>([]);
  const [pendingKycCount, setPendingKycCount] = useState(0);
  const [pendingModOrders, setPendingModOrders] = useState<OrderModerationItem[]>([]);
  const [pendingModCount, setPendingModCount] = useState(0);
  const [openReportsCount, setOpenReportsCount] = useState(0);
  const [activeComplaintsCount, setActiveComplaintsCount] = useState(0);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    setLoading(true);

    async function loadDashboard() {
      if (activeView === 'ADMIN') {
        const [allOrdersRes, withdrawalsRes, reconcileRes, complaintsRes] = await Promise.allSettled([
          orderApi.getAll(),
          withdrawalReviewApi.list({ status: 'OPEN', page: 0, size: 1 }, controller.signal),
          reconciliationApi.getReports(0, 1),
          complaintApi.queue('ACTIVE', false, 0, controller.signal),
        ]);
        if (!active) return;
        if (allOrdersRes.status === 'fulfilled') setOrders(allOrdersRes.value.data || []);
        if (withdrawalsRes.status === 'fulfilled') setOpenWithdrawalsCount(withdrawalsRes.value.data?.totalElements ?? 0);
        if (reconcileRes.status === 'fulfilled') {
          const latest = reconcileRes.value.data?.content?.[0];
          setReconcileMismatchCount(latest?.mismatchCount ?? 0);
        }
        if (complaintsRes.status === 'fulfilled') setActiveComplaintsCount(complaintsRes.value.data?.totalElements ?? 0);
      } else if (activeView === 'MODERATOR') {
        const [kycRes, modOrdersRes, reportsRes, complaintsRes] = await Promise.allSettled([
          kycReviewApi.list({ status: 'PENDING', page: 0, size: 5 }, controller.signal),
          orderModerationApi.pending({ page: 0, size: 5 }, controller.signal),
          violationReportApi.queue('OPEN', undefined, 0, controller.signal),
          complaintApi.queue('ACTIVE', false, 0, controller.signal),
        ]);
        if (!active) return;
        if (kycRes.status === 'fulfilled') {
          setPendingKycItems(kycRes.value.data?.content ?? []);
          setPendingKycCount(kycRes.value.data?.totalElements ?? 0);
        }
        if (modOrdersRes.status === 'fulfilled') {
          setPendingModOrders(modOrdersRes.value.data?.content ?? []);
          setPendingModCount(modOrdersRes.value.data?.totalElements ?? 0);
        }
        if (reportsRes.status === 'fulfilled') setOpenReportsCount(reportsRes.value.data?.totalElements ?? 0);
        if (complaintsRes.status === 'fulfilled') setActiveComplaintsCount(complaintsRes.value.data?.totalElements ?? 0);
      } else if (activeView === 'COURIER') {
        const [assignedRes, walletRes, earningsRes, statsRes] = await Promise.allSettled([
          orderApi.getAssignedToMe(),
          walletApi.getWallet(),
          earningsApi.getMyEarnings({ period: 'MONTH' }),
          earningsApi.getMyStats(),
        ]);
        if (!active) return;
        if (assignedRes.status === 'fulfilled') setOrders(assignedRes.value.data || []);
        if (walletRes.status === 'fulfilled') setWallet(walletRes.value.data ?? null);
        if (earningsRes.status === 'fulfilled') setEarnings(earningsRes.value.data ?? null);
        if (statsRes.status === 'fulfilled') setStats(statsRes.value.data ?? null);
      } else {
        const [myOrdersRes, walletRes] = await Promise.allSettled([
          orderApi.getMyOrders(),
          walletApi.getWallet(),
        ]);
        if (!active) return;
        if (myOrdersRes.status === 'fulfilled') setOrders(myOrdersRes.value.data || []);
        if (walletRes.status === 'fulfilled') setWallet(walletRes.value.data ?? null);
      }

      if (active) setLoading(false);
    }

    void loadDashboard();
    return () => {
      active = false;
      controller.abort();
    };
  }, [activeView]);

  const roleMeta = {
    ADMIN: {
      badge: 'Quản trị viên',
      subtitle: 'Theo dõi tình hình vận đơn, giải ngân ký quỹ, duyệt rút tiền và đối soát giao dịch hệ thống.',
      primaryCta: { label: 'Tổng quan quản trị', to: '/admin/dashboard', icon: <LayoutDashboard size={17} /> },
      secondaryCta: { label: 'Đơn hàng & Giải ngân', to: '/sender/orders', icon: <ClipboardList size={17} /> },
    },
    MODERATOR: {
      badge: 'Kiểm duyệt viên',
      subtitle: 'Xử lý hàng đợi duyệt danh tính KYC, kiểm duyệt nội dung đơn hàng, báo cáo vi phạm và khiếu nại.',
      primaryCta: { label: 'Duyệt hồ sơ KYC', to: '/mod/kyc', icon: <ShieldCheck size={17} /> },
      secondaryCta: { label: 'Kiểm duyệt đơn hàng', to: '/mod/orders', icon: <PackageSearch size={17} /> },
    },
    COURIER: {
      badge: 'Người giao hàng',
      subtitle: 'Nhận đơn phù hợp trên sàn, quản lý chuyến đi, tối ưu gộp đơn và theo dõi thu nhập ròng.',
      primaryCta: { label: 'Tìm đơn trên sàn', to: '/courier/market', icon: <Package size={17} /> },
      secondaryCta: { label: 'Đăng chuyến đi mới', to: '/courier/trips/new', icon: <Navigation size={17} /> },
    },
    SENDER: {
      badge: 'Người gửi hàng',
      subtitle: 'Tạo đơn giao nhận nhanh chóng, thanh toán ký quỹ an toàn và theo dõi hành trình từng kiện hàng.',
      primaryCta: { label: 'Tạo đơn hàng mới', to: '/sender/orders/new', icon: <PackagePlus size={17} /> },
      secondaryCta: { label: 'Ví tiền & Nạp tiền', to: '/wallet', icon: <Wallet size={17} /> },
    },
  }[activeView];

  const kpis: KpiItem[] = (() => {
    if (activeView === 'ADMIN') {
      const completedCount = orders.filter(o => o.status === 'COMPLETED').length;
      return [
        {
          label: 'Tổng vận đơn hệ thống',
          value: String(orders.length),
          hint: 'Xem toàn bộ đơn hàng',
          to: '/sender/orders',
          tone: 'info',
          icon: <Package size={18} />,
        },
        {
          label: 'Hoàn thành (chờ rà soát)',
          value: String(completedCount),
          hint: 'Kiểm tra & giải ngân',
          to: '/sender/orders',
          tone: 'success',
          icon: <ShieldCheck size={18} />,
        },
        {
          label: 'Yêu cầu rút tiền mở',
          value: String(openWithdrawalsCount),
          hint: 'Chờ duyệt & chi trả',
          to: '/admin/withdrawals',
          tone: 'warning',
          icon: <Banknote size={18} />,
        },
        {
          label: 'Lệch đối soát / Khiếu nại',
          value: `${reconcileMismatchCount} / ${activeComplaintsCount}`,
          hint: 'Đối soát & xử lý tranh chấp',
          to: '/admin/reconcile',
          tone: 'brand',
          icon: <Scale size={18} />,
        },
      ];
    }

    if (activeView === 'MODERATOR') {
      return [
        {
          label: 'Hồ sơ KYC chờ duyệt',
          value: String(pendingKycCount),
          hint: 'Mở danh sách duyệt KYC',
          to: '/mod/kyc',
          tone: 'warning',
          icon: <UserCheck size={18} />,
        },
        {
          label: 'Đơn chờ kiểm duyệt',
          value: String(pendingModCount),
          hint: 'Kiểm tra nội dung hàng hóa',
          to: '/mod/orders',
          tone: 'info',
          icon: <PackageSearch size={18} />,
        },
        {
          label: 'Báo cáo vi phạm mở',
          value: String(openReportsCount),
          hint: 'Xem hồ sơ báo cáo',
          to: '/mod/reports',
          tone: 'brand',
          icon: <Flag size={18} />,
        },
        {
          label: 'Khiếu nại đang xử lý',
          value: String(activeComplaintsCount),
          hint: 'Phân xử & phản hồi',
          to: '/mod/complaints',
          tone: 'success',
          icon: <Scale size={18} />,
        },
      ];
    }

    if (activeView === 'COURIER') {
      const activeOrdersCount = orders.filter(
        o => !['COMPLETED', 'CANCELLED', 'RETURNED', 'FAILED', 'EXPIRED'].includes(o.status)
      ).length;
      return [
        {
          label: 'Số dư khả dụng',
          value: formatMoney(wallet?.available),
          hint: 'Quản lý ví & rút tiền',
          to: '/wallet',
          tone: 'brand',
          icon: <Wallet size={18} />,
        },
        {
          label: 'Thu nhập ròng tháng này',
          value: formatMoney(earnings?.netEarnings),
          hint: 'Xem thống kê chi tiết',
          to: '/courier/earnings',
          tone: 'success',
          icon: <TrendingUp size={18} />,
        },
        {
          label: 'Đơn đang phụ trách',
          value: String(activeOrdersCount),
          hint: 'Xem đơn đã nhận',
          to: '/courier/orders',
          tone: 'info',
          icon: <Package size={18} />,
        },
        {
          label: 'Điểm uy tín & Hạng',
          value: stats ? `${stats.trustScore}đ • ${stats.currentTierDisplayName}` : 'Đang cập nhật',
          hint: 'Không gian người giao',
          to: '/courier',
          tone: 'warning',
          icon: <Award size={18} />,
        },
      ];
    }

    const awaitingPayCount = orders.filter(o =>
      ['PENDING', 'PENDING_ESCROW', 'AWAITING_PAYMENT'].includes(o.status)
    ).length;
    const inProgressCount = orders.filter(o =>
      ['ESCROWED', 'FINDING_SHIPPER', 'SHIPPER_ASSIGNED', 'SHIPPER_ACCEPTED', 'PICKING_UP', 'IN_TRANSIT', 'IN_DELIVERY', 'DELIVERED'].includes(o.status)
    ).length;
    const completedCount = orders.filter(o => o.status === 'COMPLETED').length;
    return [
      {
        label: 'Số dư ví khả dụng',
        value: formatMoney(wallet?.available),
        hint: 'Nạp tiền & sao kê ví',
        to: '/wallet',
        tone: 'brand',
        icon: <Wallet size={18} />,
      },
      {
        label: 'Đơn chờ thanh toán',
        value: String(awaitingPayCount),
        hint: 'Thanh toán & ký quỹ ngay',
        to: '/orders/payment',
        tone: 'warning',
        icon: <CreditCard size={18} />,
      },
      {
        label: 'Đơn đang giao / tìm tài xế',
        value: String(inProgressCount),
        hint: 'Theo dõi hành trình đơn',
        to: '/sender/orders',
        tone: 'info',
        icon: <Navigation size={18} />,
      },
      {
        label: 'Đơn đã hoàn thành',
        value: String(completedCount),
        hint: 'Xem lịch sử vận đơn',
        to: '/sender/orders',
        tone: 'success',
        icon: <ShieldCheck size={18} />,
      },
    ];
  })();

  const shortcutGroups: ShortcutGroup[] = (() => {
    if (activeView === 'ADMIN') {
      return [
        {
          title: 'Tài chính & Ký quỹ',
          subtitle: 'Nghiệp vụ đối soát và chi trả trọng tâm',
          items: [
            { label: 'Đơn hàng & Giải ngân', description: 'Giải ngân cho người giao sau khi hoàn thành', to: '/sender/orders', icon: <ClipboardList size={17} /> },
            { label: 'Duyệt rút tiền', description: 'Xử lý và chi trả yêu cầu rút về ngân hàng', to: '/admin/withdrawals', icon: <Banknote size={17} /> },
            { label: 'Đối soát giao dịch', description: 'So khớp cổng VNPay & sổ cái hệ thống', to: '/admin/reconcile', icon: <Scale size={17} /> },
            { label: 'Báo cáo thống kê', description: 'Doanh thu, hoa hồng & xuất CSV', to: '/admin/reports', icon: <TrendingUp size={17} /> },
          ],
        },
        {
          title: 'Kiểm duyệt & Cấu hình',
          subtitle: 'Quản lý người dùng và an toàn sàn',
          items: [
            { label: 'Quản lý người dùng', description: 'Tra cứu tài khoản, khóa/mở và vai trò', to: '/admin/users', icon: <Users size={17} /> },
            { label: 'Duyệt hồ sơ KYC', description: 'Xác minh danh tính người dùng', to: '/mod/kyc', icon: <ShieldCheck size={17} /> },
            { label: 'Khiếu nại & Vi phạm', description: 'Giải quyết tranh chấp và kháng nghị', to: '/mod/complaints', icon: <Flag size={17} /> },
          ],
        },
      ];
    }

    if (activeView === 'MODERATOR') {
      return [
        {
          title: 'Hàng đợi xử lý',
          subtitle: 'Ưu tiên kiểm duyệt theo SLA',
          items: [
            { label: 'Duyệt hồ sơ KYC', description: 'Kiểm tra CCCD và duyệt xác minh', to: '/mod/kyc', icon: <UserCheck size={17} /> },
            { label: 'Kiểm duyệt đơn hàng', description: 'Rà soát đơn nghi vấn hàng cấm', to: '/mod/orders', icon: <PackageSearch size={17} /> },
            { label: 'Báo cáo vi phạm', description: 'Xử lý phản ánh hành vi sai phạm', to: '/mod/reports', icon: <Flag size={17} /> },
            { label: 'Khiếu nại đơn hàng', description: 'Phân xử bồi thường & hoàn tiền', to: '/mod/complaints', icon: <Scale size={17} /> },
          ],
        },
      ];
    }

    if (activeView === 'COURIER') {
      return [
        {
          title: 'Công cụ giao nhận',
          subtitle: 'Tối ưu tuyến đường và nhận đơn',
          items: [
            { label: 'Gộp đơn tối ưu lộ trình', description: 'Sắp xếp thứ tự ghé điểm lấy/giao thông minh', to: '/courier/batch', icon: <Layers size={17} /> },
            { label: 'Chuyến đi của tôi', description: 'Quản lý tuyến cố định & nhận đơn tiện đường', to: '/courier/trips', icon: <Navigation size={17} /> },
            { label: 'Đơn được phân công', description: 'Theo dõi danh sách đơn và nhắn tin người gửi', to: '/courier/orders', icon: <PackageSearch size={17} /> },
          ],
        },
        {
          title: 'Tài chính & Hồ sơ',
          subtitle: 'Thu nhập và thông tin bưu tá',
          items: [
            { label: 'Thu nhập & Thống kê', description: 'Doanh thu theo ngày/tháng và biểu đồ', to: '/courier/earnings', icon: <TrendingUp size={17} /> },
            { label: 'Ví FPTPost & Ngân hàng', description: 'Xem số dư và rút tiền về tài khoản', to: '/wallet', icon: <Wallet size={17} /> },
            { label: 'Không gian người giao', description: 'Xem hạng thành viên và hồ sơ hoạt động', to: '/courier', icon: <Award size={17} /> },
          ],
        },
      ];
    }

    return [
      {
        title: 'Thao tác nhanh',
        subtitle: 'Các tính năng sử dụng thường xuyên',
        items: [
          { label: 'Thanh toán & Ký quỹ', description: 'Thanh toán đơn đang chờ bằng ví hoặc VNPay', to: '/orders/payment', icon: <CreditCard size={17} /> },
          { label: 'Sổ địa chỉ', description: 'Lưu địa chỉ lấy/giao quen dùng', to: '/me/addresses', icon: <MapPin size={17} /> },
          { label: 'Khiếu nại của tôi', description: 'Theo dõi tiến trình xử lý khiếu nại', to: '/me/complaints', icon: <Scale size={17} /> },
        ],
      },
      {
        title: 'Tài khoản & Tiện ích',
        subtitle: 'Quản lý xác minh và ưu đãi',
        items: [
          { label: 'Xác minh danh tính (KYC)', description: 'Nâng hạn mức và độ tin cậy tài khoản', to: '/me/kyc', icon: <ShieldCheck size={17} /> },
          { label: 'Huy hiệu & Giới thiệu', description: 'Mã giới thiệu bạn bè và phần thưởng', to: '/me/rewards', icon: <Boxes size={17} /> },
          ...(!isCourierRole ? [{
            label: 'Đăng ký Người giao',
            description: 'Nhận đơn tiện đường để tăng thu nhập',
            to: '/me/courier-register',
            icon: <Navigation size={17} />,
          }] : []),
        ],
      },
    ];
  })();

  const sortedRecentOrders = (() => {
    if (activeView === 'ADMIN') {
      return [...orders]
        .sort((a, b) => {
          if (a.status === 'COMPLETED' && b.status !== 'COMPLETED') return -1;
          if (a.status !== 'COMPLETED' && b.status === 'COMPLETED') return 1;
          return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
        })
        .slice(0, 6);
    }
    return [...orders]
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .slice(0, 6);
  })();

  return (
    <div className="home-dash">
      <section className="home-dash-banner">
        <div className="home-dash-banner-copy">
          <div className="ui-row" style={{ gap: '8px', marginBottom: '6px' }}>
            <Badge tone="info" dot>{roleMeta.badge}</Badge>
          </div>
          <h1>Xin chào, {user.fullName || user.email}</h1>
          <p>{roleMeta.subtitle}</p>
        </div>
        <div className="home-dash-banner-actions">
          <Link to={roleMeta.primaryCta.to} className="ui-button ui-button-primary">
            {roleMeta.primaryCta.icon}
            {roleMeta.primaryCta.label}
          </Link>
          <Link to={roleMeta.secondaryCta.to} className="ui-button ui-button-secondary">
            {roleMeta.secondaryCta.icon}
            {roleMeta.secondaryCta.label}
          </Link>
        </div>
      </section>

      <section className="home-dash-kpis" aria-label="Tổng quan nhanh">
        {kpis.map(item => (
          <Link key={item.label} to={item.to} className="home-kpi-card">
            <div className="home-kpi-top">
              <span className="home-kpi-label">{item.label}</span>
              <span className={`home-kpi-icon home-kpi-icon-${item.tone}`}>{item.icon}</span>
            </div>
            <strong className="home-kpi-value">{loading ? '—' : item.value}</strong>
            <span className="home-kpi-hint">
              {item.hint}
              <ArrowRight size={13} aria-hidden="true" />
            </span>
          </Link>
        ))}
      </section>

      <div className="home-dash-grid">
        <div className="home-dash-main">
          {activeView === 'MODERATOR' ? (
            <SectionCard
              title="Hàng đợi kiểm duyệt gần nhất"
              description="Các hồ sơ KYC và đơn hàng cần xử lý ưu tiên"
              actions={<Link to="/mod/kyc" className="ui-button ui-button-ghost ui-button-sm">Mở khu kiểm duyệt <ArrowRight size={14} /></Link>}
            >
              {loading ? (
                <LoadingSpinner text="Đang tải hàng đợi kiểm duyệt…" />
              ) : pendingKycItems.length === 0 && pendingModOrders.length === 0 ? (
                <div className="home-dash-empty">
                  <ShieldCheck size={28} />
                  <p>Hiện không có hồ sơ KYC hay đơn hàng nào đang chờ duyệt.</p>
                </div>
              ) : (
                <div className="home-dash-list">
                  {pendingKycItems.map(item => (
                    <Link key={`kyc-${item.id}`} to="/mod/kyc" className="home-dash-row">
                      <div className="home-dash-row-info">
                        <strong>Hồ sơ KYC • {item.fullName}</strong>
                        <span>{item.email} · CCCD: {item.idNumberMasked}</span>
                      </div>
                      <div className="home-dash-row-meta">
                        <Badge tone={item.overdue ? 'danger' : 'warning'} dot>
                          {item.overdue ? 'Quá hạn' : 'Chờ duyệt KYC'}
                        </Badge>
                        <ChevronRight size={16} aria-hidden="true" />
                      </div>
                    </Link>
                  ))}
                  {pendingModOrders.map(item => (
                    <Link key={`mod-${item.orderId}`} to="/mod/orders" className="home-dash-row">
                      <div className="home-dash-row-info">
                        <strong>Kiểm duyệt đơn • {item.orderCode || `#${item.orderId}`}</strong>
                        <span>{item.productName || 'Kiện hàng'} · {item.matchedTerms.join(', ') || 'Cần kiểm tra thủ công'}</span>
                      </div>
                      <div className="home-dash-row-meta">
                        <Badge tone="warning" dot>Chờ duyệt đơn</Badge>
                        <ChevronRight size={16} aria-hidden="true" />
                      </div>
                    </Link>
                  ))}
                </div>
              )}
            </SectionCard>
          ) : (
            <SectionCard
              title={
                activeView === 'ADMIN'
                  ? 'Đơn hàng cần theo dõi & Giải ngân'
                  : activeView === 'COURIER'
                    ? 'Đơn hàng đang phụ trách'
                    : 'Đơn hàng gần đây'
              }
              description={
                activeView === 'ADMIN'
                  ? 'Ưu tiên các đơn hoàn thành chờ xác nhận giải ngân ký quỹ'
                  : activeView === 'COURIER'
                    ? 'Danh sách vận đơn đã nhận để trao đổi và giao hàng'
                    : 'Theo dõi trạng thái các vận đơn mới nhất của bạn'
              }
              actions={
                <Link
                  to={activeView === 'COURIER' ? '/courier/orders' : '/sender/orders'}
                  className="ui-button ui-button-ghost ui-button-sm"
                >
                  Xem tất cả <ArrowRight size={14} />
                </Link>
              }
            >
              {loading ? (
                <LoadingSpinner text="Đang tải danh sách đơn hàng…" />
              ) : sortedRecentOrders.length === 0 ? (
                <div className="home-dash-empty">
                  <Package size={28} />
                  <p>
                    {activeView === 'COURIER'
                      ? 'Bạn chưa có đơn hàng nào đang nhận. Hãy ghé Sàn đơn hàng để nhận chuyến mới.'
                      : 'Chưa có đơn hàng nào. Tạo đơn đầu tiên để bắt đầu giao nhận.'}
                  </p>
                  <Link
                    to={activeView === 'COURIER' ? '/courier/market' : '/sender/orders/new'}
                    className="ui-button ui-button-primary ui-button-sm"
                  >
                    {activeView === 'COURIER' ? 'Mở sàn đơn hàng' : 'Tạo đơn hàng mới'}
                  </Link>
                </div>
              ) : (
                <div className="home-dash-list">
                  {sortedRecentOrders.map(order => {
                    const statusCfg = ORDER_STATUS_MAP[order.status] ?? { label: order.status, tone: 'neutral' as const };
                    const targetLink =
                      activeView === 'COURIER'
                        ? `/orders/${order.id}/chat`
                        : `/sender/orders/${order.id}`;
                    return (
                      <Link key={order.id} to={targetLink} className="home-dash-row">
                        <div className="home-dash-row-info">
                          <strong>{order.orderCode} — {order.productName}</strong>
                          <span>{order.pickupAddress} → {order.deliveryAddress}</span>
                        </div>
                        <div className="home-dash-row-meta">
                          <span className="home-dash-row-price">{formatMoney(getOrderAmount(order))}</span>
                          <Badge tone={statusCfg.tone} dot>{statusCfg.label}</Badge>
                          <ChevronRight size={16} aria-hidden="true" />
                        </div>
                      </Link>
                    );
                  })}
                </div>
              )}
            </SectionCard>
          )}
        </div>

        <aside className="home-dash-side">
          {shortcutGroups.map(group => (
            <SectionCard key={group.title} title={group.title} description={group.subtitle}>
              <div className="home-shortcut-list">
                {group.items.map(item => (
                  <Link key={item.to + item.label} to={item.to} className="home-shortcut-item">
                    <span className="home-shortcut-icon">{item.icon}</span>
                    <div className="home-shortcut-copy">
                      <strong>{item.label}</strong>
                      <span>{item.description}</span>
                    </div>
                    <ChevronRight size={16} className="home-shortcut-arrow" aria-hidden="true" />
                  </Link>
                ))}
              </div>
            </SectionCard>
          ))}
        </aside>
      </div>
    </div>
  );
}
