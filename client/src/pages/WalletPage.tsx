import { useEffect, useState, useId } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
  Wallet as WalletIcon,
  Lock,
  Coins,
  ArrowUpRight,
  ShieldCheck,
  RefreshCw,
  CreditCard,
  CheckCircle2,
  XCircle
} from 'lucide-react';
import { useAuthentication } from '../auth/useAuthentication';
import { walletApi } from '../api/walletApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { WalletResponse, VNPayReturnResponse } from '../types/payment';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Alert from '../components/ui/Alert';
import Badge from '../components/ui/Badge';
import ApiErrorNotice from '../components/ApiErrorNotice';
import '../styles/payment.css';

const PRESET_AMOUNTS = [
  50_000,
  100_000,
  200_000,
  500_000,
  1_000_000,
  2_000_000,
  5_000_000,
];

const BANK_OPTIONS = [
  { code: '', label: 'Tất cả phương thức', sub: 'Cổng VNPay' },
  { code: 'VNPAYQR', label: 'VNPAY-QR', sub: 'Quét mã QR' },
  { code: 'VNBANK', label: 'Thẻ ATM / Tài khoản', sub: 'Nội địa' },
  { code: 'INTCARD', label: 'Thẻ quốc tế', sub: 'Visa, Master, JCB' },
  { code: 'NCB', label: 'Ngân hàng NCB', sub: 'Thẻ ATM demo' },
];

const MIN_TOPUP = 20_000;
const MAX_TOPUP = 10_000_000;
const MAX_BALANCE = 20_000_000;

