import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../../../services/authService';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { ChangePasswordFormValues } from '../schemas/changePasswordSchema';

export function useChangePassword() {
  const navigate = useNavigate();
  const inFlight = useRef(false);
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<ApiError>();
  const [retryAt, setRetryAt] = useState(0);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    if (retryAt <= Date.now()) return;
    const timer = window.setInterval(() => {
      const current = Date.now();
      setNow(current);
      if (current >= retryAt) window.clearInterval(timer);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [retryAt]);

  async function changePassword(values: ChangePasswordFormValues) {
    if (inFlight.current || retryAt > Date.now()) return;
    inFlight.current = true; setIsPending(true); setError(undefined);
    try {
      await authService.changePassword(values.currentPassword, values.newPassword);
      navigate('/login', { replace: true, state: { passwordChanged: true } });
    } catch (cause) {
      const apiError = getApiError(cause);
      setError(apiError);
      if (apiError.retryAfterSeconds) {
        const current = Date.now();
        setNow(current);
        setRetryAt(current + apiError.retryAfterSeconds * 1000);
      }
    } finally { inFlight.current = false; setIsPending(false); }
  }

  return { changePassword, isPending, error, retryAfterSeconds: Math.max(0, Math.ceil((retryAt - now) / 1000)) };
}
