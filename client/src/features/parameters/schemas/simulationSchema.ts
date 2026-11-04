import { z } from 'zod';

const decimal = (label: string, positive = false) => z.string().trim().min(1, `Nhập ${label}.`)
  .refine(value => /^\d+(\.\d+)?$/.test(value) && Number.isFinite(Number(value))
    && (!positive || Number(value) > 0), `Nhập ${label} là số ${positive ? 'lớn hơn 0' : 'không âm'}; dùng dấu chấm cho phần thập phân.`);
const money = (label: string) => z.string().trim().min(1, `Nhập ${label}.`)
  .refine(value => /^\d+$/.test(value) && Number.isSafeInteger(Number(value)), `${label} phải là số nguyên không âm, không dùng dấu phân cách.`);

export const simulationSchema = z.object({
  distanceKm: decimal('quãng đường'),
  weightKg: decimal('khối lượng', true),
  longestSideCm: decimal('cạnh dài nhất', true),
  urgency: z.enum(['NORMAL', 'SAMEDAY', 'URGENT']),
  declaredValue: money('Giá trị khai báo'),
  insured: z.boolean(),
  courierTier: z.enum(['NEW', 'BRONZE', 'SILVER', 'GOLD', 'DIAMOND']),
  agreedFareRatio: decimal('tỷ lệ giá thỏa thuận', true),
  promotionDiscount: money('Giảm giá'),
});

export type SimulationFormValues = z.infer<typeof simulationSchema>;
