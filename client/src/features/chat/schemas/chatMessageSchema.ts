import { z } from 'zod';

export const chatMessageSchema = z.object({
  content: z
    .string()
    .max(2000, 'Tin nhắn không được vượt quá 2.000 ký tự.')
    .trim()
    .min(1, 'Vui lòng nhập nội dung tin nhắn.'),
});

export type ChatMessageFormValues = z.infer<typeof chatMessageSchema>;