import type { CourierTierCode, TrustComponentKey, TrustScoreReason } from '../../types/trust';

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'info';

export const tierLabels: Record<CourierTierCode, string> = {
  NEW: 'Tân binh',
  BRONZE: 'Đồng',
  SILVER: 'Bạc',
  GOLD: 'Vàng',
  DIAMOND: 'Kim cương',
};

export const tierTones: Record<CourierTierCode, Tone> = {
  NEW: 'neutral',
  BRONZE: 'warning',
  SILVER: 'info',
  GOLD: 'warning',
  DIAMOND: 'success',
};

export const componentLabels: Record<TrustComponentKey, { title: string; hint: string }> = {
  ONTIME: { title: 'Giao đúng hạn', hint: 'Tỷ lệ đơn giao trước hạn cam kết' },
  RATING: { title: 'Đánh giá trung bình', hint: 'Điểm sao người gửi chấm sau khi hoàn tất' },
  COMPLETION: { title: 'Hoàn thành đơn đã nhận', hint: 'Đơn hoàn tất so với đơn đã nhận rồi hủy trong 90 ngày' },
  COMPLAINT: { title: 'Không bị xử thua khiếu nại', hint: 'Khiếu nại có phán quyết bất lợi làm giảm chỉ số này' },
};

export const reasonLabels: Record<TrustScoreReason, string> = {
  NIGHTLY: 'Tính lại hằng đêm',
  COMPLAINT_CLOSED: 'Khiếu nại kết thúc',
  RATING: 'Có đánh giá mới',
  ORDER_COMPLETED: 'Hoàn tất đơn',
  CANCELLATION: 'Hủy đơn đã nhận',
  ADMIN: 'Quản trị viên',
};

const decimal = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 });
const percent = new Intl.NumberFormat('vi-VN', { style: 'percent', maximumFractionDigits: 1 });
const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });
const time = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Ho_Chi_Minh',
});

export const formatScore = (value: number) => decimal.format(value);
export const formatPercent = (value: number) => percent.format(value);
export const formatMoney = (value: number) => money.format(value);
export const formatTime = (value: string | null | undefined) => (value ? time.format(new Date(value)) : '—');
