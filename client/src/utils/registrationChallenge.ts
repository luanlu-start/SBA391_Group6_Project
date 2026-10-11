import type { OtpChallenge } from '../types/identity';

const STORAGE_KEY = 'registration_challenge';

export function parseRegistrationChallenge(value: unknown): OtpChallenge | null {
  if (!value || typeof value !== 'object') return null;
  const challenge = value as Record<string, unknown>;
  if (typeof challenge.challengeId !== 'string'
    || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(challenge.challengeId)
    || challenge.verificationChannel !== 'EMAIL'
    || typeof challenge.maskedTarget !== 'string'
    || typeof challenge.expiresAt !== 'string' || !Number.isFinite(Date.parse(challenge.expiresAt))
    || typeof challenge.resendAvailableAt !== 'string' || !Number.isFinite(Date.parse(challenge.resendAvailableAt))) return null;
  return { challengeId: challenge.challengeId, verificationChannel: 'EMAIL',
    maskedTarget: challenge.maskedTarget, expiresAt: challenge.expiresAt, resendAvailableAt: challenge.resendAvailableAt };
}

export function loadRegistrationChallenge(): OtpChallenge | null {
  try {
    return parseRegistrationChallenge(JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? 'null'));
  } catch {
    return null;
  }
}

export function saveRegistrationChallenge(challenge: OtpChallenge): void {
  try {
    // Store only challenge metadata, never passwords or the OTP.
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(parseRegistrationChallenge(challenge)));
  } catch {
    // Navigation state still supports this flow when storage is unavailable.
  }
}

export function clearRegistrationChallenge(): void {
  try { sessionStorage.removeItem(STORAGE_KEY); } catch { /* Storage may be disabled. */ }
}
