import axiosClient from '../api/axiosClient';
import type { ApiResponse } from '../types/api';
import type { KycInput, KycSnapshot } from '../types/kyc';

export const kycService = {
  status: () => axiosClient.get<unknown, ApiResponse<KycSnapshot>>('/me/kyc'),
  submit(input: KycInput, files: { front: File; back: File; portrait: File }) {
    const body = new FormData();
    body.append('request', new Blob([JSON.stringify(input)], { type: 'application/json' }));
    Object.entries(files).forEach(([name, file]) => body.append(name, file));
    return axiosClient.post<unknown, ApiResponse<KycSnapshot>>('/me/kyc', body, { headers: { 'Content-Type': undefined } });
  },
  async image(id: string) {
    const { data } = await axiosClient.post<unknown, ApiResponse<{ url: string; expiresAt: string }>>(`/me/kyc/files/${encodeURIComponent(id)}/link`, {});
    // Keep the bearer token on our API origin, even if a malformed response contains another URL.
    if (!data.url.startsWith(`/me/kyc/files/${encodeURIComponent(id)}?`)) throw new Error('Invalid file link');
    return axiosClient.get<unknown, Blob>(data.url, { responseType: 'blob', headers: { Accept: 'image/*, application/problem+json' } });
  },
};
