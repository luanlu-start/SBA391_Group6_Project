import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Calculator, Gauge } from 'lucide-react';
import { trustApi } from '../api/trustApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { PageResponse } from '../types/api';
import type { CourierTrust, TrustFilter } from '../types/trust';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Select from '../components/ui/Select';
import Input from '../components/ui/Input';
import Textarea from '../components/ui/Textarea';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import Alert from '../components/ui/Alert';
import Modal from '../components/ui/Modal';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
import { formatScore, formatTime, tierLabels, tierTones } from '../features/trust/trustPresentation';
import '../styles/trust.css';

const filters: Record<TrustFilter, string> = {
  REVIEW: 'Dưới ngưỡng xem xét khóa',
  RESTRICTED: 'Dưới ngưỡng hạn chế nhận đơn',
  ALL: 'Tất cả người giao',
};

export default function AdminTrustPage() {
  const [filter, setFilter] = useState<TrustFilter>('REVIEW');
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<PageResponse<CourierTrust> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState('');
  const [recalculating, setRecalculating] = useState(false);
  const [selected, setSelected] = useState<CourierTrust | null>(null);
  const [days, setDays] = useState('3');
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [restrictError, setRestrictError] = useState<ApiError | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    void trustApi.couriers(filter, page, controller.signal)
      .then(response => { if (!controller.signal.aborted) { setResult(response.data); setError(null); } })
      .catch(cause => { if (!controller.signal.aborted) setError(getApiError(cause)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [filter, page, revision]);

  async function recalculate() {
    setRecalculating(true);
    setNotice('');
    try {
      const response = await trustApi.recalculate();
      setNotice(`Đã tính lại điểm cho ${response.data.couriers} người giao.`);
      setRevision(value => value + 1);
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setRecalculating(false);
    }
  }

  async function restrict(event: FormEvent) {
    event.preventDefault();
    if (!selected?.courier) return;
    setSaving(true);
    setRestrictError(null);
    try {
      const response = await trustApi.restrict(selected.courier.publicId, Number(days), reason.trim());
      setNotice(`Đã tạm khóa nhận đơn đến ${formatTime(response.data.restrictedUntil)}.`);
      setSelected(null);
      setReason('');
      setRevision(value => value + 1);
    } catch (cause) {
      setRestrictError(getApiError(cause));
    } finally {
      setSaving(false);
    }
  }

  const columns: TableColumn<CourierTrust>[] = [
    { key: 'courier', header: 'Người giao', render: item => <div className="trust-cell">
      <strong>{item.courier?.fullName ?? '—'}</strong><span className="trust-muted">{item.courier?.email}</span></div> },
    { key: 'score', header: 'Điểm', align: 'right', render: item => <div className="trust-cell trust-number">
      <strong>{formatScore(item.score)}</strong>{item.flaggedForReview && <Badge tone="danger">Cần xem xét</Badge>}</div> },
    { key: 'tier', header: 'Hạng', render: item => <Badge dot tone={tierTones[item.tier]}>{tierLabels[item.tier]}</Badge> },
    { key: 'stats', header: 'Đơn / khiếu nại thua / hủy', render: item =>
      `${item.completedOrders} / ${item.lostComplaints} / ${item.recentCancellations}` },
    { key: 'restricted', header: 'Khóa nhận đơn đến', render: item => item.restrictedUntil && new Date(item.restrictedUntil) > new Date()
      ? formatTime(item.restrictedUntil) : <span className="trust-muted">Không</span> },
    { key: 'actions', header: 'Thao tác', align: 'right', render: item => <Button size="sm" variant="secondary"
      disabled={!item.courier} onClick={() => { setRestrictError(null); setSelected(item); }}>Tạm khóa</Button> },
  ];

  return <div className="ui-stack trust-page">
    <PageHeader title="Uy tín người giao" eyebrow="Quản trị"
      description="Điểm và hạng được tính tự động; người giao dưới ngưỡng xem xét cần Admin quyết định khóa nhận đơn."
      actions={<Button variant="secondary" icon={<Calculator size={17} />} loading={recalculating}
        onClick={() => void recalculate()}>Tính lại ngay</Button>} />
    {notice && <Alert tone="success">{notice}</Alert>}
    <SectionCard title="Danh sách" className="ui-card-table"
      description={result && !loading && !error ? `${result.totalElements} người giao, điểm thấp xếp trước` : 'Đang tải'}>
      <div className="trust-filters">
        <Select label="Hiển thị" value={filter} onChange={event => { setFilter(event.target.value as TrustFilter); setPage(0); }}>
          {(Object.keys(filters) as TrustFilter[]).map(key => <option key={key} value={key}>{filters[key]}</option>)}
        </Select>
      </div>
      {error ? <ApiErrorNotice message={error.message} correlationId={error.correlationId} /> : <>
        <DataTable caption="Uy tín người giao" columns={columns} rows={result?.content ?? []}
          rowKey={item => item.courier?.publicId ?? `${item.score}-${item.calculatedAt}`} loading={loading}
          emptyState={<EmptyState title="Không có người giao trong nhóm này" icon={<Gauge size={24} />}
            description="Danh sách cập nhật sau mỗi lần tính điểm." />} />
        {!loading && result && <Pagination page={result.page} size={result.size} totalElements={result.totalElements}
          totalPages={result.totalPages} onPageChange={setPage} />}
      </>}
    </SectionCard>
    {selected && <Modal isOpen onClose={() => setSelected(null)} closeDisabled={saving} title="Tạm khóa nhận đơn"
      description={`${selected.courier?.fullName ?? ''} · điểm ${formatScore(selected.score)}`}>
      <form className="ui-stack" onSubmit={restrict} noValidate>
        <Input type="number" label="Số ngày" min={1} max={30} required value={days} onChange={event => setDays(event.target.value)} />
        <Textarea label="Lý do (người giao nhận được)" required maxLength={300} rows={3} value={reason}
          onChange={event => setReason(event.target.value)} />
        {restrictError && <ApiErrorNotice message={restrictError.message} correlationId={restrictError.correlationId} />}
        <div className="trust-actions">
          <Button type="button" variant="secondary" disabled={saving} onClick={() => setSelected(null)}>Hủy</Button>
          <Button type="submit" variant="danger" loading={saving}
            disabled={!reason.trim() || !(Number(days) >= 1 && Number(days) <= 30)}>Tạm khóa</Button>
        </div>
      </form>
    </Modal>}
  </div>;
}
