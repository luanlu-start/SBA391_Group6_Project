import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useAuthentication } from '../auth/useAuthentication';
import { authService } from '../services/authService';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import ApiErrorNotice from '../components/ApiErrorNotice';
import Button from '../components/ui/Button';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Badge from '../components/ui/Badge';
import '../styles/registration.css';

export default function AccountPage() {
  const { user, initializing } = useAuthentication();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError>();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, []);

  async function logout() {
    setBusy(true);
    setError(undefined);
    try {
      await authService.logout();
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setBusy(false);
    }
  }

  if (initializing) return <section className="registration-page" role="status">Đang kiểm tra phiên đăng nhập…</section>;
  if (!user) return <Navigate to="/login" replace />;

  const isAdmin = user.roles.includes('ADMIN');
  const isModerator = user.roles.includes('MODERATOR');
  const isCourier = user.roles.includes('COURIER');
  const isSender = user.roles.includes('SENDER');
  const isStaff = isAdmin || isModerator;
  const isCustomer = isSender || isCourier || !isStaff;

  return (
    <div className="ui-stack">
      <PageHeader
        title={`Xin chào, ${user.fullName || user.email}`}
        description="Thông tin tài khoản, vai trò và lối tắt thao tác theo phân quyền hiện tại."
      />
      {error && <ApiErrorNotice error={error} />}
      <SectionCard title="Thông tin tài khoản" actions={<Badge tone="success" dot>Đã xác thực</Badge>}>
        <div className="ui-stack">
          <dl className="account-details">
            <dt>Email</dt>
            <dd>{user.email}</dd>
            <dt>Số điện thoại</dt>
            <dd>{user.phone}</dd>
            <dt>Vai trò</dt>
            <dd>
              {user.roles
                .map(role => ({
                  SENDER: 'Người gửi',
                  COURIER: 'Người giao',
                  ADMIN: 'Quản trị viên',
                  MODERATOR: 'Kiểm duyệt viên',
                }[role] || role))
                .join(', ')}
            </dd>
          </dl>

          <div className="ui-form-actions" style={{ justifyContent: 'flex-start', flexWrap: 'wrap', gap: '10px' }}>
            {isAdmin && (
              <>
                <Link className="ui-button ui-button-primary" to="/admin/dashboard">
                  Bảng điều khiển quản trị
                </Link>
                <Link className="ui-button ui-button-secondary" to="/sender/orders">
                  Quản lý đơn hàng &amp; Giải ngân
                </Link>
                <Link className="ui-button ui-button-secondary" to="/admin/withdrawals">
                  Duyệt rút tiền
                </Link>
                <Link className="ui-button ui-button-secondary" to="/admin/reconcile">
                  Đối soát giao dịch
                </Link>
                <Link className="ui-button ui-button-secondary" to="/admin/users">
                  Quản lý người dùng
                </Link>
                <Link className="ui-button ui-button-secondary" to="/mod/complaints">
                  Khiếu nại &amp; Kháng nghị
                </Link>
              </>
            )}

            {isModerator && !isAdmin && (
              <>
                <Link className="ui-button ui-button-primary" to="/mod/kyc">
                  Duyệt hồ sơ KYC
                </Link>
                <Link className="ui-button ui-button-secondary" to="/mod/orders">
                  Kiểm duyệt đơn hàng
                </Link>
                <Link className="ui-button ui-button-secondary" to="/mod/reports">
                  Báo cáo vi phạm
                </Link>
                <Link className="ui-button ui-button-secondary" to="/mod/complaints">
                  Giải quyết khiếu nại
                </Link>
              </>
            )}

            {isCourier && (
              <>
                <Link className="ui-button ui-button-primary" to="/courier/market">
                  Sàn đơn hàng
                </Link>
                <Link className="ui-button ui-button-primary" to="/courier/trips/new">
                  Đăng chuyến đi
                </Link>
                <Link className="ui-button ui-button-secondary" to="/courier/trips">
                  Chuyến đi của tôi
                </Link>
                <Link className="ui-button ui-button-secondary" to="/courier/orders">
                  Đơn đang nhận
                </Link>
                <Link className="ui-button ui-button-secondary" to="/courier/earnings">
                  Thu nhập &amp; Thống kê
                </Link>
                <Link className="ui-button ui-button-secondary" to="/courier">
                  Không gian người giao
                </Link>
              </>
            )}

            {isSender && (
              <>
                <Link className="ui-button ui-button-primary" to="/sender/orders">
                  Đơn hàng của tôi
                </Link>
                <Link className="ui-button ui-button-secondary" to="/sender/orders/new">
                  Tạo đơn hàng mới
                </Link>
                <Link className="ui-button ui-button-secondary" to="/orders/payment">
                  Thanh toán &amp; Ký quỹ đơn hàng
                </Link>
                <Link className="ui-button ui-button-secondary" to="/me/addresses">
                  Sổ địa chỉ
                </Link>
                {!isCourier && !isStaff && (
                  <Link className="ui-button ui-button-secondary" to="/me/courier-register">
                    Đăng ký Người giao
                  </Link>
                )}
              </>
            )}

            {isCustomer && (
              <>
                <Link className="ui-button ui-button-secondary" to="/wallet">
                  Ví FPTPost
                </Link>
                <Link className="ui-button ui-button-secondary" to="/me/complaints">
                  Khiếu nại của tôi
                </Link>
                <Link className="ui-button ui-button-secondary" to="/me/rewards">
                  Huy hiệu và giới thiệu
                </Link>
                <Link className="ui-button ui-button-secondary" to="/me/kyc">
                  Xác minh danh tính
                </Link>
              </>
            )}

            <Link className="ui-button ui-button-secondary" to="/me/profile">
              Hồ sơ cá nhân
            </Link>
            <Link className="ui-button ui-button-secondary" to="/me/security">
              Bảo mật và thiết bị
            </Link>
            <Link className="ui-button ui-button-secondary" to="/notifications">
              Thông báo
            </Link>
            <Link className="ui-button ui-button-secondary" to="/">
              Về trang chủ
            </Link>
            <Button variant="danger" onClick={logout} loading={busy} loadingText="Đang đăng xuất…">
              Đăng xuất
            </Button>
          </div>
        </div>
      </SectionCard>
    </div>
  );
}
