import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowRight, Eye, EyeOff, LoaderCircle, LockKeyhole, Mail, Phone, UserRound } from 'lucide-react';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
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
    resolver: zodResolver(createRegisterSchema(policies?.passwordMinLength ?? 8)),
    defaultValues: DEFAULT_VALUES,
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
      setValue('termsAccepted', false);
      setValue('privacyAccepted', false);
      setPolicy(null);
    }
  }, [error, setError, setValue]);

  const fieldError = (field: keyof RegisterFormValues) => errors[field]
    ? <p className="login-field-error" id={`${field}-error`} role="alert">{errors[field]?.message}</p> : null;

  async function submit(values: RegisterFormValues) {
    if (await registerAccount(values)) reset(DEFAULT_VALUES);
  }

  return (
    <>
      <form className="register-form" onSubmit={handleSubmit(submit)} onChange={clearError} noValidate aria-busy={isPending}>
        {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
        {policyError && <div className="register-policy-error">
          <ApiErrorNotice message="Chưa tải được chính sách đăng ký. Vui lòng thử lại." correlationId={policyError.correlationId} />
          <button className="policy-link" type="button" onClick={reloadPolicies} disabled={loadingPolicies}>Thử lại</button>
        </div>}
        <fieldset className="register-fieldset" disabled={isPending}>
          <div className="register-fields">
            <div className="login-field register-wide">
              <label htmlFor="fullName">Họ và tên</label>
              <div className="login-input-wrap"><UserRound size={18} aria-hidden="true" />
                <input id="fullName" autoComplete="name" maxLength={100} placeholder="Nhập họ và tên" {...register('fullName')}
                  aria-invalid={!!errors.fullName} aria-describedby={errors.fullName ? 'fullName-error' : undefined} required />
              </div>{fieldError('fullName')}
            </div>
            <div className="login-field">
              <label htmlFor="email">Email</label>
              <div className="login-input-wrap"><Mail size={18} aria-hidden="true" />
                <input id="email" type="email" autoComplete="email" maxLength={254} placeholder="ban@example.com" {...register('email')}
                  aria-invalid={!!errors.email} aria-describedby={errors.email ? 'email-error' : undefined} required />
              </div>{fieldError('email')}
            </div>
            <div className="login-field">
              <label htmlFor="phone">Số điện thoại</label>
              <div className="login-input-wrap"><Phone size={18} aria-hidden="true" />
                <input id="phone" type="tel" autoComplete="tel" maxLength={16} placeholder="0901234567" {...register('phone')}
                  aria-invalid={!!errors.phone} aria-describedby={errors.phone ? 'phone-error' : undefined} required />
              </div>{fieldError('phone')}
            </div>
            <div className="login-field">
              <label htmlFor="password">Mật khẩu</label>
              <div className="login-input-wrap"><LockKeyhole size={18} aria-hidden="true" />
                <input id="password" type={showPassword ? 'text' : 'password'} autoComplete="new-password" maxLength={72}
                  placeholder="Tạo mật khẩu" {...register('password')} aria-invalid={!!errors.password}
                  aria-describedby={`password-hint${errors.password ? ' password-error' : ''}`} required />
                <button className="login-password-toggle" type="button" aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  aria-pressed={showPassword} onClick={() => setShowPassword(value => !value)}>
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>{fieldError('password')}
            </div>
            <div className="login-field">
              <label htmlFor="confirmPassword">Nhập lại mật khẩu</label>
              <div className="login-input-wrap"><LockKeyhole size={18} aria-hidden="true" />
                <input id="confirmPassword" type={showConfirmation ? 'text' : 'password'} autoComplete="new-password" maxLength={72}
                  placeholder="Nhập lại mật khẩu" {...register('confirmPassword')} aria-invalid={!!errors.confirmPassword}
                  aria-describedby={errors.confirmPassword ? 'confirmPassword-error' : undefined} required />
                <button className="login-password-toggle" type="button" aria-label={showConfirmation ? 'Ẩn mật khẩu xác nhận' : 'Hiện mật khẩu xác nhận'}
                  aria-pressed={showConfirmation} onClick={() => setShowConfirmation(value => !value)}>
                  {showConfirmation ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>{fieldError('confirmPassword')}
            </div>
            <p id="password-hint" className="register-password-hint register-wide">Ít nhất {policies?.passwordMinLength ?? 8} ký tự, có chữ hoa, chữ thường và chữ số.</p>
          </div>
          {loadingPolicies && <p className="register-policy-loading" role="status">Đang tải điều khoản và chính sách…</p>}
          <div className="register-consents">
            <div>
              <label className="register-consent-row" htmlFor="adultConfirmed">
                <input id="adultConfirmed" type="checkbox" {...register('adultConfirmed')} aria-invalid={!!errors.adultConfirmed}
                  aria-describedby={errors.adultConfirmed ? 'adultConfirmed-error' : undefined} />
                <span>Tôi xác nhận đã đủ 18 tuổi.</span>
              </label>{fieldError('adultConfirmed')}
            </div>
            <div>
              <div className="register-consent-row">
                <input id="termsAccepted" type="checkbox" {...register('termsAccepted')} disabled={!policies || loadingPolicies}
                  aria-labelledby="terms-consent-label terms-policy-link"
                  aria-invalid={!!errors.termsAccepted} aria-describedby={errors.termsAccepted ? 'termsAccepted-error' : undefined} />
                <span><label id="terms-consent-label" htmlFor="termsAccepted">Tôi đã đọc và đồng ý với </label>
                  <button id="terms-policy-link" className="policy-link" type="button" disabled={!policies || loadingPolicies}
                    aria-haspopup="dialog" onClick={() => setPolicy(policies?.terms ?? null)}>Điều khoản sử dụng</button>.</span>
              </div>{fieldError('termsAccepted')}
            </div>
            <div>
              <div className="register-consent-row">
                <input id="privacyAccepted" type="checkbox" {...register('privacyAccepted')} disabled={!policies || loadingPolicies}
                  aria-labelledby="privacy-consent-label privacy-policy-link"
                  aria-invalid={!!errors.privacyAccepted} aria-describedby={errors.privacyAccepted ? 'privacyAccepted-error' : undefined} />
                <span><label id="privacy-consent-label" htmlFor="privacyAccepted">Tôi đồng ý với </label>
                  <button id="privacy-policy-link" className="policy-link" type="button" disabled={!policies || loadingPolicies}
                    aria-haspopup="dialog" onClick={() => setPolicy(policies?.privacy ?? null)}>Chính sách dữ liệu cá nhân</button>.</span>
              </div>{fieldError('privacyAccepted')}
            </div>
          </div>
          <button className="login-submit" type="submit" disabled={isPending || !policies || loadingPolicies}>
            {isPending ? 'Đang tạo tài khoản…' : 'Tạo tài khoản'}
            {isPending ? <LoaderCircle className="auth-spinner" size={18} aria-hidden="true" /> : <ArrowRight size={18} aria-hidden="true" />}
          </button>
        </fieldset>
        <p className="login-register">Đã có tài khoản? <Link className="auth-text-link" to="/login">Đăng nhập</Link></p>
      </form>
      <PolicyDialog policy={policy} onClose={() => setPolicy(null)} />
    </>
  );
}
