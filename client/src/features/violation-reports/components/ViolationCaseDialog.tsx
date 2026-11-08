import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { violationReportApi } from '../../../api/violationReportApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { ViolationAction, ViolationCase } from '../../../types/violationReport';
import Modal from '../../../components/ui/Modal';
import Button from '../../../components/ui/Button';
import Badge from '../../../components/ui/Badge';
import Alert from '../../../components/ui/Alert';
import Select from '../../../components/ui/Select';
import Textarea from '../../../components/ui/Textarea';
import LoadingSpinner from '../../../components/common/LoadingSpinner';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import { actionLabels, caseStatusLabels, formatViolationTime, reasonLabels, targetLabels, violationPartyName } from '../violationPresentation';

function EvidenceImage({ caseId, evidenceId }: { caseId: string; evidenceId: string }) {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    let objectUrl: string | null = null;
    void violationReportApi.evidence(caseId, evidenceId, controller.signal).then(blob => {
      if (controller.signal.aborted) return;
      objectUrl = URL.createObjectURL(blob);
      setUrl(objectUrl);
    }).catch(() => undefined);
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [caseId, evidenceId]);
  return url ? <a href={url} target="_blank" rel="noreferrer noopener"><img src={url} alt="Ảnh bằng chứng" /></a>
    : <span className="violation-muted">Đang tải ảnh…</span>;
}

interface Props {
  caseId: string;
  onClose: () => void;
  onChanged: (message: string) => void;
}

export default function ViolationCaseDialog({ caseId, onClose, onChanged }: Props) {
  const [item, setItem] = useState<ViolationCase | null>(null);
  const [loadError, setLoadError] = useState<ApiError | null>(null);
  const [action, setAction] = useState<ViolationAction>('WARN');
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    void violationReportApi.get(caseId, controller.signal)
      .then(response => { if (!controller.signal.aborted) setItem(response.data); })
      .catch(cause => { if (!controller.signal.aborted) setLoadError(getApiError(cause)); });
    return () => controller.abort();
  }, [caseId]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const response = await violationReportApi.resolve(caseId, action, note.trim());
      setItem(response.data);
      onChanged(`Đã xử lý hồ sơ: ${actionLabels[action].toLowerCase()}.`);
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setSaving(false);
    }
  }

  return <Modal isOpen onClose={onClose} closeDisabled={saving} size="lg" title="Hồ sơ vi phạm"
    description={item?.targetLabel ?? undefined}>
    {loadError ? <ApiErrorNotice message={loadError.message} correlationId={loadError.correlationId} />
      : !item ? <LoadingSpinner text="Đang tải hồ sơ…" /> : <div className="ui-stack">
        <div className="violation-badges">
          <Badge>{targetLabels[item.targetType]}</Badge>
          <Badge tone={item.status === 'OPEN' ? 'warning' : 'neutral'}>{caseStatusLabels[item.status]}</Badge>
          <Badge tone={item.reportCount > 1 ? 'danger' : 'neutral'}>{item.reportCount} báo cáo</Badge>
          {item.respondentPriorViolations > 0 && <Badge tone="danger">Đã vi phạm {item.respondentPriorViolations} lần</Badge>}
        </div>
        <dl className="violation-grid">
          <div><dt>Người bị báo cáo</dt><dd>{item.respondent
            ? <>{violationPartyName(item.respondent)}<br /><span className="violation-muted">{item.respondent.email}</span></> : '—'}</dd></div>
          <div><dt>Gửi lần đầu</dt><dd>{formatViolationTime(item.createdAt)}</dd></div>
          {item.relatedOrderId && <div><dt>Đơn liên quan</dt><dd>#{item.relatedOrderId}</dd></div>}
          {item.respondent && <div><dt>Hồ sơ người dùng</dt><dd><Link to={`/admin/users/${item.respondent.publicId}`}>Mở trong quản trị</Link></dd></div>}
        </dl>
        <section className="ui-stack" aria-label="Các báo cáo">
          {(item.reports ?? []).map((report, index) => <article key={`${report.createdAt}-${index}`} className="violation-report">
            <strong>{reasonLabels[report.reason]}</strong>
            <span className="violation-muted">{violationPartyName(report.reporter)} · {formatViolationTime(report.createdAt)}</span>
            {report.description && <p>{report.description}</p>}
            {report.evidenceIds.length > 0 && <div className="violation-evidence">
              {report.evidenceIds.map(id => <EvidenceImage key={id} caseId={item.id} evidenceId={id} />)}</div>}
          </article>)}
        </section>
        {item.status !== 'OPEN' && <Alert tone="info" title={item.resolution ? actionLabels[item.resolution] : caseStatusLabels[item.status]}>
          <p>{item.resolutionNote}</p>
          <p>Bởi {violationPartyName(item.handledBy)} lúc {formatViolationTime(item.handledAt)}</p>
        </Alert>}
        {item.status === 'OPEN' && !item.canResolve && <Alert tone="warning">Bạn không thể xử lý hồ sơ mà mình là người bị báo cáo.</Alert>}
        {item.canResolve && <form className="ui-stack violation-decision" onSubmit={submit} noValidate>
          <Select label="Hướng xử lý" value={action} onChange={event => setAction(event.target.value as ViolationAction)}>
            {(Object.keys(actionLabels) as ViolationAction[]).map(key => <option key={key} value={key}>{actionLabels[key]}</option>)}
          </Select>
          <Textarea label="Ghi chú (người bị báo cáo thấy khi bị cảnh cáo)" required maxLength={500} rows={3} value={note}
            onChange={event => setNote(event.target.value)} />
          {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
          <div><Button type="submit" variant={action === 'DISMISS' ? 'secondary' : 'danger'} loading={saving} disabled={!note.trim()}>
            Xác nhận</Button></div>
        </form>}
      </div>}
  </Modal>;
}
