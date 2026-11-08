import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { BadgeCheck, CircleX } from 'lucide-react';
import { orderModerationApi } from '../../../api/orderModerationApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { OrderModerationItem } from '../../../types/orderModeration';
import Modal from '../../../components/ui/Modal';
import Button from '../../../components/ui/Button';
import Badge from '../../../components/ui/Badge';
import Alert from '../../../components/ui/Alert';
import Textarea from '../../../components/ui/Textarea';
import LoadingSpinner from '../../../components/common/LoadingSpinner';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import { categoryFlagLabels, formatModerationMoney, formatModerationTime, moderationPartyName } from '../orderModerationPresentation';

interface Props {
  orderId: number;
  onClose: () => void;
  onChanged: (message: string) => void;
}

export default function OrderModerationDialog({ orderId, onClose, onChanged }: Props) {
  const [item, setItem] = useState<OrderModerationItem | null>(null);
  const [loadError, setLoadError] = useState<ApiError | null>(null);
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    void orderModerationApi.get(orderId, controller.signal)
      .then(response => { if (!controller.signal.aborted) setItem(response.data); })
      .catch(cause => { if (!controller.signal.aborted) setLoadError(getApiError(cause)); });
    return () => controller.abort();
  }, [orderId]);

  async function decide(action: () => Promise<{ data: OrderModerationItem }>, message: string) {
    setSubmitting(true);
    setActionError(null);
    try {
      const response = await action();
      setItem(response.data);
      setRejecting(false);
      onChanged(message);
    } catch (cause) {
      setActionError(getApiError(cause));
    } finally {
      setSubmitting(false);
    }
  }

  function approve() {
    if (!item) return;
    void decide(() => orderModerationApi.approve(item.orderId), `Đã duyệt đơn ${item.orderCode ?? item.orderId}. Đơn đã lên sàn.`);
  }

  function reject(event: FormEvent) {
    event.preventDefault();
    if (!item) return;
    void decide(() => orderModerationApi.reject(item.orderId, reason.trim()),
      `Đã từ chối đơn ${item.orderCode ?? item.orderId} và gửi lý do cho người gửi.`);
  }

  const reasonValid = reason.trim().length > 0 && reason.trim().length <= 500;

  return <Modal isOpen onClose={onClose} closeDisabled={submitting} size="lg"
    title={item?.orderCode ? `Kiểm duyệt đơn ${item.orderCode}` : 'Kiểm duyệt đơn hàng'}
    description="Đối chiếu nội dung, ảnh và danh mục hàng với danh sách hàng cấm trước khi quyết định.">
    {loadError ? <ApiErrorNotice message={loadError.message} correlationId={loadError.correlationId} />
      : !item ? <LoadingSpinner text="Đang tải đơn hàng…" /> : <div className="ui-stack order-moderation-detail">
        <div className="order-moderation-badges">
          {item.matchedTerms.map(term => <Badge key={term} tone="danger">{term}</Badge>)}
          {item.categoryFlag && <Badge tone="warning">{categoryFlagLabels[item.categoryFlag]}</Badge>}
          {item.senderRecentRejections > 0 && <Badge tone="warning">Người gửi bị từ chối {item.senderRecentRejections} lần gần đây</Badge>}
        </div>

        <dl className="order-moderation-grid">
          <div><dt>Tên hàng</dt><dd><strong>{item.productName ?? '—'}</strong></dd></div>
          <div><dt>Danh mục</dt><dd>{item.categoryName ?? 'Chưa chọn'}</dd></div>
          <div className="order-moderation-wide"><dt>Mô tả</dt><dd className="order-moderation-text">{item.productDescription || '—'}</dd></div>
          <div className="order-moderation-wide"><dt>Ghi chú giao hàng</dt><dd className="order-moderation-text">{item.deliveryNote || '—'}</dd></div>
          <div><dt>Số lượng · Khối lượng</dt><dd>{item.quantity ?? '—'} · {item.weightKg ?? '—'} kg</dd></div>
          <div><dt>Giá trị khai báo</dt><dd>{formatModerationMoney(item.declaredValue)}</dd></div>
          <div><dt>Người gửi</dt><dd>{moderationPartyName(item.sender)}<br /><span className="order-moderation-muted">{item.sender?.email ?? '—'}</span></dd></div>
          <div><dt>Thời điểm gửi duyệt</dt><dd>{formatModerationTime(item.submittedAt)}</dd></div>
        </dl>

        {item.imageUrls.length > 0 && <section aria-label="Ảnh hàng hóa" className="order-moderation-images">
          {item.imageUrls.map((url, index) => <a key={url} href={url} target="_blank" rel="noreferrer noopener">
            <img src={url} alt={`Ảnh hàng hóa ${index + 1}`} loading="lazy" />
          </a>)}
        </section>}

        {item.status !== 'PENDING' && <Alert tone={item.status === 'REJECTED' ? 'warning' : 'success'}
          title={item.status === 'REJECTED' ? 'Đơn đã bị từ chối' : 'Đơn đã được duyệt'}>
          <p>Bởi {moderationPartyName(item.reviewer)} lúc {formatModerationTime(item.reviewedAt)}.
            {item.rejectionReason ? ` Lý do: ${item.rejectionReason}` : ''}</p>
        </Alert>}
        {item.status === 'PENDING' && !item.canDecide && <Alert tone="warning">
          Bạn không thể kiểm duyệt đơn hàng do chính mình tạo.</Alert>}
        {actionError && <ApiErrorNotice message={actionError.message} correlationId={actionError.correlationId} />}

        {item.canDecide && !rejecting && <div className="order-moderation-actions">
          <Button icon={<BadgeCheck size={17} />} loading={submitting} onClick={approve}>Duyệt, đưa lên sàn</Button>
          <Button variant="danger" icon={<CircleX size={17} />} disabled={submitting}
            onClick={() => { setRejecting(true); setReason(''); setActionError(null); }}>Từ chối</Button>
        </div>}

        {item.canDecide && rejecting && <form className="ui-stack order-moderation-decision" onSubmit={reject} noValidate>
          <Textarea label="Lý do từ chối (người gửi sẽ thấy)" required maxLength={500} rows={3} autoFocus value={reason}
            onChange={event => setReason(event.target.value)} />
          <div className="order-moderation-actions">
            <Button type="submit" variant="danger" loading={submitting} disabled={!reasonValid}>Xác nhận từ chối</Button>
            <Button type="button" variant="ghost" disabled={submitting} onClick={() => setRejecting(false)}>Hủy</Button>
          </div>
        </form>}
      </div>}
  </Modal>;
}
