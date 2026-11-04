import { z } from 'zod';

export const loginSchema = z.object({
  identifier: z.string()
    .trim()
    .min(1, 'Vui lòng nhập email hoặc số điện thoại.')
    .max(254, 'Email hoặc số điện thoại vượt quá độ dài cho phép.'),
  password: z.string()
    .min(1, 'Vui lòng nhập mật khẩu.')
    .max(72, 'Mật khẩu không được vượt quá 72 ký tự.'),
  rememberMe: z.boolean(),
});

export type LoginFormValues = z.infer<typeof loginSchema>;
