import { useCallback, useEffect, useRef, useState } from 'react';
import { parameterApi } from '../../../api/parameterApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { PageResponse } from '../../../types/api';
import type { ParameterHistory, SystemParameter } from '../../../types/parameter';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import DataTable from '../../../components/ui/DataTable';
import type { TableColumn } from '../../../components/ui/DataTable';
import Pagination from '../../../components/ui/Pagination';
import Button from '../../../components/ui/Button';
import EmptyState from '../../../components/ui/EmptyState';
import { formatDate, formatParameterValue } from '../parameterPresentation';

export default function ParameterHistoryPanel({ parameter }: { parameter: SystemParameter }) {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<ParameterHistory> | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);
  const latestRequest = useRef(0);
  const size = 10;

  const reload = useCallback(async () => {
    const request = ++latestRequest.current;
    setLoading(true);
    setError(null);
    try {
      const response = await parameterApi.getHistory(parameter.key, page, size);
      if (request === latestRequest.current) {
        if (page > 0 && page >= response.data.totalPages) setPage(Math.max(0, response.data.totalPages - 1));
        else setResult(response.data);
      }
    } catch (cause) {
      if (request === latestRequest.current) setError(getApiError(cause));
    } finally {
      if (request === latestRequest.current) setLoading(false);
    }
  }, [parameter.key, page]);

  useEffect(() => {
    void reload();
    return () => { latestRequest.current += 1; };
  }, [reload]);

  const columns: TableColumn<ParameterHistory>[] = [
    { key: 'time', header: 'Thời điểm (giờ Việt Nam)', render: row => formatDate(row.changedAt) },
    { key: 'version', header: 'Phiên bản', render: row => row.version },
    { key: 'before', header: 'Trước', render: row => <span className="parameter-value">{formatParameterValue(parameter, row.oldValue)}</span> },
    { key: 'after', header: 'Sau', render: row => <span className="parameter-value">{formatParameterValue(parameter, row.newValue)}</span> },
    { key: 'actor', header: 'Người thay đổi', render: row => <span className="parameter-actor">{row.changedBy}</span> },
  ];

  return <div className="ui-stack">
    {error ? <>
      <ApiErrorNotice message={error.message} correlationId={error.correlationId} />
      <div><Button variant="secondary" onClick={() => void reload()}>Thử lại</Button></div>
    </> : <>
      <DataTable caption={`Lịch sử ${parameter.key}`} columns={columns} rows={result?.content ?? []}
        rowKey={row => row.id} loading={loading}
        emptyState={<EmptyState title="Chưa có thay đổi" description="Các lần cập nhật tham số sẽ được ghi nhận tại đây." />} />
      {!loading && result && <Pagination page={page} size={size} totalElements={result.totalElements}
        totalPages={result.totalPages} onPageChange={setPage} />}
    </>}
  </div>;
}
