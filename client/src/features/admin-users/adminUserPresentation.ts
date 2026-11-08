import type { AccountRequestStatus, AccountRequestType, AccountRole, AccountStatus } from '../../types/adminUsers';

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'info';

export const roleLabels: Record<AccountRole, string> = {
  SENDER: 'Người gửi', COURIER: 'Người giao', MODERATOR: 'Kiểm duyệt', ADMIN: 'Quản trị',
};

export const accountStatusLabels: Record<AccountStatus, string> = {
  PENDING_VERIFICATION: 'Chờ xác thực', ACTIVE: 'Hoạt động', LOCKED: 'Bị khóa', DELETED: 'Đã xóa',
};

export const accountStatusTones: Record<AccountStatus, Tone> = {
  PENDING_VERIFICATION: 'warning', ACTIVE: 'success', LOCKED: 'danger', DELETED: 'neutral',
};

export const requestTypeLabels: Record<AccountRequestType, string> = {
  DATA_EXPORT: 'Xuất dữ liệu cá nhân', ACCOUNT_DELETION: 'Xóa/ẩn danh tài khoản',
};

export const requestStatusLabels: Record<AccountRequestStatus, string> = {
  PENDING: 'Chờ xử lý', PROCESSING: 'Đang xử lý', COMPLETED: 'Hoàn tất', REJECTED: 'Bị từ chối',
};

const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' });
const currency = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });

export function formatAdminTime(value?: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? dateTime.format(date) : '—';
}

export function formatAdminMoney(value?: number | null): string {
  return typeof value === 'number' && Number.isFinite(value) ? currency.format(value) : '—';
}

export function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
