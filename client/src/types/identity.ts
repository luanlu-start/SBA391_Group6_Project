export interface RegistrationPolicy {
  version: string;
  title: string;
  paragraphs: string[];
}

export interface RegistrationPolicies {
  terms: RegistrationPolicy;
  privacy: RegistrationPolicy;
  passwordMinLength: number;
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  phone: string;
  password: string;
  adultConfirmed: boolean;
  termsAccepted: boolean;
  privacyAccepted: boolean;
  termsVersion: string;
  privacyVersion: string;
}

export interface RegisterResponse {
  userId: string;
  status: 'PENDING_VERIFICATION';
  challengeId: string;
  verificationChannel: 'EMAIL';
  maskedTarget: string;
  expiresAt: string;
}
