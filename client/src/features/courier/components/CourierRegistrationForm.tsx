import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { courierSchema, vehicleNames, weekDays } from '../schemas/courierSchema';
import type { CourierFormValues } from '../schemas/courierSchema';
import { courierService } from '../../../services/courierService';
import { useProfileAction } from '../../profile/hooks/useProfileAction';
import Input from '../../../components/ui/Input';
import Select from '../../../components/ui/Select';
import Checkbox from '../../../components/ui/Checkbox';
import Button from '../../../components/ui/Button';
import Modal from '../../../components/ui/Modal';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

export default function CourierRegistrationForm({ commitmentVersion, onRegistered }: { commitmentVersion: string; onRegistered: () => void }) {
  const { run, busy, error, retryAfterSeconds } = useProfileAction();
  const [policyOpen, setPolicyOpen] = useState(false);
  const { register, handleSubmit, setError, formState: { errors } } = useForm<CourierFormValues>({ resolver: zodResolver(courierSchema),
    defaultValues: { vehicleType: 'MOTORBIKE', areas: '', days: [], start: '08:00', end: '17:00', commitmentAccepted: false } });
  useEffect(() => {
    for (const name of ['vehicleType', 'areas', 'commitmentAccepted'] as const) if (error?.fieldErrors[name]) setError(name, { type: 'server', message: error.fieldErrors[name] });
    if (error?.code === 'COURIER_HOURS_INVALID') setError('end', { type: 'server', message: error.message });
  }, [error, setError]);
  async function submit(values: CourierFormValues) {
    const result = await run(() => courierService.register({ vehicleType: values.vehicleType, areas: values.areas.split(',').map(item => item.trim()),
      availableHours: values.days.map(day => ({ day, start: values.start, end: values.end })), commitmentAccepted: values.commitmentAccepted, commitmentVersion }));
    if (result) onRegistered();
  }
  return <><form className="ui-form" noValidate onSubmit={handleSubmit(submit)} aria-busy={busy}>
    {error && <ApiErrorNotice error={error} />}
    <div className="ui-form-grid"><Select label="Phương tiện" required disabled={busy} {...register('vehicleType')} error={errors.vehicleType?.message}>
      {Object.entries(vehicleNames).map(([value, title]) => <option key={value} value={value}>{title}</option>)}</Select>
      <Input label="Khu vực hoạt động" required disabled={busy} placeholder="Ninh Kiều, Bình Thủy, Cần Thơ" hint="Phân cách các khu vực bằng dấu phẩy." {...register('areas')} error={errors.areas?.message} />
    </div><fieldset className="courier-days"><legend>Ngày thường rảnh <span aria-hidden="true">*</span></legend><div className="ui-form-actions">
      {weekDays.map(day => <Checkbox key={day.value} label={day.label} value={day.value} disabled={busy} {...register('days')} />)}
    </div>{errors.days && <p role="alert" className="ui-field-error">{errors.days.message}</p>}</fieldset>
    <div className="ui-form-grid"><Input label="Giờ bắt đầu" required type="time" disabled={busy} {...register('start')} error={errors.start?.message} />
      <Input label="Giờ kết thúc" required type="time" disabled={busy} {...register('end')} error={errors.end?.message} hint="Khung giờ áp dụng cho các ngày đã chọn, theo giờ Việt Nam." /></div>
    <Checkbox disabled={busy} {...register('commitmentAccepted')} error={errors.commitmentAccepted?.message}
      label={<>Tôi đã đọc và đồng ý <button type="button" className="ui-text-link" onClick={() => setPolicyOpen(true)}>cam kết trách nhiệm người giao</button>.</>} />
    <p className="ui-muted">Bạn vẫn có thể gửi hàng và chuyển qua lại hai chế độ sau khi đăng ký.</p>
    <div className="ui-form-actions"><Button type="submit" loading={busy} loadingText="Đang đăng ký…" disabled={retryAfterSeconds > 0}>Đăng ký làm người giao</Button></div>
    {retryAfterSeconds > 0 && <p role="status">Vui lòng chờ {retryAfterSeconds} giây để thử lại.</p>}
  </form><Modal isOpen={policyOpen} onClose={() => setPolicyOpen(false)} title="Cam kết trách nhiệm người giao"><div className="ui-stack">
    <p>Tôi cung cấp thông tin trung thực, sử dụng phương tiện phù hợp và tuân thủ quy định an toàn giao thông.</p>
    <p>Tôi chỉ nhận hàng được phép vận chuyển, bảo quản hàng cẩn thận, giao đúng người và thực hiện đầy đủ các bước xác nhận lấy/giao hàng.</p>
    <p>Tôi bảo vệ thông tin người gửi và người nhận, không sử dụng dữ liệu ngoài mục đích giao hàng, đồng thời phối hợp xử lý khi có sự cố hoặc khiếu nại.</p>
    <p>Tôi hiểu trách nhiệm ký quỹ, bồi thường và xử lý vi phạm theo điều khoản của FPost.</p><Button onClick={() => setPolicyOpen(false)}>Đã đọc</Button>
  </div></Modal></>;
}
