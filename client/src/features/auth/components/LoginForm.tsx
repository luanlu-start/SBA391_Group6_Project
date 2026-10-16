import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowRight, Eye, EyeOff, LockKeyhole, UserRound } from 'lucide-react';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import Checkbox from '../../../components/ui/Checkbox';
import { useLogin } from '../hooks/useLogin';
import { loginSchema } from '../schemas/loginSchema';
import type { LoginFormValues } from '../schemas/loginSchema';

export default function LoginForm() {
  const [showPassword, setShowPassword] = useState(false);
  const { login, isPending, initializing, error, retryAfterSeconds } = useLogin();
  const { register, handleSubmit, setError, resetField, formState: { errors } } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema), defaultValues: { identifier: '', password: '', rememberMe: false },
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

  return <form className="login-form" onSubmit={handleSubmit(submit)} noValidate aria-busy={isPending}>
    {error && <ApiErrorNotice error={error} />}
    <Input id="identifier" label="Email hoặc số điện thoại" placeholder="Email hoặc số điện thoại của bạn"
      autoComplete="username" maxLength={254} {...register('identifier')} disabled={isPending} required
      icon={<UserRound size={18} />} error={errors.identifier?.message} />
    <Input id="login-password" label="Mật khẩu" type={showPassword ? 'text' : 'password'} placeholder="Nhập mật khẩu"
      autoComplete="current-password" maxLength={72} {...register('password')} disabled={isPending} required
      icon={<LockKeyhole size={18} />} error={errors.password?.message}
      endAdornment={<Button variant="ghost" size="sm" disabled={isPending}
        aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} aria-pressed={showPassword}
        onClick={() => setShowPassword(value => !value)} icon={showPassword ? <EyeOff size={18} /> : <Eye size={18} />} />} />
    <Checkbox id="remember-me" label="Ghi nhớ đăng nhập" {...register('rememberMe')} disabled={isPending} />
    {retryAfterSeconds > 0 && <p className="login-cooldown" role="status">
      Có thể thử lại sau {Math.floor(retryAfterSeconds / 60)}:{String(retryAfterSeconds % 60).padStart(2, '0')}.
    </p>}
    <Button type="submit" fullWidth disabled={disabled} loading={isPending || initializing}
      loadingText={initializing ? 'Đang kiểm tra phiên…' : 'Đang đăng nhập…'} icon={<ArrowRight size={18} />}>Đăng nhập</Button>
    <p className="login-register">Chưa có tài khoản? <Link className="auth-text-link" to="/register">Đăng ký ngay</Link></p>
  </form>;
}
