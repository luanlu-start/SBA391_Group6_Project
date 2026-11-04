import axiosClient from '../api/axiosClient';
import type { ApiResponse } from '../types/api';
import type { OtpChallenge, UserResponse } from '../types/identity';
import type { AccountDataRequest, AddressInput, ProfileInput, SavedAddress } from '../types/profile';
import { clearAuthentication, updateAuthenticatedUser } from '../auth/authSession';

export const profileService = {
  me: () => axiosClient.get<unknown, ApiResponse<UserResponse>>('/me'),
  async update(input: ProfileInput) {
    const response = await axiosClient.put<unknown, ApiResponse<UserResponse>>('/me/profile', input);
    updateAuthenticatedUser(response.data); return response;
  },
  async uploadAvatar(file: File) {
    const body = new FormData(); body.append('file', file);
    const response = await axiosClient.post<unknown, ApiResponse<UserResponse>>('/me/avatar', body, { headers: { 'Content-Type': undefined } });
    updateAuthenticatedUser(response.data); return response;
  },
  avatar: () => axiosClient.get<unknown, Blob>('/me/avatar', { responseType: 'blob' }),
  addresses: () => axiosClient.get<unknown, ApiResponse<SavedAddress[]>>('/me/addresses'),
  saveAddress: (input: AddressInput, id?: string) => id
    ? axiosClient.put<unknown, ApiResponse<SavedAddress>>(`/me/addresses/${encodeURIComponent(id)}`, input)
    : axiosClient.post<unknown, ApiResponse<SavedAddress>>('/me/addresses', input),
  deleteAddress: (id: string) => axiosClient.delete(`/me/addresses/${encodeURIComponent(id)}`),
  changeContact: (type: 'EMAIL' | 'PHONE', value: string, currentPassword: string) =>
    axiosClient.patch<unknown, ApiResponse<OtpChallenge>>('/me/contact', { type, value, currentPassword }),
  resendContactOtp: (id: string) => axiosClient.post<unknown, ApiResponse<OtpChallenge>>(`/me/contact/${encodeURIComponent(id)}/resend`, {}),
  async verifyContact(challengeId: string, otp: string) {
    await axiosClient.post('/me/contact/verify', { challengeId, otp }); clearAuthentication();
  },
  dataRequests: () => axiosClient.get<unknown, ApiResponse<AccountDataRequest[]>>('/me/data-requests'),
  requestData: (type: 'DATA_EXPORT' | 'ACCOUNT_DELETION', reason: string) =>
    axiosClient.post<unknown, ApiResponse<AccountDataRequest>>(type === 'DATA_EXPORT' ? '/me/data-export' : '/me/delete-request', { reason }),
};
