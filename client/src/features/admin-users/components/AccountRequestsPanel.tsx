import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Download, XCircle } from 'lucide-react';
import { adminUserApi } from '../../../api/adminUserApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { AccountRequestItem, AccountRequestStatus } from '../../../types/adminUsers';
import SectionCard from '../../../components/ui/SectionCard';
import Select from '../../../components/ui/Select';
import Button from '../../../components/ui/Button';
import Badge from '../../../components/ui/Badge';
import Modal from '../../../components/ui/Modal';
import Textarea from '../../../components/ui/Textarea';
import Alert from '../../../components/ui/Alert';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import { downloadBlob, formatAdminTime, requestStatusLabels, requestTypeLabels } from '../adminUserPresentation';

type Filter = AccountRequestStatus | 'ALL';

export default function AccountRequestsPanel() {
  const [filter, setFilter] = useState<Filter>('PENDING');
  const [items, setItems] = useState<AccountRequestItem[]>([]);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState<AccountRequestItem | null>(null);
  const [reason, setReason] = useState('');

  const load = useCallback((signal?: AbortSignal) => {
    void adminUserApi.accountRequests(filter, 0, signal)
      .then(response => { if (!signal?.aborted) setItems(response.data.content); })
      .catch(cause => { if (!signal?.aborted) setError(getApiError(cause)); });
  }, [filter]);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal);
    return () => controller.abort();
  }, [load]);

  async function act(item: AccountRequestItem, action: () => Promise<unknown>, message: string) {
    setBusyId(item.id);
    setError(null);
    setNotice('');
    try {
      await action();
      setNotice(message);
      setRejecting(null);
      setReason('');
      load();
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setBusyId(null);
    }
  }

  async function preview(item: AccountRequestItem) {
    try {
      downloadBlob(await adminUserApi.previewExport(item.id), `du-lieu-${item.userEmail}.json`);
    } catch (cause) {
      setError(getApiError(cause));
    }
  }

  const open = (item: AccountRequestItem) => item.status === 'PENDING' || item.status === 'PROCESSING';

  return <SectionCard title="Yêu cầu xuất/xóa dữ liệu" description="Xử lý trong 30 ngày (BR-N-040). Xóa tài khoản ẩn danh dữ liệu cá nhân, giữ đơn và sổ cái."
    actions={<Select label="Trạng thái" value={filter} onChange={event => setFilter(event.target.value as Filter)}>
      <option value="PENDING">Chờ xử lý</option><option value="COMPLETED">Hoàn tất</option>
      <option value="REJECTED">Bị từ chối</option><option value="ALL">Tất cả</option>
    </Select>}>
    {notice && <Alert tone="success">{notice}</Alert>}
    {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
    {items.length === 0 ? <p className="admin-users-muted">Không có yêu cầu nào.</p> : <ul className="admin-users-requests">
      {items.map(item => <li key={item.id}>
        <div className="admin-users-cell">
          <strong>{requestTypeLabels[item.type]}</strong>
          <Link to={`/admin/users/${item.userPublicId}`}>{item.userFullName ?? item.userEmail}</Link>
          <span className="admin-users-muted">Gửi {formatAdminTime(item.requestedAt)}{item.dueAt ? ` · Hạn ${formatAdminTime(item.dueAt)}` : ''}</span>
          {item.reason && <span className="admin-users-muted">Lý do: {item.reason}</span>}
          {item.resolutionNote && <span className="admin-users-muted">Kết quả: {item.resolutionNote}</span>}
        </div>
        <div className="admin-users-actions">
          <Badge tone={item.overdue ? 'danger' : open(item) ? 'warning' : 'neutral'}>{item.overdue ? 'Quá hạn' : requestStatusLabels[item.status]}</Badge>
          {item.type === 'DATA_EXPORT' && <Button size="sm" variant="ghost" icon={<Download size={15} />} onClick={() => void preview(item)}>Xem dữ liệu</Button>}
          {open(item) && <Button size="sm" icon={<CheckCircle2 size={15} />} loading={busyId === item.id}
            onClick={() => void act(item, () => adminUserApi.completeRequest(item.id),
              item.type === 'DATA_EXPORT' ? 'Đã mở bản sao dữ liệu cho người dùng.' : 'Đã ẩn danh tài khoản.')}>
            {item.type === 'DATA_EXPORT' ? 'Cho phép tải' : 'Ẩn danh tài khoản'}</Button>}
          {open(item) && <Button size="sm" variant="secondary" icon={<XCircle size={15} />} disabled={busyId === item.id}
            onClick={() => { setRejecting(item); setReason(''); }}>Từ chối</Button>}
        </div>
      </li>)}
    </ul>}
    <Modal isOpen={rejecting !== null} onClose={() => setRejecting(null)} closeDisabled={busyId !== null} title="Từ chối yêu cầu dữ liệu"
      description="Lý do được gửi tới người dùng.">
      <div className="ui-stack">
        <Textarea label="Lý do" required maxLength={500} rows={3} value={reason} onChange={event => setReason(event.target.value)} />
        <div className="admin-users-actions">
          <Button variant="danger" loading={busyId !== null} disabled={!reason.trim()}
            onClick={() => rejecting && void act(rejecting, () => adminUserApi.rejectRequest(rejecting.id, reason.trim()), 'Đã từ chối yêu cầu.')}>
            Xác nhận từ chối</Button>
          <Button variant="ghost" onClick={() => setRejecting(null)}>Hủy</Button>
        </div>
      </div>
    </Modal>
  </SectionCard>;
}
