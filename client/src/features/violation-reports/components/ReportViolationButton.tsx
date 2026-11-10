import { useState } from 'react';
import type { FormEvent } from 'react';
import { Flag } from 'lucide-react';
import { violationReportApi } from '../../../api/violationReportApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { ViolationReason, ViolationTargetType } from '../../../types/violationReport';
import Button from '../../../components/ui/Button';
import Modal from '../../../components/ui/Modal';
import Select from '../../../components/ui/Select';
import Textarea from '../../../components/ui/Textarea';
import Alert from '../../../components/ui/Alert';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import { reasonLabels } from '../violationPresentation';
import { useAuthentication } from '../../../auth/useAuthentication';

const MAX_FILES = 3;

interface Props {
  targetType: ViolationTargetType;
  targetId: string;
  targetName: string;
  size?: 'sm' | 'md';
  variant?: 'secondary' | 'ghost';
}

export default function ReportViolationButton({ targetType, targetId, targetName, size = 'sm', variant = 'ghost' }: Props) {
  const { user } = useAuthentication();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<ViolationReason>('FRAUD');
  const [description, setDescription] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [done, setDone] = useState('');

  const roles = user?.roles ?? [];
  const isStaffOnly = Boolean(
    user &&
    (roles.includes('ADMIN') || roles.includes('MODERATOR')) &&
    !roles.includes('SENDER') &&
    !roles.includes('COURIER')
  );
  if (!user || isStaffOnly) {
    return null;
  }

  function close() {
    if (saving) return;
    setOpen(false);
    setError(null);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const response = await violationReportApi.submit({ targetType, targetId, reason, description: description.trim() || undefined }, files);
      setDone(response.data.merged
        ? 'Đã ghi nhận. Đối tượng này đã có báo cáo khác, kiểm duyệt viên sẽ xem xét chung.'
        : 'Đã gửi báo cáo. Kiểm duyệt viên sẽ xem xét và thông báo kết quả cho bạn.');
      setDescription('');
      setFiles([]);
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setSaving(false);
    }
  }

  return <>
    <Button type="button" size={size} variant={variant} icon={<Flag size={14} />} onClick={() => { setOpen(true); setDone(''); }}>
      Báo cáo vi phạm</Button>
    <Modal isOpen={open} onClose={close} closeDisabled={saving} title="Báo cáo vi phạm" description={targetName}>
      {done ? <div className="ui-stack"><Alert tone="success">{done}</Alert>
        <div><Button variant="secondary" onClick={close}>Đóng</Button></div></div>
        : <form className="ui-stack" onSubmit={submit} noValidate>
          <Select label="Lý do" value={reason} onChange={event => setReason(event.target.value as ViolationReason)}>
            {(Object.keys(reasonLabels) as ViolationReason[]).map(key => <option key={key} value={key}>{reasonLabels[key]}</option>)}
          </Select>
          <Textarea label="Mô tả (không bắt buộc)" maxLength={1000} rows={3} value={description}
            onChange={event => setDescription(event.target.value)} />
          <label className="ui-stack">
            <span>Ảnh bằng chứng (tối đa {MAX_FILES} ảnh JPEG, PNG, WEBP)</span>
            <input type="file" accept="image/jpeg,image/png,image/webp" multiple
              onChange={event => setFiles(Array.from(event.target.files ?? []).slice(0, MAX_FILES))} />
          </label>
          {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
          <div className="ui-row">
            <Button type="submit" variant="danger" loading={saving}>Gửi báo cáo</Button>
            <Button type="button" variant="ghost" disabled={saving} onClick={close}>Hủy</Button>
          </div>
        </form>}
    </Modal>
  </>;
}
