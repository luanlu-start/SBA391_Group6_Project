import { useCallback, useEffect, useRef, useState } from 'react';
import { orderModerationApi } from '../../../api/orderModerationApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { PageResponse } from '../../../types/api';
import type { OrderModerationItem, OrderModerationQuery } from '../../../types/orderModeration';

export default function useOrderModerationQueue(query: OrderModerationQuery) {
  const [result, setResult] = useState<PageResponse<OrderModerationItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [revision, setRevision] = useState(0);
  const sequence = useRef(0);
  const reload = useCallback(() => setRevision(value => value + 1), []);
  const { q, page, size } = query;

  useEffect(() => {
    const current = ++sequence.current;
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    void orderModerationApi.pending({ q, page, size }, controller.signal).then(response => {
      if (current === sequence.current && !controller.signal.aborted) setResult(response.data);
    }).catch(cause => {
      if (current === sequence.current && !controller.signal.aborted) { setError(getApiError(cause)); setResult(null); }
    }).finally(() => {
      if (current === sequence.current && !controller.signal.aborted) setLoading(false);
    });
    return () => { controller.abort(); };
  }, [q, page, size, revision]);

  return { result, loading, error, reload };
}
