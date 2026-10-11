import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { CheckCircle2, Mail, ShieldCheck } from 'lucide-react';
import { authService } from '../services/authService';
import { getApiError } from '../api/ApiError';
import ApiErrorNotice from '../components/ApiErrorNotice';
import { clearRegistrationChallenge, loadRegistrationChallenge, parseRegistrationChallenge,
  saveRegistrationChallenge } from '../utils/registrationChallenge';
import '../styles/registration.css';

export default function VerifyOtpPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const navigationState = location.state as { challenge?: unknown } | null;
  const [challenge, setChallenge] = useState(() => parseRegistrationChallenge(navigationState?.challenge)
    ?? loadRegistrationChallenge());
  const [otp, setOtp] = useState('');
  const [now, setNow] = useState(Date.now);
  const [busy, setBusy] = useState<'verify' | 'resend' | null>(null);
  const [error, setError] = useState('');
  const [correlationId, setCorrelationId] = useState<string>();
  const [notice, setNotice] = useState('');
  const [verified, setVerified] = useState(false);
  const [mustResend, setMustResend] = useState(false);
  const [unavailable, setUnavailable] = useState(false);
  const [retryAt, setRetryAt] = useState(0);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    window.scrollTo(0, 0);
    input.current?.focus({ preventScroll: true });
  }, []);

  const expiresIn = challenge ? Math.max(0, Math.ceil((Date.parse(challenge.expiresAt) - now) / 1000)) : 0;
  const resendIn = challenge ? Math.max(0,
    Math.ceil((Math.max(Date.parse(challenge.resendAvailableAt), retryAt) - now) / 1000)) : 0;

  function showError(cause: unknown) {
    const failure = getApiError(cause);
    setError(failure.message);
    setCorrelationId(failure.correlationId);
    if (failure.retryAfterSeconds) setRetryAt(Date.now() + failure.retryAfterSeconds * 1000);
    if (['AUTH_OTP_EXPIRED', 'AUTH_OTP_ATTEMPTS_EXCEEDED', 'AUTH_OTP_SUPERSEDED'].includes(failure.code ?? '')) {
      setMustResend(true);
    }
    if (['AUTH_OTP_NOT_FOUND', 'AUTH_VERIFICATION_BLOCKED', 'AUTH_OTP_ALREADY_USED'].includes(failure.code ?? '')) {
      setUnavailable(true);
      clearRegistrationChallenge();
    }
  }

  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!challenge || busy || unavailable || mustResend || expiresIn === 0 || retryAt > Date.now()) return;
    if (!/^[0-9]{6}$/.test(otp)) {
      setError('Vui lòng nhập đủ 6 chữ số trong email.');
      setCorrelationId(undefined);
      input.current?.focus();
      return;
    }
    setBusy('verify');
    setError('');
    setCorrelationId(undefined);
    setNotice('');
    try {
      await authService.verifyOtp(challenge.challengeId, otp);
      clearRegistrationChallenge();
      setOtp('');
      setVerified(true);
    } catch (cause) {
      showError(cause);
      setOtp('');
      input.current?.focus();
    } finally { setBusy(null); }
  }

  async function resend() {
    if (!challenge || busy || unavailable || resendIn > 0) return;
    setBusy('resend');
    setError('');
    setCorrelationId(undefined);
    setNotice('');
    try {
      const response = await authService.resendOtp(challenge.challengeId);
      saveRegistrationChallenge(response.data);
      navigate(location.pathname, { replace: true, state: { challenge: response.data } });
      setChallenge(response.data);
      setOtp('');
      setMustResend(false);
      setRetryAt(0);
      setNow(Date.now());
      setNotice('Đã gửi mã mới. Vui lòng dùng mã trong email mới nhất.');
      input.current?.focus();
    } catch (cause) { showError(cause); } finally { setBusy(null); }
  }

  if (verified) return (
    <section className="registration-page">
      <div className="registration-card registration-success" role="status">
        <CheckCircle2 size={44} aria-hidden="true" />
        <h1>Xác thực thành công</h1>
        <p>Email đã được xác thực. Tài khoản của bạn đã được kích hoạt.</p>
        <Link className="registration-primary" to="/login">Đăng nhập</Link>
      </div>
    </section>
  );

  if (!challenge) return (
    <section className="registration-page">
      <div className="registration-card registration-success">
        <h1>Chưa có yêu cầu xác thực</h1>
        <p>Vui lòng đăng ký tài khoản để nhận mã xác thực email.</p>
        <Link className="registration-primary" to="/register">Đăng ký tài khoản</Link>
      </div>
    </section>
  );

  return (
    <section className="registration-page">
      <div className="registration-card registration-verify">
        <div className="registration-heading"><ShieldCheck size={24} aria-hidden="true" /><h1>Xác thực email</h1></div>
        <p>Mã xác thực đã được gửi đến email:</p>
        <div className="registration-email"><Mail size={22} aria-hidden="true" /><strong>{challenge.maskedTarget}</strong></div>
        <p className="registration-hint">Kiểm tra hộp thư đến và thư rác. Không chia sẻ mã với người khác.</p>
        {error && <ApiErrorNotice message={error} correlationId={correlationId} />}
        {notice && <p className="registration-otp-notice" role="status">{notice}</p>}
        <form onSubmit={verify} noValidate>
          <div className="registration-field">
            <label htmlFor="otp">Mã xác thực gồm 6 chữ số</label>
            <input ref={input} id="otp" className="registration-otp-input" type="text" inputMode="numeric"
              autoComplete="one-time-code" maxLength={6} value={otp}
              onChange={event => setOtp(event.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
              disabled={!!busy || unavailable || mustResend || expiresIn === 0} aria-describedby="otp-expiration"
              aria-invalid={!!error} />
            <p id="otp-expiration" className="registration-hint">{expiresIn > 0
              ? `Mã còn hiệu lực ${Math.floor(expiresIn / 60)}:${String(expiresIn % 60).padStart(2, '0')}.`
              : 'Mã đã hết hạn. Vui lòng yêu cầu gửi mã mới.'}</p>
          </div>
          <button className="registration-primary" type="submit"
            disabled={!!busy || unavailable || mustResend || expiresIn === 0 || retryAt > now || otp.length !== 6}>
            {busy === 'verify' ? 'Đang xác thực…' : 'Xác thực email'}
          </button>
        </form>
        <button className="registration-secondary" type="button" onClick={resend}
          disabled={!!busy || unavailable || resendIn > 0}>
          {busy === 'resend' ? 'Đang gửi mã…' : resendIn > 0 ? `Gửi lại mã sau ${resendIn}s` : 'Gửi lại mã'}
        </button>
        <Link className="registration-back" to="/">Về trang chủ</Link>
      </div>
    </section>
  );
}
