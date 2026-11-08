import type { ViolationAction, ViolationCaseStatus, ViolationParty, ViolationReason, ViolationTargetType } from '../../types/violationReport';

export const reasonLabels: Record<ViolationReason, string> = {
  FRAUD: 'Lừa đảo, gian lận',
  HARASSMENT: 'Quấy rối, xúc phạm',
  PROHIBITED_GOODS: 'Hàng cấm',
  OFF_PLATFORM_DEAL: 'Giao dịch ngoài nền tảng',
  SPAM: 'Spam, quảng cáo',
  OTHER: 'Lý do khác',
};

export const targetLabels: Record<ViolationTargetType, string> = {
  ORDER: 'Đơn hàng', USER: 'Người dùng', MESSAGE: 'Tin nhắn',
};

export const caseStatusLabels: Record<ViolationCaseStatus, string> = {
  OPEN: 'Chờ xử lý', RESOLVED: 'Đã xử lý', DISMISSED: 'Đã bác',
};

export const actionLabels: Record<ViolationAction, string> = {
  WARN: 'Cảnh cáo', ESCALATE: 'Cảnh cáo và đề nghị khóa', DISMISS: 'Không vi phạm',
};

const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' });

export function formatViolationTime(value?: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? dateTime.format(date) : '—';
}

export function violationPartyName(party?: ViolationParty | null): string {
  if (!party) return '—';
  return party.fullName?.trim() || party.email;
}
