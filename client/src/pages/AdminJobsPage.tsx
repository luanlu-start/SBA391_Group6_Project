import { useCallback, useEffect, useState } from 'react';
import { History, Play, RefreshCw } from 'lucide-react';
import { jobApi } from '../api/jobApi';
import type { JobRun, JobRunStatus, JobStatus } from '../api/jobApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import Alert from '../components/ui/Alert';
import Modal from '../components/ui/Modal';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import LoadingSpinner from '../components/common/LoadingSpinner';
import '../styles/admin-jobs.css';

const statusLabels: Record<JobRunStatus, string> = { SUCCESS: 'Thành công', FAILED: 'Thất bại', SKIPPED: 'Bỏ qua' };
const statusTones = { SUCCESS: 'success', FAILED: 'danger', SKIPPED: 'neutral' } as const;
const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Asia/Ho_Chi_Minh' });

function formatTime(value?: string | null) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? dateTime.format(date) : '—';
}

function formatInterval(seconds: number) {
  if (seconds % 86400 === 0) return `${seconds / 86400} ngày/lần`;
  if (seconds % 3600 === 0) return `${seconds / 3600} giờ/lần`;
  return `${Math.round(seconds / 60)} phút/lần`;
}

function JobHistory({ job, onClose }: { job: JobStatus; onClose: () => void }) {
  const [runs, setRuns] = useState<JobRun[] | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    void jobApi.history(job.name, controller.signal)
      .then(response => { if (!controller.signal.aborted) setRuns(response.data.content); })
      .catch(cause => { if (!controller.signal.aborted) setError(getApiError(cause)); });
    return () => controller.abort();
  }, [job.name]);
  return <Modal isOpen onClose={onClose} size="lg" title={`Nhật ký: ${job.title}`} description="20 lần chạy gần nhất, giờ Việt Nam.">
    {error ? <ApiErrorNotice message={error.message} correlationId={error.correlationId} />
      : !runs ? <LoadingSpinner text="Đang tải nhật ký…" />
        : runs.length === 0 ? <p className="admin-jobs-muted">Chưa có lần chạy nào.</p>
          : <ul className="admin-jobs-history">{runs.map(run => <li key={run.id}>
            <div className="admin-jobs-badges">
              <Badge dot tone={statusTones[run.status]}>{statusLabels[run.status]}</Badge>
              <Badge>{run.trigger === 'MANUAL' ? 'Thủ công' : 'Theo lịch'}</Badge>
              {run.attempts > 1 && <Badge tone="warning">{run.attempts} lần thử</Badge>}
            </div>
            <span>{formatTime(run.startedAt)} · {run.durationMs ?? '—'} ms · xử lý {run.processed}</span>
            {run.message && <span className="admin-jobs-muted">{run.message}</span>}
          </li>)}</ul>}
  </Modal>;
}

export default function AdminJobsPage() {
  const [jobs, setJobs] = useState<JobStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [running, setRunning] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ tone: 'success' | 'warning'; text: string } | null>(null);
  const [history, setHistory] = useState<JobStatus | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    void jobApi.list().then(response => { setJobs(response.data); setError(null); })
      .catch(cause => setError(getApiError(cause))).finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  async function run(job: JobStatus) {
    setRunning(job.name);
    setNotice(null);
    try {
      const response = await jobApi.run(job.name);
      const result = response.data;
      setNotice(result.status === 'SUCCESS'
        ? { tone: 'success', text: `${job.title}: ${result.message ?? 'hoàn tất'}` }
        : { tone: 'warning', text: `${job.title} thất bại sau ${result.attempts} lần thử: ${result.message ?? ''}` });
      load();
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setRunning(null);
    }
  }

  const columns: TableColumn<JobStatus>[] = [
    { key: 'job', header: 'Tác vụ', render: job => <div className="admin-jobs-cell"><strong>{job.title}</strong>
      <span className="admin-jobs-muted">{job.description}</span></div> },
    { key: 'schedule', header: 'Lịch', render: job => formatInterval(job.intervalSeconds) },
    { key: 'last', header: 'Lần chạy gần nhất', render: job => job.lastRun ? <div className="admin-jobs-cell">
      <div className="admin-jobs-badges"><Badge dot tone={statusTones[job.lastRun.status]}>{statusLabels[job.lastRun.status]}</Badge>
        {job.consecutiveFailures > 0 && <Badge tone="danger">Lỗi {job.consecutiveFailures} lần liên tiếp</Badge>}
        {job.running && <Badge tone="info">Đang chạy</Badge>}</div>
      <span className="admin-jobs-muted">{formatTime(job.lastRun.startedAt)} · xử lý {job.lastRun.processed}</span>
    </div> : <span className="admin-jobs-muted">Chưa chạy</span> },
    { key: 'next', header: 'Lần kế tiếp', render: job => <span className="admin-jobs-time">{formatTime(job.nextRunAt)}</span> },
    { key: 'actions', header: 'Thao tác', align: 'right', render: job => <div className="admin-jobs-actions">
      <Button size="sm" variant="ghost" icon={<History size={15} />} aria-label={`Nhật ký ${job.title}`} onClick={() => setHistory(job)}>Nhật ký</Button>
      <Button size="sm" variant="secondary" icon={<Play size={15} />} loading={running === job.name} disabled={running !== null || job.running}
        aria-label={`Chạy ngay ${job.title}`} onClick={() => void run(job)}>Chạy ngay</Button>
    </div> },
  ];

  return <div className="ui-stack">
    <PageHeader title="Tác vụ định kỳ" eyebrow="Quản trị"
      description="Mỗi tác vụ chỉ chạy trên một instance mỗi chu kỳ, tự thử lại khi lỗi và cảnh báo quản trị viên nếu vẫn thất bại."
      actions={<Button variant="secondary" icon={<RefreshCw size={17} />} disabled={loading} onClick={load}>Tải lại</Button>} />
    {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
    {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
    <SectionCard title="Danh sách tác vụ" className="ui-card-table">
      <DataTable caption="Tác vụ định kỳ" columns={columns} rows={jobs} rowKey={job => job.name} loading={loading} />
    </SectionCard>
    {history && <JobHistory job={history} onClose={() => setHistory(null)} />}
  </div>;
}
