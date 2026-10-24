import { z } from 'zod';

export const productSchema = z.object({
  name: z.string().trim().min(2, 'Tên sản phẩm cần ít nhất 2 ký tự.').max(100, 'Tối đa 100 ký tự.'),
  description: z.string().max(500, 'Mô tả tối đa 500 ký tự.'),
  price: z.string().regex(/^\d{1,17}(\.\d{1,2})?$/, 'Nhập giá không âm, tối đa 2 chữ số thập phân.'),
  stock: z.string().regex(/^\d+$/, 'Số lượng phải là số nguyên không âm.')
    .refine(value => Number(value) <= 2147483647, 'Số lượng vượt giới hạn cho phép.'),
  category: z.string().trim().min(1, 'Vui lòng chọn danh mục.').max(100),
  status: z.enum(['ACTIVE', 'INACTIVE']),
});

export type ProductFormValues = z.infer<typeof productSchema>;
