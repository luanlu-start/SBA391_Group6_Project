import { useEffect, useState } from 'react';
import { courierService } from '../../../services/courierService';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { CourierRegistrationStatus } from '../../../types/courier';

export function useCourier(userId?: string) {
  const [status, setStatus] = useState<CourierRegistrationStatus>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError>();
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!userId) return;
    let active = true; setLoading(true); setError(undefined);
    void courierService.status().then(({ data }) => { if (active) setStatus(data); })
      .catch(cause => { if (active) setError(getApiError(cause)); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [userId, revision]);
  return { status, loading, error, reload: () => setRevision(value => value + 1) };
}
