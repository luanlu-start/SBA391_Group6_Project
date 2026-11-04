import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Eye, RefreshCw, Search, ShieldCheck } from 'lucide-react';
import { useAuthentication } from '../auth/useAuthentication';
import type { KycReviewItem, KycReviewQuery, KycReviewStatusFilter } from '../types/kycReview';
import useKycReviewQueue from '../features/kyc-review/hooks/useKycReviewQueue';
import KycReviewDialog from '../features/kyc-review/components/KycReviewDialog';
import { formatKycReviewTime, kycReviewStatusLabels, kycReviewStatusTones } from '../features/kyc-review/kycReviewPresentation';
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
import '../styles/kyc-review.css';

const initialQuery: KycReviewQuery = { status: 'PENDING', page: 0, size: 20 };
const maxPage = 100000;

export default function KycReviewPage() {
  const { user } = useAuthentication();
  return user ? <KycReviewQueue key={user.id} currentUserId={user.id} isAdmin={user.roles.includes('ADMIN')} /> : null;
}

function KycReviewQueue({ currentUserId, isAdmin }: { currentUserId: string; isAdmin: boolean }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedId = searchParams.getAll('request').length === 1
    && /^[a-f\d]{8}-[a-f\d]{4}-[a-f\d]{4}-[a-f\d]{4}-[a-f\d]{12}$/i.test(searchParams.get('request') ?? '')
    ? searchParams.get('request') : null;
  const [query, setQuery] = useState<KycReviewQuery>(initialQuery);
  const [keyword, setKeyword] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [notice, setNotice] = useState('');
  const list = useKycReviewQueue(query);
  const searchPending = keyword.trim() !== (query.q ?? '');

  useEffect(() => { if (requestedId) setSelectedId(requestedId); }, [requestedId]);

  function openDetail(id: string) {
    setSelectedId(id); setNotice('');
    const next = new URLSearchParams(searchParams); next.set('request', id);
    setSearchParams(next, { replace: true });
  }

  function closeDetail() {
    setSelectedId(null);
    if (searchParams.has('request')) {
      const next = new URLSearchParams(searchParams); next.delete('request');
      setSearchParams(next, { replace: true });
    }
  }

  useEffect(() => {
    const q = keyword.trim();
    if (q === (query.q ?? '')) return;
    const timer = window.setTimeout(() => setQuery(previous => ({ ...previous, q: q || undefined, page: 0 })), 350);
    return () => window.clearTimeout(timer);
  }, [keyword, query.q]);

  useEffect(() => {
    if (!list.result || list.loading || list.error) return;
    const lastPage = Math.min(maxPage, Math.max(0, list.result.totalPages - 1));
    if (query.page > lastPage) setQuery(previous => ({ ...previous, page: lastPage }));
  }, [list.result, list.loading, list.error, query.page]);

  function resetFilters() { setKeyword(''); setQuery(initialQuery); }
  function updateFilter<K extends keyof KycReviewQuery>(key: K, value: KycReviewQuery[K]) {
    setQuery(previous => ({ ...previous, [key]: value, page: 0 }));
  }

  const columns: TableColumn<KycReviewItem>[] = [
    { key: 'applicant', header: 'Người nộp', render: item => <div className="kyc-review-applicant"><strong>{item.fullName}</strong>
      <span className="kyc-review-muted kyc-review-break">{item.email}</span></div> },
    { key: 'identity', header: 'Số CCCD', render: item => <span className="kyc-review-code">{item.idNumberMasked || '—'}</span> },
    { key: 'submitted', header: 'Thời gian nộp', render: item => <span className="kyc-review-time">{formatKycReviewTime(item.submittedAt)}</span> },
    { key: 'status', header: 'Trạng thái', render: item => <div className="kyc-review-badges">
      <Badge dot tone={kycReviewStatusTones[item.status]}>{kycReviewStatusLabels[item.status]}</Badge>
      {item.flagged && <Badge tone="warning">Cần Admin</Badge>}
      {item.overdue && item.status === 'PENDING' && <Badge tone="danger">Quá hạn duyệt</Badge>}
    </div> },
    { key: 'due', header: 'Hạn duyệt', render: item => <span className="kyc-review-time">{formatKycReviewTime(item.dueAt)}</span> },
    { key: 'actions', header: 'Thao tác', align: 'right', render: item => <Button variant="secondary" size="sm"
      icon={<Eye size={16} />} aria-label={`Xem hồ sơ của ${item.fullName}`} onClick={() => openDetail(item.id)}>Xem hồ sơ</Button> },
  ];

  return <div className="ui-stack kyc-review-page">
    <PageHeader title="Duyệt hồ sơ xác minh" eyebrow="Kiểm duyệt"
      description="Đối chiếu thông tin và ảnh CCCD, xử lý hồ sơ chờ xác minh."
      actions={<Button variant="secondary" disabled={list.loading || searchPending} icon={<RefreshCw size={17} />} onClick={list.reload}>Tải lại</Button>} />
    {notice && <Alert tone="success">{notice}</Alert>}
    <SectionCard title="Bộ lọc hồ sơ" description="Hồ sơ nộp trước được hiển thị trước. Thời gian dùng giờ Việt Nam (UTC+7).">
      <div className="kyc-review-filter-grid">
        <Input label="Tìm người nộp" placeholder="Nhập tên hoặc email…" maxLength={100} value={keyword}
          icon={<Search size={17} />} onChange={event => setKeyword(event.target.value)} />
        <Select label="Trạng thái hồ sơ" value={query.status} onChange={event => updateFilter('status', event.target.value as KycReviewStatusFilter)}>
          <option value="PENDING">Chờ duyệt</option><option value="APPROVED">Đã duyệt</option><option value="REJECTED">Đã từ chối</option><option value="ALL">Tất cả trạng thái</option>
        </Select>
        <Select label="Yêu cầu quản trị viên" value={query.flagged === undefined ? '' : String(query.flagged)}
          onChange={event => updateFilter('flagged', event.target.value === '' ? undefined : event.target.value === 'true')}>
          <option value="">Tất cả hồ sơ</option><option value="true">Cần Admin xem xét</option><option value="false">Chưa chuyển Admin</option>
        </Select>
        <Select label="Thời hạn duyệt" value={query.overdue === undefined ? '' : String(query.overdue)}
          onChange={event => updateFilter('overdue', event.target.value === '' ? undefined : event.target.value === 'true')}>
          <option value="">Tất cả thời hạn</option><option value="true">Quá hạn duyệt</option><option value="false">Chưa quá hạn duyệt</option>
        </Select>
        <Select label="Số hồ sơ mỗi trang" value={query.size} onChange={event => updateFilter('size', Number(event.target.value))}>
          <option value="20">20 hồ sơ</option><option value="50">50 hồ sơ</option><option value="100">100 hồ sơ</option>
        </Select>
      </div>
      <div className="kyc-review-filter-footer"><p className="kyc-review-muted" role="status">{searchPending ? 'Đang áp dụng từ khóa tìm kiếm…' : 'Bộ lọc được áp dụng tự động.'}</p>
        <Button variant="secondary" size="sm" onClick={resetFilters}>Đặt lại bộ lọc</Button></div>
    </SectionCard>
    <SectionCard title="Danh sách hồ sơ" className="ui-card-table"
      description={list.result && !list.loading && !list.error ? `${list.result.totalElements} hồ sơ khớp bộ lọc` : 'Hồ sơ nộp trước hiển thị trước'}>
      {list.error ? <div className="ui-stack"><ApiErrorNotice message={list.error.message} correlationId={list.error.correlationId} />
        <div><Button variant="secondary" onClick={list.reload}>Thử lại</Button></div></div> : <>
        <DataTable caption="Danh sách hồ sơ xác minh" columns={columns} rows={list.result?.content ?? []}
          rowKey={item => item.id} loading={list.loading || searchPending}
          emptyState={<EmptyState title="Không có hồ sơ khớp bộ lọc" description="Thử trạng thái hoặc từ khóa khác."
            icon={<ShieldCheck size={24} />} action={<Button variant="secondary" onClick={resetFilters}>Về danh sách chờ duyệt</Button>} />} />
        {!list.loading && !searchPending && list.result && <Pagination page={list.result.page} size={list.result.size}
          totalElements={list.result.totalElements} totalPages={Math.min(list.result.totalPages, maxPage + 1)}
          onPageChange={page => setQuery(previous => ({ ...previous, page }))} />}
        {!list.loading && list.result && list.result.totalPages > maxPage + 1 && <p className="kyc-review-muted">
          Thu hẹp bộ lọc để xem các hồ sơ ngoài giới hạn phân trang.</p>}
      </>}
    </SectionCard>
    {selectedId && <KycReviewDialog key={selectedId} requestId={selectedId} currentUserId={currentUserId} isAdmin={isAdmin}
      onClose={closeDetail} onChanged={message => { setNotice(message); list.reload(); }} />}
  </div>;
}