export default function WalletPage() {
  const { user, initializing } = useAuthentication();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  const [wallet, setWallet] = useState<WalletResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  // Top-up form state
  const [amount, setAmount] = useState<number>(100_000);
  const [customInput, setCustomInput] = useState<string>('100000');
  const [selectedBank, setSelectedBank] = useState<string>('');

  // VNPay return state (if returning from VNPay)
  const [returnResult, setReturnResult] = useState<VNPayReturnResponse | null>(null);
  const [verifyingReturn, setVerifyingReturn] = useState(false);

  const amountInputId = useId();

  // Load wallet data
  const loadWallet = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await walletApi.getWallet();
      setWallet(res.data);
    } catch (err) {
      const apiErr = getApiError(err);
      setError(apiErr);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (user) {
      loadWallet();
    }
  }, [user]);

  // Handle VNPay Return params if present
  useEffect(() => {
    const vnpResponseCode = searchParams.get('vnp_ResponseCode');
    const vnpTxnRef = searchParams.get('vnp_TxnRef');

    if (vnpResponseCode && vnpTxnRef) {
      const verify = async () => {
        setVerifyingReturn(true);
        try {
          const params: Record<string, string> = {};
          searchParams.forEach((val, key) => {
            params[key] = val;
          });
          const res = await walletApi.verifyVNPayReturn(params);
          setReturnResult(res.data);
          // Refresh wallet balance after return
          loadWallet();
        } catch (err) {
          const apiErr = getApiError(err);
          setError(apiErr);
        } finally {
          setVerifyingReturn(false);
        }
      };
      verify();
    }
  }, [searchParams]);

  if (initializing) {
    return <section className="ui-stack"><p className="ui-muted">Đang kiểm tra phiên đăng nhập…</p></section>;
  }

  if (!user) {
    return (
      <div className="ui-stack">
        <PageHeader title="Ví FPTPost" description="Vui lòng đăng nhập để quản lý ví và nạp tiền." />
        <SectionCard title="Yêu cầu xác thực">
          <p className="ui-muted">Bạn cần đăng nhập bằng tài khoản Người gửi (SENDER) hoặc Người giao (COURIER) để truy cập ví.</p>
          <div className="ui-form-actions" style={{ marginTop: 'var(--space-4)' }}>
            <Button variant="primary" onClick={() => navigate('/login')}>Đăng nhập ngay</Button>
          </div>
        </SectionCard>
      </div>
    );
  }

  const handlePresetSelect = (val: number) => {
    setAmount(val);
    setCustomInput(val.toString());
  };

  const handleCustomChange = (e: ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value.replace(/\D/g, '');
    const num = Number(raw);
    setCustomInput(raw);
    setAmount(num);
  };

  const validateAmount = () => {
    if (amount < MIN_TOPUP) {
      return `Số tiền nạp tối thiểu là ${MIN_TOPUP.toLocaleString('vi-VN')} đ`;
    }
    if (amount > MAX_TOPUP) {
      return `Số tiền nạp tối đa một lần là ${MAX_TOPUP.toLocaleString('vi-VN')} đ`;
    }
    if (wallet && (wallet.totalBalance + amount > MAX_BALANCE)) {
      return `Tổng số dư ví sau nạp không được vượt quá hạn mức ${MAX_BALANCE.toLocaleString('vi-VN')} đ (Hiện tại: ${wallet.totalBalance.toLocaleString('vi-VN')} đ)`;
    }
    return null;
  };

  const validationError = validateAmount();

  const handleTopup = async (e: FormEvent) => {
    e.preventDefault();
    if (validationError) return;

    try {
      setSubmitting(true);
      setError(null);
      const res = await walletApi.topup({
        amount,
        bankCode: selectedBank || undefined,
        language: 'vn',
      });

      if (res.data && res.data.paymentUrl) {
        // Redirect user to VNPay payment gateway
        window.location.href = res.data.paymentUrl;
      }
    } catch (err) {
      const apiErr = getApiError(err);
      setError(apiErr);
      setSubmitting(false);
    }
  };

  const clearReturnNotice = () => {
    setReturnResult(null);
    setSearchParams({});
  };

  return (
    <div className="ui-stack" style={{ maxWidth: 1000, margin: '0 auto' }}>
      <PageHeader
        eyebrow="Tài chính"
        title="Ví tiền FPTPost"
        description="Quản lý số dư, tiền ký quỹ đơn hàng và nạp tiền an toàn qua cổng VNPay (UC-34, SCR-42)."
        actions={
          <Button
            variant="secondary"
            size="sm"
            onClick={loadWallet}
            disabled={loading}
            icon={<RefreshCw size={15} />}
          >
            Làm mới
          </Button>
        }
      />

      {error && <ApiErrorNotice error={error} />}

      {/* Return Notification from VNPay */}
      {verifyingReturn && (
        <Alert tone="info">Đang xác thực giao dịch từ VNPay, vui lòng chờ trong giây lát…</Alert>
      )}

      {returnResult && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          {returnResult.success ? (
            <Alert
              tone="success"
              action={<Button variant="ghost" size="sm" onClick={clearReturnNotice}>Đóng</Button>}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                <CheckCircle2 size={18} color="var(--accent-emerald)" />
                <div>
                  <strong>Nạp tiền thành công!</strong> Số tiền {returnResult.amount ? (returnResult.amount / 100).toLocaleString('vi-VN') : ''} đ đã được cộng vào ví của bạn.
                  <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                    Mã GD VNPay: {returnResult.transactionNo || 'N/A'} | Mã giao dịch: {returnResult.orderCode}
                  </div>
                </div>
              </div>
            </Alert>
          ) : (
            <Alert
              tone="danger"
              action={<Button variant="ghost" size="sm" onClick={clearReturnNotice}>Đóng</Button>}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                <XCircle size={18} color="var(--accent-rose)" />
                <div>
                  <strong>Giao dịch không thành công hoặc bị hủy!</strong> {returnResult.message}
                  <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                    Mã lỗi VNPay: {returnResult.responseCode}
                  </div>
                </div>
              </div>
            </Alert>
          )}
        </div>
      )}

      {/* 3 Dashboard Cards */}
      <div className="wallet-dashboard">
        <div className="wallet-stat-card primary">
          <div className="card-glow" />
          <div className="wallet-stat-header">
            <span>Số dư khả dụng</span>
            <WalletIcon size={20} />
          </div>
          <div className="wallet-stat-value">
            {wallet ? wallet.available.toLocaleString('vi-VN') : '0'} <span style={{ fontSize: '1.1rem', fontWeight: 500 }}>đ</span>
          </div>
          <div className="wallet-stat-subtext">
            Sẵn sàng dùng để thanh toán cước phí & ký quỹ
          </div>
        </div>

        <div className="wallet-stat-card">
          <div className="wallet-stat-header">
            <span>Đang ký quỹ (Escrow)</span>
            <Lock size={20} />
          </div>
          <div className="wallet-stat-value">
            {wallet ? wallet.escrowHeld.toLocaleString('vi-VN') : '0'} <span style={{ fontSize: '1.1rem', fontWeight: 500 }}>đ</span>
          </div>
          <div className="wallet-stat-subtext">
            Tạm giữ bảo đảm cho các đơn hàng đang vận chuyển
          </div>
        </div>

        <div className="wallet-stat-card">
          <div className="wallet-stat-header">
            <span>Tổng tài sản ví</span>
            <Coins size={20} />
          </div>
          <div className="wallet-stat-value">
            {wallet ? wallet.totalBalance.toLocaleString('vi-VN') : '0'} <span style={{ fontSize: '1.1rem', fontWeight: 500 }}>đ</span>
          </div>
          <div className="wallet-stat-subtext">
            Hạn mức tối đa: {MAX_BALANCE.toLocaleString('vi-VN')} đ (BR-R-050)
          </div>
        </div>
      </div>

      {/* Topup Form */}
      <SectionCard
        title="Nạp tiền vào ví qua cổng VNPay"
        description="Nạp số dư tức thì qua thẻ ATM, QR Code hoặc Thẻ quốc tế với độ bảo mật cao."
        actions={<Badge tone="info" dot>VNPay Sandbox Verified</Badge>}
      >
        <form onSubmit={handleTopup} className="ui-stack">
          {/* Preset Buttons */}
          <div>
            <label className="ui-label" style={{ marginBottom: 'var(--space-2)' }}>Chọn nhanh số tiền nạp:</label>
            <div className="amount-presets-grid">
              {PRESET_AMOUNTS.map((val) => (
                <button
                  key={val}
                  type="button"
                  className={`amount-preset-chip ${amount === val ? 'active' : ''}`}
                  onClick={() => handlePresetSelect(val)}
                >
                  {val.toLocaleString('vi-VN')} đ
                </button>
              ))}
            </div>
          </div>

          {/* Custom Input */}
          <Input
            id={amountInputId}
            label="Hoặc nhập số tiền tùy chọn (VNĐ):"
            hint={`Tối thiểu ${MIN_TOPUP.toLocaleString('vi-VN')} đ · Tối đa ${MAX_TOPUP.toLocaleString('vi-VN')} đ`}
            error={validationError || undefined}
            type="text"
            inputMode="numeric"
            value={customInput}
            onChange={handleCustomChange}
            placeholder="VD: 200000"
          />

          {/* Payment Method Bank Selection */}
          <div>
            <label className="ui-label" style={{ marginBottom: 'var(--space-1)' }}>Phương thức thanh toán / Ngân hàng:</label>
            <div className="bank-selection-grid">
              {BANK_OPTIONS.map((bank) => (
                <div
                  key={bank.code}
                  className={`bank-option ${selectedBank === bank.code ? 'active' : ''}`}
                  onClick={() => setSelectedBank(bank.code)}
                >
                  <div className="bank-option-icon">{bank.code || <CreditCard size={18} />}</div>
                  <div className="bank-option-label"><strong>{bank.label}</strong></div>
                  <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>{bank.sub}</div>
                </div>
              ))}
            </div>
          </div>

          <div style={{
            background: 'rgba(255, 255, 255, 0.02)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-md)',
            padding: 'var(--space-4)',
            marginTop: 'var(--space-2)'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
              <ShieldCheck size={18} color="var(--primary)" />
              <span>Giao dịch được bảo vệ và mã hóa SHA-512 chuẩn ngân hàng qua VNPay Sandbox.</span>
            </div>
          </div>

          <div className="ui-form-actions" style={{ marginTop: 'var(--space-4)' }}>
            <Button
              type="submit"
              variant="primary"
              size="md"
              loading={submitting}
              loadingText="Đang kết nối cổng VNPay…"
              disabled={!!validationError || submitting || amount <= 0}
              icon={<ArrowUpRight size={18} />}
            >
              Tiến hành nạp {amount > 0 ? `${amount.toLocaleString('vi-VN')} đ` : ''} qua VNPay
            </Button>
          </div>
        </form>
      </SectionCard>
    </div>
  );
}
