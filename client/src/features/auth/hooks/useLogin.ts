import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { getLoginDestination } from '../../../auth/loginDestination';
import { authService } from '../../../services/authService';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import { useAuthentication } from '../../../auth/useAuthentication';
import type { LoginFormValues } from '../schemas/loginSchema';
import { saveRegistrationChallenge } from '../../../utils/registrationChallenge';

export function useLogin() {
  const navigate = useNavigate();
  const location = useLocation();
  const { initializing } = useAuthentication();
  const inFlight = useRef(false);
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<ApiError>();
  const [retryAt, setRetryAt] = useState(0);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    if (!retryAt) return;
    const timer = window.setInterval(() => {
      const currentTime = Date.now();
      setNow(currentTime);
      if (currentTime >= retryAt) setRetryAt(0);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [retryAt]);

  async function login(values: LoginFormValues) {
    if (inFlight.current || initializing || retryAt > Date.now()) return false;
    inFlight.current = true;
    setIsPending(true);
    setError(undefined);
    try {
      await authService.login(values.identifier, values.password, values.rememberMe);
      navigate(getLoginDestination(location.state), { replace: true });
      return true;
    } catch (cause) {
      const failure = getApiError(cause);
      if (failure.code === 'AUTH_ACCOUNT_UNVERIFIED' && failure.verification) {
        saveRegistrationChallenge(failure.verification);
        navigate('/verify', { replace: true, state: { challenge: failure.verification } });
        return true;
      }
      setError(failure);
      if (failure.retryAfterSeconds) {
        const currentTime = Date.now();
        setNow(currentTime);
        setRetryAt(currentTime + failure.retryAfterSeconds * 1000);
      }
      return false;
    } finally {
      inFlight.current = false;
      setIsPending(false);
    }
  }

  return {
    login,
    isPending,
    initializing,
    error,
    retryAfterSeconds: Math.max(0, Math.ceil((retryAt - now) / 1000)),
  };
}
