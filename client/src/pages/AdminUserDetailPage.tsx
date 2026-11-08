import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Save } from 'lucide-react';
import { adminUserApi } from '../api/adminUserApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { AccountRole, AdminUserDetail } from '../types/adminUsers';
import {
  accountStatusLabels, accountStatusTones, formatAdminMoney, formatAdminTime, requestStatusLabels, requestTypeLabels, roleLabels,
} from '../features/admin-users/adminUserPresentation';
import { ORDER_STATUS_MAP } from '../types/order';
import type { OrderStatus } from '../types/order';
import ApiErrorNotice from '../components/ApiErrorNotice';
import LoadingSpinner from '../components/common/LoadingSpinner';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Checkbox from '../components/ui/Checkbox';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import Alert from '../components/ui/Alert';
import '../styles/admin-users.css';

const allRoles: AccountRole[] = ['SENDER', 'COURIER', 'MODERATOR', 'ADMIN'];
const kycLabels = { PENDING: 'Chờ duyệt', APPROVED: 'Đã xác minh', REJECTED: 'Bị từ chối' } as const;

export default function AdminUserDetailPage() {
  const { publicId = '' } = useParams<{ publicId: string }>();
  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [roles, setRoles] = useState<AccountRole[]>([]);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    void adminUserApi.get(publicId, controller.signal)
      .then(response => { if (!controller.signal.aborted) { setDetail(response.data); setRoles(response.data.account.roles); } })
      .catch(cause => { if (!controller.signal.aborted) setError(getApiError(cause)); });
    return () => controller.abort();
  }, [publicId]);

  async function saveRoles() {
    setSaving(true);
    setSaveError(null);
    setNotice('');
    try {
      const response = await adminUserApi.updateRoles(publicId, roles);
      setDetail(response.data);
      setRoles(response.data.account.roles);
      setNotice('Đã cập nhật vai trò. Các phiên đăng nhập của người dùng đã bị thu hồi.');
    } catch (cause) {
      setSaveError(getApiError(cause));
    } finally {
      setSaving(false);
    }
  }

  if (error) return <div className="ui-stack"><ApiErrorNotice message={error.message} correlationId={error.correlationId} />
    <Link to="/admin/users" className="ui-button ui-button-secondary">Về danh sách</Link></div>;
  if (!detail) return <LoadingSpinner text="Đang tải người dùng…" />;

  const account = detail.account;
  const changed = [...roles].sort().join() !== [...account.roles].sort().join();
  const editable = !detail.self && account.status !== 'DELETED';

  return <div className="ui-stack admin-users-page">
    <PageHeader title={account.fullName ?? account.email} eyebrow="Chi tiết người dùng" description={account.email}
      actions={<Link to="/admin/users" className="ui-button ui-button-ghost"><ArrowLeft size={16} /> Danh sách</Link>} />
    <div className="admin-users-detail-grid">
      <SectionCard title="Tài khoản">
        <dl className="admin-users-dl">
          <div><dt>Trạng thái</dt><dd><Badge dot tone={accountStatusTones[account.status]}>{accountStatusLabels[account.status]}</Badge></dd></div>
          <div><dt>Số điện thoại</dt><dd className="admin-users-code">{account.phone}{account.phoneVerified ? ' · đã xác thực' : ''}</dd></div>
          <div><dt>Ngày sinh</dt><dd>{detail.dob ?? '—'}</dd></div>
          <div><dt>KYC</dt><dd>{detail.kycStatus ? kycLabels[detail.kycStatus] : 'Chưa nộp'}</dd></div>
          <div><dt>Ngày tạo</dt><dd>{formatAdminTime(account.createdAt)}</dd></div>
          <div><dt>Phiên đang hoạt động</dt><dd>{detail.activeSessions}</dd></div>
          <div><dt>Mã người dùng</dt><dd className="admin-users-code">{account.publicId}</dd></div>
          {detail.courierTier && <div><dt>Người giao</dt><dd>Hạng {detail.courierTier} · {detail.courierCompletedOrders ?? 0} đơn · điểm {detail.courierTrustScore ?? '—'}</dd></div>}
        </dl>
      </SectionCard>
      <SectionCard title="Vai trò" description="Đổi vai trò sẽ đăng xuất người dùng khỏi mọi thiết bị.">
        <div className="ui-stack">
          {allRoles.map(role => <Checkbox key={role} label={roleLabels[role]} checked={roles.includes(role)} disabled={!editable || saving}
            onChange={event => setRoles(previous => event.target.checked ? [...previous, role] : previous.filter(item => item !== role))} />)}
          {detail.self && <Alert tone="info">Bạn không thể tự đổi vai trò của chính mình.</Alert>}
          {notice && <Alert tone="success">{notice}</Alert>}
          {saveError && <ApiErrorNotice message={saveError.message} correlationId={saveError.correlationId} />}
          <div><Button icon={<Save size={16} />} loading={saving} disabled={!editable || !changed || roles.length === 0} onClick={() => void saveRoles()}>
            Lưu vai trò</Button></div>
        </div>
      </SectionCard>
      <SectionCard title="Ví">
        <dl className="admin-users-dl">
          <div><dt>Khả dụng</dt><dd>{formatAdminMoney(detail.walletAvailable)}</dd></div>
          <div><dt>Đang ký quỹ</dt><dd>{formatAdminMoney(detail.walletEscrowHeld)}</dd></div>
          <div><dt>Chờ rút</dt><dd>{formatAdminMoney(detail.walletPendingWithdraw)}</dd></div>
        </dl>
        <h3 className="admin-users-subheading">Giao dịch gần đây</h3>
        {detail.recentTransactions.length === 0 ? <p className="admin-users-muted">Chưa có giao dịch.</p> : <ul className="admin-users-list">
          {detail.recentTransactions.map(item => <li key={item.transactionCode}><span>{item.type}</span>
            <strong>{formatAdminMoney(item.amount)}</strong><span className="admin-users-muted">{formatAdminTime(item.createdAt)}</span></li>)}
        </ul>}
      </SectionCard>
      <SectionCard title="Đơn hàng" description={`${detail.sentOrderCount} đơn đã gửi · ${detail.courierOrderCount} đơn đã giao`}>
        {detail.recentOrders.length === 0 ? <p className="admin-users-muted">Chưa có đơn hàng.</p> : <ul className="admin-users-list">
          {detail.recentOrders.map(order => <li key={`${order.participation}-${order.id}`}>
            <span className="admin-users-code">{order.orderCode}</span><span>{order.productName}</span>
            <Badge tone={ORDER_STATUS_MAP[order.status as OrderStatus]?.tone ?? 'neutral'}>
              {ORDER_STATUS_MAP[order.status as OrderStatus]?.label ?? order.status}</Badge>
            <span className="admin-users-muted">{order.participation === 'SENDER' ? 'Người gửi' : 'Người giao'}</span>
          </li>)}
        </ul>}
      </SectionCard>
      <SectionCard title="Yêu cầu dữ liệu cá nhân">
        {detail.dataRequests.length === 0 ? <p className="admin-users-muted">Không có yêu cầu.</p> : <ul className="admin-users-list">
          {detail.dataRequests.map(item => <li key={item.id}><span>{requestTypeLabels[item.type]}</span>
            <Badge>{requestStatusLabels[item.status]}</Badge><span className="admin-users-muted">{formatAdminTime(item.requestedAt)}</span></li>)}
        </ul>}
      </SectionCard>
    </div>
  </div>;
}
