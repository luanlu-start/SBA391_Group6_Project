import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { ApiError } from '../../../api/ApiError';
import type { KycRejectionReason, KycReviewAction } from '../../../types/kycReview';
import { reviewDecisionSchema } from '../schemas/reviewDecisionSchema';
import type { ReviewDecisionValues } from '../schemas/reviewDecisionSchema';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import Button from '../../../components/ui/Button';
import Select from '../../../components/ui/Select';
import Textarea from '../../../components/ui/Textarea';
import Checkbox from '../../../components/ui/Checkbox';
import Alert from '../../../components/ui/Alert';

interface Props {
  action: KycReviewAction;
  reasons: KycRejectionReason[];
  reasonsLoading: boolean;
  reasonsError: ApiError | null;
  busy: boolean;
  conflict: boolean;
  failure: ApiError | null;
  onRetryReasons: () => void;
  onCancel: () => void;
  onConfirm: (values: ReviewDecisionValues) => Promise<void>;
}

const titles = { approve: 'Xác nhận duyệt hồ sơ', reject: 'Xác nhận từ chối hồ sơ', flag: 'Chuyển quản trị viên xem xét' };
const labels = { approve: 'Xác nhận duyệt', reject: 'Xác nhận từ chối', flag: 'Xác nhận chuyển Admin' };

export default function KycReviewDecisionForm({ action, reasons, reasonsLoading, reasonsError, busy, conflict,
  failure, onRetryReasons, onCancel, onConfirm }: Props) {
  const { register, handleSubmit, watch, setFocus, formState: { errors } } = useForm<ReviewDecisionValues>({
    resolver: zodResolver(reviewDecisionSchema(action, reasons)),
    defaultValues: { reasonCode: '', note: '', flagReason: '', confirmed: false },
  });
  const reason = reasons.find(item => item.code === watch('reasonCode'));
  const unavailableReasons = action === 'reject' && (reasonsLoading || !!reasonsError || reasons.length === 0);

  useEffect(() => {
    if (action === 'reject' && unavailableReasons) return;
    setFocus(action === 'approve' ? 'confirmed' : action === 'reject' ? 'reasonCode' : 'flagReason');
  }, [action, setFocus, unavailableReasons]);

  return <form className="ui-form kyc-review-decision" noValidate onSubmit={handleSubmit(values => void onConfirm(values))}>
    <h3>{titles[action]}</h3>
    {failure && <ApiErrorNotice message={failure.message} correlationId={failure.correlationId} />}
    <fieldset className="kyc-review-fields" disabled={busy || conflict}>
      {action === 'approve' && <div className="ui-stack">
        <Alert tone="info">Hồ sơ sẽ được đánh dấu đã xác minh. Kiểm tra kỹ thông tin và các ảnh đối chiếu trước khi xác nhận.</Alert>
        <Checkbox label="Tôi đã đối chiếu thông tin và tài liệu của hồ sơ này." required
          {...register('confirmed')} error={errors.confirmed?.message} />
      </div>}
      {action === 'reject' && <div className="ui-stack">
        <p>Người nộp sẽ nhận được lý do từ chối để bổ sung hồ sơ.</p>
        {reasonsLoading && <p role="status">Đang tải danh sách lý do…</p>}
        {reasonsError && <div className="ui-stack"><ApiErrorNotice message={reasonsError.message} correlationId={reasonsError.correlationId} />
          <div><Button variant="secondary" size="sm" onClick={onRetryReasons}>Tải lại lý do</Button></div></div>}
        {!reasonsLoading && !reasonsError && reasons.length === 0 && <Alert tone="warning">Chưa có danh sách lý do từ chối. Tải lại trước khi xác nhận.</Alert>}
        <Select label="Lý do từ chối" required {...register('reasonCode')} disabled={unavailableReasons}
          error={errors.reasonCode?.message}>
          <option value="">Chọn lý do</option>
          {reasons.map(item => <option key={item.code} value={item.code}>{item.label}</option>)}
        </Select>
        <Textarea label="Ghi chú cho người nộp" maxLength={400} required={reason?.noteRequired}
          hint={reason?.noteRequired ? 'Bắt buộc với lý do đã chọn, tối đa 400 ký tự.' : 'Có thể bổ sung hướng dẫn, tối đa 400 ký tự.'}
          {...register('note')} error={errors.note?.message} />
      </div>}
      {action === 'flag' && <div className="ui-stack">
        <Alert tone="warning">Sau khi chuyển, chỉ quản trị viên được duyệt hoặc từ chối hồ sơ này.</Alert>
        <Textarea label="Lý do cần quản trị viên xem xét" required maxLength={500}
          hint="Mô tả điểm nghi ngờ hoặc thông tin cần xác minh thêm, tối đa 500 ký tự."
          {...register('flagReason')} error={errors.flagReason?.message} />
      </div>}
    </fieldset>
    <div className="ui-form-actions"><Button variant="secondary" disabled={busy} onClick={onCancel}>Hủy thao tác</Button>
      <Button type="submit" variant={action === 'reject' ? 'danger' : 'primary'} loading={busy} loadingText="Đang lưu quyết định…"
        disabled={conflict || unavailableReasons}>{labels[action]}</Button></div>
  </form>;
}
