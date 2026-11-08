import { useEffect, useState } from 'react';
import { Eye, PackageSearch, RefreshCw, Search } from 'lucide-react';
import { useAuthentication } from '../auth/useAuthentication';
import type { OrderModerationItem, OrderModerationQuery } from '../types/orderModeration';
import useOrderModerationQueue from '../features/order-moderation/hooks/useOrderModerationQueue';
import OrderModerationDialog from '../features/order-moderation/components/OrderModerationDialog';
import ModerationKeywordPanel from '../features/order-moderation/components/ModerationKeywordPanel';
import { categoryFlagLabels, formatModerationTime, moderationPartyName } from '../features/order-moderation/orderModerationPresentation';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Input from '../components/ui/Input';
import Button from '../components/ui/Button';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
import Alert from '../components/ui/Alert';
import Badge from '../components/ui/Badge';
import '../styles/order-moderation.css';

const initialQuery: OrderModerationQuery = { page: 0, size: 20 };

export default function OrderModerationPage() {
  const { user } = useAuthentication();
  const [query, setQuery] = useState<OrderModerationQuery>(initialQuery);
  const [keyword, setKeyword] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [notice, setNotice] = useState('');
  const list = useOrderModerationQueue(query);
  const searchPending = keyword.trim() !== (query.q ?? '');

  useEffect(() => {
    const q = keyword.trim();
    if (q === (query.q ?? '')) return;
    const timer = window.setTimeout(() => setQuery(previous => ({ ...previous, q: q || undefined, page: 0 })), 350);
    return () => window.clearTimeout(timer);
  }, [keyword, query.q]);

  const columns: TableColumn<OrderModerationItem>[] = [
    { key: 'order', header: 'Đơn hàng', render: item => <div className="order-moderation-cell">
      <strong>{item.productName ?? '—'}</strong>
      <span className="order-moderation-muted">{item.orderCode ?? `#${item.orderId}`}</span></div> },
    { key: 'reason', header: 'Lý do vào hàng đợi', render: item => <div className="order-moderation-badges">
      {item.matchedTerms.map(term => <Badge key={term} tone="danger">{term}</Badge>)}
      {item.categoryFlag && <Badge tone="warning">{categoryFlagLabels[item.categoryFlag]}</Badge>}
    </div> },
    { key: 'sender', header: 'Người gửi', render: item => <div className="order-moderation-cell">
      <span>{moderationPartyName(item.sender)}</span>
      {item.senderRecentRejections > 0 && <span className="order-moderation-warning">{item.senderRecentRejections} lần bị từ chối gần đây</span>}
    </div> },
    { key: 'submitted', header: 'Gửi duyệt', render: item => <span className="order-moderation-time">{formatModerationTime(item.submittedAt)}</span> },
    { key: 'actions', header: 'Thao tác', align: 'right', render: item => <Button variant="secondary" size="sm" icon={<Eye size={16} />}
      aria-label={`Xem đơn ${item.orderCode ?? item.orderId}`} onClick={() => { setNotice(''); setSelectedId(item.orderId); }}>Xem</Button> },
  ];

  return <div className="ui-stack order-moderation-page">
    <PageHeader title="Kiểm duyệt đơn hàng" eyebrow="Kiểm duyệt"
      description="Đơn có từ khóa hoặc danh mục hàng cấm/nghi ngờ chờ quyết định trước khi lên sàn."
      actions={<Button variant="secondary" icon={<RefreshCw size={17} />} disabled={list.loading || searchPending} onClick={list.reload}>Tải lại</Button>} />
    {notice && <Alert tone="success">{notice}</Alert>}

    <SectionCard title="Hàng đợi" className="ui-card-table"
      description={list.result && !list.loading && !list.error ? `${list.result.totalElements} đơn chờ kiểm duyệt, đơn gửi trước hiển thị trước` : 'Đang tải hàng đợi'}>
      <div className="order-moderation-search">
        <Input label="Tìm đơn" placeholder="Mã vận đơn hoặc tên hàng…" maxLength={100} value={keyword}
          icon={<Search size={17} />} onChange={event => setKeyword(event.target.value)} />
      </div>
      {list.error ? <div className="ui-stack">
        <ApiErrorNotice message={list.error.message} correlationId={list.error.correlationId} />
        <div><Button variant="secondary" onClick={list.reload}>Thử lại</Button></div>
      </div> : <>
        <DataTable caption="Đơn hàng chờ kiểm duyệt" columns={columns} rows={list.result?.content ?? []}
          rowKey={item => item.orderId} loading={list.loading || searchPending}
          emptyState={<EmptyState title="Không có đơn chờ kiểm duyệt" description="Đơn mới vi phạm từ khóa hoặc danh mục sẽ xuất hiện tại đây."
            icon={<PackageSearch size={24} />} />} />
        {!list.loading && !searchPending && list.result && <Pagination page={list.result.page} size={list.result.size}
          totalElements={list.result.totalElements} totalPages={list.result.totalPages}
          onPageChange={page => setQuery(previous => ({ ...previous, page }))} />}
      </>}
    </SectionCard>

    {user?.roles.includes('ADMIN') && <ModerationKeywordPanel />}

    {selectedId !== null && <OrderModerationDialog key={selectedId} orderId={selectedId}
      onClose={() => setSelectedId(null)} onChanged={message => { setNotice(message); list.reload(); }} />}
  </div>;
}
