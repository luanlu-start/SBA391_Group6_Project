import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { ImagePlus, Send } from 'lucide-react';
import { complaintApi } from '../../../api/complaintApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { ComplaintDetail } from '../../../types/complaint';
import Button from '../../../components/ui/Button';
import Textarea from '../../../components/ui/Textarea';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import { formatTime, personName, roleLabels } from '../complaintPresentation';

const MAX_FILES = 3;

function EvidenceImage({ complaintId, evidenceId }: { complaintId: string; evidenceId: string }) {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    let objectUrl: string | null = null;
    void complaintApi.evidence(complaintId, evidenceId, controller.signal).then(blob => {
      if (controller.signal.aborted) return;
      objectUrl = URL.createObjectURL(blob);
      setUrl(objectUrl);
    }).catch(() => undefined);
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [complaintId, evidenceId]);
  return url ? <a href={url} target="_blank" rel="noreferrer noopener"><img src={url} alt="Ảnh bằng chứng" /></a>
    : <span className="complaint-muted">Đang tải ảnh…</span>;
}

interface Props {
  complaint: ComplaintDetail;
  onChanged: (next: ComplaintDetail) => void;
}

export default function ComplaintThread({ complaint, onChanged }: Props) {
  const [body, setBody] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const picker = useRef<HTMLInputElement>(null);

  function pick(event: ChangeEvent<HTMLInputElement>) {
    setFiles(Array.from(event.target.files ?? []).slice(0, MAX_FILES));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSending(true);
    setError(null);
    try {
      const response = await complaintApi.message(complaint.id, body.trim(), files);
      setBody('');
      setFiles([]);
      if (picker.current) picker.current.value = '';
      onChanged(response.data);
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setSending(false);
    }
  }

  return <div className="ui-stack">
    <ol className="complaint-thread" aria-label="Trao đổi trong khiếu nại">
      {complaint.messages.map(message => <li key={message.id}
        className={`complaint-message complaint-message-${message.authorRole.toLowerCase()}`}>
        <div className="complaint-message-meta">
          <strong>{message.authorRole === 'SYSTEM' ? roleLabels.SYSTEM : personName(message.author)}</strong>
          {message.authorRole !== 'SYSTEM' && <span>{roleLabels[message.authorRole]}</span>}
          <time dateTime={message.createdAt}>{formatTime(message.createdAt)}</time>
        </div>
        {message.body && <p>{message.body}</p>}
        {message.evidenceIds.length > 0 && <div className="complaint-evidence">
          {message.evidenceIds.map(id => <EvidenceImage key={id} complaintId={complaint.id} evidenceId={id} />)}
        </div>}
      </li>)}
    </ol>
    {complaint.permissions.message && <form className="ui-stack complaint-composer" onSubmit={submit} noValidate>
      <Textarea label="Gửi trao đổi" hint="Cả người gửi, người giao và kiểm duyệt viên đều thấy nội dung này."
        maxLength={2000} rows={3} value={body} onChange={event => setBody(event.target.value)} />
      <div className="complaint-composer-actions">
        <label className="ui-button ui-button-secondary ui-button-sm complaint-file-picker">
          <ImagePlus size={16} aria-hidden="true" />
          <span>{files.length ? `${files.length} ảnh đã chọn` : 'Đính kèm ảnh (tối đa 3)'}</span>
          <input ref={picker} type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={pick} />
        </label>
        <Button type="submit" size="sm" icon={<Send size={16} />} loading={sending}
          disabled={!body.trim() && files.length === 0}>Gửi</Button>
      </div>
      {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
    </form>}
  </div>;
}
