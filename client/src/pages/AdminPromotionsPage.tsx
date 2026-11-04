import { useEffect, useRef, useState } from 'react';
import { Pencil, Play, Plus, RefreshCw, Search, TicketPercent, Pause } from 'lucide-react';
import { promotionApi } from '../api/promotionApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { Promotion, PromotionEffectiveStatus, PromotionQuery } from '../types/promotion';
import usePromotions from '../features/promotions/hooks/usePromotions';
import PromotionEditor from '../features/promotions/components/PromotionEditor';
import { formatPromotionValue, promotionStatusLabels, promotionStatusTones, promotionToWrite } from '../features/promotions/promotionPresentation';
import { formatAuditDate } from '../features/audit/auditPresentation';
import ApiErrorNotice from '../components/ApiErrorNotice';
import LoadingSpinner from '../components/common/LoadingSpinner';
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
import Modal from '../components/ui/Modal';
import '../styles/promotions.css';

const maximumPage = 100000;

export default function AdminPromotionsPage() {
  const [query, setQuery] = useState<PromotionQuery>({ page: 0, size: 20 });
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<PromotionEffectiveStatus | ''>('');
  const [size, setSize] = useState('20');
  const list = usePromotions(query);
  const [notice, setNotice] = useState('');
  const [editorOpen, setEditorOpen] = useState(false);
  const [editor, setEditor] = useState<Promotion | null>(null);
  const [editorKey, setEditorKey] = useState(0);
  const [editorLoading, setEditorLoading] = useState(false);
  const [editorBusy, setEditorBusy] = useState(false);
  const [editorError, setEditorError] = useState<ApiError | null>(null);
  const [confirmation, setConfirmation] = useState<Promotion | null>(null);
  const [toggleBusy, setToggleBusy] = useState(false);
  const [toggleError, setToggleError] = useState<ApiError | null>(null);
  const [toggleConflict, setToggleConflict] = useState(false);
  const detailsSequence = useRef(0);
  const detailsRequest = useRef<AbortController | null>(null);
  const toggling = useRef(false);
  const mounted = useRef(true);
  const busy = editorBusy || toggleBusy;
  const dirtyFilters = keyword.trim() !== (query.q ?? '') || status !== (query.status ?? '') || Number(size) !== query.size;

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; detailsSequence.current += 1; detailsRequest.current?.abort(); };
  }, []);

  useEffect(() => {
    if (!list.result || list.loading || list.error) return;
    const lastPage = Math.max(0, list.result.totalPages - 1);
    if (query.page > lastPage) setQuery(previous => ({ ...previous, page: Math.min(lastPage, maximumPage) }));
  }, [list.result, list.loading, list.error, query.page]);

  function resetFilters() {
    setKeyword(''); setStatus(''); setSize('20'); setQuery({ page: 0, size: 20 });
  }

  function closeEditor() {
    if (editorBusy) return;
    detailsSequence.current += 1; detailsRequest.current?.abort();
    setEditorOpen(false); setEditorLoading(false); setEditorError(null);
  }

  function createPromotion() {
    detailsSequence.current += 1; detailsRequest.current?.abort();
    setEditor(null); setEditorLoading(false); setEditorError(null); setEditorBusy(false);
    setEditorKey(previous => previous + 1); setEditorOpen(true); setNotice('');
  }

  async function editPromotion(promotion: Promotion) {
    const sequence = ++detailsSequence.current;
    detailsRequest.current?.abort();
    const controller = new AbortController(); detailsRequest.current = controller;
    setEditor(promotion); setEditorLoading(true); setEditorError(null); setEditorOpen(true); setNotice('');
    try {
      const response = await promotionApi.get(promotion.id, controller.signal);
      if (mounted.current && sequence === detailsSequence.current) {
        setEditor(response.data); setEditorKey(previous => previous + 1);
      }
    } catch (cause) {
      if (mounted.current && sequence === detailsSequence.current && !controller.signal.aborted) setEditorError(getApiError(cause));
    } finally {
      if (mounted.current && sequence === detailsSequence.current) setEditorLoading(false);
    }
  }

  async function saved(promotion: Promotion) {
    setEditorBusy(false); setEditorOpen(false);
    setNotice(`Đã ${editor ? 'cập nhật' : 'tạo'} khuyến mãi ${promotion.code}.`);
    await list.reload();
  }

  function confirmStatus(promotion: Promotion) {
    setConfirmation(promotion); setToggleError(null); setToggleConflict(false); setNotice('');
  }

  async function toggleStatus() {
    if (!confirmation || toggling.current || toggleConflict) return;
    toggling.current = true; setToggleBusy(true); setToggleError(null);
    const nextStatus = confirmation.status === 'PAUSED' ? 'ACTIVE' : 'PAUSED';
    try {
      const response = await promotionApi.update(confirmation.id, {
        ...promotionToWrite(confirmation), status: nextStatus, expectedVersion: confirmation.version,
      });
      if (!mounted.current) return;
      setNotice(`Đã ${nextStatus === 'PAUSED' ? 'tạm dừng' : 'kích hoạt'} khuyến mãi ${response.data.code}.`);
      setConfirmation(null); await list.reload();
    } catch (cause) {
      if (!mounted.current) return;
      const error = getApiError(cause); setToggleError(error);
      if (error.status === 409) { setToggleConflict(true); await list.reload(); }
    } finally {
      toggling.current = false;
      if (mounted.current) setToggleBusy(false);
    }
  }

  const columns: TableColumn<Promotion>[] = [
    { key: 'code', header: 'Khuyến mãi', render: promotion => <>
      <strong className="ui-table-primary promotion-code">{promotion.code}</strong>
      <span className="ui-table-secondary">{promotion.type === 'FIXED_AMOUNT' ? 'Giảm số tiền cố định' : 'Giảm theo phần trăm'}</span>
    </> },
    { key: 'value', header: 'Giá trị giảm', render: promotion => <strong className="promotion-value">{formatPromotionValue(promotion)}</strong> },
    { key: 'validity', header: 'Thời hạn (giờ Việt Nam)', render: promotion => <>
      <span className="promotion-date">Từ {formatAuditDate(promotion.validFrom)}</span>
      <span className="ui-table-secondary promotion-date">Đến {formatAuditDate(promotion.validTo)}</span>
    </> },
    { key: 'usage', header: 'Lượt sử dụng', render: promotion => <>
      <span>{promotion.usedCount.toLocaleString('vi-VN')} / {promotion.usageLimit.toLocaleString('vi-VN')}</span>
      <span className="ui-table-secondary">{promotion.target === 'ALL' ? 'Tất cả người dùng' : `${promotion.targetUserIds.length} người được chọn`}</span>
    </> },
    { key: 'status', header: 'Trạng thái', render: promotion => <Badge dot tone={promotionStatusTones[promotion.effectiveStatus]}>
      {promotionStatusLabels[promotion.effectiveStatus]}</Badge> },
    { key: 'actions', header: 'Thao tác', align: 'right', render: promotion => <div className="ui-table-actions">
      <Button variant="ghost" size="sm" disabled={busy} aria-label={`Sửa ${promotion.code}`} icon={<Pencil size={16} />}
        onClick={() => void editPromotion(promotion)}>Sửa</Button>
      <Button variant="secondary" size="sm" disabled={busy} aria-label={`${promotion.status === 'PAUSED' ? 'Kích hoạt' : 'Tạm dừng'} ${promotion.code}`}
        icon={promotion.status === 'PAUSED' ? <Play size={15} /> : <Pause size={15} />} onClick={() => confirmStatus(promotion)}>
        {promotion.status === 'PAUSED' ? 'Kích hoạt' : 'Tạm dừng'}</Button>
    </div> },
  ];

  return <div className="ui-stack promotion-page">
    <PageHeader title="Quản lý khuyến mãi" eyebrow="Quản trị"
      description="Tạo ưu đãi, điều chỉnh thời hạn và đối tượng áp dụng, theo dõi số lượt đã sử dụng."
      actions={<div className="ui-row promotion-header-actions">
        <Button variant="secondary" disabled={busy || list.loading} icon={<RefreshCw size={17} />} onClick={() => void list.reload()}>Tải lại</Button>
        <Button disabled={busy} icon={<Plus size={17} />} onClick={createPromotion}>Tạo khuyến mãi</Button>
      </div>} />
    {notice && <Alert tone="success">{notice}</Alert>}
    <SectionCard title="Bộ lọc khuyến mãi">
      <form className="ui-form" onSubmit={event => {
        event.preventDefault(); setQuery({ q: keyword.trim() || undefined, status: status || undefined, page: 0, size: Number(size) });
      }}>
        <div className="promotion-filter-grid">
          <Input label="Tìm mã khuyến mãi" placeholder="Nhập một phần mã…" maxLength={50} icon={<Search size={17} />}
            value={keyword} onChange={event => setKeyword(event.target.value)} />
          <Select label="Trạng thái" value={status} onChange={event => setStatus(event.target.value as PromotionEffectiveStatus | '')}>
            <option value="">Tất cả trạng thái</option>
            {Object.entries(promotionStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </Select>
          <Select label="Số mã mỗi trang" value={size} onChange={event => setSize(event.target.value)}>
            <option value="20">20 mã</option><option value="50">50 mã</option><option value="100">100 mã</option>
          </Select>
        </div>
        {dirtyFilters && <p className="promotion-muted" role="status">Bộ lọc đã thay đổi. Bấm “Tra cứu” để áp dụng.</p>}
        <div className="ui-form-actions"><Button variant="secondary" onClick={resetFilters}>Đặt lại bộ lọc</Button>
          <Button type="submit" icon={<Search size={17} />}>Tra cứu</Button></div>
      </form>
    </SectionCard>
    <SectionCard title="Danh sách khuyến mãi" className="ui-card-table"
      description={list.result && !list.loading && !list.error ? `${list.result.totalElements} khuyến mãi · Mới tạo trước` : 'Mới tạo trước'}>
      {list.error ? <div className="ui-card-body ui-stack">
        <ApiErrorNotice message={list.error.message} correlationId={list.error.correlationId} />
        <div><Button variant="secondary" onClick={() => void list.reload()}>Thử lại</Button></div>
      </div> : <>
        <DataTable caption="Danh sách khuyến mãi" columns={columns} rows={list.result?.content ?? []} rowKey={promotion => promotion.id}
          loading={list.loading} emptyState={<EmptyState title={query.q || query.status ? 'Không có khuyến mãi khớp bộ lọc' : 'Chưa có khuyến mãi'}
            description={query.q || query.status ? 'Thử mã hoặc trạng thái khác.' : 'Tạo khuyến mãi đầu tiên để quản lý ưu đãi.'}
            icon={<TicketPercent size={24} />} action={<Button variant="secondary" onClick={query.q || query.status ? resetFilters : createPromotion}>
              {query.q || query.status ? 'Xóa bộ lọc' : 'Tạo khuyến mãi'}</Button>} />} />
        {!list.loading && list.result && <Pagination page={list.result.page} size={list.result.size}
          totalElements={list.result.totalElements} totalPages={Math.min(list.result.totalPages, maximumPage + 1)}
          onPageChange={page => setQuery(previous => ({ ...previous, page }))} disabled={busy} />}
        {!list.loading && list.result && list.result.totalPages > maximumPage + 1 && <p className="promotion-page-limit promotion-muted">
          Thu hẹp bộ lọc để xem các khuyến mãi ngoài giới hạn phân trang.</p>}
      </>}
    </SectionCard>
    <Modal isOpen={editorOpen} onClose={closeEditor} closeDisabled={editorBusy} size="lg"
      title={editor ? `Chỉnh sửa: ${editor.code}` : 'Tạo khuyến mãi'} description="Thời gian nhập và hiển thị theo giờ Việt Nam (UTC+7).">
      {editorLoading ? <LoadingSpinner text="Đang tải khuyến mãi…" /> : editorError ? <div className="ui-stack">
        <ApiErrorNotice message={editorError.message} correlationId={editorError.correlationId} />
        {editor && <div><Button variant="secondary" onClick={() => void editPromotion(editor)}>Thử lại</Button></div>}
      </div> : <PromotionEditor key={editorKey} promotion={editor} onCancel={closeEditor} onSaved={saved}
        onReload={() => { if (editor) void editPromotion(editor); }} onConflict={() => { void list.reload(); }} onBusyChange={setEditorBusy} />}
    </Modal>
    <Modal isOpen={!!confirmation} onClose={() => { if (!toggleBusy) setConfirmation(null); }} closeDisabled={toggleBusy}
      title={`${confirmation?.status === 'PAUSED' ? 'Kích hoạt' : 'Tạm dừng'} khuyến mãi?`}>
      {confirmation && <div className="ui-stack">
        <p>Khuyến mãi <strong className="promotion-code">{confirmation.code}</strong> sẽ được {confirmation.status === 'PAUSED' ? 'kích hoạt' : 'tạm dừng'}.</p>
        {confirmation.status === 'PAUSED' && <p className="promotion-muted">Khuyến mãi chỉ hoạt động trong thời hạn áp dụng và khi còn lượt sử dụng.</p>}
        {toggleError && <ApiErrorNotice message={toggleError.message} correlationId={toggleError.correlationId} />}
        {toggleConflict && <Alert tone="warning">Khuyến mãi đã thay đổi. Đóng hộp thoại và kiểm tra lại dữ liệu mới trước khi thao tác.</Alert>}
        <div className="ui-form-actions"><Button variant="secondary" disabled={toggleBusy} onClick={() => setConfirmation(null)}>
          {toggleConflict ? 'Đóng và kiểm tra lại' : 'Hủy bỏ'}</Button>
          <Button loading={toggleBusy} disabled={toggleConflict} loadingText="Đang lưu…" onClick={() => void toggleStatus()}>
            {confirmation.status === 'PAUSED' ? 'Kích hoạt' : 'Tạm dừng'}</Button></div>
      </div>}
    </Modal>
  </div>;
}
