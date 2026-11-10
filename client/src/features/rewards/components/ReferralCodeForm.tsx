import { useState, type FormEvent } from 'react';
import { Gift } from 'lucide-react';
import { getApiError, type ApiError } from '../../../api/ApiError';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import Button from '../../../components/ui/Button';
import Input from '../../../components/ui/Input';
import { rewardsService } from '../../../services/rewardsService';
import type { ReferralDetail } from '../../../types/rewards';

interface ReferralCodeFormProps {
  onApplied: (referral: ReferralDetail) => void;
  onEligibilityChanged: () => void;
}

export default function ReferralCodeForm({ onApplied, onEligibilityChanged }: ReferralCodeFormProps) {
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError>();
  const [fieldError, setFieldError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const normalized = code.trim().toUpperCase();
    if (!/^[A-F0-9]{32}$/.test(normalized)) {
      setFieldError('Mã giới thiệu phải có 32 ký tự từ 0–9 hoặc A–F.');
      return;
    }
    setBusy(true);
    setError(undefined);
    setFieldError('');
    try {
      const response = await rewardsService.applyReferral(normalized);
      onApplied(response.data);
    } catch (cause) {
      const failure = getApiError(cause);
      if (['AUTH_REFERRAL_ALREADY_RECORDED', 'AUTH_REFERRAL_TOO_LATE'].includes(failure.code ?? '')) {
        onEligibilityChanged();
      } else {
        setError(failure);
      }
    } finally {
      setBusy(false);
    }
  }

  return <form className="ui-stack" onSubmit={submit} noValidate aria-busy={busy}>
    <p className="ui-muted">Bạn chưa dùng mã giới thiệu. Nhập mã trước khi hoàn tất đơn đầu tiên để tham gia nhận thưởng.</p>
    {error && <ApiErrorNotice error={error} />}
    <Input id="referral-code" label="Mã giới thiệu" value={code} autoComplete="off" required
      maxLength={64} disabled={busy} icon={<Gift size={18} />}
      placeholder="Nhập mã của người giới thiệu" error={fieldError || error?.fieldErrors.referralCode}
      hint="Mã chỉ được ghi nhận một lần và không thể thay đổi sau khi lưu."
      onChange={event => { setCode(event.target.value); setFieldError(''); setError(undefined); }} />
    <div className="ui-form-actions">
      <Button type="submit" loading={busy} loadingText="Đang ghi nhận…" disabled={!code.trim()}>
        Lưu mã giới thiệu
      </Button>
    </div>
  </form>;
}
