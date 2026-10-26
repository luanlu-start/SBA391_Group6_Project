import { z } from 'zod';

export const kycSchema = z.object({
  idNumber: z.string().regex(/^\d{12}$/, 'Số CCCD phải có đúng 12 chữ số.'),
  issuedDate: z.string().min(1, 'Vui lòng chọn ngày cấp.'),
  expiryDate: z.string().min(1, 'Vui lòng chọn ngày hết hạn.'),
  consentAccepted: z.boolean().refine(value => value, 'Bạn cần đồng ý xử lý dữ liệu xác minh.'),
}).refine(value => !value.issuedDate || value.issuedDate <= new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' }), {
  path: ['issuedDate'], message: 'Ngày cấp không được ở tương lai.',
}).refine(value => !value.expiryDate || value.expiryDate >= new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' }), {
  path: ['expiryDate'], message: 'CCCD phải còn hiệu lực.',
}).refine(value => !value.issuedDate || !value.expiryDate || value.expiryDate >= value.issuedDate, {
  path: ['expiryDate'], message: 'Ngày hết hạn không được trước ngày cấp.',
});
export type KycFormValues = z.infer<typeof kycSchema>;
