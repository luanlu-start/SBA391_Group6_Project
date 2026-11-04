import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import type { OtpChallenge } from '../../../types/identity';
import { profileService } from '../../../services/profileService';
import { useProfileAction } from '../hooks/useProfileAction';
import { useCountdown } from '../hooks/useCountdown';
import { contactSchema, contactOtpSchema } from '../schemas/profileSchema';
import type { ContactFormValues } from '../schemas/profileSchema';
import Input from '../../../components/ui/Input';
import Select from '../../../components/ui/Select';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

export default function ContactChangeForm() {
  const navigate = useNavigate(); const [challenge, setChallenge] = useState<OtpChallenge>();
  const { busy, error, retryAfterSeconds, run } = useProfileAction();
  const resendSeconds = useCountdown(Date.parse(challenge?.resendAvailableAt ?? '') || 0);
  const form = useForm<ContactFormValues>({ resolver: zodResolver(contactSchema), defaultValues: { type: 'EMAIL', value: '', currentPassword: '' } });
  const otpForm = useForm<z.infer<typeof contactOtpSchema>>({ resolver: zodResolver(contactOtpSchema), defaultValues: { otp: '' } });
  useEffect(() => {
    if (error?.code === 'AUTH_CURRENT_PASSWORD_INCORRECT') form.setError('currentPassword', { message: error.message });
  }, [error, form]);
  async function request(values: ContactFormValues) {
    const response = await run(() => profileService.changeContact(values.type, values.value, values.currentPassword));
    if (response) { setChallenge(response.data.data); form.setValue('currentPassword', ''); }
  }
  async function verify({ otp }: { otp: string }) {
    if (challenge && await run(() => profileService.verifyContact(challenge.challengeId, otp))) {
      navigate('/login', { replace: true, state: { contactChanged: true } });
    }
  }
  async function resend() {
    if (!challenge) return;
    const response = await run(() => profileService.resendContactOtp(challenge.challengeId));
    if (response) { setChallenge(response.data.data); otpForm.reset(); }
  }
  return <div className="ui-stack">
    {error && <ApiErrorNotice error={error} />}
    {challenge ? <form className="ui-form" noValidate onSubmit={otpForm.handleSubmit(verify)}>
      <Alert tone="info">Mã OTP đã gửi tới {challenge.maskedTarget}. Thông tin hiện tại chỉ được thay đổi sau khi xác thực thành công. Bạn sẽ cần đăng nhập lại.</Alert>
      <Input label="Mã OTP đổi liên hệ" required autoComplete="one-time-code" inputMode="numeric" maxLength={6} disabled={busy} {...otpForm.register('otp')} error={otpForm.formState.errors.otp?.message} />
      <div className="ui-row"><Button variant="secondary" onClick={() => void resend()} disabled={busy || resendSeconds > 0 || retryAfterSeconds > 0}>
        {resendSeconds > 0 ? `Gửi lại sau ${resendSeconds}s` : 'Gửi lại mã'}</Button>
        <Button variant="ghost" disabled={busy} onClick={() => setChallenge(undefined)}>Hủy thay đổi</Button></div>
      <div className="ui-form-actions"><Button type="submit" loading={busy} disabled={retryAfterSeconds > 0} loadingText="Đang xác thực…">Xác nhận thay đổi</Button></div>
    </form> : <form className="ui-form" noValidate onSubmit={form.handleSubmit(request)}>
      <div className="ui-form-grid"><Select label="Thông tin cần đổi" disabled={busy} {...form.register('type')}><option value="EMAIL">Email</option><option value="PHONE">Số điện thoại</option></Select>
        <Input label="Email hoặc số điện thoại mới" required maxLength={254} disabled={busy} {...form.register('value')} error={form.formState.errors.value?.message} />
        <Input label="Mật khẩu hiện tại để đổi liên hệ" required type="password" autoComplete="current-password" maxLength={72} fieldClassName="ui-span-full" disabled={busy}
          {...form.register('currentPassword')} error={form.formState.errors.currentPassword?.message} /></div>
      <p className="ui-muted">Đổi email: nhận OTP tại email mới. Đổi SĐT: nhận OTP tại email hiện tại để xác nhận thao tác; số mới chưa được xác thực qua SMS.</p>
      <div className="ui-form-actions"><Button type="submit" loading={busy} disabled={retryAfterSeconds > 0} loadingText="Đang gửi…">Gửi mã xác nhận</Button></div>
    </form>}
    {retryAfterSeconds > 0 && <p role="status">Có thể thử lại sau {retryAfterSeconds} giây.</p>}
  </div>;
}
