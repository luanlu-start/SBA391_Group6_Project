import { useEffect, useState } from 'react';
import { orderModerationApi } from '../../../api/orderModerationApi';
import type { OrderModerationOutcome } from '../../../types/orderModeration';
import type { OrderStatus } from '../../../types/order';
import Alert from '../../../components/ui/Alert';
import { formatModerationTime } from '../orderModerationPresentation';

interface Props {
  orderId: number;
  status: OrderStatus;
}

export default function OrderModerationNotice({ orderId, status }: Props) {
  const [outcome, setOutcome] = useState<OrderModerationOutcome | null>(null);
  const relevant = status === 'PENDING' || status === 'REJECTED';

  useEffect(() => {
    if (!relevant) return;
    const controller = new AbortController();
    void orderModerationApi.outcome(orderId, controller.signal)
      .then(response => { if (!controller.signal.aborted) setOutcome(response.data); })
      .catch(() => { if (!controller.signal.aborted) setOutcome(null); });
    return () => controller.abort();
  }, [orderId, relevant]);

  if (!relevant || !outcome) return null;
  if (outcome.status === 'REJECTED') {
    return <Alert tone="danger" title="Đơn không được duyệt">
      <p>{outcome.rejectionReason ?? 'Đơn vi phạm quy định hàng hóa.'}</p>
      <p>Thời điểm từ chối: {formatModerationTime(outcome.reviewedAt)}. Vui lòng tạo đơn mới với hàng hóa phù hợp quy định.</p>
    </Alert>;
  }
  if (outcome.status === 'PENDING') {
    return <Alert tone="info" title="Đơn đang chờ kiểm duyệt">
      <p>Đơn cần kiểm duyệt viên xem xét trước khi hiển thị trên sàn. Bạn sẽ nhận thông báo khi có kết quả.</p>
    </Alert>;
  }
  return null;
}
