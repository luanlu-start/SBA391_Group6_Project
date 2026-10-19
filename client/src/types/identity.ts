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
  resendAvailableAt: string;
}

export interface OtpChallenge {
  challengeId: string;
  verificationChannel: 'EMAIL';
  maskedTarget: string;
  expiresAt: string;
  resendAvailableAt: string;
}

export interface OtpVerificationResponse {
  userId: string;
  status: 'ACTIVE';
  verificationChannel: 'EMAIL';
  verifiedAt: string;
}

export interface UserResponse {
  id: string;
  fullName: string;
  email: string;
  phone: string;
  status: 'ACTIVE';
  roles: string[];
  emailVerifiedAt: string;
  dob?: string | null;
  bio?: string | null;
  emergencyContact?: string | null;
  fptBadge?: boolean;
  avatarAvailable?: boolean;
}

export interface AuthenticationResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresAt: string;
  user: UserResponse;
}

export interface SessionResponse {
  id: string;
  deviceInfo: string;
  ip: string;
  createdAt: string;
  expiresAt: string;
  current: boolean;
}

export interface SessionRevocationResponse {
  currentSessionRevoked: boolean;
}
