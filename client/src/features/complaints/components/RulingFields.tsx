import type { ComplaintDetail, ComplaintOutcome } from '../../../types/complaint';
import Select from '../../../components/ui/Select';
import Input from '../../../components/ui/Input';
import { formatMoney, outcomeLabels, refundFor } from '../complaintPresentation';
import type { RulingDraft } from '../rulingDraft';

interface Props {
  complaint: ComplaintDetail;
  draft: RulingDraft;
  onChange: (draft: RulingDraft) => void;
}

/** Hướng phán quyết, tỷ lệ chia và bồi thường; xem trước số tiền mỗi bên nhận từ ký quỹ. */
export default function RulingFields({ complaint, draft, onChange }: Props) {
  const escrowAmount = complaint.escrow && ['HELD', 'RELEASED'].includes(complaint.escrow.status) ? complaint.escrow.amount : 0;
  const released = complaint.escrow?.status === 'RELEASED';
  const percent = Math.min(99, Math.max(1, Number(draft.senderPercent) || 0));
  const refund = refundFor(draft.outcome, escrowAmount, percent);
  const canCompensate = complaint.order.insured && draft.outcome !== 'COURIER_FAVOR';

  return <div className="ui-stack">
    <Select label="Hướng phán quyết" value={draft.outcome}
      onChange={event => onChange({ ...draft, outcome: event.target.value as ComplaintOutcome })}>
      {(Object.keys(outcomeLabels) as ComplaintOutcome[]).map(key => <option key={key} value={key}>{outcomeLabels[key]}</option>)}
    </Select>
    {draft.outcome === 'SPLIT' && <Input type="number" label="Tỷ lệ hoàn cho người gửi (%)" min={1} max={99} required
      value={draft.senderPercent} onChange={event => onChange({ ...draft, senderPercent: event.target.value })} />}
    {canCompensate && <Input type="number" label="Bồi thường từ Quỹ bảo đảm (₫)" min={0} step={1000}
      max={complaint.order.declaredValue ?? undefined}
      hint={`Đơn có bảo hiểm, tối đa ${formatMoney(complaint.order.declaredValue)}. Admin duyệt chi sau khi phán quyết có hiệu lực.`}
      value={draft.compensationAmount} onChange={event => onChange({ ...draft, compensationAmount: event.target.value })} />}
    <dl className="complaint-preview">
      <div><dt>{released ? 'Thu hồi từ người giao về người gửi' : 'Hoàn người gửi'}</dt><dd>{formatMoney(refund)}</dd></div>
      <div><dt>Trả người giao</dt><dd>{formatMoney(released ? 0 : escrowAmount - refund)}</dd></div>
    </dl>
    {!complaint.escrow && <p className="complaint-muted">Đơn chưa có ký quỹ nên phán quyết không phát sinh bút toán ký quỹ.</p>}
  </div>;
}
