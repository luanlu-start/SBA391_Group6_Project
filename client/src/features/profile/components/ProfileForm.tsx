import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { UserResponse } from '../../../types/identity';
import { profileService } from '../../../services/profileService';
import { useProfileAction } from '../hooks/useProfileAction';
import { profileSchema } from '../schemas/profileSchema';
import type { ProfileFormValues } from '../schemas/profileSchema';
import Input from '../../../components/ui/Input';
import Textarea from '../../../components/ui/Textarea';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

export default function ProfileForm({ user }: { user: UserResponse }) {
  const { busy, error, notice, run } = useProfileAction();
  const { register, handleSubmit, setError, formState: { errors } } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema), defaultValues: { fullName: user.fullName, dob: user.dob ?? '', bio: user.bio ?? '', emergencyContact: user.emergencyContact ?? '' },
  });
  useEffect(() => {
    for (const name of ['fullName', 'dob', 'bio', 'emergencyContact'] as const) if (error?.fieldErrors[name]) setError(name, { type: 'server', message: error.fieldErrors[name] });
    if (error?.code === 'PROFILE_AGE_INVALID') setError('dob', { type: 'server', message: error.message });
  }, [error, setError]);
  return <form className="ui-form" noValidate aria-busy={busy} onSubmit={handleSubmit(values => run(() => profileService.update({ ...values, dob: values.dob || null }), 'Đã cập nhật hồ sơ.'))}>
    {error && <ApiErrorNotice error={error} />}{notice && <Alert tone="success">{notice}</Alert>}
    <div className="ui-form-grid">
      <Input label="Họ và tên" required autoComplete="name" maxLength={100} disabled={busy} {...register('fullName')} error={errors.fullName?.message} />
      <Input label="Ngày sinh" type="date" autoComplete="bday" disabled={busy} {...register('dob')} error={errors.dob?.message} hint="Thông tin này dùng để đối chiếu khi xác minh danh tính." />
      <Textarea label="Giới thiệu ngắn" maxLength={500} fieldClassName="ui-span-full" disabled={busy} {...register('bio')} error={errors.bio?.message} />
      <Input label="Liên hệ khẩn cấp" maxLength={200} fieldClassName="ui-span-full" disabled={busy} {...register('emergencyContact')} error={errors.emergencyContact?.message} />
    </div><div className="ui-form-actions"><Button type="submit" loading={busy} loadingText="Đang lưu…">Lưu hồ sơ</Button></div>
  </form>;
}
