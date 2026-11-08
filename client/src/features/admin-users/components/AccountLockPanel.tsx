import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Lock, LockOpen } from 'lucide-react';
import { adminUserApi } from '../../../api/adminUserApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { AdminUserDetail } from '../../../types/adminUsers';
import SectionCard from '../../../components/ui/SectionCard';
import Select from '../../../components/ui/Select';
import Textarea from '../../../components/ui/Textarea';
import Checkbox from '../../../components/ui/Checkbox';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import { formatAdminTime } from '../adminUserPresentation';

const durations = [
  { value: '24', label: '24 giờ' },
  { value: '72', label: '3 ngày' },
  { value: '168', label: '7 ngày' },
  { value: '720', label: '30 ngày' },
  { value: '', label: 'Vĩnh viễn (đến khi mở khóa)' },
];

interface Props {
  detail: AdminUserDetail;
  onChanged: (detail: AdminUserDetail, message: string) => void;
}

export default function AccountLockPanel({ detail, onChanged }: Props) {
  const [reason, setReason] = useState('');
  const [duration, setDuration] = useState('72');
  const [blacklistPhone, setBlacklistPhone] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const account = detail.account;
  const locked = account.status === 'LOCKED';
  const disabled = detail.self || account.status === 'DELETED';

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const response = locked
        ? await adminUserApi.unlock(account.publicId, reason.trim())
        : await adminUserApi.lock(account.publicId, reason.trim(), duration ? Number(duration) : null, blacklistPhone);
      setReason('');
      setBlacklistPhone(false);
      onChanged(response.data, locked ? 'Đã mở khóa tài khoản.' : 'Đã khóa tài khoản và thu hồi mọi phiên đăng nhập.');
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setSaving(false);
    }
  }

  return <SectionCard title={locked ? 'Tài khoản đang bị khóa' : 'Khóa tài khoản'}
    description="Khóa có hiệu lực ngay: mọi phiên bị thu hồi, đơn chưa có người giao bị hủy.">
    <form className="ui-stack" onSubmit={submit} noValidate>
      {detail.lock && <Alert tone="warning" title={detail.lock.permanent ? 'Khóa vĩnh viễn' : `Khóa đến ${formatAdminTime(detail.lock.lockedUntil)}`}>
        <p>Lý do: {detail.lock.reason}</p>
        <p>Bởi {detail.lock.lockedByName ?? '—'} lúc {formatAdminTime(detail.lock.lockedAt)}</p>
      </Alert>}
      {detail.ordersNeedingAction.length > 0 && <Alert tone="info" title="Đơn cần xử lý thủ công">
        <p>Các đơn đã ký quỹ hoặc có người giao cần chuyển người giao khác hoặc hủy và hoàn tiền:</p>
        <ul>{detail.ordersNeedingAction.map(order => <li key={`${order.participation}-${order.id}`}>
          <Link to={`/sender/orders/${order.id}`}>{order.orderCode}</Link> · {order.productName} · {order.status}</li>)}</ul>
      </Alert>}
      {detail.self && <Alert tone="info">Bạn không thể khóa tài khoản của chính mình.</Alert>}
      {!locked && <Select label="Thời hạn khóa" value={duration} disabled={disabled || saving} onChange={event => setDuration(event.target.value)}>
        {durations.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
      </Select>}
      <Textarea label={locked ? 'Lý do mở khóa' : 'Lý do khóa (người dùng sẽ thấy)'} required maxLength={500} rows={3}
        value={reason} disabled={disabled || saving} onChange={event => setReason(event.target.value)} />
      {!locked && <Checkbox label="Thêm số điện thoại vào danh sách đen" checked={blacklistPhone} disabled={disabled || saving}
        onChange={event => setBlacklistPhone(event.target.checked)} />}
      {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
      <div><Button type="submit" variant={locked ? 'secondary' : 'danger'} icon={locked ? <LockOpen size={16} /> : <Lock size={16} />}
        loading={saving} disabled={disabled || !reason.trim()}>{locked ? 'Mở khóa' : 'Khóa tài khoản'}</Button></div>
    </form>
  </SectionCard>;
}
