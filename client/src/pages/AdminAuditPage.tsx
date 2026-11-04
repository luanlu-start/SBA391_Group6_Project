import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Eye, History, RefreshCw, Search } from 'lucide-react';
import type { AuditLog, AuditQuery } from '../types/audit';
import useAuditLogs from '../features/audit/hooks/useAuditLogs';
import { auditFilterSchema, auditFiltersToQuery, emptyAuditFilters } from '../features/audit/schemas/auditFilterSchema';
import type { AuditFilterValues } from '../features/audit/schemas/auditFilterSchema';
import { formatAuditDate } from '../features/audit/auditPresentation';
import AuditLogDetail from '../features/audit/components/AuditLogDetail';
import AuditFilterChooser from '../features/audit/components/AuditFilterChooser';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Input from '../components/ui/Input';
import Select from '../components/ui/Select';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
import Alert from '../components/ui/Alert';
import Modal from '../components/ui/Modal';
import '../styles/audit.css';

const maximumPage = 100000;

export default function AdminAuditPage() {
  const [query, setQuery] = useState<AuditQuery>({ page: 0, size: 20 });
  const [selected, setSelected] = useState<AuditLog | null>(null);
  const [optionsRefresh, setOptionsRefresh] = useState(0);
  const logs = useAuditLogs(query);
  const { control, register, handleSubmit, reset, setError, setValue, watch, formState: { errors, isDirty } } = useForm<AuditFilterValues>({
    resolver: zodResolver(auditFilterSchema), defaultValues: emptyAuditFilters,
  });
  const selectedEntityType = watch('entityType');

  useEffect(() => {
    if (!logs.result || logs.loading || logs.error) return;
    const lastPage = Math.max(0, logs.result.totalPages - 1);
    if (query.page > lastPage) setQuery(previous => ({ ...previous, page: Math.min(lastPage, maximumPage) }));
  }, [logs.result, logs.loading, logs.error, query.page]);

  useEffect(() => {
    if (!logs.error) return;
    for (const field of ['actor', 'action', 'entityType', 'entityId', 'from', 'to', 'size'] as const) {
      const message = logs.error.fieldErrors[field];
      if (message) setError(field, { type: 'server', message });
    }
  }, [logs.error, setError]);

  const hasFilters = !!(query.actor || query.action || query.entityType || query.entityId || query.from || query.to);
  const unavailable = logs.error?.status === 503 || logs.error?.code === 'AUDIT_STORE_UNAVAILABLE';
  const columns: TableColumn<AuditLog>[] = [
    { key: 'at', header: 'Thời điểm (giờ Việt Nam)', render: event => <span className="audit-date">{formatAuditDate(event.at)}</span> },
    { key: 'actor', header: 'Người thực hiện', render: event => <>
      <Badge>{event.actorType}</Badge><span className="ui-table-secondary audit-token">{event.actorPublicId ?? 'Không ghi nhận'}</span>
    </> },
    { key: 'action', header: 'Thao tác', render: event => <code className="audit-token">{event.action}</code> },
    { key: 'entity', header: 'Đối tượng', render: event => <>
      <span className="ui-table-primary audit-token">{event.entityType}</span>
      <span className="ui-table-secondary audit-token">{event.entityId}</span>
    </> },
    { key: 'eventId', header: 'Mã sự kiện', render: event => <code className="audit-token">{event.eventId}</code> },
    { key: 'detail', header: 'Chi tiết', align: 'right', render: event => <Button variant="ghost" size="sm"
      aria-label={`Chi tiết sự kiện ${event.eventId}`} icon={<Eye size={16} />} onClick={() => setSelected(event)}>Xem</Button> },
  ];

  function resetFilters() {
    reset(emptyAuditFilters);
    setSelected(null);
    setQuery({ page: 0, size: 20 });
  }

  function changePage(page: number) {
    if (Number.isInteger(page) && page >= 0 && page <= maximumPage) setQuery(previous => ({ ...previous, page }));
  }

  return <div className="ui-stack audit-page">
    <PageHeader title="Nhật ký kiểm toán" eyebrow="Quản trị"
      description="Tra cứu các thao tác đã được hệ thống ghi nhận và kiểm tra dữ liệu trước, sau thay đổi."
      actions={<Button variant="secondary" disabled={logs.loading} onClick={() => {
        setOptionsRefresh(previous => previous + 1); void logs.reload();
      }}
        icon={<RefreshCw size={17} />}>Tải lại</Button>} />
    <SectionCard title="Bộ lọc tra cứu" description="Các điều kiện được kết hợp với nhau. Thời gian dùng múi giờ Việt Nam (UTC+7), bao gồm hai mốc."
      className="audit-filters">
      <form className="ui-form" noValidate onSubmit={handleSubmit(values => {
        reset(values); setSelected(null); setQuery(auditFiltersToQuery(values));
      })}>
        <div className="audit-filter-grid">
          <Controller name="actor" control={control} render={({ field }) => <AuditFilterChooser
            field="actor" label="Người thực hiện" allLabel="Tất cả người thực hiện" placeholder="Tìm theo tên hoặc email…"
            hint="Chọn người thực hiện theo tên hoặc email, không cần nhập UUID." value={field.value}
            onChange={field.onChange} onBlur={field.onBlur} error={errors.actor?.message} refreshToken={optionsRefresh} />} />
          <Controller name="action" control={control} render={({ field }) => <AuditFilterChooser
            field="action" label="Thao tác" allLabel="Tất cả thao tác" placeholder="Tìm thao tác…"
            hint="Chọn thao tác đã được ghi nhận trong nhật ký." value={field.value}
            onChange={field.onChange} onBlur={field.onBlur} error={errors.action?.message} refreshToken={optionsRefresh} />} />
          <Controller name="entityType" control={control} render={({ field }) => <AuditFilterChooser
            field="entityType" label="Loại đối tượng" allLabel="Tất cả loại đối tượng" placeholder="Tìm loại đối tượng…"
            hint="Chọn loại đối tượng đã được ghi nhận trong nhật ký." value={field.value}
            onChange={value => {
              if (value !== field.value) setValue('entityId', '', { shouldDirty: true, shouldValidate: true });
              field.onChange(value);
            }} onBlur={field.onBlur} error={errors.entityType?.message} refreshToken={optionsRefresh} />} />
          <Controller name="entityId" control={control} render={({ field }) => <AuditFilterChooser
            field="entityId" label="Đối tượng" allLabel="Tất cả đối tượng" placeholder="Tìm đối tượng…"
            hint={selectedEntityType ? 'Chọn đối tượng thuộc loại đã chọn.' : 'Có thể chọn loại đối tượng trước để thu hẹp danh sách.'}
            value={field.value} onChange={field.onChange} onBlur={field.onBlur} error={errors.entityId?.message}
            entityType={selectedEntityType || undefined} refreshToken={optionsRefresh} />} />
          <Input label="Từ thời điểm (giờ Việt Nam)" type="datetime-local" step={1}
            {...register('from')} error={errors.from?.message} />
          <Input label="Đến thời điểm (giờ Việt Nam)" type="datetime-local" step={1}
            {...register('to')} error={errors.to?.message} />
          <Select label="Số sự kiện mỗi trang" {...register('size')} error={errors.size?.message}>
            <option value="20">20 sự kiện</option><option value="50">50 sự kiện</option><option value="100">100 sự kiện</option>
          </Select>
        </div>
        {isDirty && <p className="audit-note" role="status">Bộ lọc đã thay đổi. Bấm “Tra cứu” để áp dụng các điều kiện mới.</p>}
        <div className="ui-form-actions">
          <Button variant="secondary" onClick={resetFilters}>Đặt lại bộ lọc</Button>
          <Button type="submit" icon={<Search size={17} />}>Tra cứu</Button>
        </div>
      </form>
    </SectionCard>
    <SectionCard title="Các sự kiện đã ghi nhận" className="ui-card-table audit-results"
      description={!logs.loading && !logs.error && logs.result
        ? `${logs.result.totalElements} sự kiện${hasFilters ? ' khớp bộ lọc' : ''} · Mới nhất trước` : 'Mới nhất trước'}>
      {logs.error ? <div className="ui-card-body ui-stack">
        {unavailable && <Alert tone="warning" title="Chưa thể đọc nhật ký kiểm toán">
          Kho nhật ký tạm thời không khả dụng. Thử tải lại sau; kết quả trống chỉ xuất hiện khi tra cứu thành công.
        </Alert>}
        <ApiErrorNotice message={logs.error.message} correlationId={logs.error.correlationId} />
        <div><Button variant="secondary" onClick={() => void logs.reload()}>Thử lại</Button></div>
      </div> : <>
        <DataTable caption="Danh sách sự kiện kiểm toán" columns={columns} rows={logs.result?.content ?? []}
          rowKey={event => event.eventId} loading={logs.loading}
          emptyState={<EmptyState title={hasFilters ? 'Không có sự kiện khớp bộ lọc' : 'Chưa có sự kiện kiểm toán'}
            description={hasFilters ? 'Thay đổi thao tác, đối tượng hoặc mở rộng khoảng thời gian.'
              : 'Các sự kiện sẽ xuất hiện sau khi hệ thống ghi nhận thành công.'} icon={<History size={24} />}
            action={hasFilters ? <Button variant="secondary" onClick={resetFilters}>Xóa bộ lọc</Button> : undefined} />} />
        {!logs.loading && logs.result && <Pagination page={logs.result.page} size={logs.result.size}
          totalElements={logs.result.totalElements} totalPages={Math.min(logs.result.totalPages, maximumPage + 1)}
          onPageChange={changePage} />}
        {!logs.loading && logs.result && logs.result.totalPages > maximumPage + 1 && <p className="audit-note audit-page-limit">
          Hãy thu hẹp bộ lọc để xem các sự kiện ngoài giới hạn phân trang.
        </p>}
      </>}
    </SectionCard>
    <Modal isOpen={!!selected} onClose={() => setSelected(null)} title="Chi tiết sự kiện kiểm toán" size="lg"
      description="Dữ liệu tại thời điểm ghi nhận sự kiện.">
      {selected && <AuditLogDetail event={selected} />}
    </Modal>
  </div>;
}
