import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowRight, Eye, EyeOff, KeyRound, LockKeyhole, UserRound } from 'lucide-react';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import { usePasswordRecovery } from '../hooks/usePasswordRecovery';
import { resetPasswordSchema } from '../schemas/passwordRecoverySchema';
import type { ResetPasswordFormValues } from '../schemas/passwordRecoverySchema';

interface Props { initialIdentifier?: string; resendAvailableAt?: string }

export default function ResetPasswordForm({ initialIdentifier = '', resendAvailableAt }: Props) {
  const [showPassword, setShowPassword] = useState(false);
  const [requested, setRequested] = useState(Boolean(initialIdentifier));
  const { resetPassword, requestOtp, isPending, error, resendAfterSeconds, retryAfterSeconds } = usePasswordRecovery(resendAvailableAt);
  const { register, handleSubmit, setError, getValues, trigger, formState: { errors } } = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema), defaultValues: { identifier: initialIdentifier, otp: '', newPassword: '', confirmPassword: '' },
  });
  useEffect(() => {
    for (const field of ['identifier', 'otp', 'newPassword'] as const) {
      const message = error?.fieldErrors[field];
      if (message) setError(field, { type: 'server', message });
    }
  }, [error, setError]);

  async function resend() {
    if (await trigger('identifier')) {
      if (await requestOtp(getValues('identifier').trim())) setRequested(true);
    }
  }
  const cooldown = Math.max(resendAfterSeconds, retryAfterSeconds);
  return <form className="login-form" onSubmit={handleSubmit(resetPassword)} noValidate aria-busy={isPending}>
    {requested && <Alert tone="info">Nếu thông tin khớp với tài khoản, mã OTP sẽ được gửi đến email đã đăng ký.
      Kiểm tra hộp thư đến và thư rác. Mã có hiệu lực 5 phút.</Alert>}
    {error && <ApiErrorNotice error={error} />}
    <Input id="reset-identifier" label="Email hoặc số điện thoại" autoComplete="username" maxLength={254}
      required disabled={isPending} icon={<UserRound size={18} />} {...register('identifier')} error={errors.identifier?.message} />
    <Input id="reset-otp" label="Mã OTP" autoComplete="one-time-code" inputMode="numeric" maxLength={6}
      placeholder="Nhập 6 chữ số" required disabled={isPending} icon={<KeyRound size={18} />}
      {...register('otp')} error={errors.otp?.message} />
    <div className="recovery-resend">
      <span>Chưa nhận được mã?</span>
      <Button variant="ghost" size="sm" disabled={isPending || cooldown > 0} onClick={resend}>
        {cooldown > 0 ? `Gửi lại sau ${cooldown}s` : 'Gửi lại mã'}
      </Button>
    </div>
    <Input id="reset-password" label="Mật khẩu mới" autoComplete="new-password" type={showPassword ? 'text' : 'password'}
      maxLength={72} required disabled={isPending} hint="Ít nhất 8 ký tự, có chữ hoa, chữ thường và chữ số."
      icon={<LockKeyhole size={18} />} {...register('newPassword')} error={errors.newPassword?.message}
      endAdornment={<Button variant="ghost" size="sm" disabled={isPending} onClick={() => setShowPassword(value => !value)}
        aria-label={showPassword ? 'Ẩn mật khẩu mới' : 'Hiện mật khẩu mới'} aria-pressed={showPassword}
        icon={showPassword ? <EyeOff size={18} /> : <Eye size={18} />} />} />
    <Input id="reset-confirm-password" label="Nhập lại mật khẩu mới" autoComplete="new-password" type={showPassword ? 'text' : 'password'}
      maxLength={72} required disabled={isPending} icon={<LockKeyhole size={18} />}
      {...register('confirmPassword')} error={errors.confirmPassword?.message} />
    <p className="recovery-note">Sau khi đặt lại mật khẩu, bạn cần đăng nhập lại trên các thiết bị.</p>
    {retryAfterSeconds > 0 && <p className="login-cooldown" role="status">Có thể thử lại sau {retryAfterSeconds} giây.</p>}
    <Button type="submit" fullWidth disabled={isPending || retryAfterSeconds > 0} loading={isPending}
      loadingText="Đang xử lý…" icon={<ArrowRight size={18} />}>Đặt lại mật khẩu</Button>
    <p className="login-register"><Link className="auth-text-link" to="/login">Quay lại đăng nhập</Link></p>
  </form>;
}
