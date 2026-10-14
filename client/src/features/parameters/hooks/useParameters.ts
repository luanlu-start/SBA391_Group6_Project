import { useCallback, useEffect, useRef, useState } from 'react';
import { parameterApi } from '../../../api/parameterApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { SystemParameter } from '../../../types/parameter';

export default function useParameters() {
  const [parameters, setParameters] = useState<SystemParameter[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const latestRequest = useRef(0);

  const reload = useCallback(async () => {
    const request = ++latestRequest.current;
    setLoading(true);
    setError(null);
    try {
      const response = await parameterApi.getAll();
      if (request === latestRequest.current) setParameters(response.data);
    } catch (cause) {
      if (request === latestRequest.current) setError(getApiError(cause));
    } finally {
      if (request === latestRequest.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
    return () => { latestRequest.current += 1; };
  }, [reload]);

  return { parameters, loading, error, reload };
}
