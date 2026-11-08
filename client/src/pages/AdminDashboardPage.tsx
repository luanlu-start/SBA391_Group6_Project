import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Download, RefreshCw } from 'lucide-react';
import { reportApi, saveReport } from '../api/reportApi';
import type { DailyPoint, Dashboard } from '../api/reportApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Badge from '../components/ui/Badge';
import LoadingSpinner from '../components/common/LoadingSpinner';
import { ORDER_STATUS_MAP } from '../types/order';
import type { OrderStatus } from '../types/order';
import { formatMoney, formatNumber, formatPercent, isoDay, lastDays } from '../features/reports/reportFormat';
import '../styles/admin-reports.css';

const presets = [7, 30, 90];

function DailyChart({ points }: { points: DailyPoint[] }) {
  const peakOrders = Math.max(1, ...points.map(point => point.ordersCreated));
  const peakAmount = Math.max(1, ...points.map(point => point.escrowReleasedAmount));
  const dense = points.length > 45;
  return <div className="admin-report-chart" role="img"
    aria-label={`Biểu đồ ${points.length} ngày: số đơn tạo mới và tiền giải ngân theo ngày`}>
    <div className="admin-report-legend" aria-hidden="true">
      <span><i className="admin-report-swatch admin-report-swatch-orders" />Đơn tạo mới</span>
      <span><i className="admin-report-swatch admin-report-swatch-amount" />Tiền giải ngân</span>
    </div>
    <div className="admin-report-bars" aria-hidden="true">
      {points.map(point => <div key={point.date} className="admin-report-day"
        title={`${point.date}: ${formatNumber(point.ordersCreated)} đơn · ${formatMoney(point.escrowReleasedAmount)}`}>
        <div className="admin-report-pair">
          <span className="admin-report-bar admin-report-bar-orders" style={{ height: `${(point.ordersCreated / peakOrders) * 100}%` }} />
          <span className="admin-report-bar admin-report-bar-amount" style={{ height: `${(point.escrowReleasedAmount / peakAmount) * 100}%` }} />
        </div>
        {!dense && <span className="admin-report-tick">{point.date.slice(8)}/{point.date.slice(5, 7)}</span>}
      </div>)}
    </div>
  </div>;
}

export default function AdminDashboardPage() {
  const [range, setRange] = useState(() => lastDays(30));
  const [draft, setDraft] = useState(range);
  const [data, setData] = useState<Dashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const load = useCallback((signal?: AbortSignal) => {
    setLoading(true);
    void reportApi.dashboard(range.from, range.to, signal)
      .then(response => { if (!signal?.aborted) { setData(response.data); setError(null); } })
      .catch(cause => { if (!signal?.aborted) setError(getApiError(cause)); })
      .finally(() => { if (!signal?.aborted) setLoading(false); });
  }, [range]);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal);
    return () => controller.abort();
  }, [load]);

  function apply(event: FormEvent) {
    event.preventDefault();
    setRange({ ...draft });
  }

  function preset(days: number) {
    const next = lastDays(days);
    setDraft(next);
    setRange(next);
  }

  async function download() {
    setExporting(true);
    try {
      saveReport(await reportApi.export('DASHBOARD', range));
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setExporting(false);
    }
  }

  const statuses = data ? Object.entries(data.ordersByStatus).sort((a, b) => b[1] - a[1]) : [];
  const tiles = data ? [
    { label: 'Đơn tạo mới', value: formatNumber(data.ordersCreated), note: `Tỷ lệ hủy ${formatPercent(data.cancellationRate)}` },
    { label: 'Tiền giải ngân', value: formatMoney(data.escrowReleasedAmount), note: `${formatNumber(data.escrowReleasedCount)} lần giải ngân ký quỹ` },
    { label: 'Hoa hồng ước tính', value: formatMoney(data.estimatedCommission), note: 'Theo tỷ lệ phí khóa trên từng đơn' },
    { label: 'Nạp ví', value: formatMoney(data.topUpAmount), note: `Yêu cầu rút ${formatMoney(data.withdrawalRequestedAmount)}` },
    { label: 'Người dùng mới', value: formatNumber(data.newUsers), note: `${formatNumber(data.newCouriers)} người giao đăng ký mới` },
    { label: 'Người giao hoạt động', value: formatNumber(data.activeCouriers), note: 'Có cập nhật đơn trong kỳ' },
  ] : [];

  return <div className="ui-stack">
    <PageHeader title="Tổng quan điều hành" eyebrow="Quản trị"
      description="Số liệu đơn hàng, dòng tiền và người dùng trong kỳ, tính theo ngày giờ Việt Nam."
      actions={<>
        <Button variant="secondary" icon={<RefreshCw size={17} />} disabled={loading} onClick={() => load()}>Tải lại</Button>
        <Button variant="secondary" icon={<Download size={17} />} loading={exporting} disabled={!data} onClick={() => void download()}>Xuất CSV</Button>
      </>} />
    <SectionCard>
      <form className="admin-report-filters" onSubmit={apply}>
        <Input type="date" label="Từ ngày" value={draft.from} max={draft.to} required
          onChange={event => setDraft(current => ({ ...current, from: event.target.value }))} />
        <Input type="date" label="Đến ngày" value={draft.to} min={draft.from} max={isoDay(new Date())} required
          onChange={event => setDraft(current => ({ ...current, to: event.target.value }))} />
        <Button type="submit" disabled={loading}>Xem số liệu</Button>
        <div className="admin-report-presets" role="group" aria-label="Chọn nhanh khoảng thời gian">
          {presets.map(days => <Button key={days} type="button" size="sm" variant="ghost" onClick={() => preset(days)}>{days} ngày</Button>)}
        </div>
      </form>
    </SectionCard>
    {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
    {!data ? loading && <LoadingSpinner text="Đang tổng hợp số liệu…" /> : <>
      <div className={`admin-report-tiles${loading ? ' is-refreshing' : ''}`}>
        {tiles.map(tile => <div key={tile.label} className="admin-report-tile">
          <span className="admin-report-label">{tile.label}</span>
          <strong>{tile.value}</strong>
          <span className="admin-report-muted">{tile.note}</span>
        </div>)}
      </div>
      <SectionCard title="Diễn biến theo ngày" description={`Từ ${data.from} đến ${data.to}`}>
        <DailyChart points={data.daily} />
      </SectionCard>
      <SectionCard title="Đơn theo trạng thái" description="Đơn được tạo trong kỳ, xếp theo số lượng">
        {statuses.length === 0 ? <p className="admin-report-muted">Không có đơn nào trong kỳ.</p>
          : <ul className="admin-report-statuses">{statuses.map(([status, count]) => {
            const config = ORDER_STATUS_MAP[status as OrderStatus];
            return <li key={status}>
              <Badge dot tone={config?.tone ?? 'neutral'}>{config?.label ?? status}</Badge>
              <span className="admin-report-meter"><span style={{ width: `${(count / data.ordersCreated) * 100}%` }} /></span>
              <strong>{formatNumber(count)}</strong>
            </li>;
          })}</ul>}
      </SectionCard>
    </>}
  </div>;
}
