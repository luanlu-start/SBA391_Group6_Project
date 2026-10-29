import { useEffect, useRef, useState } from 'react';
import { auditApi } from '../../../api/auditApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { AuditFilterField, AuditFilterOptions } from '../../../types/audit';

export default function useAuditFilterOptions(field: AuditFilterField, search: string, searchAlias: string | undefined, entityType: string | undefined,
  refreshToken: number, retryToken: number) {
  const [result, setResult] = useState<AuditFilterOptions | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const latestRequest = useRef(0);

  useEffect(() => {
    const request = ++latestRequest.current;
    const controller = new AbortController();
    setResult(null);
    setLoading(true);
    setError(null);
    const timer = window.setTimeout(() => {
      void (async () => {
        try {
          const searches = searchAlias && searchAlias !== search ? [searchAlias, search] : [search];
          const responses = await Promise.all(searches.map(q => auditApi.getFilterOptions({
            field, q: q || undefined, entityType, limit: 30,
          }, controller.signal)));
          const merged = new Map(responses.flatMap(response => response.data.options).map(option => [option.value, option]));
          if (request === latestRequest.current) setResult({
            options: [...merged.values()].slice(0, 30),
            hasMore: merged.size > 30 || responses.some(response => response.data.hasMore),
          });
        } catch (cause) {
          if (request === latestRequest.current && !controller.signal.aborted) setError(getApiError(cause));
        } finally {
          if (request === latestRequest.current) setLoading(false);
        }
      })();
    }, search ? 250 : 0);
    return () => {
      latestRequest.current += 1;
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [field, search, searchAlias, entityType, refreshToken, retryToken]);

  return { result, loading, error };
}
