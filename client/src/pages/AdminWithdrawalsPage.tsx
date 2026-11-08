import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { Banknote, Download, Eye, RefreshCw, Search, Upload } from 'lucide-react';
import { withdrawalReviewApi } from '../api/withdrawalReviewApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import { apiErrorMessages } from '../i18n/apiErrors';
import type { AdminWithdrawal, WithdrawalImportResult, WithdrawalQueueFilter, WithdrawalQueueQuery } from '../types/withdrawalReview';
import useWithdrawalQueue from '../features/withdrawal-review/hooks/useWithdrawalQueue';
import WithdrawalReviewDialog from '../features/withdrawal-review/components/WithdrawalReviewDialog';
import { formatVnd, formatWithdrawalTime, partyName, withdrawalStatusLabels, withdrawalStatusTones } from '../features/withdrawal-review/withdrawalReviewPresentation';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Input from '../components/ui/Input';
import Select from '../components/ui/Select';
import Button from '../components/ui/Button';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
import Alert from '../components/ui/Alert';
import Badge from '../components/ui/Badge';
import '../styles/withdrawal-review.css';

const initialQuery: WithdrawalQueueQuery = { status: 'OPEN', page: 0, size: 20 };
const statusOptions: { value: WithdrawalQueueFilter; label: string }[] = [
  { value: 'OPEN', label: 'Cần xử lý' },
  { value: 'PENDING_REVIEW', label: 'Chờ duyệt' },
  { value: 'APPROVED_L1', label: 'Chờ duyệt cấp 2' },
  { value: 'APPROVED', label: 'Đã duyệt, chờ chi' },
  { value: 'PROCESSING', label: 'Đang chi' },
  { value: 'PAID', label: 'Đã chi' },
  { value: 'REJECTED', label: 'Đã từ chối' },
  { value: 'FAILED', label: 'Chi thất bại' },
  { value: 'CANCELLED', label: 'Người dùng đã hủy' },
  { value: 'ALL', label: 'Tất cả trạng thái' },
];

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function exportFileName() {
  const parts = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date()).replace(/[^0-9]/g, '');
  return `lenh-chi-${parts}.csv`;
}

