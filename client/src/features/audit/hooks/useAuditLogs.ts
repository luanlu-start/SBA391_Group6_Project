import { useCallback, useEffect, useRef, useState } from 'react';
import { auditApi } from '../../../api/auditApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { PageResponse } from '../../../types/api';
import type { AuditLog, AuditQuery } from '../../../types/audit';

export default function useAuditLogs(query: AuditQuery) {
  const [result, setResult] = useState<PageResponse<AuditLog> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const latestRequest = useRef(0);
  const activeRequest = useRef<AbortController | null>(null);

  const reload = useCallback(async () => {
    const request = ++latestRequest.current;
    activeRequest.current?.abort();
    const controller = new AbortController();
    activeRequest.current = controller;
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const response = await auditApi.getLogs(query, controller.signal);
      if (request === latestRequest.current) setResult(response.data);
    } catch (cause) {
      if (request === latestRequest.current && !controller.signal.aborted) setError(getApiError(cause));
    } finally {
      if (request === latestRequest.current) setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    void reload();
    return () => { latestRequest.current += 1; activeRequest.current?.abort(); };
  }, [reload]);

  return { result, loading, error, reload };
}
