import { z } from 'zod';
import { createPasswordSchema } from './passwordSchema';

export function createRegisterSchema(passwordMinLength: number) {
  return z.object({
    fullName: z.string().trim().min(1, 'Vui lòng nhập họ và tên.').max(100, 'Họ tên không được vượt quá 100 ký tự.'),
    email: z.string().trim().min(1, 'Vui lòng nhập email.').max(254, 'Email không được vượt quá 254 ký tự.')
      .regex(/^[^\s@]+@[^\s@]+\.[^\s@]+$/, 'Email không hợp lệ.'),
    phone: z.string().trim().regex(/^(?:0[35789]\d{8}|\+84[35789]\d{8})$/, 'Nhập số di động Việt Nam dạng 09… hoặc +849…'),
    password: createPasswordSchema(passwordMinLength),
    confirmPassword: z.string().min(1, 'Vui lòng nhập lại mật khẩu.'),
    adultConfirmed: z.boolean().refine(Boolean, 'Bạn cần xác nhận đủ 18 tuổi.'),
    termsAccepted: z.boolean().refine(Boolean, 'Bạn cần đồng ý điều khoản sử dụng.'),
    privacyAccepted: z.boolean().refine(Boolean, 'Bạn cần đồng ý chính sách dữ liệu cá nhân.'),
  }).superRefine((values, context) => {
    if (values.password !== values.confirmPassword) {
      context.addIssue({ code: 'custom', path: ['confirmPassword'], message: 'Mật khẩu xác nhận chưa khớp.' });
    }
    const normalizedPhone = values.phone.startsWith('0') ? '+84' + values.phone.slice(1) : values.phone;
    if (values.password.toLowerCase() === values.email.toLowerCase()
      || values.password === values.phone || values.password === normalizedPhone) {
      context.addIssue({ code: 'custom', path: ['password'], message: 'Mật khẩu không được trùng email hoặc số điện thoại.' });
    }
  });
}

export type RegisterFormValues = z.infer<ReturnType<typeof createRegisterSchema>>;
