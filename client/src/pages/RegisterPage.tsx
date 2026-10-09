import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Eye, EyeOff, Mail, UserPlus } from 'lucide-react';
import { identityApi } from '../api/identityApi';
import { getApiError } from '../api/ApiError';
import type { RegistrationPolicies, RegisterResponse } from '../types/identity';
import '../styles/registration.css';

const INITIAL_FORM = { fullName: '', email: '', phone: '', password: '', confirmPassword: '',
  adultConfirmed: false, termsAccepted: false, privacyAccepted: false };

export default function RegisterPage() {
  const [form, setForm] = useState(INITIAL_FORM);
  const [policies, setPolicies] = useState<RegistrationPolicies | null>(null);
  const [policyError, setPolicyError] = useState('');
  const [loadingPolicies, setLoadingPolicies] = useState(true);
  const [retry, setRetry] = useState(0);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [result, setResult] = useState<RegisterResponse | null>(null);

  useEffect(() => {
    let active = true;
    setLoadingPolicies(true);
    setPolicyError('');
    identityApi.getRegistrationPolicies().then(response => {
      if (active) setPolicies(response.data);
    }).catch(() => {
      if (active) setPolicyError('Chưa tải được chính sách đăng ký. Vui lòng thử lại.');
    }).finally(() => { if (active) setLoadingPolicies(false); });
    return () => { active = false; };
  }, [retry]);

  function update(field: keyof typeof INITIAL_FORM, value: string | boolean) {
    setForm(previous => ({ ...previous, [field]: value }));
    setErrors(previous => ({ ...previous, [field]: '' }));
    setError('');
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!policies || submitting) return;
    const validation: Record<string, string> = {};
    if (!form.fullName.trim() || form.fullName.trim().length > 100) validation.fullName = 'Họ tên phải có từ 1 đến 100 ký tự.';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) validation.email = 'Email không hợp lệ.';
    if (!/^(?:0[35789]\d{8}|\+84[35789]\d{8})$/.test(form.phone.trim())) validation.phone = 'Nhập số di động Việt Nam dạng 09… hoặc +849…';
    const password = form.password;
    if ([...password].length < policies.passwordMinLength || new TextEncoder().encode(password).length > 72
      || !/\p{Lu}/u.test(password) || !/\p{Ll}/u.test(password) || !/\p{Nd}/u.test(password)
      || password.toLowerCase() === form.email.trim().toLowerCase() || password === form.phone.trim()) {
      validation.password = `Mật khẩu cần ít nhất ${policies.passwordMinLength} ký tự, có chữ hoa, chữ thường, chữ số và tối đa 72 byte.`;
    }
    if (password !== form.confirmPassword) validation.confirmPassword = 'Mật khẩu xác nhận chưa khớp.';
    if (!form.adultConfirmed) validation.adultConfirmed = 'Bạn cần xác nhận đủ 18 tuổi.';
    if (!form.termsAccepted) validation.termsAccepted = 'Bạn cần đồng ý điều khoản sử dụng.';
    if (!form.privacyAccepted) validation.privacyAccepted = 'Bạn cần đồng ý chính sách dữ liệu.';
    setErrors(validation);
    if (Object.keys(validation).length) {
      document.getElementById(Object.keys(validation)[0])?.focus();
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      const response = await identityApi.register({ fullName: form.fullName.trim(), email: form.email.trim(),
        phone: form.phone.trim(), password, adultConfirmed: form.adultConfirmed,
        termsAccepted: form.termsAccepted, privacyAccepted: form.privacyAccepted,
        termsVersion: policies.terms.version, privacyVersion: policies.privacy.version });
      setForm(INITIAL_FORM);
      setResult(response.data);
    } catch (cause) {
      const apiError = getApiError(cause);
      setError(apiError.message);
      if (apiError.errors) setErrors(apiError.errors);
      if (apiError.code === 3001) setErrors({ email: apiError.message });
      if (apiError.code === 3002) setErrors({ phone: apiError.message });
      if (apiError.code === 3003) setErrors({ password: apiError.message });
      if (apiError.code === 3004) {
        setForm(previous => ({ ...previous, termsAccepted: false, privacyAccepted: false }));
        setPolicies(null);
        setRetry(previous => previous + 1);
      }
    } finally { setSubmitting(false); }
  }

  const fieldError = (field: string) => errors[field]
    ? <p id={`${field}-error`} className="registration-field-error" role="alert">{errors[field]}</p> : null;

  if (result) return (
    <section className="registration-page">
      <div className="registration-card registration-success" role="status">
        <CheckCircle2 size={44} aria-hidden="true" />
        <h1>Đăng ký thành công</h1>
        <p>Tài khoản của bạn đang chờ xác thực.</p>
        <div className="registration-email"><Mail size={22} aria-hidden="true" /><strong>{result.maskedTarget}</strong></div>
        <p>Mã xác thực đã được gửi đến email của bạn. Kiểm tra hộp thư đến và thư rác.</p>
        <p>Mã hết hạn lúc <strong>{new Date(result.expiresAt).toLocaleTimeString('vi-VN')}</strong>.</p>
        <Link className="registration-primary" to="/">Về trang chủ</Link>
      </div>
    </section>
  );

  return (
    <section className="registration-page">
      <div className="registration-intro">
        <span className="registration-eyebrow">FPTPost · Giao hàng tiện đường</span>
        <h1>Tạo tài khoản</h1>
        <p>Kết nối với cộng đồng người gửi và người giao hàng.</p>
      </div>
      <form className="registration-card" onSubmit={submit} noValidate>
        <div className="registration-card-title"><UserPlus size={24} aria-hidden="true" /><h2>Thông tin đăng ký</h2></div>
        <p className="registration-hint">Điền thông tin của bạn. Chúng tôi sẽ gửi mã xác thực qua email.</p>
        {error && <div className="registration-alert" role="alert">{error}</div>}
        {policyError && <div className="registration-alert" role="alert">{policyError} <button type="button" onClick={() => setRetry(previous => previous + 1)}>Thử lại</button></div>}
        <fieldset disabled={submitting}>
          <div className="registration-fields">
            <div className="registration-field registration-wide">
              <label htmlFor="fullName">Họ và tên</label>
              <input id="fullName" autoComplete="name" maxLength={100} value={form.fullName} onChange={e => update('fullName', e.target.value)} aria-invalid={!!errors.fullName} aria-describedby={errors.fullName ? 'fullName-error' : undefined} />
              {fieldError('fullName')}
            </div>
            <div className="registration-field">
              <label htmlFor="email">Email</label>
              <input id="email" type="email" autoComplete="email" maxLength={254} value={form.email} onChange={e => update('email', e.target.value)} aria-invalid={!!errors.email} aria-describedby={errors.email ? 'email-error' : undefined} />
              {fieldError('email')}
            </div>
            <div className="registration-field">
              <label htmlFor="phone">Số điện thoại</label>
              <input id="phone" type="tel" autoComplete="tel" placeholder="0901234567" maxLength={16} value={form.phone} onChange={e => update('phone', e.target.value)} aria-invalid={!!errors.phone} aria-describedby={errors.phone ? 'phone-error' : undefined} />
              {fieldError('phone')}
            </div>
            <div className="registration-field">
              <label htmlFor="password">Mật khẩu</label>
              <div className="registration-password">
                <input id="password" type={showPassword ? 'text' : 'password'} autoComplete="new-password" maxLength={72} value={form.password} onChange={e => update('password', e.target.value)} aria-invalid={!!errors.password} aria-describedby="password-hint password-error" />
                <button type="button" aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} onClick={() => setShowPassword(previous => !previous)}>{showPassword ? <EyeOff size={18} /> : <Eye size={18} />}</button>
              </div>
              <p id="password-hint" className="registration-hint">Ít nhất {policies?.passwordMinLength ?? 8} ký tự, có chữ hoa, chữ thường và chữ số.</p>
              {fieldError('password')}
            </div>
            <div className="registration-field">
              <label htmlFor="confirmPassword">Nhập lại mật khẩu</label>
              <input id="confirmPassword" type="password" autoComplete="new-password" maxLength={72} value={form.confirmPassword} onChange={e => update('confirmPassword', e.target.value)} aria-invalid={!!errors.confirmPassword} aria-describedby={errors.confirmPassword ? 'confirmPassword-error' : undefined} />
              {fieldError('confirmPassword')}
            </div>
          </div>
          <div className="registration-policies">
            {loadingPolicies && <p role="status">Đang tải điều khoản và chính sách…</p>}
            {policies && [policies.terms, policies.privacy].map(policy => (
              <details key={policy.title}><summary>{policy.title} <span>{policy.version}</span></summary>
                {policy.paragraphs.map(paragraph => <p key={paragraph}>{paragraph}</p>)}
              </details>
            ))}
          </div>
          <div className="registration-consents">
            <label><input id="adultConfirmed" type="checkbox" checked={form.adultConfirmed} onChange={e => update('adultConfirmed', e.target.checked)} />Tôi xác nhận đã đủ 18 tuổi.</label>
            {fieldError('adultConfirmed')}
            <label><input id="termsAccepted" type="checkbox" checked={form.termsAccepted} onChange={e => update('termsAccepted', e.target.checked)} disabled={!policies} />Tôi đã đọc và đồng ý Điều khoản sử dụng.</label>
            {fieldError('termsAccepted')}
            <label><input id="privacyAccepted" type="checkbox" checked={form.privacyAccepted} onChange={e => update('privacyAccepted', e.target.checked)} disabled={!policies} />Tôi đã đọc và đồng ý Chính sách dữ liệu cá nhân.</label>
            {fieldError('privacyAccepted')}
          </div>
          <button className="registration-primary" type="submit" disabled={submitting || !policies || loadingPolicies}>
            {submitting ? 'Đang tạo tài khoản…' : 'Tạo tài khoản'}
          </button>
        </fieldset>
      </form>
    </section>
  );
}
