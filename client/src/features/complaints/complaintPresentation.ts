import type {
  ComplaintOutcome, ComplaintPerson, ComplaintQueueFilter, ComplaintReason, ComplaintRole, ComplaintStatus, EscrowState,
} from '../../types/complaint';

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'info';

export const statusLabels: Record<ComplaintStatus, string> = {
  OPEN: 'Mới mở',
  INVESTIGATING: 'Đang xử lý',
  AWAITING_INFO: 'Chờ bổ sung',
  RESOLVED: 'Đã phán quyết',
  APPEALED: 'Đang kháng nghị',
  CLOSED: 'Đã đóng',
};

export const statusTones: Record<ComplaintStatus, Tone> = {
  OPEN: 'warning',
  INVESTIGATING: 'info',
  AWAITING_INFO: 'warning',
  RESOLVED: 'success',
  APPEALED: 'danger',
  CLOSED: 'neutral',
};

export const queueFilters: Record<ComplaintQueueFilter, string> = {
  ACTIVE: 'Cần xử lý',
  ...statusLabels,
  ALL: 'Tất cả',
};

export const reasonLabels: Record<ComplaintReason, string> = {
  DAMAGED: 'Hàng hư hỏng',
  LOST: 'Mất hàng',
  LATE: 'Giao trễ',
  WRONG_ITEM: 'Giao sai hàng',
  NOT_DELIVERED: 'Không nhận được hàng',
  PAYMENT: 'Thanh toán',
  CONDUCT: 'Thái độ, hành vi',
  OTHER: 'Khác',
};

export const outcomeLabels: Record<ComplaintOutcome, string> = {
  SENDER_FAVOR: 'Hoàn tiền cho người gửi',
  COURIER_FAVOR: 'Trả tiền cho người giao',
  SPLIT: 'Chia ký quỹ theo tỷ lệ',
};

export const roleLabels: Record<ComplaintRole, string> = {
  SENDER: 'Người gửi',
  COURIER: 'Người giao',
  MODERATOR: 'Kiểm duyệt viên',
  ADMIN: 'Quản trị viên',
  SYSTEM: 'Hệ thống',
};

export const escrowLabels: Record<EscrowState, string> = {
  HELD: 'Đang giữ',
  RELEASED: 'Đã giải ngân',
  REFUNDED: 'Đã hoàn người gửi',
  PARTIALLY_REFUNDED: 'Đã chia hai bên',
};

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });
const time = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' });

export const formatMoney = (value: number | null | undefined) => money.format(value ?? 0);
export const formatTime = (value: string | null | undefined) => (value ? time.format(new Date(value)) : '—');
export const personName = (person: ComplaintPerson | null | undefined) => person?.fullName || person?.email || '—';

/** Số tiền hoàn cho người gửi theo phán quyết, cùng cách làm tròn xuống của máy chủ. */
export function refundFor(outcome: ComplaintOutcome, escrowAmount: number, senderPercent: number) {
  if (outcome === 'SENDER_FAVOR') return escrowAmount;
  if (outcome === 'COURIER_FAVOR') return 0;
  return Math.floor((escrowAmount * senderPercent) / 100);
}
