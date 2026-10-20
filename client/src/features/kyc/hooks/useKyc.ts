import { useEffect, useState } from 'react';
import { kycService } from '../../../services/kycService';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { KycSnapshot } from '../../../types/kyc';

export function useKyc(userId?: string) {
  const [snapshot, setSnapshot] = useState<KycSnapshot>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError>();
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!userId) return;
    let active = true; setLoading(true); setError(undefined);
    void kycService.status().then(({ data }) => { if (active) setSnapshot(data); })
      .catch(cause => { if (active) setError(getApiError(cause)); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [userId, revision]);
  return { snapshot, setSnapshot, loading, error, reload: () => setRevision(value => value + 1) };
}
