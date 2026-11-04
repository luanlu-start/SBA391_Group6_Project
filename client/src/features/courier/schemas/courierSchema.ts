import { z } from 'zod';

export const weekDays = [
  { value: 'MONDAY', label: 'Thứ hai' }, { value: 'TUESDAY', label: 'Thứ ba' }, { value: 'WEDNESDAY', label: 'Thứ tư' },
  { value: 'THURSDAY', label: 'Thứ năm' }, { value: 'FRIDAY', label: 'Thứ sáu' }, { value: 'SATURDAY', label: 'Thứ bảy' }, { value: 'SUNDAY', label: 'Chủ nhật' },
] as const;
export const vehicleNames = { BICYCLE: 'Xe đạp', MOTORBIKE: 'Xe máy', CAR: 'Ô tô' };
export const courierSchema = z.object({
  vehicleType: z.enum(['BICYCLE', 'MOTORBIKE', 'CAR']),
  areas: z.string().trim().min(1, 'Vui lòng nhập khu vực hoạt động.').max(809, 'Tối đa 10 khu vực, mỗi khu vực tối đa 80 ký tự.')
    .refine(value => { const areas = value.split(',').map(item => item.trim()); return areas.length <= 10 && areas.every(item => item.length > 0 && item.length <= 80)
      && new Set(areas.map(item => item.toLocaleLowerCase('vi-VN'))).size === areas.length; }, 'Nhập 1–10 khu vực khác nhau, mỗi khu vực tối đa 80 ký tự.'),
  days: z.array(z.enum(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'])).min(1, 'Vui lòng chọn ít nhất một ngày.'),
  start: z.string().regex(/^\d{2}:\d{2}$/, 'Vui lòng chọn giờ bắt đầu.'),
  end: z.string().regex(/^\d{2}:\d{2}$/, 'Vui lòng chọn giờ kết thúc.'),
  commitmentAccepted: z.boolean().refine(value => value, 'Bạn cần đồng ý cam kết trách nhiệm.'),
}).refine(value => value.start < value.end, { path: ['end'], message: 'Giờ kết thúc phải sau giờ bắt đầu trong cùng ngày.' });
export type CourierFormValues = z.infer<typeof courierSchema>;
