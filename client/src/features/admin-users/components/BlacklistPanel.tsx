import { useState } from 'react';
import type { FormEvent } from 'react';
import { Ban } from 'lucide-react';
import { adminUserApi } from '../../../api/adminUserApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { BlacklistKind } from '../../../types/adminUsers';
import SectionCard from '../../../components/ui/SectionCard';
import Select from '../../../components/ui/Select';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

const placeholders: Record<BlacklistKind, string> = {
  PHONE: '09xxxxxxxx hoặc +849xxxxxxxx', ID_NUMBER: '12 chữ số CCCD', DEVICE: 'Mã thiết bị',
};

export default function BlacklistPanel() {
  const [kind, setKind] = useState<BlacklistKind>('PHONE');
  const [value, setValue] = useState('');
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setNotice('');
    try {
      await adminUserApi.blacklist(kind, value.trim(), reason.trim());
      setNotice('Đã thêm vào danh sách đen. Giá trị chỉ được lưu dưới dạng băm.');
      setValue('');
      setReason('');
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setSaving(false);
    }
  }

  return <SectionCard title="Danh sách đen" description="SĐT, CCCD hoặc thiết bị trong danh sách đen không thể đăng ký mới hoặc xác minh KYC (BR-R-082).">
    <form className="admin-users-blacklist-form" onSubmit={submit} noValidate>
      <Select label="Loại" value={kind} onChange={event => setKind(event.target.value as BlacklistKind)}>
        <option value="PHONE">Số điện thoại</option><option value="ID_NUMBER">Số CCCD</option><option value="DEVICE">Thiết bị</option>
      </Select>
      <Input label="Giá trị" maxLength={200} value={value} placeholder={placeholders[kind]} autoComplete="off"
        onChange={event => setValue(event.target.value)} />
      <Input label="Lý do" maxLength={500} value={reason} onChange={event => setReason(event.target.value)} />
      <div className="admin-users-blacklist-submit"><Button type="submit" variant="danger" icon={<Ban size={16} />} loading={saving}
        disabled={!value.trim() || !reason.trim()}>Thêm vào danh sách đen</Button></div>
    </form>
    {notice && <Alert tone="success">{notice}</Alert>}
    {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
  </SectionCard>;
}
