import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { SystemParameter } from '../../../types/parameter';
import { createParameterSchema } from '../schemas/parameterSchema';
import type { ParameterFormValues } from '../schemas/parameterSchema';
import { formatParameterValue } from '../parameterPresentation';
import Input from '../../../components/ui/Input';
import Textarea from '../../../components/ui/Textarea';
import Button from '../../../components/ui/Button';

interface Props {
  parameter: SystemParameter;
  draftValue?: string;
  onCancel: () => void;
  onDraft: (value: string) => void;
}

export default function ParameterEditor({ parameter, draftValue, onCancel, onDraft }: Props) {
  const { register, handleSubmit, watch, formState: { errors } } = useForm<ParameterFormValues>({
    resolver: zodResolver(createParameterSchema(parameter)),
    defaultValues: { value: draftValue ?? parameter.value },
  });
  const proposed = watch('value');
  const hint = parameter.unit?.startsWith('%')
    ? 'Nhập tỷ lệ dạng thập phân: 0.08 tương ứng 8%. Giữ nguyên đơn vị của tham số.'
    : parameter.unit ? `Đơn vị: ${parameter.unit}.` : 'Giữ đầy đủ điều kiện và ý nghĩa của giá trị.';

  return <form className="ui-form" onSubmit={handleSubmit(values => onDraft(values.value))} noValidate>
    <p className="parameter-description">{parameter.description || 'Tham số dùng chung của hệ thống.'}</p>
    <dl className="ui-summary-list">
      <div><dt>Mã tham số</dt><dd><code>{parameter.prmId}</code> · <code>{parameter.key}</code></dd></div>
      <div><dt>Phiên bản hiện tại</dt><dd>{parameter.version}</dd></div>
      <div><dt>Đang áp dụng</dt><dd>{formatParameterValue(parameter)}</dd></div>
      <div><dt>Đề xuất</dt><dd>{proposed.trim() ? formatParameterValue(parameter, proposed) : 'Chưa nhập giá trị'}</dd></div>
    </dl>
    {parameter.valueType === 'TEXT'
      ? <Textarea label="Giá trị đề xuất" rows={4} maxLength={1000} required hint={hint}
        {...register('value')} error={errors.value?.message} />
      : <Input label="Giá trị đề xuất" inputMode={parameter.valueType === 'INTEGER' ? 'numeric' : 'decimal'}
        maxLength={1000} required hint={hint} {...register('value')} error={errors.value?.message} />}
    <div className="ui-form-actions">
      <Button variant="secondary" onClick={onCancel}>Hủy bỏ</Button>
      <Button type="submit">Lưu bản nháp</Button>
    </div>
  </form>;
}
