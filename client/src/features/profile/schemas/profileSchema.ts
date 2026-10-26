import { z } from 'zod';

export const profileSchema = z.object({
  fullName: z.string().trim().min(2, 'Họ tên cần ít nhất 2 ký tự.').max(100, 'Họ tên tối đa 100 ký tự.'),
  dob: z.string().refine(value => !value || /^\d{4}-\d{2}-\d{2}$/.test(value), 'Ngày sinh không hợp lệ.'),
  bio: z.string().max(500, 'Giới thiệu tối đa 500 ký tự.'),
  emergencyContact: z.string().max(200, 'Liên hệ khẩn cấp tối đa 200 ký tự.'),
});
export type ProfileFormValues = z.infer<typeof profileSchema>;
export const addressSchema = z.object({
  label: z.string().trim().min(1, 'Vui lòng đặt tên địa chỉ.').max(60, 'Tên địa chỉ tối đa 60 ký tự.'),
  addressText: z.string().trim().min(5, 'Vui lòng nhập địa chỉ đầy đủ.').max(500, 'Địa chỉ tối đa 500 ký tự.'),
  lat: z.string().min(1, 'Vui lòng chọn vị trí.').refine(value => Number.isFinite(Number(value)) && Number(value) >= -90 && Number(value) <= 90, 'Vĩ độ phải từ -90 đến 90.'),
  lng: z.string().min(1, 'Vui lòng chọn vị trí.').refine(value => Number.isFinite(Number(value)) && Number(value) >= -180 && Number(value) <= 180, 'Kinh độ phải từ -180 đến 180.'),
  isDefault: z.boolean(),
});
export type AddressFormValues = z.infer<typeof addressSchema>;
export const contactSchema = z.object({
  type: z.enum(['EMAIL', 'PHONE']), value: z.string().trim().min(1, 'Vui lòng nhập thông tin mới.').max(254),
  currentPassword: z.string().min(1, 'Vui lòng nhập mật khẩu hiện tại.').max(72),
}).superRefine((value, context) => {
  const valid = value.type === 'EMAIL' ? /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.value) : /^(0[35789]\d{8}|\+84[35789]\d{8})$/.test(value.value);
  if (!valid) context.addIssue({ code: 'custom', path: ['value'], message: value.type === 'EMAIL' ? 'Email không hợp lệ.' : 'Số di động Việt Nam không hợp lệ.' });
});
export type ContactFormValues = z.infer<typeof contactSchema>;
export const contactOtpSchema = z.object({ otp: z.string().regex(/^\d{6}$/, 'Mã OTP gồm 6 chữ số.') });
