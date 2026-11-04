import { useEffect, useState } from 'react';
import { profileService } from '../../../services/profileService';
import type { AccountDataRequest } from '../../../types/profile';
import { useProfileAction } from '../hooks/useProfileAction';
import Button from '../../../components/ui/Button';
import Textarea from '../../../components/ui/Textarea';
import Alert from '../../../components/ui/Alert';
import Badge from '../../../components/ui/Badge';
import Modal from '../../../components/ui/Modal';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';

export default function PrivacyRequests() {
  const [requests, setRequests] = useState<AccountDataRequest[]>([]); const [deleting, setDeleting] = useState(false); const [reason, setReason] = useState('');
  const [loadError, setLoadError] = useState<ApiError>();
  const { busy, error, notice, run } = useProfileAction();
  useEffect(() => { let active = true; void profileService.dataRequests().then(({ data }) => { if (active) setRequests(data); }).catch(cause => { if (active) setLoadError(getApiError(cause)); }); return () => { active = false; }; }, []);
  async function submit(type: AccountDataRequest['type']) {
    const response = await run(() => profileService.requestData(type, reason), 'Yêu cầu đã được tiếp nhận, chờ quản trị viên xử lý.');
    if (response) { setRequests(items => [response.data.data, ...items.filter(item => item.id !== response.data.data.id)]); setDeleting(false); setReason(''); }
  }
  return <div className="ui-stack"><p className="ui-muted">Bạn có thể yêu cầu xuất dữ liệu hoặc xóa/ẩn danh tài khoản. Quản trị viên xử lý yêu cầu; tài khoản vẫn hoạt động trong thời gian chờ.</p>
    {loadError && <ApiErrorNotice error={loadError} />}{error && <ApiErrorNotice error={error} />}{notice && <Alert tone="success">{notice}</Alert>}
    <div className="ui-row"><Button variant="secondary" disabled={busy} onClick={() => void submit('DATA_EXPORT')}>Yêu cầu xuất dữ liệu</Button>
      <Button variant="danger" disabled={busy} onClick={() => setDeleting(true)}>Yêu cầu xóa tài khoản</Button></div>
    {requests.length > 0 && <ul className="account-request-list">{requests.map(item => <li key={item.id}><div><strong>{item.type === 'DATA_EXPORT' ? 'Xuất dữ liệu cá nhân' : 'Xóa/ẩn danh tài khoản'}</strong>
      <p className="ui-muted">{new Date(item.requestedAt).toLocaleString('vi-VN')}{item.dueAt && ` · Hạn xử lý ${new Date(item.dueAt).toLocaleDateString('vi-VN')}`}</p></div>
      <Badge>{({ PENDING: 'Chờ xử lý', PROCESSING: 'Đang xử lý', COMPLETED: 'Hoàn tất', REJECTED: 'Bị từ chối' })[item.status]}</Badge></li>)}</ul>}
    <Modal isOpen={deleting} onClose={() => setDeleting(false)} closeDisabled={busy} title="Yêu cầu xóa tài khoản?" description="Yêu cầu được gửi cho quản trị viên xử lý trong tối đa 30 ngày. Dữ liệu giao dịch cần lưu được xử lý theo quy trình riêng.">
      <div className="ui-stack"><Textarea label="Lý do (không bắt buộc)" value={reason} maxLength={500} disabled={busy} onChange={event => setReason(event.target.value)} />
        <div className="ui-form-actions"><Button variant="secondary" disabled={busy} onClick={() => setDeleting(false)}>Hủy</Button><Button variant="danger" loading={busy} onClick={() => void submit('ACCOUNT_DELETION')}>Gửi yêu cầu xóa</Button></div></div>
    </Modal>
  </div>;
}
