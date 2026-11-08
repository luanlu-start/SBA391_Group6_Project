const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });
const number = new Intl.NumberFormat('vi-VN');
const percent = new Intl.NumberFormat('vi-VN', { style: 'percent', maximumFractionDigits: 1 });
const vietnamDay = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' });

export const formatMoney = (value: number) => money.format(value);
export const formatNumber = (value: number) => number.format(value);
export const formatPercent = (ratio: number) => percent.format(ratio);

/** Ngày yyyy-MM-dd theo giờ Việt Nam, khớp cách máy chủ chia kỳ báo cáo. */
export const isoDay = (date: Date) => vietnamDay.format(date);

export function lastDays(days: number) {
  const today = new Date();
  return { from: isoDay(new Date(today.getTime() - (days - 1) * 86_400_000)), to: isoDay(today) };
}

export function previousMonth() {
  const [year, month] = isoDay(new Date()).split('-').map(Number);
  return month === 1 ? `${year - 1}-12` : `${year}-${String(month - 1).padStart(2, '0')}`;
}
