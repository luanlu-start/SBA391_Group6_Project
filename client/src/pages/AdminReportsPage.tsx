import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Download } from 'lucide-react';
import { reportApi, saveReport } from '../api/reportApi';
import type { CourierRevenue, ReportType } from '../api/reportApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Alert from '../components/ui/Alert';
import EmptyState from '../components/ui/EmptyState';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import { formatMoney, formatNumber, isoDay, lastDays, previousMonth } from '../features/reports/reportFormat';
import '../styles/admin-reports.css';

const columns: TableColumn<CourierRevenue>[] = [
  { key: 'courier', header: 'Người giao', render: row => <div className="admin-report-cell">
    <strong>{row.fullName ?? 'Chưa cập nhật họ tên'}</strong>
    <span className="admin-report-muted">{row.email ?? row.courierPublicId ?? '—'}</span>
  </div> },
  { key: 'orders', header: 'Đơn giải ngân', align: 'right', render: row => formatNumber(row.releasedOrders) },
  { key: 'gross', header: 'Doanh thu gộp', align: 'right', render: row => formatMoney(row.grossAmount) },
  { key: 'commission', header: 'Hoa hồng', align: 'right', render: row => formatMoney(row.commission) },
  { key: 'withholding', header: 'Khấu trừ thuế', align: 'right', render: row => formatMoney(row.withholding) },
  { key: 'net', header: 'Thu nhập ròng', align: 'right', render: row => <strong>{formatMoney(row.netIncome)}</strong> },
];

export default function AdminReportsPage() {
  const [month, setMonth] = useState(previousMonth);
  const [shownMonth, setShownMonth] = useState(month);
  const [rows, setRows] = useState<CourierRevenue[]>([]);
  const [loading, setLoading] = useState(true);
  const [orders, setOrders] = useState(() => lastDays(30));
  const [exporting, setExporting] = useState<ReportType | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    void reportApi.courierRevenue(shownMonth, controller.signal)
      .then(response => { if (!controller.signal.aborted) { setRows(response.data); setError(null); } })
      .catch(cause => { if (!controller.signal.aborted) setError(getApiError(cause)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [shownMonth]);

  function showMonth(event: FormEvent) {
    event.preventDefault();
    setShownMonth(month);
  }

  async function download(type: ReportType, params: { from?: string; to?: string; month?: string }) {
    setExporting(type);
    setNotice(null);
    try {
      const file = await reportApi.export(type, params);
      saveReport(file);
      setNotice(`Đã tải ${file.fileName}.`);
      setError(null);
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setExporting(null);
    }
  }

  function exportOrders(event: FormEvent) {
    event.preventDefault();
    void download('ORDERS', orders);
  }

  const total = rows.reduce((sum, row) => ({
    releasedOrders: sum.releasedOrders + row.releasedOrders, grossAmount: sum.grossAmount + row.grossAmount,
    commission: sum.commission + row.commission, withholding: sum.withholding + row.withholding, netIncome: sum.netIncome + row.netIncome,
  }), { releasedOrders: 0, grossAmount: 0, commission: 0, withholding: 0, netIncome: 0 });

  return <div className="ui-stack">
    <PageHeader title="Báo cáo" eyebrow="Quản trị"
      description="Doanh thu người giao theo tháng và dữ liệu đơn hàng xuất CSV UTF-8, mở trực tiếp bằng Excel." />
    {notice && <Alert tone="success">{notice}</Alert>}
    {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
    <SectionCard title="Doanh thu người giao" className="ui-card-table"
      description="Tổng tiền ký quỹ đã giải ngân trong tháng; hoa hồng theo tỷ lệ phí khóa trên đơn, khấu trừ theo tham số WITHHOLD."
      actions={<Button size="sm" variant="secondary" icon={<Download size={15} />} loading={exporting === 'COURIER_REVENUE'}
        disabled={exporting !== null || rows.length === 0} onClick={() => void download('COURIER_REVENUE', { month: shownMonth })}>Xuất CSV</Button>}>
      <form className="admin-report-filters" onSubmit={showMonth}>
        <Input type="month" label="Tháng" value={month} max={isoDay(new Date()).slice(0, 7)} required
          pattern="\d{4}-\d{2}" placeholder="yyyy-mm"
          onChange={event => setMonth(event.target.value)} />
        <Button type="submit" disabled={loading || !month}>Xem báo cáo</Button>
      </form>
      <DataTable caption={`Doanh thu người giao tháng ${shownMonth}`} columns={columns} rows={rows} loading={loading}
        rowKey={row => row.courierPublicId ?? `${row.email}-${row.grossAmount}`}
        emptyState={<EmptyState title="Chưa có khoản giải ngân"
          description="Không có đơn nào được giải ngân cho người giao trong tháng này." />} />
      {rows.length > 0 && <dl className="admin-report-summary">
        <div><dt>Người giao</dt><dd>{formatNumber(rows.length)}</dd></div>
        <div><dt>Đơn giải ngân</dt><dd>{formatNumber(total.releasedOrders)}</dd></div>
        <div><dt>Doanh thu gộp</dt><dd>{formatMoney(total.grossAmount)}</dd></div>
        <div><dt>Hoa hồng</dt><dd>{formatMoney(total.commission)}</dd></div>
        <div><dt>Khấu trừ thuế</dt><dd>{formatMoney(total.withholding)}</dd></div>
        <div><dt>Thu nhập ròng</dt><dd>{formatMoney(total.netIncome)}</dd></div>
      </dl>}
    </SectionCard>
    <SectionCard title="Xuất danh sách đơn hàng" description="Mỗi dòng là một đơn tạo trong kỳ: mã đơn, trạng thái, giá đề xuất/chốt và thời điểm kết thúc.">
      <form className="admin-report-filters" onSubmit={exportOrders}>
        <Input type="date" label="Từ ngày" value={orders.from} max={orders.to} required
          onChange={event => setOrders(current => ({ ...current, from: event.target.value }))} />
        <Input type="date" label="Đến ngày" value={orders.to} min={orders.from} max={isoDay(new Date())} required
          onChange={event => setOrders(current => ({ ...current, to: event.target.value }))} />
        <Button type="submit" icon={<Download size={17} />} loading={exporting === 'ORDERS'} disabled={exporting !== null}>Xuất CSV</Button>
      </form>
    </SectionCard>
  </div>;
}
