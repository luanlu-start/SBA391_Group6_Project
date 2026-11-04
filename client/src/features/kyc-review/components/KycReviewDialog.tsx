import { useCallback, useEffect, useRef, useState } from 'react';
import { Check, Flag, RefreshCw, X } from 'lucide-react';
import { kycReviewApi } from '../../../api/kycReviewApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { KycRejectionReason, KycReviewAction, KycReviewDetail } from '../../../types/kycReview';
import type { ReviewDecisionValues } from '../schemas/reviewDecisionSchema';
import { formatKycReviewDate, formatKycReviewTime, kycReviewStatusLabels, kycReviewStatusTones } from '../kycReviewPresentation';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import LoadingSpinner from '../../../components/common/LoadingSpinner';
import Alert from '../../../components/ui/Alert';
import Badge from '../../../components/ui/Badge';
import Button from '../../../components/ui/Button';
import Modal from '../../../components/ui/Modal';
import KycReviewImages from './KycReviewImages';
import KycReviewDecisionForm from './KycReviewDecisionForm';

interface Props {
  requestId: string;
  currentUserId: string;
  isAdmin: boolean;
  onClose: () => void;
  onChanged: (message: string) => void;
}

export default function KycReviewDialog({ requestId, currentUserId, isAdmin, onClose, onChanged }: Props) {
  const [detail, setDetail] = useState<KycReviewDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<ApiError | null>(null);
  const [action, setAction] = useState<KycReviewAction | null>(null);
  const [busy, setBusy] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [mutationError, setMutationError] = useState<ApiError | null>(null);
  const [imageLoading, setImageLoading] = useState(false);
  const [notice, setNotice] = useState('');
  const [reasons, setReasons] = useState<KycRejectionReason[]>([]);
  const [reasonsLoading, setReasonsLoading] = useState(true);
  const [reasonsError, setReasonsError] = useState<ApiError | null>(null);
  const [reasonsRevision, setReasonsRevision] = useState(0);
  const request = useRef<AbortController | null>(null);
  const sequence = useRef(0);
  const mounted = useRef(true);
  const saving = useRef(false);
  const mutationRequest = useRef<AbortController | null>(null);

  const loadDetail = useCallback(async () => {
    const current = ++sequence.current;
    request.current?.abort();
    const controller = new AbortController(); request.current = controller;
    setLoading(true); setDetail(null); setFailure(null); setAction(null); setImageLoading(false); setMutationError(null);
    try {
      const response = await kycReviewApi.get(requestId, controller.signal);
      if (mounted.current && current === sequence.current && !controller.signal.aborted) {
        setDetail(response.data); setConflict(false);
      }
    } catch (cause) {
      if (mounted.current && current === sequence.current && !controller.signal.aborted) setFailure(getApiError(cause));
    } finally {
      if (mounted.current && current === sequence.current && !controller.signal.aborted) setLoading(false);
    }
  }, [requestId]);

  useEffect(() => {
    mounted.current = true; void loadDetail();
    return () => { mounted.current = false; sequence.current += 1; request.current?.abort(); mutationRequest.current?.abort(); };
  }, [loadDetail]);

  useEffect(() => {
    const controller = new AbortController();
    setReasonsLoading(true); setReasonsError(null);
    void kycReviewApi.rejectionReasons(controller.signal).then(response => {
      if (!controller.signal.aborted) setReasons(response.data);
    }).catch(cause => {
      if (!controller.signal.aborted) { setReasonsError(getApiError(cause)); setReasons([]); }
    }).finally(() => { if (!controller.signal.aborted) setReasonsLoading(false); });
    return () => controller.abort();
  }, [reasonsRevision]);

  const selfReview = detail?.applicantId === currentUserId;
  const finalDecisionAllowed = !!detail && detail.status === 'PENDING' && !selfReview && (!detail.flagged || isAdmin);
  const disabled = busy || imageLoading || loading || conflict;

  function selectAction(next: KycReviewAction) {
    if (!detail || disabled || selfReview || detail.status !== 'PENDING') return;
    if (next === 'flag' ? detail.flagged : !finalDecisionAllowed) return;
    setAction(next); setMutationError(null); setNotice('');
  }

  async function confirm(values: ReviewDecisionValues) {
    if (!detail || !action || disabled || saving.current || selfReview || detail.status !== 'PENDING') return;
    if (action === 'flag' ? detail.flagged : !finalDecisionAllowed) return;
    saving.current = true; setBusy(true); setMutationError(null);
    const controller = new AbortController(); mutationRequest.current = controller;
    const selectedAction = action;
    try {
      if (selectedAction === 'approve') await kycReviewApi.approve(detail.id, detail.version, controller.signal);
      else if (selectedAction === 'reject') await kycReviewApi.reject(detail.id, detail.version, values.reasonCode, values.note || undefined, controller.signal);
      else await kycReviewApi.flag(detail.id, detail.version, values.flagReason, controller.signal);
      if (!mounted.current) return;
      const message = selectedAction === 'approve' ? `Đã duyệt hồ sơ của ${detail.fullName}.`
        : selectedAction === 'reject' ? `Đã từ chối hồ sơ của ${detail.fullName}.` : `Đã chuyển hồ sơ của ${detail.fullName} cho quản trị viên.`;
      setNotice(message); onChanged(message); await loadDetail();
    } catch (cause) {
      if (!mounted.current) return;
      const error = getApiError(cause); setMutationError(error);
      if (error.status === 409 || !error.status || error.status >= 500) setConflict(true);
    } finally {
      saving.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  return <Modal isOpen title="Chi tiết hồ sơ xác minh" size="lg" closeDisabled={busy}
    onClose={() => { if (!saving.current) onClose(); }} description="Đối chiếu thông tin và tài liệu. Thời gian hiển thị theo giờ Việt Nam (UTC+7).">
    {loading ? <LoadingSpinner text="Đang tải hồ sơ mới nhất…" /> : failure ? <div className="ui-stack">
      <ApiErrorNotice message={failure.message} correlationId={failure.correlationId} />
      <div><Button variant="secondary" onClick={() => void loadDetail()}>Tải lại hồ sơ</Button></div>
    </div> : detail && <div className="ui-stack kyc-review-detail">
      {notice && <Alert tone="success">{notice}</Alert>}
      <section className="ui-stack" aria-labelledby="kyc-review-person-title">
        <div className="kyc-review-detail-heading"><div><h3 id="kyc-review-person-title">{detail.fullName}</h3>
          <p className="kyc-review-muted kyc-review-break">{detail.email}</p></div>
          <Badge tone={kycReviewStatusTones[detail.status]} dot>{kycReviewStatusLabels[detail.status]}</Badge></div>
        <dl className="kyc-review-detail-grid">
          <div><dt>Số CCCD</dt><dd className="kyc-review-code">{detail.idNumberMasked || '—'}</dd></div>
          <div><dt>Ngày sinh</dt><dd>{formatKycReviewDate(detail.dob)}</dd></div>
          <div><dt>Ngày cấp CCCD</dt><dd>{formatKycReviewDate(detail.issuedDate)}</dd></div>
          <div><dt>Ngày hết hạn CCCD</dt><dd>{formatKycReviewDate(detail.expiryDate)}</dd></div>
          <div><dt>Nộp lúc</dt><dd>{formatKycReviewTime(detail.submittedAt)}</dd></div>
          <div><dt>Hạn duyệt</dt><dd>{formatKycReviewTime(detail.dueAt)}</dd></div>
        </dl>
      </section>
      {detail.overdue && detail.status === 'PENDING' && <Alert tone="warning">Hồ sơ đã quá hạn duyệt. Cần ưu tiên kiểm tra và xử lý.</Alert>}
      {detail.flagged && <Alert tone="warning" title="Cần quản trị viên xem xét">
        <p className="kyc-review-break">{detail.flagReason || 'Hồ sơ đã được chuyển quản trị viên.'}</p>
        {!isAdmin && detail.status === 'PENDING' && <p>Chỉ quản trị viên được đưa ra quyết định cuối cùng cho hồ sơ này.</p>}
      </Alert>}
      {detail.status !== 'PENDING' && <section className="ui-stack" aria-labelledby="kyc-review-result-title">
        <h3 id="kyc-review-result-title">Kết quả xử lý</h3>
        <dl className="kyc-review-detail-grid"><div><dt>Người xử lý</dt><dd>{detail.reviewerName || '—'}</dd></div>
          <div><dt>Thời điểm xử lý</dt><dd>{formatKycReviewTime(detail.reviewedAt)}</dd></div></dl>
        {detail.rejectReason && <Alert tone="danger" title="Lý do từ chối"><span className="kyc-review-break">{detail.rejectReason}</span></Alert>}
      </section>}
      <KycReviewImages key={`${detail.id}-${detail.version}`} requestId={detail.id} applicantName={detail.fullName}
        files={detail.files} disabled={busy} onLoadingChange={setImageLoading} />
      {selfReview && detail.status === 'PENDING' && <Alert tone="warning">Bạn không được duyệt, từ chối hoặc chuyển xử lý hồ sơ của chính mình.</Alert>}
      {imageLoading && <p className="kyc-review-muted" role="status">Chờ tải xong ảnh trước khi đưa ra quyết định.</p>}
      {conflict && <Alert tone="warning" action={<Button variant="secondary" disabled={busy} onClick={() => void loadDetail()}>Tải hồ sơ mới nhất</Button>}>
        Chưa thể tiếp tục với phiên bản đang mở. Tải lại hồ sơ, kiểm tra kết quả và đối chiếu thông tin mới trước khi thao tác.
      </Alert>}
      {action ? <KycReviewDecisionForm key={`${detail.id}-${detail.version}-${action}`} action={action} reasons={reasons}
        reasonsLoading={reasonsLoading} reasonsError={reasonsError} busy={busy || imageLoading} conflict={conflict} failure={mutationError}
        onRetryReasons={() => setReasonsRevision(value => value + 1)} onCancel={() => {
          if (!busy) {
            setAction(null); setMutationError(null);
            window.requestAnimationFrame(() => document.getElementById(`kyc-review-action-${action}`)?.focus());
          }
        }}
        onConfirm={confirm} /> : detail.status === 'PENDING' && !selfReview && <div className="kyc-review-actions">
        <Button id="kyc-review-action-approve" disabled={disabled || !finalDecisionAllowed} icon={<Check size={17} />} onClick={() => selectAction('approve')}>Duyệt hồ sơ</Button>
        <Button id="kyc-review-action-reject" variant="danger" disabled={disabled || !finalDecisionAllowed} icon={<X size={17} />} onClick={() => selectAction('reject')}>Từ chối</Button>
        <Button id="kyc-review-action-flag" variant="secondary" disabled={disabled || detail.flagged} icon={<Flag size={17} />} onClick={() => selectAction('flag')}>Chuyển Admin</Button>
      </div>}
      <div className="kyc-review-dialog-footer"><Button variant="secondary" disabled={busy || imageLoading} icon={<RefreshCw size={16} />}
        onClick={() => void loadDetail()}>Tải lại hồ sơ</Button>
        <Button variant="ghost" disabled={busy} onClick={onClose}>Đóng</Button></div>
    </div>}
  </Modal>;
}
