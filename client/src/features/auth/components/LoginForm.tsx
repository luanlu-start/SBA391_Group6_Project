import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowRight, Eye, EyeOff, LoaderCircle, LockKeyhole, UserRound } from 'lucide-react';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import { useLogin } from '../hooks/useLogin';
import { loginSchema } from '../schemas/loginSchema';
import type { LoginFormValues } from '../schemas/loginSchema';

export default function LoginForm() {
  const [showPassword, setShowPassword] = useState(false);
  const { login, isPending, initializing, error, retryAfterSeconds } = useLogin();
  const { register, handleSubmit, setError, resetField, formState: { errors } } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { identifier: '', password: '' },
  });
  const disabled = isPending || initializing || retryAfterSeconds > 0;

  useEffect(() => {
    for (const field of ['identifier', 'password'] as const) {
      const message = error?.fieldErrors[field];
      if (message) setError(field, { type: 'server', message });
    }
  }, [error, setError]);

  async function submit(values: LoginFormValues) {
    if (await login(values)) resetField('password');
  }

  return (
    <form className="login-form" onSubmit={handleSubmit(submit)} noValidate aria-busy={isPending}>
      {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
      <div className="login-field">
        <label htmlFor="identifier">Email hoặc số điện thoại</label>
        <div className="login-input-wrap">
          <UserRound size={19} aria-hidden="true" />
          <input id="identifier" type="text" placeholder="Email hoặc số điện thoại của bạn"
            autoComplete="username" maxLength={254} {...register('identifier')} disabled={isPending}
            aria-invalid={!!errors.identifier} aria-describedby={errors.identifier ? 'identifier-error' : undefined} required />
        </div>
        {errors.identifier && <p className="login-field-error" id="identifier-error" role="alert">{errors.identifier.message}</p>}
      </div>
      <div className="login-field">
        <label htmlFor="login-password">Mật khẩu</label>
        <div className="login-input-wrap">
          <LockKeyhole size={19} aria-hidden="true" />
          <input id="login-password" type={showPassword ? 'text' : 'password'} placeholder="Nhập mật khẩu"
            autoComplete="current-password" maxLength={72} {...register('password')} disabled={isPending}
            aria-invalid={!!errors.password} aria-describedby={errors.password ? 'password-error' : undefined} required />
          <button className="login-password-toggle" type="button" disabled={isPending}
            aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} aria-pressed={showPassword}
            onClick={() => setShowPassword(value => !value)}>
            {showPassword ? <EyeOff size={19} /> : <Eye size={19} />}
          </button>
        </div>
        {errors.password && <p className="login-field-error" id="password-error" role="alert">{errors.password.message}</p>}
      </div>
      {retryAfterSeconds > 0 && <p className="login-cooldown" role="status">
        Có thể thử lại sau {Math.floor(retryAfterSeconds / 60)}:{String(retryAfterSeconds % 60).padStart(2, '0')}.
      </p>}
      <button className="login-submit" type="submit" disabled={disabled}>
        {isPending ? 'Đang đăng nhập…' : initializing ? 'Đang kiểm tra phiên…' : 'Đăng nhập'}
        {isPending || initializing ? <LoaderCircle className="auth-spinner" size={18} aria-hidden="true" />
          : <ArrowRight size={18} aria-hidden="true" />}
      </button>
      <p className="login-register">Chưa có tài khoản? <Link className="auth-text-link" to="/register">Đăng ký ngay</Link></p>
    </form>
  );
}
