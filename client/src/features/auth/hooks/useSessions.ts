import { useCallback, useEffect, useRef, useState } from 'react';
import { authService } from '../../../services/authService';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { SessionResponse } from '../../../types/identity';

export function useSessions(enabled: boolean) {
  const [sessions, setSessions] = useState<SessionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<ApiError>();
  const [revokeError, setRevokeError] = useState<ApiError>();
  const [revokingId, setRevokingId] = useState<string>();
  const [notice, setNotice] = useState('');
  const requestVersion = useRef(0);
  const mutationPending = useRef(false);

  const reload = useCallback(async () => {
    if (!enabled) return;
    const version = ++requestVersion.current;
    setLoading(true);
    setLoadError(undefined);
    try {
      const { data } = await authService.getSessions();
      if (version === requestVersion.current) setSessions(data);
    } catch (cause) {
      if (version === requestVersion.current) setLoadError(getApiError(cause));
    } finally {
      if (version === requestVersion.current) setLoading(false);
    }
  }, [enabled]);

  useEffect(() => {
    if (enabled) void reload();
    return () => { requestVersion.current += 1; };
  }, [enabled, reload]);

  async function revoke(id: string) {
    if (!enabled || mutationPending.current) return false;
    mutationPending.current = true;
    setRevokingId(id);
    setRevokeError(undefined);
    setNotice('');
    try {
      const result = await authService.revokeSession(id);
      if (!result.currentSessionRevoked) {
        setSessions(items => items.filter(session => session.id !== id));
        setNotice('Đã đăng xuất phiên được chọn. Các phiên khác vẫn hoạt động bình thường.');
        await reload();
      }
      return true;
    } catch (cause) {
      const error = getApiError(cause);
      setRevokeError(error);
      if (error.code === 'AUTH_SESSION_NOT_FOUND') await reload();
      return false;
    } finally {
      mutationPending.current = false;
      setRevokingId(undefined);
    }
  }

  return { sessions, loading, loadError, revokeError, revokingId, notice, reload, revoke,
    clearRevokeError: () => setRevokeError(undefined) };
}
