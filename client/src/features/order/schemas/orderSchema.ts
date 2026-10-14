import { z } from 'zod';

export const orderSchema = z.object({
  senderId: z
    .string()
    .trim()
    .min(1, 'Vui lòng nhập ID người gửi.')
    .regex(/^[1-9]\d*$/, 'ID người gửi phải là số nguyên dương.'),
  pickupAddress: z
    .string()
    .trim()
    .min(5, 'Địa chỉ lấy hàng cần ít nhất 5 ký tự.')
    .max(500, 'Địa chỉ lấy hàng không được vượt quá 500 ký tự.'),
  deliveryAddress: z
    .string()
    .trim()
    .min(5, 'Địa chỉ giao hàng cần ít nhất 5 ký tự.')
    .max(500, 'Địa chỉ giao hàng không được vượt quá 500 ký tự.'),
  receiverName: z
    .string()
    .trim()
    .min(2, 'Tên người nhận cần ít nhất 2 ký tự.')
    .max(100, 'Tên người nhận không được vượt quá 100 ký tự.'),
  receiverPhone: z
    .string()
    .trim()
    .min(9, 'Số điện thoại người nhận cần ít nhất 9 ký tự.')
    .max(20, 'Số điện thoại không được vượt quá 20 ký tự.')
    .regex(/^[0-9+() -]+$/, 'Số điện thoại không hợp lệ.'),
  productName: z
    .string()
    .trim()
    .min(2, 'Tên hàng hóa cần ít nhất 2 ký tự.')
    .max(255, 'Tên hàng hóa không được vượt quá 255 ký tự.'),
  productDescription: z
    .string()
    .max(1000, 'Mô tả hàng hóa không được vượt quá 1000 ký tự.')
    .optional(),
  quantity: z
    .string()
    .trim()
    .min(1, 'Vui lòng nhập số lượng kiện.')
    .regex(/^[1-9]\d*$/, 'Số lượng kiện hàng phải là số nguyên tối thiểu là 1.')
    .refine(val => Number(val) <= 100000, 'Số lượng không được vượt quá 100,000 kiện.'),
  weightKg: z
    .string()
    .trim()
    .min(1, 'Vui lòng nhập khối lượng kiện hàng.')
    .regex(/^\d+(\.\d{1,3})?$/, 'Khối lượng phải là số hợp lệ (tối đa 3 chữ số thập phân).')
    .refine(val => Number(val) >= 0.001, 'Khối lượng phải tối thiểu 0.001 kg (1g).'),
  declaredValue: z
    .string()
    .trim()
    .regex(/^(\d+(\.\d{1,2})?)?$/, 'Giá trị khai báo phải là số không âm (tối đa 2 chữ số thập phân).')
    .optional(),
  deliveryNote: z
    .string()
    .max(500, 'Ghi chú giao hàng không được vượt quá 500 ký tự.')
    .optional(),
  imageUrls: z
    .string()
    .optional(),
});

export type OrderFormValues = z.infer<typeof orderSchema>;
