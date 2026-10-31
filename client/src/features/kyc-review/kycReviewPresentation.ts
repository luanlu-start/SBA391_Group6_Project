import type { KycReviewFileType, KycReviewStatus } from '../../types/kycReview';

export const kycReviewStatusLabels: Record<KycReviewStatus, string> = {
  PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối',
};

export const kycReviewStatusTones = {
  PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger',
} as const;

export const kycReviewFileLabels: Record<KycReviewFileType, string> = {
  ID_FRONT: 'Mặt trước CCCD', ID_BACK: 'Mặt sau CCCD', SELFIE_WITH_ID: 'Ảnh chân dung cùng CCCD',
};

export function formatKycReviewTime(value?: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh',
  }).format(date) : '—';
}

export function formatKycReviewDate(value?: string | null): string {
  if (!value) return '—';
  const parts = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  return parts ? `${parts[3]}/${parts[2]}/${parts[1]}` : '—';
}
