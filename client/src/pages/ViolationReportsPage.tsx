import { useEffect, useState } from 'react';
import { Eye, Flag, RefreshCw } from 'lucide-react';
import { violationReportApi } from '../api/violationReportApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { PageResponse } from '../types/api';
import type { ViolationCase, ViolationCaseStatus, ViolationTargetType } from '../types/violationReport';
import ViolationCaseDialog from '../features/violation-reports/components/ViolationCaseDialog';
import { caseStatusLabels, formatViolationTime, reasonLabels, targetLabels, violationPartyName } from '../features/violation-reports/violationPresentation';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Select from '../components/ui/Select';
import Button from '../components/ui/Button';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
import Alert from '../components/ui/Alert';
import Badge from '../components/ui/Badge';
import '../styles/violation-reports.css';

export default function ViolationReportsPage() {
  const [status, setStatus] = useState<ViolationCaseStatus>('OPEN');
  const [targetType, setTargetType] = useState<ViolationTargetType | ''>('');
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<PageResponse<ViolationCase> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    void violationReportApi.queue(status, targetType || undefined, page, controller.signal)
      .then(response => { if (!controller.signal.aborted) setResult(response.data); })
      .catch(cause => { if (!controller.signal.aborted) { setError(getApiError(cause)); setResult(null); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [status, targetType, page, revision]);

  const columns: TableColumn<ViolationCase>[] = [
    { key: 'target', header: 'Đối tượng', render: item => <div className="violation-cell">
      <strong>{item.targetLabel ?? item.targetId}</strong>
      <span className="violation-muted">{targetLabels[item.targetType]}</span></div> },
    { key: 'reason', header: 'Lý do gần nhất', render: item => reasonLabels[item.latestReason] },
    { key: 'count', header: 'Số báo cáo', align: 'right', render: item =>
      <Badge tone={item.reportCount > 1 ? 'danger' : 'neutral'}>{item.reportCount}</Badge> },
    { key: 'respondent', header: 'Người bị báo cáo', render: item => violationPartyName(item.respondent) },
    { key: 'created', header: 'Gửi lần đầu', render: item => <span className="violation-time">{formatViolationTime(item.createdAt)}</span> },
    { key: 'actions', header: 'Thao tác', align: 'right', render: item => <Button size="sm" variant="secondary" icon={<Eye size={16} />}
      aria-label={`Xem hồ sơ ${item.targetLabel ?? item.targetId}`} onClick={() => { setNotice(''); setSelected(item.id); }}>Xem</Button> },
  ];

  return <div className="ui-stack violation-page">
    <PageHeader title="Báo cáo vi phạm" eyebrow="Kiểm duyệt"
      description="Báo cáo trùng về cùng đối tượng được gộp; hồ sơ nhiều báo cáo hiển thị trước."
      actions={<Button variant="secondary" icon={<RefreshCw size={17} />} disabled={loading} onClick={() => setRevision(value => value + 1)}>Tải lại</Button>} />
    {notice && <Alert tone="success">{notice}</Alert>}
    <SectionCard title="Hàng đợi" className="ui-card-table"
      description={result && !loading && !error ? `${result.totalElements} hồ sơ` : 'Đang tải'}>
      <div className="violation-filters">
        <Select label="Trạng thái" value={status} onChange={event => { setStatus(event.target.value as ViolationCaseStatus); setPage(0); }}>
          {(Object.keys(caseStatusLabels) as ViolationCaseStatus[]).map(key => <option key={key} value={key}>{caseStatusLabels[key]}</option>)}
        </Select>
        <Select label="Loại đối tượng" value={targetType} onChange={event => { setTargetType(event.target.value as ViolationTargetType | ''); setPage(0); }}>
          <option value="">Tất cả</option>
          {(Object.keys(targetLabels) as ViolationTargetType[]).map(key => <option key={key} value={key}>{targetLabels[key]}</option>)}
        </Select>
      </div>
      {error ? <ApiErrorNotice message={error.message} correlationId={error.correlationId} /> : <>
        <DataTable caption="Hồ sơ vi phạm" columns={columns} rows={result?.content ?? []} rowKey={item => item.id} loading={loading}
          emptyState={<EmptyState title="Không có hồ sơ" description="Báo cáo mới của người dùng sẽ xuất hiện tại đây." icon={<Flag size={24} />} />} />
        {!loading && result && <Pagination page={result.page} size={result.size} totalElements={result.totalElements}
          totalPages={result.totalPages} onPageChange={setPage} />}
      </>}
    </SectionCard>
    {selected && <ViolationCaseDialog key={selected} caseId={selected} onClose={() => setSelected(null)}
      onChanged={message => { setNotice(message); setRevision(value => value + 1); }} />}
  </div>;
}
