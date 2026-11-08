import { useState } from 'react';
import type { FormEvent } from 'react';
import { complaintApi } from '../../../api/complaintApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { AppealDecision, ComplaintDetail } from '../../../types/complaint';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import SectionCard from '../../../components/ui/SectionCard';
import Button from '../../../components/ui/Button';
import Select from '../../../components/ui/Select';
import Textarea from '../../../components/ui/Textarea';
import RulingFields from './RulingFields';
import { emptyRuling, rulingPayload } from '../rulingDraft';
import type { RulingDraft } from '../rulingDraft';
import { formatTime } from '../complaintPresentation';

interface Props {
  complaint: ComplaintDetail;
  onChanged: (next: ComplaintDetail, notice: string) => void;
}

/** Các thao tác người xem được phép làm theo quyền máy chủ trả về. */
export default function ComplaintActions({ complaint, onChanged }: Props) {
  const { permissions } = complaint;
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [infoMessage, setInfoMessage] = useState('');
  const [ruling, setRuling] = useState<RulingDraft>(emptyRuling);
  const [rulingReason, setRulingReason] = useState('');
  const [appealReason, setAppealReason] = useState('');
  const [decision, setDecision] = useState<AppealDecision>('UPHOLD');
  const [amended, setAmended] = useState<RulingDraft>(emptyRuling);
  const [reviewNote, setReviewNote] = useState('');

  if (!permissions.claim && !permissions.requestInfo && !permissions.rule && !permissions.appeal && !permissions.reviewAppeal) return null;

  async function run(action: string, call: () => Promise<{ data: ComplaintDetail }>, notice: string, event?: FormEvent) {
    event?.preventDefault();
    setBusy(action);
    setError(null);
    try {
      const response = await call();
      onChanged(response.data, notice);
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setBusy(null);
    }
  }

  const takingOver = complaint.assignee !== null;

  return <SectionCard title="Xử lý" description={permissions.appeal && complaint.ruling?.appealDeadline
    ? `Hạn kháng nghị: ${formatTime(complaint.ruling.appealDeadline)}` : undefined}>
    <div className="ui-stack">
      {permissions.claim && <div>
        <Button loading={busy === 'claim'} onClick={() => void run('claim', () => complaintApi.claim(complaint.id),
          takingOver ? 'Bạn đã tiếp quản hồ sơ.' : 'Bạn đã tiếp nhận khiếu nại.')}>
          {takingOver ? 'Tiếp quản hồ sơ' : 'Tiếp nhận xử lý'}</Button>
      </div>}

      {permissions.requestInfo && <form className="ui-stack complaint-action" noValidate onSubmit={event => void run('info',
        () => complaintApi.requestInfo(complaint.id, infoMessage.trim()), 'Đã gửi yêu cầu bổ sung bằng chứng.', event)}>
        <Textarea label="Yêu cầu bổ sung bằng chứng" required maxLength={2000} rows={2} value={infoMessage}
          onChange={event => setInfoMessage(event.target.value)} />
        <div><Button type="submit" variant="secondary" loading={busy === 'info'} disabled={!infoMessage.trim()}>Gửi yêu cầu</Button></div>
      </form>}

      {permissions.rule && <form className="ui-stack complaint-action" noValidate onSubmit={event => void run('rule',
        () => complaintApi.rule(complaint.id, { ...rulingPayload(ruling), reason: rulingReason.trim() }),
        'Đã ra phán quyết. Tiền được xử lý khi hết hạn kháng nghị.', event)}>
        <h3 className="complaint-action-title">Phán quyết</h3>
        <RulingFields complaint={complaint} draft={ruling} onChange={setRuling} />
        <Textarea label="Lý do phán quyết (các bên đều thấy)" required maxLength={1000} rows={3} value={rulingReason}
          onChange={event => setRulingReason(event.target.value)} />
        <div><Button type="submit" loading={busy === 'rule'} disabled={!rulingReason.trim()}>Xác nhận phán quyết</Button></div>
      </form>}

      {permissions.appeal && <form className="ui-stack complaint-action" noValidate onSubmit={event => void run('appeal',
        () => complaintApi.appeal(complaint.id, appealReason.trim()), 'Đã gửi kháng nghị. Admin sẽ xem xét lại.', event)}>
        <Textarea label="Kháng nghị phán quyết" hint="Mỗi khiếu nại chỉ được kháng nghị một lần." required maxLength={1000} rows={3}
          value={appealReason} onChange={event => setAppealReason(event.target.value)} />
        <div><Button type="submit" variant="danger" loading={busy === 'appeal'} disabled={!appealReason.trim()}>Gửi kháng nghị</Button></div>
      </form>}

      {permissions.reviewAppeal && <form className="ui-stack complaint-action" noValidate onSubmit={event => void run('review',
        () => complaintApi.reviewAppeal(complaint.id, decision === 'AMEND'
          ? { decision, ...rulingPayload(amended), note: reviewNote.trim() } : { decision, note: reviewNote.trim() }),
        'Đã xem xét kháng nghị và đóng hồ sơ.', event)}>
        <h3 className="complaint-action-title">Xem xét kháng nghị</h3>
        <Select label="Quyết định" value={decision} onChange={event => setDecision(event.target.value as AppealDecision)}>
          <option value="UPHOLD">Giữ nguyên phán quyết</option>
          <option value="AMEND">Sửa phán quyết</option>
        </Select>
        {decision === 'AMEND' && <RulingFields complaint={complaint} draft={amended} onChange={setAmended} />}
        <Textarea label="Ghi chú xem xét" required maxLength={1000} rows={3} value={reviewNote}
          onChange={event => setReviewNote(event.target.value)} />
        <div><Button type="submit" loading={busy === 'review'} disabled={!reviewNote.trim()}>Xác nhận và xử lý tiền</Button></div>
      </form>}

      {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
    </div>
  </SectionCard>;
}
