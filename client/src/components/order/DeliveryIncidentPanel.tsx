import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { AlertTriangle, History } from 'lucide-react';
import { incidentApi, INCIDENT_TYPES } from '../../api/incidentApi';
import type { IncidentType, DeliveryIncident } from '../../api/incidentApi';
import { getApiError, type ApiError } from '../../api/ApiError';
import type { PageResponse } from '../../types/api';
import Button from '../ui/Button';
import Modal from '../ui/Modal';
import Alert from '../ui/Alert';
import ApiErrorNotice from '../ApiErrorNotice';

/** Lazy-loaded incident timeline shared by Courier, Sender and staff. Backend enforces order access. */
export default function DeliveryIncidentPanel({ orderId, canReport = false, initialOpen = false, size = 'sm' }:
  { orderId: number; canReport?: boolean; initialOpen?: boolean; size?: 'sm' | 'md' }) {
  const [open, setOpen] = useState(initialOpen);
  const [type, setType] = useState<IncidentType | ''>('');
  const [description, setDescription] = useState('');
  const [photo, setPhoto] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [history, setHistory] = useState<PageResponse<DeliveryIncident> | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [historyError, setHistoryError] = useState<ApiError | null>(null);
  const [validation, setValidation] = useState('');
  const [success, setSuccess] = useState(false);
  const [preview, setPreview] = useState<string | null>(null);
  const [evidenceLoading, setEvidenceLoading] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true);
    setHistory(null);
    setHistoryError(null);
    incidentApi.history(orderId, page)
      .then(res => { if (active) setHistory(res.data); })
      .catch(cause => { if (active) setHistoryError(getApiError(cause)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [open, orderId, page]);

  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setValidation('');
    setError(null);
    setSuccess(false);
    if (!type || !description.trim() || description.length > 1000) {
      setValidation('Chọn loại sự cố và nhập mô tả từ 1 đến 1.000 ký tự.');
      return;
    }
    if (photo && (!['image/jpeg', 'image/png', 'image/webp'].includes(photo.type) || photo.size > 5 * 1024 * 1024)) {
      setValidation('Ảnh phải là JPG, PNG hoặc WebP, tối đa 5 MB.');
      return;
    }
    setBusy(true);
    try {
      const res = await incidentApi.report(orderId, type, description.trim(), photo);
      setSuccess(true);
      setType(''); setDescription(''); setPhoto(null);
      if (fileInput.current) fileInput.current.value = '';
      // Keep the successful result visible even if refreshing the timeline fails.
      setPage(0);
      setHistory(previous => previous && ({ ...previous, content: [res.data, ...previous.content].slice(0, 10),
        totalElements: previous.totalElements + 1, totalPages: Math.ceil((previous.totalElements + 1) / 10) }));
      try { setHistory((await incidentApi.history(orderId, 0)).data); setHistoryError(null); }
      catch (cause) { setHistoryError(getApiError(cause)); }
    } catch (cause) { setError(getApiError(cause)); }
    finally { setBusy(false); }
  }

  async function showEvidence(incidentId: number) {
    setEvidenceLoading(true);
    setError(null);
    try { setPreview(URL.createObjectURL(await incidentApi.evidence(orderId, incidentId))); }
    catch (cause) { setError(getApiError(cause)); }
    finally { setEvidenceLoading(false); }
  }

  return <div style={{ display: 'inline-flex' }}>
    <Button variant="secondary" size={size} icon={canReport ? <AlertTriangle size={15} /> : <History size={15} />}
      onClick={() => { setPage(0); setSuccess(false); setError(null); setOpen(true); }}>
      {canReport ? 'Báo cáo sự cố' : 'Lịch sử sự cố'}
    </Button>
    <Modal isOpen={open} title="Sự cố giao hàng" closeDisabled={busy || evidenceLoading}
      onClose={() => { setOpen(false); setPreview(null); }}>
      <div className="ui-stack">
        {success && <Alert tone="success">Đã ghi nhận sự cố và tạo thông báo cho người gửi, kiểm duyệt viên.</Alert>}
        {error && <ApiErrorNotice error={error} />}
        {canReport && <form className="ui-stack" onSubmit={event => void submit(event)}>
          <label htmlFor={`incident-type-${orderId}`}>Loại sự cố *</label>
          <select id={`incident-type-${orderId}`} className="ui-control" required value={type} disabled={busy}
            onChange={event => setType(event.target.value as IncidentType | '')}>
            <option value="">Chọn loại sự cố</option>
            {Object.entries(INCIDENT_TYPES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select>
          <label htmlFor={`incident-description-${orderId}`}>Mô tả *</label>
          <textarea id={`incident-description-${orderId}`} className="ui-control" required maxLength={1000}
            rows={4} value={description} disabled={busy} onChange={event => setDescription(event.target.value)} />
          <label htmlFor={`incident-photo-${orderId}`}>Ảnh bằng chứng (tùy chọn, JPG/PNG/WebP, tối đa 5 MB)</label>
          <input ref={fileInput} id={`incident-photo-${orderId}`} type="file" accept="image/jpeg,image/png,image/webp"
            disabled={busy} onChange={event => setPhoto(event.target.files?.[0] ?? null)} />
          {validation && <p role="alert">{validation}</p>}
          <p>Báo cáo chỉ ghi nhận sự cố, không đổi trạng thái đơn hàng.</p>
          <Button type="submit" loading={busy} disabled={!type || !description.trim()}>Gửi báo cáo</Button>
        </form>}
        <h3>Lịch sử sự cố</h3>
        {historyError && <ApiErrorNotice error={historyError} />}
        {loading && <p role="status">Đang tải lịch sử...</p>}
        {history?.content.length === 0 && <p>Chưa có sự cố được ghi nhận.</p>}
        {history?.content.map(incident => <article key={incident.id} className="ui-card">
          <div className="ui-card-body ui-stack">
            <strong>{INCIDENT_TYPES[incident.type]}</strong>
            <time dateTime={incident.createdAt}>{new Date(incident.createdAt).toLocaleString('vi-VN')}</time>
            <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{incident.description}</p>
            {incident.hasEvidence && <Button variant="ghost" disabled={evidenceLoading}
              onClick={() => void showEvidence(incident.id)}>Xem ảnh bằng chứng</Button>}
          </div>
        </article>)}
        {preview && <div><img src={preview} alt="Bằng chứng sự cố giao hàng" style={{ maxWidth: '100%' }} />
          <Button variant="ghost" onClick={() => setPreview(null)}>Đóng ảnh</Button></div>}
        {history && history.totalPages > 1 && <div className="ui-row">
          <Button variant="ghost" disabled={loading || busy || page === 0} onClick={() => setPage(page - 1)}>Trước</Button>
          <span>Trang {page + 1}/{history.totalPages}</span>
          <Button variant="ghost" disabled={loading || busy || page + 1 >= history.totalPages}
            onClick={() => setPage(page + 1)}>Sau</Button>
        </div>}
      </div>
    </Modal>
  </div>;
}
