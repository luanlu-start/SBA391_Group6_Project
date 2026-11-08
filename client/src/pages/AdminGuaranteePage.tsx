import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { HandCoins, RefreshCw } from 'lucide-react';
import { guaranteeApi } from '../api/guaranteeApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { PageResponse } from '../types/api';
import type { Compensation, CompensationFilter, CompensationStatus, GuaranteeFund } from '../types/guarantee';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Select from '../components/ui/Select';
import Input from '../components/ui/Input';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import Alert from '../components/ui/Alert';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
import CompensationReviewDialog from '../features/guarantee/CompensationReviewDialog';
import { formatMoney, formatTime, personName } from '../features/complaints/complaintPresentation';
import '../styles/guarantee.css';

const statusLabels: Record<CompensationStatus, string> = {
  PENDING_REVIEW: 'Chờ duyệt',
  QUEUED: 'Chờ quỹ',
  PAID: 'Đã chi',
  REJECTED: 'Từ chối',
};
const statusTones: Record<CompensationStatus, 'warning' | 'info' | 'success' | 'neutral'> = {
  PENDING_REVIEW: 'warning',
  QUEUED: 'info',
  PAID: 'success',
  REJECTED: 'neutral',
};

export default function AdminGuaranteePage() {
  const [fund, setFund] = useState<GuaranteeFund | null>(null);
  const [filter, setFilter] = useState<CompensationFilter>('PENDING_REVIEW');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<PageResponse<Compensation> | null>(null);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState('');
  const [selected, setSelected] = useState<Compensation | null>(null);
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [contributing, setContributing] = useState(false);
  const [contributionError, setContributionError] = useState<ApiError | null>(null);

  const refresh = useCallback(() => setRevision(value => value + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    Promise.all([guaranteeApi.overview(controller.signal), guaranteeApi.compensations(filter, page, controller.signal)])
      .then(([overview, list]) => {
        if (controller.signal.aborted) return;
        setFund(overview.data);
        setItems(list.data);
        setError(null);
      })
      .catch(cause => { if (!controller.signal.aborted) setError(getApiError(cause)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [filter, page, revision]);

  async function contribute(event: FormEvent) {
    event.preventDefault();
    setContributing(true);
    setContributionError(null);
    try {
      const response = await guaranteeApi.contribute(Number(amount), note.trim());
      setFund(response.data);
      setAmount('');
      setNote('');
      setNotice('Đã ghi nhận khoản trích vào quỹ và chi các khoản đủ điều kiện trong hàng đợi.');
      refresh();
    } catch (cause) {
      setContributionError(getApiError(cause));
    } finally {
      setContributing(false);
    }
  }

  const shortfall = fund ? Math.max(0, fund.queuedAmount - fund.balance) : 0;
  const columns: TableColumn<Compensation>[] = [
    { key: 'complaint', header: 'Khiếu nại', render: item => <div className="guarantee-cell">
      {item.complaintId ? <Link to={`/mod/complaints/${item.complaintId}`}><strong>{item.complaintCode}</strong></Link> : '—'}
      <span className="guarantee-muted">Đơn {item.orderCode ?? '—'} · phán quyết {formatTime(item.ruledAt)}</span></div> },
    { key: 'beneficiary', header: 'Người nhận', render: item => <div className="guarantee-cell">
      <span>{personName(item.beneficiary)}</span><span className="guarantee-muted">{item.beneficiary?.email}</span></div> },
    { key: 'amount', header: 'Số tiền', align: 'right', render: item => <div className="guarantee-cell guarantee-amount">
      <strong>{formatMoney(item.approvedAmount ?? item.requestedAmount)}</strong>
      {item.approvedAmount !== null && item.approvedAmount !== item.requestedAmount &&
        <span className="guarantee-muted">Đề nghị {formatMoney(item.requestedAmount)}</span>}</div> },
    { key: 'status', header: 'Trạng thái', render: item => <div className="guarantee-cell">
      <Badge dot tone={statusTones[item.status]}>{statusLabels[item.status]}{item.queuePosition ? ` · thứ ${item.queuePosition}` : ''}</Badge>
      {item.note && <span className="guarantee-muted">{item.note}</span>}</div> },
    { key: 'actions', header: 'Thao tác', align: 'right', render: item => item.status === 'PENDING_REVIEW'
      ? <Button size="sm" variant="secondary" onClick={() => { setNotice(''); setSelected(item); }}>Xét duyệt</Button>
      : <span className="guarantee-muted">{formatTime(item.paidAt ?? item.reviewedAt)}</span> },
  ];

  return <div className="ui-stack guarantee-page">
    <PageHeader title="Quỹ bảo đảm" eyebrow="Quản trị"
      description="Chi bồi thường cho đơn có bảo hiểm theo phán quyết khiếu nại. Khi quỹ không đủ, khoản đã duyệt được chi theo thứ tự phán quyết."
      actions={<Button variant="secondary" icon={<RefreshCw size={17} />} disabled={loading} onClick={refresh}>Tải lại</Button>} />
    {notice && <Alert tone="success">{notice}</Alert>}
    {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
    {fund && <div className="guarantee-tiles">
      <div className="guarantee-tile"><span className="guarantee-label">Số dư quỹ</span><strong>{formatMoney(fund.balance)}</strong></div>
      <div className="guarantee-tile"><span className="guarantee-label">Chờ duyệt</span><strong>{formatMoney(fund.pendingAmount)}</strong>
        <span className="guarantee-muted">{fund.pendingCount} đề nghị</span></div>
      <div className="guarantee-tile"><span className="guarantee-label">Đang chờ quỹ</span><strong>{formatMoney(fund.queuedAmount)}</strong>
        <span className="guarantee-muted">{fund.queuedCount} khoản</span></div>
      <div className="guarantee-tile"><span className="guarantee-label">Đã chi</span><strong>{formatMoney(fund.paidAmount)}</strong>
        <span className="guarantee-muted">{fund.paidCount} khoản</span></div>
    </div>}
    {shortfall > 0 && <Alert tone="warning" title="Quỹ chưa đủ để chi hết hàng đợi">
      Cần thêm {formatMoney(shortfall)}. Các khoản sẽ tự chi theo thứ tự phán quyết khi quỹ được bổ sung.</Alert>}

    <div className="guarantee-layout">
      <SectionCard title="Đề nghị bồi thường" className="ui-card-table"
        description={items && !loading ? `${items.totalElements} khoản` : 'Đang tải'}>
        <div className="guarantee-filters">
          <Select label="Trạng thái" value={filter} onChange={event => { setFilter(event.target.value as CompensationFilter); setPage(0); }}>
            {(Object.keys(statusLabels) as CompensationStatus[]).map(key => <option key={key} value={key}>{statusLabels[key]}</option>)}
            <option value="ALL">Tất cả</option>
          </Select>
        </div>
        <DataTable caption="Đề nghị bồi thường" columns={columns} rows={items?.content ?? []} rowKey={item => item.id} loading={loading}
          emptyState={<EmptyState title="Không có khoản bồi thường" icon={<HandCoins size={24} />}
            description="Phán quyết có bồi thường sẽ xuất hiện tại đây sau khi hết hạn kháng nghị." />} />
        {!loading && items && <Pagination page={items.page} size={items.size} totalElements={items.totalElements}
          totalPages={items.totalPages} onPageChange={setPage} />}
      </SectionCard>

      <SectionCard title="Trích vào quỹ" description="Ghi nhận phí bảo hiểm và phần hoa hồng trích theo FUND_SHARE.">
        <form className="ui-stack" onSubmit={contribute} noValidate>
          <Input type="number" label="Số tiền (₫)" min={1} step={1000} required value={amount} onChange={event => setAmount(event.target.value)} />
          <Input label="Nội dung" required maxLength={300} placeholder="Ví dụ: Trích phí bảo hiểm tháng 10/2026" value={note}
            onChange={event => setNote(event.target.value)} />
          {contributionError && <ApiErrorNotice message={contributionError.message} correlationId={contributionError.correlationId} />}
          <div><Button type="submit" loading={contributing} disabled={!(Number(amount) > 0) || !note.trim()}>Ghi nhận</Button></div>
        </form>
      </SectionCard>
    </div>

    <SectionCard title="Biến động gần đây" className="ui-card-table">
      {fund && fund.movements.length === 0 ? <p className="guarantee-muted">Quỹ chưa có giao dịch.</p>
        : <ul className="guarantee-movements">{fund?.movements.map(movement => <li key={movement.transactionCode}>
          <span className={movement.type === 'GUARANTEE_FUND_PAYOUT' ? 'guarantee-out' : 'guarantee-in'}>
            {movement.type === 'GUARANTEE_FUND_PAYOUT' ? '−' : '+'}{formatMoney(movement.amount)}</span>
          <span>{movement.description}</span>
          <span className="guarantee-muted">{formatTime(movement.createdAt)}</span>
        </li>)}</ul>}
    </SectionCard>

    {selected && fund && <CompensationReviewDialog item={selected} balance={fund.balance} onClose={() => setSelected(null)}
      onDone={message => { setSelected(null); setNotice(message); refresh(); }} />}
  </div>;
}
