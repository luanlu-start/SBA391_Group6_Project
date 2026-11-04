import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../../../services/authService';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { ResetPasswordFormValues } from '../schemas/passwordRecoverySchema';

export function usePasswordRecovery(initialResendAt?: string) {
  const navigate = useNavigate();
  const inFlight = useRef(false);
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<ApiError>();
  const [resendAt, setResendAt] = useState(() => Date.parse(initialResendAt ?? '') || 0);
  const [retryAt, setRetryAt] = useState(0);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    if (Math.max(resendAt, retryAt) <= Date.now()) return;
    const timer = window.setInterval(() => {
      const currentTime = Date.now();
      setNow(currentTime);
      if (currentTime >= Math.max(resendAt, retryAt)) window.clearInterval(timer);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [resendAt, retryAt]);

  function failure(cause: unknown) {
    const apiError = getApiError(cause);
    setError(apiError);
    if (apiError.retryAfterSeconds) {
      const currentTime = Date.now();
      const until = currentTime + apiError.retryAfterSeconds * 1000;
      setNow(currentTime);
      if (apiError.code === 'AUTH_OTP_RESEND_TOO_SOON') setResendAt(until);
      else setRetryAt(until);
    }
  }

  async function requestOtp(identifier: string) {
    if (inFlight.current || Math.max(resendAt, retryAt) > Date.now()) return null;
    inFlight.current = true; setIsPending(true); setError(undefined);
    try {
      const { data } = await authService.forgotPassword(identifier);
      setNow(Date.now());
      setResendAt(Date.parse(data.resendAvailableAt));
      return data;
    } catch (cause) { failure(cause); return null; }
    finally { inFlight.current = false; setIsPending(false); }
  }

  async function resetPassword(values: ResetPasswordFormValues) {
    if (inFlight.current || retryAt > Date.now()) return;
    inFlight.current = true; setIsPending(true); setError(undefined);
    try {
      await authService.resetPassword(values.identifier, values.otp, values.newPassword);
      navigate('/login', { replace: true, state: { passwordReset: true } });
    } catch (cause) { failure(cause); }
    finally { inFlight.current = false; setIsPending(false); }
  }

  return { requestOtp, resetPassword, isPending, error,
    resendAfterSeconds: Math.max(0, Math.ceil((resendAt - now) / 1000)),
    retryAfterSeconds: Math.max(0, Math.ceil((retryAt - now) / 1000)) };
}
