import { useEffect, useState } from 'react';
import { useAuthentication } from '../../../auth/useAuthentication';
import { updateAuthenticatedUser } from '../../../auth/authSession';
import { profileService } from '../../../services/profileService';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';

export function useProfile() {
  const { user, initializing } = useAuthentication();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError>();
  const [reload, setReload] = useState(0);
  const userId = user?.id;
  useEffect(() => {
    if (!userId || initializing) return;
    let active = true; setLoading(true); setError(undefined);
    void profileService.me().then(({ data }) => { if (active) updateAuthenticatedUser(data); })
      .catch(cause => { if (active) setError(getApiError(cause)); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [userId, initializing, reload]);
  return { user, initializing, loading, error, reload: () => setReload(value => value + 1) };
}
