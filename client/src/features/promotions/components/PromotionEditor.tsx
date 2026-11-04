import { useEffect, useRef, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { promotionApi } from '../../../api/promotionApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { Promotion } from '../../../types/promotion';
import type { PromotionFormValues } from '../schemas/promotionSchema';
import { promotionDefaults, promotionFormToWrite, promotionSchema } from '../schemas/promotionSchema';
import PromotionTargetPicker from './PromotionTargetPicker';
import Input from '../../../components/ui/Input';
import Select from '../../../components/ui/Select';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

interface Props {
  promotion: Promotion | null;
  onCancel: () => void;
  onSaved: (promotion: Promotion) => Promise<void>;
  onReload: () => void;
  onConflict: () => void;
  onBusyChange: (busy: boolean) => void;
}

export default function PromotionEditor({ promotion, onCancel, onSaved, onReload, onConflict, onBusyChange }: Props) {
  const { control, register, watch, setValue, setError, handleSubmit, formState: { errors } } = useForm<PromotionFormValues>({
    resolver: zodResolver(promotionSchema), defaultValues: promotionDefaults(promotion),
  });
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<ApiError | null>(null);
  const [conflict, setConflict] = useState(false);
  const saving = useRef(false);
  const mounted = useRef(true);
  const type = watch('type');
  const target = watch('target');

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  async function save(values: PromotionFormValues) {
    if (saving.current || conflict) return;
    if (promotion && Number(values.usageLimit) < promotion.usedCount) {
      setError('usageLimit', { type: 'validate', message: `Số lượt tối đa không được thấp hơn ${promotion.usedCount} lượt đã dùng.` });
      return;
    }
    saving.current = true; setBusy(true); onBusyChange(true); setFailure(null);
    try {
      const request = promotionFormToWrite(values);
      const response = promotion
        ? await promotionApi.update(promotion.id, { ...request, expectedVersion: promotion.version })
        : await promotionApi.create(request);
      if (mounted.current) await onSaved(response.data);
    } catch (cause) {
      if (!mounted.current) return;
      const error = getApiError(cause);
      setFailure(error);
      for (const field of ['code', 'type', 'value', 'validFrom', 'validTo', 'usageLimit', 'target', 'targetUserIds', 'status'] as const) {
        const message = error.fieldErrors[field];
        if (message) setError(field, { type: 'server', message });
      }
      if (promotion && error.status === 409) { setConflict(true); onConflict(); }
    } finally {
      saving.current = false;
      if (mounted.current) { setBusy(false); onBusyChange(false); }
    }
  }

  return <form className="ui-form promotion-editor" noValidate onSubmit={handleSubmit(values => void save(values))}>
    {failure && <ApiErrorNotice message={failure.message} correlationId={failure.correlationId} />}
    {conflict && <Alert tone="warning" action={<Button variant="secondary" onClick={onReload}>Tải phiên bản mới</Button>}>
      Khuyến mãi đã được cập nhật ở nơi khác. Tải phiên bản mới, kiểm tra lại và nhập lại thay đổi trước khi lưu.
    </Alert>}
    {promotion && <p className="promotion-muted">Đã dùng {promotion.usedCount}/{promotion.usageLimit} lượt. Số lượt đã dùng được hệ thống ghi nhận tự động.</p>}
    <fieldset disabled={busy || conflict} className="promotion-fields">
      <div className="promotion-editor-grid">
        <Input label="Mã khuyến mãi" placeholder="Ví dụ: FPOST2026" maxLength={50} required readOnly={!!promotion}
          hint={promotion ? 'Mã được giữ nguyên khi chỉnh sửa.' : '3–50 ký tự: chữ cái không dấu, chữ số, gạch ngang hoặc gạch dưới. Mã được lưu dạng chữ hoa.'}
          {...register('code')} error={errors.code?.message} />
        <Select label="Hình thức giảm" {...register('type')} error={errors.type?.message}>
          <option value="FIXED_AMOUNT">Giảm số tiền cố định</option><option value="PERCENTAGE">Giảm theo phần trăm</option>
        </Select>
        <Input label={type === 'FIXED_AMOUNT' ? 'Số tiền giảm (đồng)' : 'Tỷ lệ giảm (%)'} required maxLength={20}
          inputMode={type === 'FIXED_AMOUNT' ? 'numeric' : 'decimal'} placeholder={type === 'FIXED_AMOUNT' ? 'Ví dụ: 25000' : 'Ví dụ: 10.5'}
          hint={type === 'FIXED_AMOUNT' ? 'Nhập số đồng nguyên, không dùng dấu phân cách hàng nghìn.' : 'Lớn hơn 0, tối đa 100%. Dùng dấu chấm cho phần thập phân.'}
          {...register('value')} error={errors.value?.message} />
        <Input label="Số lượt tối đa" required inputMode="numeric" maxLength={10}
          hint={promotion ? `Đã sử dụng: ${promotion.usedCount} lượt.` : 'Giới hạn tổng số lượt sử dụng mã.'}
          {...register('usageLimit')} error={errors.usageLimit?.message} />
        <Input label="Bắt đầu (giờ Việt Nam)" type="datetime-local" step={1} required
          {...register('validFrom')} error={errors.validFrom?.message} />
        <Input label="Kết thúc (giờ Việt Nam)" type="datetime-local" step={1} required
          {...register('validTo')} error={errors.validTo?.message} />
        <Controller name="target" control={control} render={({ field }) => <Select label="Đối tượng áp dụng" value={field.value}
          onBlur={field.onBlur} onChange={event => {
            field.onChange(event.target.value);
            if (event.target.value === 'ALL') setValue('targetUserIds', [], { shouldDirty: true, shouldValidate: true });
          }} error={errors.target?.message}>
          <option value="ALL">Tất cả người dùng</option><option value="SELECTED_USERS">Người dùng được chọn</option>
        </Select>} />
        <Select label="Thiết lập hoạt động" {...register('status')} error={errors.status?.message}
          hint="Mã chỉ hoạt động khi còn hạn và còn lượt sử dụng.">
          <option value="ACTIVE">Kích hoạt</option><option value="PAUSED">Tạm dừng</option>
        </Select>
      </div>
      {target === 'SELECTED_USERS' && <Controller name="targetUserIds" control={control} render={({ field }) => <PromotionTargetPicker
        value={field.value} onChange={field.onChange} initialOptions={promotion?.targetUsers ?? []}
        error={errors.targetUserIds?.message} disabled={busy || conflict} />} />}
    </fieldset>
    <div className="ui-form-actions">
      <Button variant="secondary" onClick={onCancel} disabled={busy}>Hủy bỏ</Button>
      <Button type="submit" loading={busy} disabled={conflict} loadingText="Đang lưu…">{promotion ? 'Lưu thay đổi' : 'Tạo khuyến mãi'}</Button>
    </div>
  </form>;
}
