import { useEffect, useMemo, useRef, useState } from 'react';
import { History, Pencil, RefreshCw, Search, Settings2, Trash2 } from 'lucide-react';
import { parameterApi } from '../api/parameterApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { ParameterChange, ParameterDraft, SystemParameter } from '../types/parameter';
import useParameters from '../features/parameters/hooks/useParameters';
import ParameterEditor from '../features/parameters/components/ParameterEditor';
import ParameterHistoryPanel from '../features/parameters/components/ParameterHistoryPanel';
import PricingSimulator from '../features/parameters/components/PricingSimulator';
import { formatParameterEffectiveFrom, formatParameterValue, normalizeSearch } from '../features/parameters/parameterPresentation';
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
import Modal from '../components/ui/Modal';
import '../styles/parameters.css';

const pageSize = 12;

export default function AdminParametersPage() {
  const list = useParameters();
  const [keyword, setKeyword] = useState('');
  const [group, setGroup] = useState('');
  const [page, setPage] = useState(0);
  const [editor, setEditor] = useState<SystemParameter | null>(null);
  const [history, setHistory] = useState<SystemParameter | null>(null);
  const [drafts, setDrafts] = useState<Record<string, ParameterDraft>>({});
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [saveError, setSaveError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState('');
  const saving = useRef(false);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);
  const groups = useMemo(() => [...new Set(list.parameters.map(parameter => parameter.group))], [list.parameters]);
  const currentByKey = useMemo(() => new Map(list.parameters.map(parameter => [parameter.key, parameter])), [list.parameters]);
  const filtered = useMemo(() => {
    const search = normalizeSearch(keyword.trim());
    return list.parameters.filter(parameter => (!group || parameter.group === group)
      && (!search || normalizeSearch(`${parameter.prmId} ${parameter.key} ${parameter.name} ${parameter.description ?? ''}`).includes(search)));
  }, [list.parameters, keyword, group]);
  const totalPages = Math.ceil(filtered.length / pageSize);
  const currentPage = Math.min(page, Math.max(0, totalPages - 1));
  const draftEntries = Object.entries(drafts);
  const overrides = Object.fromEntries(draftEntries.map(([key, draft]) => [key, draft.value]));
  const staleKeys = draftEntries.filter(([key, draft]) => currentByKey.get(key)?.version !== draft.expectedVersion).map(([key]) => key);
  const sourceRevision = list.parameters.map(parameter => `${parameter.key}:${parameter.version}`).join('|');
  const mutationDisabled = busy || list.loading || !!list.error;

  function removeDraft(key: string) {
    setDrafts(previous => {
      const next = { ...previous };
      delete next[key];
      return next;
    });
    setSaveError(null);
  }

  function stageValue(parameter: SystemParameter, value: string) {
    setDrafts(previous => {
      const next = { ...previous };
      const original = previous[parameter.key]?.original ?? parameter;
      if (value === original.value) delete next[parameter.key];
      else next[parameter.key] = { original, value, expectedVersion: original.version };
      return next;
    });
    setEditor(null);
    setSaveError(null);
    setNotice('');
  }

  function adoptLatestVersion(key: string) {
    const current = currentByKey.get(key);
    if (!current) return;
    setDrafts(previous => {
      const draft = previous[key];
      if (!draft) return previous;
      const next = { ...previous };
      if (draft.value === current.value) delete next[key];
      else next[key] = { ...draft, original: current, expectedVersion: current.version };
      return next;
    });
    setSaveError(null);
    setNotice('Đã dùng phiên bản hiện tại làm mốc so sánh. Giá trị đề xuất của bạn được giữ lại; hãy kiểm tra lại mô phỏng.');
  }

  async function applyChanges() {
    if (saving.current || mutationDisabled || staleKeys.length || !draftEntries.length) return;
    saving.current = true;
    setBusy(true);
    setSaveError(null);
    const changes: Record<string, ParameterChange> = Object.fromEntries(draftEntries.map(([key, draft]) =>
      [key, { value: draft.value, expectedVersion: draft.expectedVersion }]));
    try {
      if (draftEntries.length === 1) {
        const key = draftEntries[0][0];
        await parameterApi.update(key, changes[key]);
      } else await parameterApi.updateBatch(changes);
      if (!mounted.current) return;
      setDrafts({});
      setConfirmOpen(false);
      setNotice(`Đã áp dụng ${draftEntries.length} tham số. Lịch sử thay đổi đã được ghi nhận.`);
      await list.reload();
    } catch (cause) {
      if (!mounted.current) return;
      const failure = getApiError(cause);
      setSaveError(failure);
      setConfirmOpen(false);
      if (failure.status === 409) await list.reload();
    } finally {
      saving.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  const columns: TableColumn<SystemParameter>[] = [
    { key: 'name', header: 'Tham số', render: parameter => <>
      <span className="ui-table-primary">{parameter.name}</span>
      <span className="ui-table-secondary"><code>{parameter.key}</code> · {parameter.prmId}</span>
      {parameter.description && <span className="ui-table-secondary">{parameter.description}</span>}
    </> },
    { key: 'group', header: 'Nhóm', render: parameter => parameter.group },
    { key: 'value', header: 'Đang áp dụng', render: parameter => <>
      <span className="parameter-value">{formatParameterValue(parameter)}</span>
      {drafts[parameter.key] && <span className="ui-table-secondary"><Badge tone="warning">Có bản nháp</Badge></span>}
    </> },
    { key: 'version', header: 'Phiên bản', render: parameter => <>
      <Badge>{parameter.version}</Badge><span className="ui-table-secondary">{formatParameterEffectiveFrom(parameter)}</span>
    </> },
    { key: 'actions', header: 'Thao tác', align: 'right', render: parameter => <div className="ui-table-actions">
      <Button variant="ghost" size="sm" aria-label={`Sửa ${parameter.key}`} disabled={mutationDisabled}
        icon={<Pencil size={16} />} onClick={() => setEditor(parameter)} />
      <Button variant="ghost" size="sm" aria-label={`Lịch sử ${parameter.key}`} disabled={busy}
        icon={<History size={16} />} onClick={() => setHistory(parameter)} />
    </div> },
  ];
  const draftColumns: TableColumn<[string, ParameterDraft]>[] = [
    { key: 'name', header: 'Tham số', render: ([key, draft]) => <>
      <span className="ui-table-primary">{draft.original.name}</span><span className="ui-table-secondary"><code>{key}</code></span>
    </> },
    { key: 'before', header: 'Trước', render: ([key, draft]) => <>
      <span className="parameter-value">{formatParameterValue(draft.original)}</span>
      <span className="ui-table-secondary">Phiên bản {draft.expectedVersion}</span>
      {staleKeys.includes(key) && currentByKey.has(key) && <span className="ui-table-secondary parameter-changed">
        Hiện tại: {formatParameterValue(currentByKey.get(key)!)} · v{currentByKey.get(key)!.version}
      </span>}
    </> },
    { key: 'after', header: 'Đề xuất', render: ([key, draft]) => <>
      <span className="parameter-value parameter-changed">{formatParameterValue(draft.original, draft.value)}</span>
      {(saveError?.fieldErrors[`changes.${key}.value`] ?? (draftEntries.length === 1 ? saveError?.fieldErrors.value : undefined))
        && <span className="ui-field-error">{saveError?.fieldErrors[`changes.${key}.value`] ?? saveError?.fieldErrors.value}</span>}
    </> },
    { key: 'actions', header: 'Thao tác', align: 'right', render: ([key, draft]) => <div className="ui-row parameter-draft-actions">
      {staleKeys.includes(key) && <Button variant="secondary" size="sm" disabled={mutationDisabled || !currentByKey.has(key)}
        onClick={() => adoptLatestVersion(key)}>Dùng phiên bản mới</Button>}
      <Button variant="ghost" size="sm" aria-label={`Sửa bản nháp ${key}`} disabled={mutationDisabled}
        onClick={() => setEditor(currentByKey.get(key) ?? draft.original)} icon={<Pencil size={16} />} />
      <Button variant="ghost" size="sm" aria-label={`Bỏ bản nháp ${key}`} disabled={busy}
        onClick={() => removeDraft(key)} icon={<Trash2 size={16} />} />
    </div> },
  ];

  return <div className="ui-stack">
    <PageHeader title="Tham số và biểu phí" eyebrow="Quản trị"
      description="Quản lý cấu hình, kiểm tra tác động của bản nháp và xem lịch sử thay đổi."
      actions={<Button variant="secondary" onClick={() => void list.reload()} disabled={busy || list.loading}
        icon={<RefreshCw size={17} />}>Tải lại</Button>} />
    {notice && <Alert tone="success">{notice}</Alert>}
    {saveError && <ApiErrorNotice message={saveError.message} correlationId={saveError.correlationId} />}
    {saveError?.status === 409 && <Alert tone="warning">
      Tham số đã được người khác cập nhật. Bản nháp của bạn vẫn được giữ; kiểm tra giá trị hiện tại rồi chọn “Dùng phiên bản mới” trước khi áp dụng lại.
    </Alert>}
    <SectionCard>
      <div className="ui-toolbar">
        <Input label="Tìm tham số" icon={<Search size={17} />} value={keyword} placeholder="Key, mã PRM hoặc tên tham số…"
          onChange={event => { setKeyword(event.target.value); setPage(0); }} />
        <Select label="Nhóm tham số" value={group} onChange={event => { setGroup(event.target.value); setPage(0); }}>
          <option value="">Tất cả nhóm</option>{groups.map(value => <option key={value} value={value}>{value}</option>)}
        </Select>
      </div>
    </SectionCard>
    <SectionCard title="Bộ tham số hệ thống" className="ui-card-table"
      description={list.loading || list.error ? undefined : `${filtered.length} / ${list.parameters.length} tham số`}>
      {list.error ? <div className="ui-card-body ui-stack">
        <ApiErrorNotice message={list.error.message} correlationId={list.error.correlationId} />
        <div><Button variant="secondary" onClick={() => void list.reload()}>Thử lại</Button></div>
      </div> : <>
        <DataTable caption="Danh sách tham số hệ thống" columns={columns}
          rows={filtered.slice(currentPage * pageSize, (currentPage + 1) * pageSize)} rowKey={parameter => parameter.key}
          loading={list.loading} emptyState={<EmptyState title="Chưa có tham số phù hợp"
            description={list.parameters.length ? 'Thử từ khóa hoặc nhóm khác.' : 'Bộ tham số chưa được nạp. Tải lại sau khi hệ thống sẵn sàng.'}
            icon={<Settings2 size={24} />} />} />
        {!list.loading && <Pagination page={currentPage} size={pageSize} totalElements={filtered.length}
          totalPages={totalPages} onPageChange={setPage} disabled={busy} />}
      </>}
    </SectionCard>
    {draftEntries.length > 0 && <SectionCard id="parameter-drafts" title={`Bản nháp (${draftEntries.length})`}
      description="Chưa áp dụng. Các tham số liên quan được cập nhật cùng nhau khi xác nhận."
      actions={<Button variant="ghost" size="sm" disabled={busy} onClick={() => { setDrafts({}); setSaveError(null); }}>Bỏ tất cả bản nháp</Button>}>
      <div className="ui-stack">
        <DataTable caption="So sánh bản nháp tham số" columns={draftColumns} rows={draftEntries} rowKey={([key]) => key} />
        {!list.loading && staleKeys.length > 0 && <Alert tone="warning">Có {staleKeys.length} bản nháp dùng phiên bản cũ. Kiểm tra và chọn phiên bản mới cho từng tham số.</Alert>}
        <div className="ui-form-actions"><Button disabled={mutationDisabled || staleKeys.length > 0}
          onClick={() => { setSaveError(null); setConfirmOpen(true); }}>Xác nhận áp dụng {draftEntries.length} tham số</Button></div>
      </div>
    </SectionCard>}
    {list.parameters.length > 0 && <PricingSimulator overrides={overrides}
      sourceRevision={sourceRevision} disabled={mutationDisabled} />}
    <Modal isOpen={!!editor} onClose={() => setEditor(null)} title={editor ? `Chỉnh sửa: ${editor.name}` : 'Chỉnh sửa tham số'}
      description="Lưu bản nháp để mô phỏng trước khi áp dụng.">
      {editor && <ParameterEditor key={editor.key} parameter={editor} draftValue={drafts[editor.key]?.value}
        onCancel={() => setEditor(null)} onDraft={value => stageValue(editor, value)} />}
    </Modal>
    <Modal isOpen={!!history} onClose={() => setHistory(null)} size="lg"
      title={history ? `Lịch sử: ${history.name}` : 'Lịch sử tham số'} description="Thời điểm hiển thị theo giờ Việt Nam.">
      {history && <ParameterHistoryPanel key={history.key} parameter={history} />}
    </Modal>
    <Modal isOpen={confirmOpen} onClose={() => setConfirmOpen(false)} closeDisabled={busy} size="lg"
      title="Áp dụng bản nháp tham số?" description="Kiểm tra lần cuối các giá trị trước và sau.">
      <div className="ui-stack">
        <DataTable caption="Tham số sẽ áp dụng" columns={draftColumns.slice(0, 3)} rows={draftEntries} rowKey={([key]) => key} />
        <Alert tone="warning">Cấu hình mới áp dụng cho các thao tác tiếp theo. Giá đã chốt của đơn hiện hữu giữ nguyên theo phiên bản được lưu trên đơn.</Alert>
        <div className="ui-form-actions">
          <Button variant="secondary" disabled={busy} onClick={() => setConfirmOpen(false)}>Quay lại kiểm tra</Button>
          <Button onClick={() => void applyChanges()} loading={busy} disabled={mutationDisabled || staleKeys.length > 0}
            loadingText="Đang áp dụng…">Áp dụng thay đổi</Button>
        </div>
      </div>
    </Modal>
  </div>;
}
