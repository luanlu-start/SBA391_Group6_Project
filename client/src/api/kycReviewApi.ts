import { CanceledError } from 'axios';
import type { AxiosRequestTransformer } from 'axios';
import axiosClient from './axiosClient';
import { ApiError } from './ApiError';
import { getSessionState } from '../auth/authSession';
import type { ApiResponse, PageResponse } from '../types/api';
import type { KycRejectionReason, KycReviewDetail, KycReviewFileLink, KycReviewItem, KycReviewQuery } from '../types/kycReview';

const root = '/mod/kyc';
const requestPath = (id: string) => `${root}/${encodeURIComponent(id)}`;

function ownerRequest(signal?: AbortSignal) {
  const owner = getSessionState().user?.id;
  const guard: AxiosRequestTransformer = data => {
    if (!owner || getSessionState().user?.id !== owner) throw new CanceledError('KYC review request owner changed');
    return data;
  };
  const defaults = axiosClient.defaults.transformRequest;
  return { signal, transformRequest: [guard, ...(Array.isArray(defaults) ? defaults : defaults ? [defaults] : [])] };
}

export function validateKycReviewFileLink(requestId: string, fileId: string, suppliedUrl: string): string {
  const path = `${requestPath(requestId)}/files/${encodeURIComponent(fileId)}`;
  const invalid = () => new ApiError('Liên kết ảnh không hợp lệ. Vui lòng yêu cầu tải ảnh lại.', 403, 'KYC_REVIEW_FILE_LINK_INVALID');
  if (typeof suppliedUrl !== 'string' || !suppliedUrl.startsWith(`${path}?`) || suppliedUrl.includes('\\')) throw invalid();
  const parsed = new URL(suppliedUrl, 'https://kyc.invalid');
  const keys = [...parsed.searchParams.keys()];
  if (parsed.origin !== 'https://kyc.invalid' || parsed.pathname !== path || parsed.hash
    || keys.length !== 2 || parsed.searchParams.getAll('expires').length !== 1
    || parsed.searchParams.getAll('signature').length !== 1
    || !parsed.searchParams.get('expires') || !parsed.searchParams.get('signature')) throw invalid();
  return suppliedUrl;
}

export const kycReviewApi = {
  list: (query: KycReviewQuery, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<PageResponse<KycReviewItem>>>(root, { ...ownerRequest(signal), params: query }),
  get: (id: string, signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<KycReviewDetail>>(requestPath(id), ownerRequest(signal)),
  rejectionReasons: (signal?: AbortSignal) =>
    axiosClient.get<unknown, ApiResponse<KycRejectionReason[]>>(`${root}/rejection-reasons`, ownerRequest(signal)),
  approve: (id: string, expectedVersion: number, signal?: AbortSignal) =>
    axiosClient.post<unknown, ApiResponse<KycReviewDetail>>(`${requestPath(id)}/approve`, { expectedVersion }, ownerRequest(signal)),
  reject: (id: string, expectedVersion: number, reasonCode: string, note?: string, signal?: AbortSignal) =>
    axiosClient.post<unknown, ApiResponse<KycReviewDetail>>(`${requestPath(id)}/reject`, { expectedVersion, reasonCode, note }, ownerRequest(signal)),
  flag: (id: string, expectedVersion: number, reason: string, signal?: AbortSignal) =>
    axiosClient.post<unknown, ApiResponse<KycReviewDetail>>(`${requestPath(id)}/flag`, { expectedVersion, reason }, ownerRequest(signal)),
  async image(id: string, fileId: string, signal: AbortSignal): Promise<Blob> {
    const config = ownerRequest(signal);
    const link = await axiosClient.post<unknown, ApiResponse<KycReviewFileLink>>(
      `${requestPath(id)}/files/${encodeURIComponent(fileId)}/link`, undefined, config);
    const url = validateKycReviewFileLink(id, fileId, link.data.url);
    const blob = await axiosClient.get<unknown, Blob>(url, {
      ...config, responseType: 'blob', headers: { Accept: 'image/jpeg, image/png, image/webp, application/problem+json' },
    });
    if (!(blob instanceof Blob) || !['image/jpeg', 'image/png', 'image/webp'].includes(blob.type.toLowerCase()) || blob.size === 0) {
      throw new ApiError('Ảnh hồ sơ không hợp lệ. Vui lòng tải lại hoặc kiểm tra tài liệu đã nộp.', 400, 'KYC_REVIEW_INVALID_DOCUMENT');
    }
    return blob;
  },
};
