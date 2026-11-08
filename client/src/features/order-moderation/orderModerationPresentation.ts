import type { ModerationParty } from '../../types/orderModeration';

const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' });
const currency = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });

export const categoryFlagLabels = {
  PROHIBITED: 'Danh mục cấm',
  REQUIRES_REVIEW: 'Danh mục cần kiểm tra',
} as const;

export function formatModerationTime(value?: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? dateTime.format(date) : '—';
}

export function formatModerationMoney(value?: number | null): string {
  return typeof value === 'number' && Number.isFinite(value) ? currency.format(value) : '—';
}

export function moderationPartyName(party?: ModerationParty | null): string {
  if (!party) return '—';
  return party.fullName?.trim() || party.email;
}
