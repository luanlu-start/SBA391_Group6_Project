import { useState } from 'react';
import { guaranteeApi } from '../../api/guaranteeApi';
import { getApiError } from '../../api/ApiError';
import type { ApiError } from '../../api/ApiError';
import type { Compensation } from '../../types/guarantee';
import Modal from '../../components/ui/Modal';
import Input from '../../components/ui/Input';
import Textarea from '../../components/ui/Textarea';
import Button from '../../components/ui/Button';
import ApiErrorNotice from '../../components/ApiErrorNotice';
import { formatMoney, personName } from '../complaints/complaintPresentation';

interface Props {
  item: Compensation;
  balance: number;
  onClose: () => void;
  onDone: (message: string) => void;
}

/** Duyệt chi (có thể giảm mức) hoặc từ chối một đề nghị bồi thường. */
export default function CompensationReviewDialog({ item, balance, onClose, onDone }: Props) {
  const [amount, setAmount] = useState(String(item.requestedAmount));
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState<'APPROVE' | 'REJECT' | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const value = Number(amount);
  const valid = Number.isInteger(value) && value >= 1 && value <= item.requestedAmount;

  async function submit(decision: 'APPROVE' | 'REJECT') {
    setBusy(decision);
    setError(null);
    try {
      const response = await guaranteeApi.decide(item.complaintId ?? '', decision === 'APPROVE'
        ? { decision, amount: value, note: note.trim() || undefined } : { decision, note: note.trim() });
      onDone(response.data.status === 'PAID' ? `Đã chi ${formatMoney(response.data.approvedAmount)} vào ví người nhận.`
        : response.data.status === 'QUEUED' ? `Đã duyệt; khoản bồi thường đứng thứ ${response.data.queuePosition} trong hàng đợi.`
          : 'Đã từ chối đề nghị bồi thường.');
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setBusy(null);
    }
  }

  return <Modal isOpen onClose={onClose} closeDisabled={busy !== null} title={`Bồi thường khiếu nại ${item.complaintCode ?? ''}`}
    description={`Người nhận: ${personName(item.beneficiary)} · Đơn ${item.orderCode ?? '—'}`}>
    <div className="ui-stack">
      <dl className="guarantee-facts">
        <div><dt>Mức đề nghị</dt><dd>{formatMoney(item.requestedAmount)}</dd></div>
        <div><dt>Giá trị khai báo</dt><dd>{formatMoney(item.declaredValue)}</dd></div>
        <div><dt>Số dư quỹ</dt><dd>{formatMoney(balance)}</dd></div>
      </dl>
      <Input type="number" label="Số tiền duyệt chi (₫)" min={1} max={item.requestedAmount} step={1000} value={amount}
        error={amount && !valid ? `Từ 1 ₫ đến ${formatMoney(item.requestedAmount)}` : undefined}
        hint={value > balance ? 'Quỹ chưa đủ: khoản này sẽ xếp hàng theo thứ tự phán quyết.' : undefined}
        onChange={event => setAmount(event.target.value)} />
      <Textarea label="Ghi chú" hint="Bắt buộc khi từ chối; người nhận thấy lý do này." maxLength={500} rows={3} value={note}
        onChange={event => setNote(event.target.value)} />
      {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
      <div className="guarantee-actions">
        <Button variant="danger" loading={busy === 'REJECT'} disabled={busy !== null || !note.trim()}
          onClick={() => void submit('REJECT')}>Từ chối</Button>
        <Button loading={busy === 'APPROVE'} disabled={busy !== null || !valid} onClick={() => void submit('APPROVE')}>Duyệt chi</Button>
      </div>
    </div>
  </Modal>;
}
