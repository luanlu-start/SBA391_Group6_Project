import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { deliveryReturnApi } from '../api/deliveryReturnApi';
import type { DeliveryReturnDetail } from '../api/deliveryReturnApi';
import { getApiError, ApiError } from '../api/ApiError';
import type { ApiResponse } from '../types/api';
import { ORDER_STATUS_MAP } from '../types/order';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Alert from '../components/ui/Alert';
import ApiErrorNotice from '../components/ApiErrorNotice';

const actions: Record<string, string> = {
  CONTACT_ATTEMPT: 'Ghi nhận cuộc gọi không thành công', DELIVERY_FAILED: 'Giao hàng thất bại', RETURN_STARTED: 'Bắt đầu hoàn hàng',
  DELIVERY_RESUMED: 'Tiếp tục giao hàng', RETURN_CODE_ISSUED: 'Người gửi tạo mã xác nhận', RETURN_CONFIRMED: 'Đã nhận lại hàng',
};
const date = (value: string) => new Date(value).toLocaleString('vi-VN');
const money = (value: number) => `${value.toLocaleString('vi-VN')} đ`;

export default function DeliveryReturnPage() {
  const { id } = useParams<{ id: string }>();
  const orderId = Number(id);
  if (!Number.isSafeInteger(orderId) || orderId <= 0) return <Alert tone="danger">Mã đơn hàng không hợp lệ.</Alert>;
  return <ReturnWorkflow key={id} orderId={orderId} />;
}

