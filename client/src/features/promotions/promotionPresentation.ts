import type { Promotion, PromotionEffectiveStatus, PromotionWrite } from '../../types/promotion';

export const promotionStatusLabels: Record<PromotionEffectiveStatus, string> = {
  ACTIVE: 'Đang hoạt động', PAUSED: 'Tạm dừng', SCHEDULED: 'Chưa đến hạn', EXPIRED: 'Đã hết hạn', EXHAUSTED: 'Đã hết lượt',
};

export const promotionStatusTones = {
  ACTIVE: 'success', PAUSED: 'neutral', SCHEDULED: 'info', EXPIRED: 'danger', EXHAUSTED: 'warning',
} as const;

const currencyFormat = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });

export function formatPromotionValue(promotion: Pick<Promotion, 'type' | 'value'>) {
  if (promotion.type === 'PERCENTAGE') return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(Number(promotion.value))}%`;
  const integer = promotion.value.replace(/\.0+$/, '');
  return /^\d+$/.test(integer) ? currencyFormat.format(BigInt(integer)) : promotion.value;
}

export function instantToVietnamInput(value: string) {
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? new Date(date.getTime() + 7 * 60 * 60 * 1000).toISOString().slice(0, 19) : '';
}

export function promotionToWrite(promotion: Promotion): PromotionWrite {
  return {
    code: promotion.code, type: promotion.type, value: promotion.value,
    validFrom: promotion.validFrom, validTo: promotion.validTo, usageLimit: promotion.usageLimit,
    target: promotion.target, targetUserIds: [...promotion.targetUserIds], status: promotion.status,
  };
}
