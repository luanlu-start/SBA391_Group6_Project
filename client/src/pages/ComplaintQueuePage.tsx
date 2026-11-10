import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { RefreshCw, Scale } from 'lucide-react';
import { complaintApi } from '../api/complaintApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { PageResponse } from '../types/api';
import type { ComplaintQueueFilter, ComplaintSummary } from '../types/complaint';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Select from '../components/ui/Select';
import Checkbox from '../components/ui/Checkbox';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
import {
  formatTime, personName, queueFilters, reasonLabels, roleLabels, statusLabels, statusTones,
} from '../features/complaints/complaintPresentation';
import '../styles/complaints.css';

export default function ComplaintQueuePage() {
  const [status, setStatus] = useState<ComplaintQueueFilter>('ACTIVE');
  const [mine, setMine] = useState(false);
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<PageResponse<ComplaintSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    void complaintApi.queue(status, mine, page, controller.signal)
      .then(response => { if (!controller.signal.aborted) setResult(response.data); })
      .catch(cause => { if (!controller.signal.aborted) { setError(getApiError(cause)); setResult(null); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [status, mine, page, revision]);

  const columns: TableColumn<ComplaintSummary>[] = [
    { key: 'code', header: 'Khiếu nại', render: item => <div className="complaint-cell">
      <Link to={`/mod/complaints/${item.id}`}><strong>{item.code}</strong></Link>
      <span className="complaint-muted">{item.orderCode} · {item.productName}</span></div> },
    { key: 'reason', header: 'Lý do', render: item => reasonLabels[item.reason] },
    { key: 'opener', header: 'Người mở', render: item => <div className="complaint-cell">
      <span>{personName(item.openedBy)}</span><span className="complaint-muted">{roleLabels[item.openerRole]}</span></div> },
    { key: 'status', header: 'Trạng thái', render: item => <div className="complaint-badges">
      <Badge dot tone={statusTones[item.status]}>{statusLabels[item.status]}</Badge>
      {(item.responseOverdue || item.resolutionOverdue) && <Badge tone="danger">Quá hạn</Badge>}</div> },
    { key: 'due', header: 'Hạn xử lý', render: item => <div className="complaint-cell">
      <span className="complaint-time">{formatTime(item.status === 'RESOLVED' ? item.appealDeadline : item.resolveDueAt)}</span>
      {item.status === 'RESOLVED' && <span className="complaint-muted">Hết hạn kháng nghị</span>}</div> },
    { key: 'assignee', header: 'Phụ trách', render: item => item.assignee ? personName(item.assignee) : <span className="complaint-muted">Chưa nhận</span> },
  ];

  return <div className="ui-stack complaint-page">
    <PageHeader title="Hàng đợi khiếu nại" eyebrow="Kiểm duyệt"
      description="Sắp theo hạn xử lý gần nhất. Phản hồi đầu tiên trong 24 giờ và phán quyết trong 5 ngày làm việc."
      actions={<Button variant="secondary" icon={<RefreshCw size={17} />} disabled={loading}
        onClick={() => setRevision(value => value + 1)}>Tải lại</Button>} />
    <SectionCard title="Danh sách" className="ui-card-table"
      description={result && !loading && !error ? `${result.totalElements} khiếu nại` : 'Đang tải'}>
      <div className="complaint-filters">
        <Select label="Trạng thái" value={status} onChange={event => { setStatus(event.target.value as ComplaintQueueFilter); setPage(0); }}>
          {(Object.keys(queueFilters) as ComplaintQueueFilter[]).map(key => <option key={key} value={key}>{queueFilters[key]}</option>)}
        </Select>
        <Checkbox label="Chỉ hồ sơ tôi phụ trách" checked={mine} onChange={event => { setMine(event.target.checked); setPage(0); }} />
      </div>
      {error ? <ApiErrorNotice message={error.message} correlationId={error.correlationId} /> : <>
        <DataTable caption="Khiếu nại" columns={columns} rows={result?.content ?? []} rowKey={item => item.id} loading={loading}
          emptyState={<EmptyState title="Không có khiếu nại" description="Khiếu nại mới của người dùng sẽ xuất hiện tại đây."
            icon={<Scale size={24} />} />} />
        {!loading && result && <Pagination page={result.page} size={result.size} totalElements={result.totalElements}
          totalPages={result.totalPages} onPageChange={setPage} />}
      </>}
    </SectionCard>
  </div>;
}
