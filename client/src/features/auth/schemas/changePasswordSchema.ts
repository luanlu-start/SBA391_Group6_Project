import { z } from 'zod';
import { createPasswordSchema } from './passwordSchema';

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, 'Vui lòng nhập mật khẩu hiện tại.')
    .refine(value => new TextEncoder().encode(value).length <= 72, 'Mật khẩu hiện tại vượt giới hạn cho phép.'),
  newPassword: createPasswordSchema(),
  confirmPassword: z.string().min(1, 'Vui lòng nhập lại mật khẩu mới.'),
}).superRefine((values, context) => {
  if (values.newPassword !== values.confirmPassword) {
    context.addIssue({ code: 'custom', path: ['confirmPassword'], message: 'Mật khẩu xác nhận chưa khớp.' });
  }
  if (values.newPassword === values.currentPassword) {
    context.addIssue({ code: 'custom', path: ['newPassword'], message: 'Mật khẩu mới phải khác mật khẩu hiện tại.' });
  }
});

export type ChangePasswordFormValues = z.infer<typeof changePasswordSchema>;
