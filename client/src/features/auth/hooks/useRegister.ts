import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../../../services/authService';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { RegistrationPolicies } from '../../../types/identity';
import { saveRegistrationChallenge } from '../../../utils/registrationChallenge';
import type { RegisterFormValues } from '../schemas/registerSchema';

export function useRegister() {
  const navigate = useNavigate();
  const inFlight = useRef(false);
  const [policies, setPolicies] = useState<RegistrationPolicies | null>(null);
  const [loadingPolicies, setLoadingPolicies] = useState(true);
  const [policyError, setPolicyError] = useState<ApiError>();
  const [policyRetry, setPolicyRetry] = useState(0);
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<ApiError>();

  useEffect(() => {
    let active = true;
    setLoadingPolicies(true);
    setPolicyError(undefined);
    authService.getRegistrationPolicies().then(response => {
      if (active) setPolicies(response.data);
    }).catch(cause => {
      if (active) setPolicyError(getApiError(cause));
    }).finally(() => { if (active) setLoadingPolicies(false); });
    return () => { active = false; };
  }, [policyRetry]);

  function reloadPolicies() {
    setPolicies(null);
    setLoadingPolicies(true);
    setPolicyRetry(value => value + 1);
  }

  async function registerAccount(values: RegisterFormValues) {
    if (!policies || loadingPolicies || inFlight.current) return false;
    inFlight.current = true;
    setIsPending(true);
    setError(undefined);
    try {
      const response = await authService.register({ fullName: values.fullName, email: values.email,
        phone: values.phone, password: values.password, adultConfirmed: values.adultConfirmed,
        termsAccepted: values.termsAccepted, privacyAccepted: values.privacyAccepted,
        termsVersion: policies.terms.version, privacyVersion: policies.privacy.version });
      saveRegistrationChallenge(response.data);
      navigate('/verify', { replace: true, state: { challenge: response.data } });
      return true;
    } catch (cause) {
      const failure = getApiError(cause);
      setError(failure);
      if (failure.code === 'AUTH_POLICY_CHANGED') reloadPolicies();
      return false;
    } finally {
      inFlight.current = false;
      setIsPending(false);
    }
  }

  return { registerAccount, policies, loadingPolicies, policyError, reloadPolicies, isPending,
    error, clearError: () => setError(undefined) };
}
