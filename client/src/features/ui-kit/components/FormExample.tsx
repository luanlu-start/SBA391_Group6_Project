import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ArrowRight, UserRound, Phone } from 'lucide-react';
import Input from '../../../components/ui/Input';
import Select from '../../../components/ui/Select';
import Textarea from '../../../components/ui/Textarea';
import Checkbox from '../../../components/ui/Checkbox';
import Button from '../../../components/ui/Button';
import FormSection from '../../../components/ui/FormSection';
import SectionCard from '../../../components/ui/SectionCard';
import Alert from '../../../components/ui/Alert';
import Badge from '../../../components/ui/Badge';

const schema = z.object({
  recipient: z.string().trim().min(2, 'Vui lòng nhập họ tên người nhận.').max(100),
  phone: z.string().regex(/^0\d{9}$/, 'Nhập số điện thoại gồm 10 chữ số, bắt đầu bằng 0.'),
  address: z.string().trim().min(10, 'Vui lòng nhập địa chỉ đầy đủ.').max(200),
  service: z.enum(['standard', 'express']),
  weight: z.string().refine(value => Number(value) > 0 && Number(value) <= 30, 'Nhập khối lượng từ trên 0 đến 30 kg.'),
  note: z.string().max(500, 'Ghi chú tối đa 500 ký tự.'),
  fragile: z.boolean(),
});
type FormValues = z.infer<typeof schema>;
const defaults: FormValues = { recipient: '', phone: '', address: '', service: 'standard', weight: '', note: '', fragile: false };

export default function FormExample() {
  const [submitted, setSubmitted] = useState(false);
  const { register, handleSubmit, reset, watch, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema), defaultValues: defaults,
  });
  const values = watch();
  return <div className="ui-showcase-grid">
    <SectionCard title="Thông tin giao hàng" description="Form mẫu · React Hook Form + Zod">
      <form className="ui-form" noValidate onChange={() => setSubmitted(false)} onSubmit={handleSubmit(() => setSubmitted(true))}>
        {submitted && <Alert tone="success" title="Dữ liệu hợp lệ">Form mẫu đã qua kiểm tra. Chưa tạo đơn hay gửi dữ liệu lên API.</Alert>}
        <FormSection title="Người nhận" description="Thông tin liên hệ để giao bưu kiện.">
          <Input label="Họ và tên" placeholder="Nguyễn Văn An" required icon={<UserRound size={17} />}
            {...register('recipient')} error={errors.recipient?.message} />
          <Input label="Số điện thoại" placeholder="0901234567" type="tel" required icon={<Phone size={17} />}
            {...register('phone')} error={errors.phone?.message} />
          <Input label="Địa chỉ nhận hàng" placeholder="Số nhà, đường, phường/xã, tỉnh/thành phố"
            fieldClassName="ui-span-full" required {...register('address')} error={errors.address?.message} />
        </FormSection>
        <FormSection title="Bưu kiện" description="Phân nhóm trường theo nội dung, dùng một hoặc hai cột.">
          <Select label="Dịch vụ" required {...register('service')} error={errors.service?.message}>
            <option value="standard">Giao tiêu chuẩn</option><option value="express">Giao nhanh</option>
          </Select>
          <Input label="Khối lượng (kg)" type="number" placeholder="0.5" step="0.1" min="0.1" max="30" required
            hint="Khối lượng minh họa tối đa 30 kg." {...register('weight')} error={errors.weight?.message} />
          <Textarea label="Ghi chú" placeholder="Hướng dẫn giao hàng, mô tả bưu kiện…" hint="Không bắt buộc. Tối đa 500 ký tự."
            fieldClassName="ui-span-full" {...register('note')} error={errors.note?.message} />
          <Checkbox label="Bưu kiện dễ vỡ, cần xử lý cẩn thận." {...register('fragile')} className="ui-span-full" />
        </FormSection>
        <div className="ui-form-actions">
          <Button variant="secondary" onClick={() => { reset(defaults); setSubmitted(false); }}>Làm lại</Button>
          <Button type="submit" icon={<ArrowRight size={17} />}>Kiểm tra form mẫu</Button>
        </div>
      </form>
    </SectionCard>
    <div className="ui-stack">
      <SectionCard title="Tóm tắt nhập liệu" actions={<Badge tone="info">Bản mẫu</Badge>}>
        <dl className="ui-summary-list">
          <div><dt>Người nhận</dt><dd>{values.recipient || 'Chưa nhập'}</dd></div>
          <div><dt>Dịch vụ</dt><dd>{values.service === 'standard' ? 'Tiêu chuẩn' : 'Giao nhanh'}</dd></div>
          <div><dt>Khối lượng</dt><dd>{values.weight ? `${values.weight} kg` : 'Chưa nhập'}</dd></div>
          <div><dt>Dễ vỡ</dt><dd>{values.fragile ? 'Có' : 'Không'}</dd></div>
        </dl>
      </SectionCard>
      <SectionCard title="Cách ghép một form" description="Dùng cùng cấu trúc ở mỗi module.">
        <pre className="ui-code-example">{`Page\n  → Form component\n    → Input / Select / Textarea\n    → schema Zod\n    → hook gọi API`}</pre>
      </SectionCard>
    </div>
  </div>;
}
