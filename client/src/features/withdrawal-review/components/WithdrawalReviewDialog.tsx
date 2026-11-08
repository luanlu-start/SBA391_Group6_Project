import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { BadgeCheck, CircleX, Landmark, ReceiptText } from 'lucide-react';
import { withdrawalReviewApi } from '../../../api/withdrawalReviewApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { AdminWithdrawal } from '../../../types/withdrawalReview';
import Modal from '../../../components/ui/Modal';
import Button from '../../../components/ui/Button';
import Badge from '../../../components/ui/Badge';
import Alert from '../../../components/ui/Alert';
import Input from '../../../components/ui/Input';
import Textarea from '../../../components/ui/Textarea';
import LoadingSpinner from '../../../components/common/LoadingSpinner';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import { formatVnd, formatWithdrawalTime, partyName, withdrawalStatusLabels, withdrawalStatusTones } from '../withdrawalReviewPresentation';

type Mode = 'reject' | 'paid' | 'failed' | null;

interface Props {
  withdrawalId: number;
  onClose: () => void;
  onChanged: (message: string) => void;
}

const referencePattern = /^[A-Za-z0-9._/-]{1,100}$/;

export default function WithdrawalReviewDialog({ withdrawalId, onClose, onChanged }: Props) {
  const [item, setItem] = useState<AdminWithdrawal | null>(null);
  const [loadError, setLoadError] = useState<ApiError | null>(null);
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [mode, setMode] = useState<Mode>(null);
  const [text, setText] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    void withdrawalReviewApi.get(withdrawalId, controller.signal)
      .then(response => { if (!controller.signal.aborted) setItem(response.data); })
      .catch(cause => { if (!controller.signal.aborted) setLoadError(getApiError(cause)); });
    return () => controller.abort();
  }, [withdrawalId]);

  function openMode(next: Mode) {
    setMode(next);
    setText('');
    setActionError(null);
  }

  async function run(action: () => Promise<{ data: AdminWithdrawal }>, message: (updated: AdminWithdrawal) => string) {
    setSubmitting(true);
    setActionError(null);
    try {
      const response = await action();
      setItem(response.data);
      setMode(null);
      setText('');
      onChanged(message(response.data));
    } catch (cause) {
      setActionError(getApiError(cause));
    } finally {
      setSubmitting(false);
    }
  }

  function approve() {
    if (!item) return;
    void run(() => withdrawalReviewApi.approve(item.id), updated => updated.status === 'APPROVED_L1'
      ? `Đã duyệt cấp 1 yêu cầu ${updated.requestCode}. Cần một quản trị viên khác duyệt cấp 2.`
      : updated.status === 'PAID'
        ? `Yêu cầu ${updated.requestCode} đã được duyệt và chi trả.`
        : `Đã duyệt yêu cầu ${updated.requestCode}. Yêu cầu đang chờ chi trả.`);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!item || !mode) return;
    const value = text.trim();
    if (mode === 'paid') {
      void run(() => withdrawalReviewApi.markPaid(item.id, value), updated => `Đã ghi nhận chi trả ${updated.requestCode}.`);
    } else if (mode === 'failed') {
      void run(() => withdrawalReviewApi.markFailed(item.id, value),
        updated => `Đã ghi nhận chi thất bại ${updated.requestCode} và hoàn tiền về ví.`);
    } else {
      void run(() => withdrawalReviewApi.reject(item.id, value),
        updated => `Đã từ chối ${updated.requestCode} và hoàn tiền về ví người dùng.`);
    }
  }

  const value = text.trim();
  const valid = mode === 'paid' ? referencePattern.test(value) : value.length > 0 && value.length <= 500;
  const approveLabel = item?.status === 'APPROVED_L1' ? 'Duyệt cấp 2'
    : item?.dualApprovalRequired ? 'Duyệt cấp 1' : 'Duyệt yêu cầu';

  return <Modal isOpen onClose={onClose} closeDisabled={submitting} size="lg"
    title={item ? `Yêu cầu ${item.requestCode}` : 'Yêu cầu rút tiền'}
    description="Đối chiếu ví và thông tin nhận tiền trước khi quyết định.">
    {loadError ? <ApiErrorNotice message={loadError.message} correlationId={loadError.correlationId} />
      : !item ? <LoadingSpinner text="Đang tải yêu cầu…" /> : <div className="ui-stack withdrawal-review-detail">
        <div className="withdrawal-review-badges">
          <Badge dot tone={withdrawalStatusTones[item.status]}>{withdrawalStatusLabels[item.status]}</Badge>
          {item.dualApprovalRequired && <Badge tone="info">Duyệt hai cấp</Badge>}
          {item.overdue && <Badge tone="danger">Quá hạn xử lý</Badge>}
        </div>

        <dl className="withdrawal-review-grid">
          <div><dt>Người yêu cầu</dt><dd>{partyName(item.applicant)}<br /><span className="withdrawal-review-muted">{item.applicant?.email ?? '—'}</span></dd></div>
          <div><dt>Số tiền yêu cầu</dt><dd><strong>{formatVnd(item.amount)}</strong></dd></div>
          <div><dt>Phí rút</dt><dd>{formatVnd(item.feeAmount)}</dd></div>
          <div><dt>Thực chuyển</dt><dd><strong>{formatVnd(item.netAmount)}</strong></dd></div>
          <div><dt>Ngân hàng</dt><dd>{item.bankName}</dd></div>
          <div><dt>Số tài khoản</dt><dd className="withdrawal-review-code">{item.accountNumber}</dd></div>
          <div><dt>Chủ tài khoản</dt><dd>{item.accountHolderName}</dd></div>
          <div><dt>Hạn xử lý</dt><dd>{formatWithdrawalTime(item.dueAt)}</dd></div>
        </dl>

        {item.reconciliation && <section className="withdrawal-review-panel" aria-labelledby="withdrawal-reconciliation">
          <h3 id="withdrawal-reconciliation"><Landmark size={17} aria-hidden="true" /> Đối chiếu ví</h3>
          <dl className="withdrawal-review-grid">
            <div><dt>Số dư khả dụng</dt><dd>{formatVnd(item.reconciliation.walletAvailable)}</dd></div>
            <div><dt>Đang chờ rút</dt><dd>{formatVnd(item.reconciliation.walletPendingWithdraw)}</dd></div>
            <div><dt>Đang ký quỹ</dt><dd>{formatVnd(item.reconciliation.walletEscrowHeld)}</dd></div>
            <div><dt>Đã chi trước đây</dt><dd>{item.reconciliation.paidCount} lần · {formatVnd(item.reconciliation.paidTotal)}</dd></div>
            <div><dt>Bị từ chối / chi lỗi</dt><dd>{item.reconciliation.unsuccessfulCount} lần</dd></div>
            <div><dt>Lần chi gần nhất</dt><dd>{formatWithdrawalTime(item.reconciliation.lastPaidAt)}</dd></div>
          </dl>
        </section>}

        <section className="withdrawal-review-panel" aria-labelledby="withdrawal-history">
          <h3 id="withdrawal-history"><ReceiptText size={17} aria-hidden="true" /> Lịch sử xử lý</h3>
          <ul className="withdrawal-review-timeline">
            <li>Tạo yêu cầu · {formatWithdrawalTime(item.createdAt)}</li>
            {item.firstApprovedAt && <li>Duyệt cấp 1 bởi {partyName(item.firstApprover)} · {formatWithdrawalTime(item.firstApprovedAt)}</li>}
            {item.approvedAt && <li>Duyệt {item.dualApprovalRequired ? 'cấp 2 ' : ''}bởi {partyName(item.approver)} · {formatWithdrawalTime(item.approvedAt)}</li>}
            {item.rejectedAt && <li>Từ chối bởi {partyName(item.rejectedBy)} · {formatWithdrawalTime(item.rejectedAt)}: {item.rejectionReason}</li>}
            {item.payoutSubmittedAt && <li>Gửi chi ({item.payoutProvider ?? '—'}) · {formatWithdrawalTime(item.payoutSubmittedAt)}</li>}
            {item.status === 'PAID' && <li>Đã chi bởi {partyName(item.settledBy)} · {formatWithdrawalTime(item.settledAt)} · mã tham chiếu <span className="withdrawal-review-code">{item.payoutReference}</span></li>}
            {item.status === 'FAILED' && <li>Chi thất bại · {formatWithdrawalTime(item.settledAt)}: {item.failureReason}</li>}
          </ul>
        </section>

        {item.status === 'APPROVED_L1' && !item.canApprove && item.canReject && <Alert tone="info">
          Bạn đã duyệt cấp 1. Một quản trị viên khác cần duyệt cấp 2 trước khi chi trả.</Alert>}
        {!item.canApprove && !item.canReject && !item.canSettle && ['PENDING_REVIEW', 'APPROVED_L1', 'APPROVED', 'PROCESSING'].includes(item.status)
          && <Alert tone="warning">Bạn không thể xử lý yêu cầu rút tiền do chính mình đứng tên.</Alert>}
        {actionError && <ApiErrorNotice message={actionError.message} correlationId={actionError.correlationId} />}

        {(item.canApprove || item.canReject || item.canSettle) && <div className="withdrawal-review-actions">
          {item.canApprove && <Button icon={<BadgeCheck size={17} />} loading={submitting && mode === null}
            disabled={submitting} onClick={approve}>{approveLabel}</Button>}
          {item.canReject && <Button variant="danger" icon={<CircleX size={17} />} disabled={submitting}
            onClick={() => openMode('reject')}>Từ chối</Button>}
          {item.canSettle && <Button variant="secondary" disabled={submitting} onClick={() => openMode('paid')}>Nhập mã đã chi</Button>}
          {item.canSettle && <Button variant="ghost" disabled={submitting} onClick={() => openMode('failed')}>Ghi nhận chi thất bại</Button>}
        </div>}

        {mode && <form className="ui-stack withdrawal-review-decision" onSubmit={submit} noValidate>
          {mode === 'paid'
            ? <Input label="Mã tham chiếu chuyển khoản" required maxLength={100} value={text} autoFocus
              hint="Chỉ gồm chữ, số và các ký tự . _ / -" onChange={event => setText(event.target.value)} />
            : <Textarea label={mode === 'reject' ? 'Lý do từ chối (người dùng sẽ thấy)' : 'Nguyên nhân chi thất bại'}
              required maxLength={500} rows={3} value={text} autoFocus onChange={event => setText(event.target.value)} />}
          <div className="withdrawal-review-actions">
            <Button type="submit" variant={mode === 'paid' ? 'primary' : 'danger'} loading={submitting} disabled={!valid}>
              {mode === 'paid' ? 'Xác nhận đã chi' : mode === 'failed' ? 'Ghi nhận thất bại và hoàn ví' : 'Từ chối và hoàn ví'}
            </Button>
            <Button type="button" variant="ghost" disabled={submitting} onClick={() => openMode(null)}>Hủy</Button>
          </div>
        </form>}
      </div>}
  </Modal>;
}
