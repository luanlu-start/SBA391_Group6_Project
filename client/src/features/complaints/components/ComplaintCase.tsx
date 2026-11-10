import { useCallback, useEffect, useState } from 'react';
import { MessagesSquare, RefreshCw } from 'lucide-react';
import { complaintApi } from '../../../api/complaintApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { ComplaintChatHistory, ComplaintDetail } from '../../../types/complaint';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import LoadingSpinner from '../../../components/common/LoadingSpinner';
import PageHeader from '../../../components/ui/PageHeader';
import SectionCard from '../../../components/ui/SectionCard';
import Badge from '../../../components/ui/Badge';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import ComplaintThread from './ComplaintThread';
import ComplaintActions from './ComplaintActions';
import {
  escrowLabels, formatMoney, formatTime, outcomeLabels, personName, reasonLabels, roleLabels, statusLabels, statusTones,
} from '../complaintPresentation';
import '../../../styles/complaints.css';

function OrderChat({ complaintId }: { complaintId: string }) {
  const [history, setHistory] = useState<ComplaintChatHistory | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      setHistory((await complaintApi.chat(complaintId)).data);
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setLoading(false);
    }
  }

  return <SectionCard title="Lịch sử chat của đơn" description="Chỉ đọc, dùng để đối chiếu khi phán quyết."
    actions={!history && <Button size="sm" variant="secondary" icon={<MessagesSquare size={16} />} loading={loading}
      onClick={() => void load()}>Xem lịch sử</Button>}>
    {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
    {history && !history.available && <Alert tone="warning">Kho chat tạm thời không đọc được. Vui lòng thử lại sau.</Alert>}
    {history?.available && (history.messages.length === 0 ? <p className="complaint-muted">Hai bên chưa trao đổi trong chat của đơn.</p>
      : <ol className="complaint-chat">{history.messages.map((line, index) => <li key={`${line.createdAt}-${index}`}>
        <span className="complaint-muted">{roleLabels[line.authorRole]} · {formatTime(line.createdAt)}</span>
        <p>{line.content}</p>
      </li>)}</ol>)}
    {!history && !error && <p className="complaint-muted">Tải khi cần để không làm chậm hồ sơ.</p>}
  </SectionCard>;
}

interface Props {
  id: string;
  staffView: boolean;
}

