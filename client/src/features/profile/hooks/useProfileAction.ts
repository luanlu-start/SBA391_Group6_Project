import { useRef, useState } from 'react';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import { useCountdown } from './useCountdown';

export function useProfileAction() {
  const pending = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError>();
  const [notice, setNotice] = useState('');
  const [retryAt, setRetryAt] = useState(0);
  const retryAfterSeconds = useCountdown(retryAt);
  async function run<T>(operation: () => Promise<T>, message = ''): Promise<{ data: T } | undefined> {
    if (pending.current || retryAt > Date.now()) return;
    pending.current = true; setBusy(true); setError(undefined); setNotice('');
    try { const data = await operation(); setNotice(message); return { data }; }
    catch (cause) {
      const failure = getApiError(cause); setError(failure);
      if (failure.retryAfterSeconds) setRetryAt(Date.now() + failure.retryAfterSeconds * 1000);
    } finally { pending.current = false; setBusy(false); }
  }
  return { run, busy, error, notice, retryAfterSeconds, clearError: () => setError(undefined) };
}
