import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowRight, Eye, EyeOff, LockKeyhole, Mail, Phone, UserRound } from 'lucide-react';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import Input from '../../../components/ui/Input';
import Checkbox from '../../../components/ui/Checkbox';
import Button from '../../../components/ui/Button';
import type { RegistrationPolicy } from '../../../types/identity';
import { useRegister } from '../hooks/useRegister';
import { createRegisterSchema } from '../schemas/registerSchema';
import type { RegisterFormValues } from '../schemas/registerSchema';
import PolicyDialog from './PolicyDialog';

const DEFAULT_VALUES: RegisterFormValues = { fullName: '', email: '', phone: '', password: '',
  confirmPassword: '', adultConfirmed: false, termsAccepted: false, privacyAccepted: false };
const FIELDS = Object.keys(DEFAULT_VALUES) as (keyof RegisterFormValues)[];

export default function RegisterForm() {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [policy, setPolicy] = useState<RegistrationPolicy | null>(null);
  const { registerAccount, policies, loadingPolicies, policyError, reloadPolicies, isPending, error, clearError } = useRegister();
  const { register, handleSubmit, setError, setValue, reset, formState: { errors } } = useForm<RegisterFormValues>({
    resolver: zodResolver(createRegisterSchema(policies?.passwordMinLength ?? 8)), defaultValues: DEFAULT_VALUES,
  });

  useEffect(() => {
    for (const field of FIELDS) {
      const message = error?.fieldErrors[field];
      if (message) setError(field, { type: 'server', message });
    }
    const field = error?.code === 'AUTH_EMAIL_ALREADY_EXISTS' ? 'email'
      : error?.code === 'AUTH_PHONE_ALREADY_EXISTS' ? 'phone'
      : error?.code === 'AUTH_PASSWORD_INVALID' ? 'password' : null;
    if (field && error) setError(field, { type: 'server', message: error.message }, { shouldFocus: true });
    if (error?.code === 'AUTH_POLICY_CHANGED') {
      setValue('termsAccepted', false); setValue('privacyAccepted', false); setPolicy(null);
    }
  }, [error, setError, setValue]);

  async function submit(values: RegisterFormValues) {
    if (await registerAccount(values)) reset(DEFAULT_VALUES);
  }

  return <>
    <form className="register-form" onSubmit={handleSubmit(submit)} noValidate aria-busy={isPending}>
      {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
      {policyError && <div className="ui-stack">
        <ApiErrorNotice message="Chưa tải được chính sách đăng ký. Vui lòng thử lại." correlationId={policyError.correlationId} />
        <Button variant="secondary" size="sm" onClick={() => { clearError(); void reloadPolicies(); }}>Thử lại</Button>
      </div>}
      <fieldset className="register-fieldset" disabled={isPending}>
        <div className="ui-form-grid">
          <Input id="fullName" label="Họ và tên" autoComplete="name" maxLength={100} placeholder="Nhập họ và tên"
            {...register('fullName')} fieldClassName="ui-span-full" required icon={<UserRound size={18} />} error={errors.fullName?.message} />
          <Input id="email" label="Email" type="email" autoComplete="email" maxLength={254} placeholder="ban@example.com"
            {...register('email')} required icon={<Mail size={18} />} error={errors.email?.message} />
          <Input id="phone" label="Số điện thoại" type="tel" autoComplete="tel" maxLength={16} placeholder="0901234567"
            {...register('phone')} required icon={<Phone size={18} />} error={errors.phone?.message} />
          <Input id="password" label="Mật khẩu" type={showPassword ? 'text' : 'password'} autoComplete="new-password"
            maxLength={72} placeholder="Tạo mật khẩu" {...register('password')} required icon={<LockKeyhole size={18} />}
            error={errors.password?.message} aria-describedby="password-hint"
            endAdornment={<Button variant="ghost" size="sm" aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              aria-pressed={showPassword} onClick={() => setShowPassword(value => !value)} icon={showPassword ? <EyeOff size={18} /> : <Eye size={18} />} />} />
          <Input id="confirmPassword" label="Nhập lại mật khẩu" type={showConfirmation ? 'text' : 'password'} autoComplete="new-password"
            maxLength={72} placeholder="Nhập lại mật khẩu" {...register('confirmPassword')} required icon={<LockKeyhole size={18} />}
            error={errors.confirmPassword?.message}
            endAdornment={<Button variant="ghost" size="sm" aria-label={showConfirmation ? 'Ẩn mật khẩu xác nhận' : 'Hiện mật khẩu xác nhận'}
              aria-pressed={showConfirmation} onClick={() => setShowConfirmation(value => !value)} icon={showConfirmation ? <EyeOff size={18} /> : <Eye size={18} />} />} />
          <p id="password-hint" className="ui-field-hint ui-span-full">Ít nhất {policies?.passwordMinLength ?? 8} ký tự, có chữ hoa, chữ thường và chữ số.</p>
        </div>
        {loadingPolicies && <p className="ui-field-hint" role="status">Đang tải điều khoản và chính sách…</p>}
        <div className="register-consents">
          <Checkbox id="adultConfirmed" label="Tôi xác nhận đã đủ 18 tuổi." {...register('adultConfirmed')} error={errors.adultConfirmed?.message} />
          <Checkbox id="termsAccepted" {...register('termsAccepted')} disabled={!policies || loadingPolicies} error={errors.termsAccepted?.message}
            label={<>Tôi đã đọc và đồng ý với <button id="terms-policy-link" className="policy-link" type="button" disabled={!policies || loadingPolicies}
              aria-haspopup="dialog" onClick={() => setPolicy(policies?.terms ?? null)}>Điều khoản sử dụng</button>.</>} />
          <Checkbox id="privacyAccepted" {...register('privacyAccepted')} disabled={!policies || loadingPolicies} error={errors.privacyAccepted?.message}
            label={<>Tôi đồng ý với <button id="privacy-policy-link" className="policy-link" type="button" disabled={!policies || loadingPolicies}
              aria-haspopup="dialog" onClick={() => setPolicy(policies?.privacy ?? null)}>Chính sách dữ liệu cá nhân</button>.</>} />
        </div>
        <Button type="submit" fullWidth disabled={!policies || loadingPolicies} loading={isPending} loadingText="Đang tạo tài khoản…"
          icon={<ArrowRight size={18} />}>Tạo tài khoản</Button>
      </fieldset>
      <p className="login-register">Đã có tài khoản? <Link className="auth-text-link" to="/login">Đăng nhập</Link></p>
    </form>
    <PolicyDialog policy={policy} onClose={() => setPolicy(null)} />
  </>;
}