export default function ComplaintCase({ id, staffView }: Props) {
  const [complaint, setComplaint] = useState<ComplaintDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState('');

  const load = useCallback((signal?: AbortSignal) => {
    setLoading(true);
    void complaintApi.get(id, signal)
      .then(response => { if (!signal?.aborted) { setComplaint(response.data); setError(null); } })
      .catch(cause => { if (!signal?.aborted) setError(getApiError(cause)); })
      .finally(() => { if (!signal?.aborted) setLoading(false); });
  }, [id]);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal);
    return () => controller.abort();
  }, [load]);

  if (!complaint) {
    return error ? <ApiErrorNotice message={error.message} correlationId={error.correlationId} />
      : <LoadingSpinner text="Đang tải khiếu nại…" />;
  }

  const { order, escrow, ruling, appeal, settlement } = complaint;
  const active = ['OPEN', 'INVESTIGATING', 'AWAITING_INFO'].includes(complaint.status);

  return <div className="ui-stack complaint-page">
    <PageHeader title={`Khiếu nại ${complaint.code}`} eyebrow={staffView ? 'Kiểm duyệt' : 'Khiếu nại của tôi'}
      description={`${reasonLabels[complaint.reason]} · Đơn ${order.orderCode} · ${order.productName}`}
      actions={<Button variant="secondary" icon={<RefreshCw size={17} />} disabled={loading} onClick={() => load()}>Tải lại</Button>} />
    <div className="complaint-badges">
      <Badge dot tone={statusTones[complaint.status]}>{statusLabels[complaint.status]}</Badge>
      {complaint.responseOverdue && <Badge tone="danger">Quá hạn phản hồi</Badge>}
      {complaint.resolutionOverdue && <Badge tone="danger">Quá hạn xử lý</Badge>}
      {order.insured && <Badge tone="info">Có bảo hiểm</Badge>}
    </div>
    {notice && <Alert tone="success">{notice}</Alert>}
    {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}

    <div className="complaint-layout">
      <div className="ui-stack">
        <SectionCard title="Hồ sơ">
          <dl className="complaint-grid">
            <div><dt>Người mở</dt><dd>{personName(complaint.openedBy)} ({roleLabels[complaint.openerRole]})</dd></div>
            <div><dt>Mở lúc</dt><dd>{formatTime(complaint.createdAt)}</dd></div>
            <div><dt>Người gửi</dt><dd>{personName(complaint.sender)}{complaint.sender?.email &&
              <span className="complaint-muted"> · {complaint.sender.email}</span>}</dd></div>
            <div><dt>Người giao</dt><dd>{personName(complaint.courier)}{complaint.courier?.email &&
              <span className="complaint-muted"> · {complaint.courier.email}</span>}</dd></div>
            <div><dt>Kiểm duyệt viên</dt><dd>{complaint.assignee ? personName(complaint.assignee) : 'Chưa tiếp nhận'}</dd></div>
            <div><dt>{active ? 'Hạn phản hồi / hạn xử lý' : 'Phản hồi đầu tiên'}</dt><dd>{active
              ? `${formatTime(complaint.firstResponseDueAt)} / ${formatTime(complaint.resolveDueAt)}`
              : formatTime(complaint.firstResponseAt)}</dd></div>
            <div><dt>Ký quỹ</dt><dd>{escrow ? `${formatMoney(escrow.amount)} · ${escrowLabels[escrow.status]}${escrow.frozen ? ' · đang đóng băng' : ''}`
              : 'Chưa ký quỹ'}</dd></div>
            <div><dt>Giá trị khai báo</dt><dd>{formatMoney(order.declaredValue)}</dd></div>
          </dl>
        </SectionCard>

        {ruling && <SectionCard title="Phán quyết" description={ruling.ruledAt
          ? `${personName(ruling.ruledBy)} · ${formatTime(ruling.ruledAt)}` : undefined}>
          <dl className="complaint-grid">
            <div><dt>Hướng xử lý</dt><dd>{outcomeLabels[ruling.outcome]}{ruling.senderPercent ? ` (${ruling.senderPercent}% cho người gửi)` : ''}</dd></div>
            <div><dt>{escrow?.status === 'RELEASED' && !settlement ? 'Thu hồi về người gửi' : 'Hoàn người gửi'}</dt><dd>{formatMoney(ruling.refundAmount)}</dd></div>
            <div><dt>Trả người giao</dt><dd>{formatMoney(ruling.courierAmount)}</dd></div>
            {ruling.compensationAmount > 0 && <div><dt>Bồi thường từ Quỹ bảo đảm</dt><dd>{formatMoney(ruling.compensationAmount)}</dd></div>}
            {complaint.status === 'RESOLVED' && <div><dt>Hạn kháng nghị</dt><dd>{formatTime(ruling.appealDeadline)}</dd></div>}
          </dl>
          {ruling.reason && <p className="complaint-text">{ruling.reason}</p>}
        </SectionCard>}

        {appeal && <SectionCard title="Kháng nghị" description={`${personName(appeal.appealedBy)} · ${formatTime(appeal.appealedAt)}`}>
          <p className="complaint-text">{appeal.reason}</p>
          {appeal.decision && <Alert tone="info" title={appeal.decision === 'UPHOLD' ? 'Admin giữ nguyên phán quyết' : 'Admin sửa phán quyết'}>
            <p>{appeal.note}</p>
            <p>{personName(appeal.reviewedBy)} · {formatTime(appeal.reviewedAt)}</p>
          </Alert>}
        </SectionCard>}

        {settlement && <SectionCard title="Kết quả xử lý tiền" description={`Đóng lúc ${formatTime(settlement.closedAt)}`}>
          <dl className="complaint-grid">
            {settlement.clawbackAmount > 0 && <div><dt>Đã thu hồi từ người giao</dt><dd>{formatMoney(settlement.clawbackAmount)}</dd></div>}
            {settlement.unrecoveredAmount > 0 && <div><dt>Chưa thu hồi được</dt><dd>{formatMoney(settlement.unrecoveredAmount)}</dd></div>}
            {escrow && <div><dt>Ký quỹ</dt><dd>{escrowLabels[escrow.status]}</dd></div>}
          </dl>
          {settlement.unrecoveredAmount > 0 && <p className="complaint-muted">Phần chưa thu hồi đã được báo cho Admin xử lý.</p>}
        </SectionCard>}

        <SectionCard title="Trao đổi và bằng chứng">
          <ComplaintThread complaint={complaint} onChanged={next => { setNotice(''); setComplaint(next); }} />
        </SectionCard>
      </div>
      <div className="ui-stack">
        <ComplaintActions complaint={complaint} onChanged={(next, message) => { setComplaint(next); setNotice(message); }} />
        {staffView && complaint.viewerRole !== 'SENDER' && complaint.viewerRole !== 'COURIER' && <OrderChat complaintId={complaint.id} />}
      </div>
    </div>
  </div>;
}
