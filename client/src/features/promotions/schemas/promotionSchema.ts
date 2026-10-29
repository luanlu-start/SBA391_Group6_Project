import { z } from 'zod';
import type { Promotion, PromotionWrite } from '../../../types/promotion';
import { vietnamDateTimeToIso } from '../../audit/auditPresentation';
import { instantToVietnamInput } from '../promotionPresentation';

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const dateTime = z.string().refine(value => !!value && !!vietnamDateTimeToIso(value), 'Chọn thời điểm hợp lệ theo giờ Việt Nam.');

export const promotionSchema = z.object({
  code: z.string().trim().min(3, 'Mã khuyến mãi phải có ít nhất 3 ký tự.').max(50, 'Mã khuyến mãi tối đa 50 ký tự.')
    .regex(/^[a-zA-Z0-9_-]+$/, 'Mã chỉ gồm chữ cái không dấu, chữ số, dấu gạch ngang hoặc gạch dưới.'),
  type: z.enum(['FIXED_AMOUNT', 'PERCENTAGE']),
  value: z.string().trim().min(1, 'Nhập giá trị giảm.').max(20, 'Giá trị giảm quá dài.'),
  validFrom: dateTime,
  validTo: dateTime,
  usageLimit: z.string().trim().refine(value => /^[1-9]\d*$/.test(value) && Number(value) <= 2147483647,
    'Số lượt phải là số nguyên từ 1 đến 2.147.483.647.'),
  target: z.enum(['ALL', 'SELECTED_USERS']),
  targetUserIds: z.array(z.string().regex(uuid, 'Chọn người dùng hợp lệ trong danh sách.')).max(100, 'Chọn tối đa 100 người dùng.'),
  status: z.enum(['ACTIVE', 'PAUSED']),
}).superRefine((values, context) => {
  if (values.type === 'FIXED_AMOUNT') {
    if (!/^\d+$/.test(values.value) || BigInt(values.value) < 1n || BigInt(values.value) > 9999999999999999n) context.addIssue({
      code: 'custom', path: ['value'], message: 'Nhập số tiền nguyên từ 1 đến 9.999.999.999.999.999 đồng.',
    });
  } else if (!/^\d+(?:\.\d{1,2})?$/.test(values.value) || Number(values.value) <= 0 || Number(values.value) > 100) context.addIssue({
    code: 'custom', path: ['value'], message: 'Tỷ lệ phải lớn hơn 0, tối đa 100%, với tối đa 2 chữ số thập phân.',
  });
  const from = vietnamDateTimeToIso(values.validFrom);
  const to = vietnamDateTimeToIso(values.validTo);
  if (from && to && from >= to) context.addIssue({ code: 'custom', path: ['validTo'], message: 'Thời điểm kết thúc phải sau thời điểm bắt đầu.' });
  if (values.target === 'SELECTED_USERS' && !values.targetUserIds.length) context.addIssue({
    code: 'custom', path: ['targetUserIds'], message: 'Chọn ít nhất một người dùng được áp dụng.',
  });
});

export type PromotionFormValues = z.infer<typeof promotionSchema>;

export function promotionDefaults(promotion: Promotion | null): PromotionFormValues {
  return promotion ? {
    code: promotion.code, type: promotion.type,
    value: promotion.type === 'FIXED_AMOUNT' ? promotion.value.replace(/\.0+$/, '') : promotion.value,
    validFrom: instantToVietnamInput(promotion.validFrom), validTo: instantToVietnamInput(promotion.validTo),
    usageLimit: String(promotion.usageLimit), target: promotion.target, targetUserIds: [...promotion.targetUserIds], status: promotion.status,
  } : { code: '', type: 'FIXED_AMOUNT', value: '', validFrom: '', validTo: '', usageLimit: '100', target: 'ALL', targetUserIds: [], status: 'ACTIVE' };
}

export function promotionFormToWrite(values: PromotionFormValues): PromotionWrite {
  return {
    code: values.code.trim().toUpperCase(), type: values.type, value: values.value.trim(),
    validFrom: vietnamDateTimeToIso(values.validFrom)!, validTo: vietnamDateTimeToIso(values.validTo)!,
    usageLimit: Number(values.usageLimit), target: values.target,
    targetUserIds: values.target === 'ALL' ? [] : values.targetUserIds, status: values.status,
  };
}
