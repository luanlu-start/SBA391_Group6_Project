import { z } from 'zod';
import { createPasswordSchema } from './passwordSchema';

export const forgotPasswordSchema = z.object({
  identifier: z.string().trim().min(1, 'Vui lòng nhập email hoặc số điện thoại.')
    .max(254, 'Email hoặc số điện thoại vượt quá độ dài cho phép.')
    .regex(/^(?:[^\s@]+@[^\s@]+\.[^\s@]+|0[35789]\d{8}|\+84[35789]\d{8})$/, 'Nhập email hoặc số di động Việt Nam hợp lệ.'),
});

export const resetPasswordSchema = forgotPasswordSchema.extend({
  otp: z.string().regex(/^[0-9]{6}$/, 'Mã OTP gồm đúng 6 chữ số.'),
  newPassword: createPasswordSchema(),
  confirmPassword: z.string().min(1, 'Vui lòng nhập lại mật khẩu mới.'),
}).superRefine((values, context) => {
  if (values.newPassword !== values.confirmPassword) {
    context.addIssue({ code: 'custom', path: ['confirmPassword'], message: 'Mật khẩu xác nhận chưa khớp.' });
  }
  if (values.newPassword.toLowerCase() === values.identifier.toLowerCase()) {
    context.addIssue({ code: 'custom', path: ['newPassword'], message: 'Mật khẩu không được trùng email hoặc số điện thoại.' });
  }
});

export type ForgotPasswordFormValues = z.infer<typeof forgotPasswordSchema>;
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;