export default function AdminWithdrawalsPage() {
  const [query, setQuery] = useState<WithdrawalQueueQuery>(initialQuery);
  const [keyword, setKeyword] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [notice, setNotice] = useState('');
  const [batchError, setBatchError] = useState<Pick<ApiError, 'message' | 'correlationId'> | null>(null);
  const [importResult, setImportResult] = useState<WithdrawalImportResult | null>(null);
  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const list = useWithdrawalQueue(query);
  const searchPending = keyword.trim() !== (query.q ?? '');

  useEffect(() => {
    const q = keyword.trim();
    if (q === (query.q ?? '')) return;
    const timer = window.setTimeout(() => setQuery(previous => ({ ...previous, q: q || undefined, page: 0 })), 350);
    return () => window.clearTimeout(timer);
  }, [keyword, query.q]);

  function updateFilter<K extends keyof WithdrawalQueueQuery>(key: K, value: WithdrawalQueueQuery[K]) {
    setQuery(previous => ({ ...previous, [key]: value, page: 0 }));
  }

  function resetFilters() {
    setKeyword('');
    setQuery(initialQuery);
  }

  async function exportApproved() {
    setExporting(true);
    setBatchError(null);
    setNotice('');
    try {
      const blob = await withdrawalReviewApi.exportApproved();
      downloadBlob(blob, exportFileName());
      setNotice('Đã xuất lệnh chi. Các yêu cầu trong tệp đã chuyển sang Đang chi.');
      list.reload();
    } catch (cause) {
      // Blob responses do not expose the problem code, so the conflict status identifies an empty batch.
      const error = getApiError(cause);
      setBatchError(error.status === 409
        ? { message: apiErrorMessages.WITHDRAWAL_EXPORT_EMPTY, correlationId: error.correlationId }
        : error);
    } finally {
      setExporting(false);
    }
  }

  async function importFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    setImporting(true);
    setBatchError(null);
    setNotice('');
    setImportResult(null);
    try {
      const response = await withdrawalReviewApi.importResult(file);
      setImportResult(response.data);
      list.reload();
    } catch (cause) {
      setBatchError(getApiError(cause));
    } finally {
      setImporting(false);
    }
  }

  const columns: TableColumn<AdminWithdrawal>[] = [
    { key: 'request', header: 'Yêu cầu', render: item => <div className="withdrawal-review-cell">
      <strong className="withdrawal-review-code">{item.requestCode}</strong>
      <span className="withdrawal-review-muted">{partyName(item.applicant)}</span></div> },
    { key: 'amount', header: 'Thực chuyển', align: 'right', render: item => <strong>{formatVnd(item.netAmount)}</strong> },
    { key: 'bank', header: 'Tài khoản nhận', render: item => <div className="withdrawal-review-cell">
      <span>{item.bankName}</span><span className="withdrawal-review-muted withdrawal-review-code">{item.accountNumber}</span></div> },
    { key: 'status', header: 'Trạng thái', render: item => <div className="withdrawal-review-badges">
      <Badge dot tone={withdrawalStatusTones[item.status]}>{withdrawalStatusLabels[item.status]}</Badge>
      {item.dualApprovalRequired && item.status === 'PENDING_REVIEW' && <Badge tone="info">Hai cấp</Badge>}
      {item.overdue && <Badge tone="danger">Quá hạn</Badge>}
    </div> },
    { key: 'due', header: 'Hạn xử lý', render: item => <span className="withdrawal-review-time">{formatWithdrawalTime(item.dueAt)}</span> },
    { key: 'actions', header: 'Thao tác', align: 'right', render: item => <Button variant="secondary" size="sm"
      icon={<Eye size={16} />} aria-label={`Xem yêu cầu ${item.requestCode}`} onClick={() => { setNotice(''); setSelectedId(item.id); }}>
      Xem</Button> },
  ];

  return <div className="ui-stack withdrawal-review-page">
    <PageHeader title="Duyệt và chi trả rút tiền" eyebrow="Quản trị"
      description="Duyệt hai cấp với yêu cầu từ ngưỡng DUAL_APPROVE, xuất lệnh chi và nhập kết quả chuyển khoản."
      actions={<div className="withdrawal-review-actions">
        <Button variant="secondary" icon={<RefreshCw size={17} />} disabled={list.loading || searchPending} onClick={list.reload}>Tải lại</Button>
        <Button variant="secondary" icon={<Download size={17} />} loading={exporting} onClick={() => void exportApproved()}>Xuất lệnh chi</Button>
        <Button icon={<Upload size={17} />} loading={importing} onClick={() => fileInput.current?.click()}>Nhập kết quả chi</Button>
        <input ref={fileInput} type="file" accept=".csv,text/csv" hidden aria-hidden="true" onChange={event => void importFile(event)} />
      </div>} />

    {notice && <Alert tone="success">{notice}</Alert>}
    {batchError && <ApiErrorNotice message={batchError.message} correlationId={batchError.correlationId} />}
    {importResult && <Alert tone={importResult.errors.length ? 'warning' : 'success'} title="Kết quả nhập tệp chi">
      <p>Đã xử lý {importResult.processed} dòng: {importResult.paid} đã chi, {importResult.failed} chi thất bại.</p>
      {importResult.errors.length > 0 && <ul className="withdrawal-review-errors">
        {importResult.errors.map(error => <li key={`${error.line}-${error.code}`}>
          Dòng {error.line}{error.requestCode ? ` (${error.requestCode})` : ''}: {apiErrorMessages[error.code] ?? error.code}
        </li>)}
      </ul>}
    </Alert>}

    <SectionCard title="Bộ lọc" description="Yêu cầu cần xử lý hiển thị theo thứ tự tạo trước. Thời gian dùng giờ Việt Nam (UTC+7).">
      <div className="withdrawal-review-filter-grid">
        <Input label="Tìm yêu cầu" placeholder="Mã yêu cầu, số tài khoản hoặc chủ tài khoản…" maxLength={100} value={keyword}
          icon={<Search size={17} />} onChange={event => setKeyword(event.target.value)} />
        <Select label="Trạng thái" value={query.status}
          onChange={event => updateFilter('status', event.target.value as WithdrawalQueueFilter)}>
          {statusOptions.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
        </Select>
        <Select label="Số yêu cầu mỗi trang" value={query.size} onChange={event => updateFilter('size', Number(event.target.value))}>
          <option value="20">20 yêu cầu</option><option value="50">50 yêu cầu</option><option value="100">100 yêu cầu</option>
        </Select>
      </div>
      <div className="withdrawal-review-filter-footer">
        <p className="withdrawal-review-muted" role="status">
          {searchPending ? 'Đang áp dụng từ khóa tìm kiếm…' : 'Tệp kết quả cần cột request_code, result (PAID/FAILED), reference, reason.'}
        </p>
        <Button variant="secondary" size="sm" onClick={resetFilters}>Đặt lại bộ lọc</Button>
      </div>
    </SectionCard>

    <SectionCard title="Hàng đợi rút tiền" className="ui-card-table"
      description={list.result && !list.loading && !list.error ? `${list.result.totalElements} yêu cầu khớp bộ lọc` : 'Đang tải hàng đợi'}>
      {list.error ? <div className="ui-stack">
        <ApiErrorNotice message={list.error.message} correlationId={list.error.correlationId} />
        <div><Button variant="secondary" onClick={list.reload}>Thử lại</Button></div>
      </div> : <>
        <DataTable caption="Hàng đợi yêu cầu rút tiền" columns={columns} rows={list.result?.content ?? []}
          rowKey={item => item.id} loading={list.loading || searchPending}
          emptyState={<EmptyState title="Không có yêu cầu khớp bộ lọc" description="Thử trạng thái hoặc từ khóa khác."
            icon={<Banknote size={24} />} action={<Button variant="secondary" onClick={resetFilters}>Về hàng đợi cần xử lý</Button>} />} />
        {!list.loading && !searchPending && list.result && <Pagination page={list.result.page} size={list.result.size}
          totalElements={list.result.totalElements} totalPages={list.result.totalPages}
          onPageChange={page => setQuery(previous => ({ ...previous, page }))} />}
      </>}
    </SectionCard>

    {selectedId !== null && <WithdrawalReviewDialog key={selectedId} withdrawalId={selectedId}
      onClose={() => setSelectedId(null)} onChanged={message => { setNotice(message); list.reload(); }} />}
  </div>;
}
