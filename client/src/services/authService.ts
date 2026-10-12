import axiosClient from '../api/axiosClient';
import type { ApiResponse } from '../types/api';
import type { AuthenticationResponse, UserResponse, RegistrationPolicies, RegisterRequest,
  RegisterResponse, OtpChallenge, OtpVerificationResponse, SessionResponse, SessionRevocationResponse } from '../types/identity';
import { clearAuthentication, setAuthentication } from '../auth/authSession';

let refreshPromise: Promise<AuthenticationResponse> | null = null;
async function csrfHeaders() {
  const { data } = await axiosClient.get<unknown, ApiResponse<{ headerName: string; token: string }>>('/auth/csrf');
  return { [data.headerName]: data.token };
}
async function refreshOnce() {
  const { data } = await axiosClient.post<unknown, ApiResponse<AuthenticationResponse>>('/auth/refresh', {},
    { headers: await csrfHeaders() });
  setAuthentication(data);
  return data;
}
export const authService = {
  getRegistrationPolicies: () => axiosClient.get<unknown, ApiResponse<RegistrationPolicies>>('/auth/registration-policies'),
  register: (data: RegisterRequest) => axiosClient.post<unknown, ApiResponse<RegisterResponse>>('/auth/register', data),
  verifyOtp: (challengeId: string, otp: string) =>
    axiosClient.post<unknown, ApiResponse<OtpVerificationResponse>>('/auth/otp/verify', { challengeId, otp }),
  resendOtp: (challengeId: string) =>
    axiosClient.post<unknown, ApiResponse<OtpChallenge>>('/auth/otp/resend', { challengeId }),
  async login(identifier: string, password: string) {
    const { data } = await axiosClient.post<unknown, ApiResponse<AuthenticationResponse>>('/auth/login', { identifier, password },
      { headers: await csrfHeaders() });
    setAuthentication(data);
    return data;
  },
  refresh() {
    if (!refreshPromise) refreshPromise = refreshOnce().finally(() => { refreshPromise = null; });
    return refreshPromise;
  },
  async logout() {
    await axiosClient.post('/auth/logout', {}, { headers: await csrfHeaders() });
    clearAuthentication();
  },
  getSessions: () => axiosClient.get<unknown, ApiResponse<SessionResponse[]>>('/auth/sessions'),
  async revokeSession(id: string) {
    const { data } = await axiosClient.delete<unknown, ApiResponse<SessionRevocationResponse>>(
      `/auth/sessions/${encodeURIComponent(id)}`);
    if (data.currentSessionRevoked) clearAuthentication();
    return data;
  },
  me: () => axiosClient.get<unknown, ApiResponse<UserResponse>>('/me'),
};