function ReturnWorkflow({ orderId }: { orderId: number }) {
  const validId = Number.isSafeInteger(orderId) && orderId > 0;
  const [detail, setDetail] = useState<DeliveryReturnDetail | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState('');
  const contactRequestId = useRef(crypto.randomUUID());
  const [description, setDescription] = useState('');
  const [photo, setPhoto] = useState<File | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const [resumeNote, setResumeNote] = useState('');
  const [challenge, setChallenge] = useState<{ code: string; expiresAt: string } | null>(null);
  const [code, setCode] = useState('');
  const [received, setReceived] = useState(false);
  const [preview, setPreview] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!validId) return;
    setLoading(true); setError(null);
    try { setDetail((await deliveryReturnApi.detail(orderId)).data); }
    catch (cause) { setError(getApiError(cause)); }
    finally { setLoading(false); }
  }, [orderId, validId]);
  useEffect(() => { setDetail(null); setChallenge(null); setCode(''); void load(); }, [load]);
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);

  async function act(work: () => Promise<ApiResponse<DeliveryReturnDetail>>, message: string) {
    setBusy(true); setError(null); setSuccess('');
    try { setDetail((await work()).data); setSuccess(message); return true; }
    catch (cause) { setError(getApiError(cause)); return false; }
    finally { setBusy(false); }
  }
  async function recordContact(event: FormEvent) {
    event.preventDefault();
    if (await act(() => deliveryReturnApi.contact(orderId, contactRequestId.current, note.trim()), 'Đã ghi nhận cuộc gọi không thành công.')) {
      contactRequestId.current = crypto.randomUUID(); setNote('');
    }
  }
  async function report(event: FormEvent) {
    event.preventDefault();
    if (!photo) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(photo.type) || photo.size > 5 * 1024 * 1024) {
      setError(new ApiError('Ảnh phải là JPG/PNG/WebP và không quá 5 MB.'));
      return;
    }
    if (await act(() => deliveryReturnApi.report(orderId, description.trim(), photo), 'Đã ghi nhận giao thất bại. Hãy trả hàng về điểm lấy.')) {
      setDescription(''); setPhoto(null); if (fileInput.current) fileInput.current.value = '';
    }
  }
  async function issueCode() {
    setBusy(true); setError(null); setSuccess('');
    try { setChallenge((await deliveryReturnApi.issueCode(orderId)).data); setCode(''); await load(); }
    catch (cause) { setError(getApiError(cause)); }
    finally { setBusy(false); }
  }
  async function confirm(event: FormEvent) {
    event.preventDefault();
    if (await act(() => deliveryReturnApi.confirm(orderId, code.trim(), received), 'Đã xác nhận nhận lại hàng. Chưa có bút toán thu phí hoặc giải ngân tự động.')) {
      setChallenge(null); setCode(''); setReceived(false);
    }
  }
  async function evidence(caseId: number) {
    setBusy(true); setError(null);
    try { setPreview(URL.createObjectURL(await deliveryReturnApi.evidence(orderId, caseId))); }
    catch (cause) { setError(getApiError(cause)); }
    finally { setBusy(false); }
  }

  if (!validId) return <Alert tone="danger">Mã đơn hàng không hợp lệ.</Alert>;
  return <div className="ui-stack">
    <PageHeader title="Giao thất bại và hoàn hàng" description={`Đơn hàng #${orderId}`}
      actions={<Button variant="secondary" disabled={busy || loading} onClick={() => void load()}>Làm mới</Button>} />
    {error && <ApiErrorNotice error={error} />}
    {success && <Alert tone="success">{success}</Alert>}
    {loading && <p role="status">Đang tải...</p>}
    {detail && <>
      <SectionCard title={`Trạng thái: ${ORDER_STATUS_MAP[detail.orderStatus]?.label ?? detail.orderStatus}`}>
        {detail.latestCase ? <div className="ui-stack">
          <p>{detail.latestCase.description}</p>
          <p>Phí giao dự kiến: {money(detail.latestCase.deliveryFee)} · Phí hoàn dự kiến: {money(detail.latestCase.returnFee)} ({detail.latestCase.returnRate * 100}%).</p>
          {detail.latestCase.financialSettlementPending
            ? <Alert tone="warning">Phí đang chờ xử lý tài chính. Luồng này chưa trừ ví, hoàn tiền hoặc giải ngân ký quỹ.</Alert>
            : <Alert>Đã tiếp tục giao hàng; dự kiến phí hoàn của lần báo thất bại này không được thu.</Alert>}
          <Button variant="secondary" disabled={busy} onClick={() => void evidence(detail.latestCase!.id)}>Xem bằng chứng liên hệ</Button>
        </div> : <p>Chưa có yêu cầu hoàn hàng.</p>}
      </SectionCard>
      {detail.canReport && <>
        <SectionCard title="Bằng chứng liên hệ người nhận">
          <p>Cần {detail.requiredCalls} cuộc gọi không thành công trong {detail.contactWindowMinutes} phút gần nhất. Ghi nhận sau mỗi cuộc gọi thực tế; ứng dụng không tự thực hiện cuộc gọi.</p>
          <form className="ui-stack" onSubmit={event => void recordContact(event)}>
            <label htmlFor="contact-note">Kết quả cuộc gọi *</label>
            <textarea id="contact-note" className="ui-control" rows={2} required maxLength={500} disabled={busy}
              value={note} onChange={event => setNote(event.target.value)} />
            <Button type="submit" loading={busy} disabled={loading || !note.trim()}>Ghi nhận một lần gọi không thành công</Button>
          </form>
          <p>Đã ghi nhận {detail.contacts.length}/{detail.requiredCalls} cuộc gọi còn hiệu lực.</p>
          <ul>{detail.contacts.map(contact => <li key={contact.id}>{date(contact.createdAt)} — {contact.note}</li>)}</ul>
        </SectionCard>
        <SectionCard title="Báo giao hàng thất bại">
          <form className="ui-stack" onSubmit={event => void report(event)}>
            <label htmlFor="failure-reason">Lý do *</label>
            <select id="failure-reason" className="ui-control" disabled={busy}><option value="RECIPIENT_UNREACHABLE">Không liên lạc được người nhận</option></select>
            <label htmlFor="failure-description">Mô tả *</label>
            <textarea id="failure-description" className="ui-control" required maxLength={1000} rows={3} value={description}
              disabled={busy} onChange={event => setDescription(event.target.value)} />
            <label htmlFor="failure-photo">Ảnh bằng chứng các cuộc gọi/tin nhắn (JPG/PNG/WebP, tối đa 5 MB) *</label>
            <input id="failure-photo" ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp" required disabled={busy}
              onChange={event => setPhoto(event.target.files?.[0] ?? null)} />
            <Button type="submit" variant="danger" loading={busy}
              disabled={loading || !photo || !description.trim() || detail.contacts.length < detail.requiredCalls}>Báo thất bại và bắt đầu hoàn hàng</Button>
          </form>
        </SectionCard>
      </>}
      {detail.canResume && <SectionCard title="Người nhận đã liên lạc lại">
        <form className="ui-stack" onSubmit={event => { event.preventDefault(); void act(() => deliveryReturnApi.resume(orderId, resumeNote.trim()), 'Đã chuyển lại sang đang giao.'); }}>
          <label htmlFor="resume-note">Mô tả việc tiếp tục giao *</label>
          <textarea id="resume-note" className="ui-control" required maxLength={1000} disabled={busy} value={resumeNote}
            onChange={event => setResumeNote(event.target.value)} />
          <Button type="submit" loading={busy} disabled={loading || !resumeNote.trim()}>Tiếp tục giao hàng</Button>
        </form>
      </SectionCard>}
      {detail.canConfirm && <SectionCard title="Người gửi xác nhận nhận lại hàng">
        <div className="ui-stack">
          <p>Chỉ xác nhận sau khi bạn đã thực sự nhận lại kiện hàng từ Courier. Mã chỉ dành cho người gửi, hiệu lực 10 phút, tối đa 5 lần nhập sai.</p>
          <Button variant="secondary" disabled={busy || loading} onClick={() => void issueCode()}>Tạo mã xác nhận nhận lại hàng</Button>
          {challenge && <div><QRCodeSVG value={challenge.code} size={140} title="Mã xác nhận hoàn hàng" />
            <p>Mã: <strong>{challenge.code}</strong> · Hết hạn: {date(challenge.expiresAt)}</p></div>}
          <form className="ui-stack" onSubmit={event => void confirm(event)}>
            <label htmlFor="return-code">Mã xác nhận *</label>
            <input id="return-code" className="ui-control" inputMode="numeric" autoComplete="off" pattern="[0-9]{6}" maxLength={6}
              required value={code} disabled={busy} onChange={event => setCode(event.target.value)} />
            <label><input type="checkbox" checked={received} disabled={busy} required onChange={event => setReceived(event.target.checked)} /> Tôi đã nhận lại hàng</label>
            <Button type="submit" loading={busy} disabled={loading || !received || !/^[0-9]{6}$/.test(code)}>Xác nhận đã nhận lại hàng</Button>
          </form>
        </div>
      </SectionCard>}
      <SectionCard title="Lịch sử giao thất bại / hoàn hàng" description="Hiển thị tối đa 50 mốc gần nhất.">
        {detail.history.length === 0 ? <p>Chưa có lịch sử.</p> : <ol className="ui-stack">{detail.history.map(event => <li key={event.id}>
          <strong>{actions[event.action] ?? event.action}</strong> · {date(event.createdAt)}
          <p>{ORDER_STATUS_MAP[event.fromStatus]?.label} → {ORDER_STATUS_MAP[event.toStatus]?.label}</p>
          <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{event.note}</p>
          {event.action === 'DELIVERY_FAILED' && event.returnCaseId && <Button variant="ghost" disabled={busy}
            onClick={() => void evidence(event.returnCaseId!)}>Xem bằng chứng</Button>}
        </li>)}</ol>}
      </SectionCard>
      {preview && <SectionCard title="Bằng chứng liên hệ"><img src={preview} alt="Bằng chứng liên hệ người nhận" style={{ maxWidth: '100%' }} />
        <Button variant="ghost" onClick={() => setPreview(null)}>Đóng ảnh</Button></SectionCard>}
    </>}
  </div>;
}
