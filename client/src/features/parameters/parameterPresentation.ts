import type { SystemParameter } from '../../types/parameter';

const numberFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 10 });
const currencyFormat = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });
const dateFormat = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' });

export function formatParameterValue(parameter: SystemParameter, value = parameter.value) {
  if (parameter.valueType === 'TEXT') return value;
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return value;
  if (parameter.unit?.startsWith('%')) return `${numberFormat.format(numeric * 100)}${parameter.unit}`;
  return `${numberFormat.format(numeric)}${parameter.unit ? ` ${parameter.unit}` : ''}`;
}

export const formatMoney = (value: number) => currencyFormat.format(value);
export const formatRatio = (value: number) => `${numberFormat.format(value * 100)}%`;
export function formatDate(value: string) {
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? dateFormat.format(date) : 'Chưa có thời điểm';
}

export function formatParameterEffectiveFrom(parameter: SystemParameter) {
  return parameter.version === 0 ? 'Giá trị ban đầu' : formatDate(parameter.effectiveFrom);
}

export function normalizeSearch(value: string) {
  return value.toLocaleLowerCase('vi-VN').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd');
}
