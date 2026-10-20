import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, Mail, UserRound } from 'lucide-react';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import { usePasswordRecovery } from '../hooks/usePasswordRecovery';
import { forgotPasswordSchema } from '../schemas/passwordRecoverySchema';
import type { ForgotPasswordFormValues } from '../schemas/passwordRecoverySchema';

export default function ForgotPasswordForm() {
  const navigate = useNavigate();
  const { requestOtp, isPending, error, retryAfterSeconds, resendAfterSeconds } = usePasswordRecovery();
  const { register, handleSubmit, setError, formState: { errors } } = useForm<ForgotPasswordFormValues>({
    resolver: zodResolver(forgotPasswordSchema), defaultValues: { identifier: '' },
  });
  useEffect(() => {
    if (error?.fieldErrors.identifier) setError('identifier', { type: 'server', message: error.fieldErrors.identifier });
  }, [error, setError]);

  async function submit(values: ForgotPasswordFormValues) {
    const response = await requestOtp(values.identifier);
    if (response) navigate('/reset', { state: { identifier: values.identifier, resendAvailableAt: response.resendAvailableAt } });
  }
  const cooldown = Math.max(retryAfterSeconds, resendAfterSeconds);
  return <form className="login-form" onSubmit={handleSubmit(submit)} noValidate aria-busy={isPending}>
    {error && <ApiErrorNotice error={error} />}
    <Input id="recovery-identifier" label="Email hoặc số điện thoại" autoComplete="username" required
      placeholder="Email hoặc số điện thoại đã đăng ký" maxLength={254} disabled={isPending}
      icon={<UserRound size={18} />} {...register('identifier')} error={errors.identifier?.message} />
    {cooldown > 0 && <p className="login-cooldown" role="status">Có thể yêu cầu lại sau {cooldown} giây.</p>}
    <Button type="submit" fullWidth loading={isPending} loadingText="Đang gửi yêu cầu…"
      disabled={isPending || cooldown > 0} icon={<Mail size={18} />}>Gửi mã đặt lại mật khẩu</Button>
    <Link className="auth-text-link recovery-back" to="/login"><ArrowLeft size={16} />Quay lại đăng nhập</Link>
  </form>;
}
