import { useCallback, useEffect, useRef, useState } from 'react';
import { promotionApi } from '../../../api/promotionApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { PageResponse } from '../../../types/api';
import type { Promotion, PromotionQuery } from '../../../types/promotion';

export default function usePromotions(query: PromotionQuery) {
  const [result, setResult] = useState<PageResponse<Promotion> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const requestSequence = useRef(0);
  const activeRequest = useRef<AbortController | null>(null);

  const reload = useCallback(async () => {
    const sequence = ++requestSequence.current;
    activeRequest.current?.abort();
    const controller = new AbortController();
    activeRequest.current = controller;
    setLoading(true); setError(null); setResult(null);
    try {
      const response = await promotionApi.list(query, controller.signal);
      if (sequence === requestSequence.current) setResult(response.data);
    } catch (cause) {
      if (sequence === requestSequence.current && !controller.signal.aborted) setError(getApiError(cause));
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    void reload();
    return () => { requestSequence.current += 1; activeRequest.current?.abort(); };
  }, [reload]);

  return { result, loading, error, reload };
}
