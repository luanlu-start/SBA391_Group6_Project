import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Eye, EyeOff, LockKeyhole } from 'lucide-react';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import { useChangePassword } from '../hooks/useChangePassword';
import { changePasswordSchema } from '../schemas/changePasswordSchema';
import type { ChangePasswordFormValues } from '../schemas/changePasswordSchema';

const fields = [
  { name: 'currentPassword', label: 'Mật khẩu hiện tại', autoComplete: 'current-password' },
  { name: 'newPassword', label: 'Mật khẩu mới', autoComplete: 'new-password' },
  { name: 'confirmPassword', label: 'Nhập lại mật khẩu mới', autoComplete: 'new-password' },
] as const;

export default function ChangePasswordForm() {
  const [visible, setVisible] = useState({ currentPassword: false, newPassword: false, confirmPassword: false });
  const { changePassword, isPending, error, retryAfterSeconds } = useChangePassword();
  const { register, handleSubmit, setError, formState: { errors } } = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });
  useEffect(() => {
    for (const { name } of fields) {
      const message = error?.fieldErrors[name];
      if (message) setError(name, { type: 'server', message });
    }
    if (error?.code === 'AUTH_CURRENT_PASSWORD_INCORRECT') setError('currentPassword', { type: 'server', message: error.message }, { shouldFocus: true });
    if (error?.code === 'AUTH_PASSWORD_UNCHANGED' || error?.code === 'AUTH_PASSWORD_INVALID') {
      setError('newPassword', { type: 'server', message: error.message }, { shouldFocus: true });
    }
  }, [error, setError]);

  return <form className="ui-form" onSubmit={handleSubmit(changePassword)} noValidate aria-busy={isPending}>
    {error && !['AUTH_CURRENT_PASSWORD_INCORRECT', 'AUTH_PASSWORD_UNCHANGED', 'AUTH_PASSWORD_INVALID'].includes(error.code ?? '')
      && <ApiErrorNotice error={error} />}
    <div className="ui-form-grid">
      {fields.map(({ name, label, autoComplete }) => <Input key={name} id={`change-${name}`} label={label}
        fieldClassName={name === 'currentPassword' ? 'ui-span-full' : undefined} autoComplete={autoComplete}
        type={visible[name] ? 'text' : 'password'} maxLength={72} required disabled={isPending}
        hint={name === 'newPassword' ? 'Ít nhất 8 ký tự, có chữ hoa, chữ thường và chữ số.' : undefined}
        icon={<LockKeyhole size={18} />} {...register(name)} error={errors[name]?.message}
        endAdornment={<Button variant="ghost" size="sm" disabled={isPending}
          onClick={() => setVisible(value => ({ ...value, [name]: !value[name] }))}
          aria-label={`${visible[name] ? 'Ẩn' : 'Hiện'} ${label.toLowerCase()}`} aria-pressed={visible[name]}
          icon={visible[name] ? <EyeOff size={18} /> : <Eye size={18} />} />} />)}
    </div>
    <p className="ui-muted">Đổi mật khẩu thành công sẽ đăng xuất tất cả phiên, bao gồm phiên đang dùng.
      Bạn cần đăng nhập lại bằng mật khẩu mới.</p>
    {retryAfterSeconds > 0 && <p className="ui-muted" role="status">Có thể thử lại sau {retryAfterSeconds} giây.</p>}
    <div className="ui-form-actions">
      <Link className="auth-text-link" to="/forgot">Quên mật khẩu hiện tại?</Link>
      <Button type="submit" loading={isPending} disabled={retryAfterSeconds > 0}
        loadingText="Đang đổi mật khẩu…" icon={<LockKeyhole size={17} />}>Đổi mật khẩu</Button>
    </div>
  </form>;
}
