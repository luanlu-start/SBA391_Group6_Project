import type { WithdrawalStatus } from '../../types/payment';
import type { WithdrawalParty } from '../../types/withdrawalReview';

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'info';

export const withdrawalStatusLabels: Record<WithdrawalStatus, string> = {
  PENDING_REVIEW: 'Chờ duyệt',
  APPROVED_L1: 'Chờ duyệt cấp 2',
  APPROVED: 'Đã duyệt, chờ chi',
  PROCESSING: 'Đang chi',
  PAID: 'Đã chi',
  REJECTED: 'Đã từ chối',
  FAILED: 'Chi thất bại',
  CANCELLED: 'Người dùng đã hủy',
};

export const withdrawalStatusTones: Record<WithdrawalStatus, Tone> = {
  PENDING_REVIEW: 'warning',
  APPROVED_L1: 'info',
  APPROVED: 'info',
  PROCESSING: 'info',
  PAID: 'success',
  REJECTED: 'danger',
  FAILED: 'danger',
  CANCELLED: 'neutral',
};

const currency = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });
const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' });

export function formatVnd(value: number | null | undefined): string {
  return typeof value === 'number' && Number.isFinite(value) ? currency.format(value) : '—';
}

export function formatWithdrawalTime(value?: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? dateTime.format(date) : '—';
}

export function partyName(party: WithdrawalParty | null | undefined): string {
  if (!party) return '—';
  return party.fullName?.trim() || party.email;
}
