import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Scale } from 'lucide-react';
import { complaintApi } from '../api/complaintApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { PageResponse } from '../types/api';
import type { ComplaintSummary } from '../types/complaint';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Badge from '../components/ui/Badge';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
import { formatTime, reasonLabels, statusLabels, statusTones } from '../features/complaints/complaintPresentation';
import SignedIn from '../features/complaints/components/SignedIn';
import '../styles/complaints.css';

export default function MyComplaintsPage() {
  return <SignedIn><ComplaintList /></SignedIn>;
}

function ComplaintList() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<ComplaintSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    void complaintApi.mine(page, controller.signal)
      .then(response => { if (!controller.signal.aborted) { setResult(response.data); setError(null); } })
      .catch(cause => { if (!controller.signal.aborted) setError(getApiError(cause)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [page]);

  const columns: TableColumn<ComplaintSummary>[] = [
    { key: 'code', header: 'Khiếu nại', render: item => <div className="complaint-cell">
      <Link to={`/complaints/${item.id}`}><strong>{item.code}</strong></Link>
      <span className="complaint-muted">{item.orderCode} · {item.productName}</span></div> },
    { key: 'reason', header: 'Lý do', render: item => reasonLabels[item.reason] },
    { key: 'status', header: 'Trạng thái', render: item => <Badge dot tone={statusTones[item.status]}>{statusLabels[item.status]}</Badge> },
    { key: 'created', header: 'Mở lúc', render: item => <span className="complaint-time">{formatTime(item.createdAt)}</span> },
  ];

  return <div className="ui-stack complaint-page">
    <PageHeader title="Khiếu nại của tôi" eyebrow="Tài khoản"
      description="Khiếu nại về các đơn bạn gửi hoặc giao. Mở từng hồ sơ để trao đổi, bổ sung bằng chứng hoặc kháng nghị." />
    <SectionCard className="ui-card-table">
      {error ? <ApiErrorNotice message={error.message} correlationId={error.correlationId} /> : <>
        <DataTable caption="Khiếu nại của tôi" columns={columns} rows={result?.content ?? []} rowKey={item => item.id} loading={loading}
          emptyState={<EmptyState title="Chưa có khiếu nại" description="Khi đơn của bạn phát sinh khiếu nại, hồ sơ sẽ hiển thị tại đây."
            icon={<Scale size={24} />} />} />
        {!loading && result && <Pagination page={result.page} size={result.size} totalElements={result.totalElements}
          totalPages={result.totalPages} onPageChange={setPage} />}
      </>}
    </SectionCard>
  </div>;
}
