import { useEffect, useState, useId } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
  Wallet as WalletIcon,
  Lock,
  Coins,
  ArrowUpRight,
  ArrowDownLeft,
  ShieldCheck,
  RefreshCw,
  CreditCard,
  CheckCircle2,
  XCircle,
  FileText,
  Filter,
  RotateCcw,
  Calendar,
  History,
  FileSpreadsheet,
  Banknote,
  Send,
  AlertCircle,
  Clock,
} from 'lucide-react';
import { useAuthentication } from '../auth/useAuthentication';
import { walletApi } from '../api/walletApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type {
  WalletResponse,
  VNPayReturnResponse,
  WalletTransactionResponse,
  WalletStatementResponse,
  WithdrawalResponse,
  WithdrawalStatus,
  LedgerTransactionType,
} from '../types/payment';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Alert from '../components/ui/Alert';
import Badge from '../components/ui/Badge';
import Modal from '../components/ui/Modal';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
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

const POPULAR_BANKS = [
  'Vietcombank',
  'Vietinbank',
  'BIDV',
  'Agribank',
  'Techcombank',
  'MB Bank',
  'ACB',
  'VPBank',
  'TPBank',
  'Sacombank',
  'HDBank',
  'VIB',
  'SHB',
  'MSB',
  'OCB',
  'SeABank',
  'Nam A Bank',
  'Kienlongbank',
  'Bac A Bank',
  'NCB',
];

const TX_TYPE_LABELS: Record<LedgerTransactionType, { label: string; tone: 'success' | 'warning' | 'info' | 'neutral' }> = {
  WALLET_TOPUP: { label: 'Nạp tiền ví', tone: 'success' },
  ORDER_ESCROW_HOLD: { label: 'Ký quỹ đơn hàng', tone: 'warning' },
  ESCROW_RELEASE: { label: 'Nhận giải ngân', tone: 'success' },
  ESCROW_REFUND: { label: 'Hoàn tiền ký quỹ', tone: 'info' },
  WITHDRAWAL_REQUEST: { label: 'Yêu cầu rút tiền', tone: 'warning' },
  WITHDRAWAL_CANCEL: { label: 'Hoàn trả rút tiền', tone: 'info' },
};

const WDR_STATUS_CONFIG: Record<WithdrawalStatus, { label: string; tone: 'warning' | 'success' | 'danger' | 'neutral' }> = {
  PENDING_REVIEW: { label: 'Chờ duyệt', tone: 'warning' },
  APPROVED: { label: 'Đã duyệt & chi trả', tone: 'success' },
  REJECTED: { label: 'Bị từ chối', tone: 'danger' },
  CANCELLED: { label: 'Đã hủy', tone: 'neutral' },
};

const MIN_TOPUP = 20_000;
const MAX_TOPUP = 10_000_000;
const MAX_BALANCE = 20_000_000;
const MIN_WITHDRAWAL = 50_000;

function formatDateTime(isoString?: string) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    return d.toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  } catch {
    return isoString;
  }
}

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

  // Transaction history state (UC-38)
  const [transactions, setTransactions] = useState<WalletTransactionResponse[]>([]);
  const [txLoading, setTxLoading] = useState(false);
  const [txPage, setTxPage] = useState(0);
  const [txTotalPages, setTxTotalPages] = useState(0);
  const [txTotalElements, setTxTotalElements] = useState(0);
  const [txType, setTxType] = useState<LedgerTransactionType | ''>('');
  const [txFromDate, setTxFromDate] = useState('');
  const [txToDate, setTxToDate] = useState('');

  // Monthly Statement Modal state (UC-38)
  const [isStatementOpen, setIsStatementOpen] = useState(false);
  const [statement, setStatement] = useState<WalletStatementResponse | null>(null);
  const [statementLoading, setStatementLoading] = useState(false);
  const currentDate = new Date();
  const [statementMonth, setStatementMonth] = useState<number>(currentDate.getMonth() + 1);
  const [statementYear, setStatementYear] = useState<number>(currentDate.getFullYear());

  // Withdrawal Request state (UC-40)
  const [isWithdrawModalOpen, setIsWithdrawModalOpen] = useState(false);
  const [withdrawAmount, setWithdrawAmount] = useState<number>(50_000);
  const [withdrawAmountInput, setWithdrawAmountInput] = useState<string>('50000');
  const [withdrawBank, setWithdrawBank] = useState<string>('Vietcombank');
  const [withdrawAccount, setWithdrawAccount] = useState<string>('');
  const [withdrawHolder, setWithdrawHolder] = useState<string>('');
  const [withdrawSubmitting, setWithdrawSubmitting] = useState(false);
  const [withdrawSuccess, setWithdrawSuccess] = useState<string | null>(null);
  const [withdrawError, setWithdrawError] = useState<string | null>(null);

  // Withdrawal tracking list state (UC-40)
  const [withdrawals, setWithdrawals] = useState<WithdrawalResponse[]>([]);
  const [wdrLoading, setWdrLoading] = useState(false);
  const [wdrPage, setWdrPage] = useState(0);
  const [wdrTotalPages, setWdrTotalPages] = useState(0);
  const [wdrTotalElements, setWdrTotalElements] = useState(0);
  const [cancellingId, setCancellingId] = useState<number | null>(null);

  const amountInputId = useId();
  const withdrawAmountInputId = useId();
  const withdrawAccountInputId = useId();
  const withdrawHolderInputId = useId();

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

  // Load transaction history (UC-38)
  const loadTransactions = async (page = 0) => {
    try {
      setTxLoading(true);
      const res = await walletApi.getTransactions({
        page,
        size: 10,
        type: (txType as LedgerTransactionType) || undefined,
        fromDate: txFromDate || undefined,
        toDate: txToDate || undefined,
      });
      setTransactions(res.data.content);
      setTxPage(res.data.page);
      setTxTotalPages(res.data.totalPages);
      setTxTotalElements(res.data.totalElements);
    } catch (err) {
      console.error('Failed to load transaction history', err);
    } finally {
      setTxLoading(false);
    }
  };

  // Load Monthly Statement (UC-38)
  const loadStatement = async (month = statementMonth, year = statementYear) => {
    try {
      setStatementLoading(true);
      const res = await walletApi.getStatement(month, year);
      setStatement(res.data);
    } catch (err) {
      console.error('Failed to load monthly statement', err);
    } finally {
      setStatementLoading(false);
    }
  };

  // Load Withdrawal Requests (UC-40)
  const loadWithdrawals = async (page = 0) => {
    try {
      setWdrLoading(true);
      const res = await walletApi.getMyWithdrawals(page, 10);
      setWithdrawals(res.data.content);
      setWdrPage(res.data.page);
      setWdrTotalPages(res.data.totalPages);
      setWdrTotalElements(res.data.totalElements);
    } catch (err) {
      console.error('Failed to load withdrawal requests', err);
    } finally {
      setWdrLoading(false);
    }
  };

  useEffect(() => {
    if (user) {
      loadWallet();
      loadTransactions(0);
      loadWithdrawals(0);
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
          loadWallet();
          loadTransactions(0);
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

  const handleFilterSubmit = (e: FormEvent) => {
    e.preventDefault();
    loadTransactions(0);
  };

  const handleResetFilter = () => {
    setTxType('');
    setTxFromDate('');
    setTxToDate('');
    setTxLoading(true);
    walletApi.getTransactions({ page: 0, size: 10 }).then((res) => {
      setTransactions(res.data.content);
      setTxPage(res.data.page);
      setTxTotalPages(res.data.totalPages);
      setTxTotalElements(res.data.totalElements);
    }).finally(() => setTxLoading(false));
  };

  const handleOpenStatementModal = () => {
    setIsStatementOpen(true);
    loadStatement(statementMonth, statementYear);
  };

  // Withdrawal Handlers (UC-40)
  const handleOpenWithdrawModal = () => {
    setWithdrawError(null);
    if (wallet) {
      const defaultAmt = Math.min(wallet.available, 100_000);
      setWithdrawAmount(defaultAmt);
      setWithdrawAmountInput(defaultAmt.toString());
    }
    setIsWithdrawModalOpen(true);
  };

  const handleWithdrawAmountChange = (e: ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value.replace(/\D/g, '');
    const num = Number(raw);
    setWithdrawAmountInput(raw);
    setWithdrawAmount(num);
  };

  const handleSetMaxWithdraw = () => {
    if (wallet) {
      setWithdrawAmount(wallet.available);
      setWithdrawAmountInput(wallet.available.toString());
    }
  };

  const validateWithdrawal = () => {
    if (withdrawAmount < MIN_WITHDRAWAL) {
      return `Số tiền rút tối thiểu là ${MIN_WITHDRAWAL.toLocaleString('vi-VN')} đ`;
    }
    if (wallet && withdrawAmount > wallet.available) {
      return `Số tiền rút vượt quá số dư khả dụng hiện tại (${wallet.available.toLocaleString('vi-VN')} đ)`;
    }
    if (!withdrawBank.trim()) {
      return 'Vui lòng chọn ngân hàng thụ hưởng';
    }
    if (!withdrawAccount.trim()) {
      return 'Vui lòng nhập số tài khoản ngân hàng';
    }
    if (!withdrawHolder.trim()) {
      return 'Vui lòng nhập họ và tên chủ tài khoản thụ hưởng';
    }
    return null;
  };

  const withdrawValidationError = validateWithdrawal();

  const handleCreateWithdrawal = async (e: FormEvent) => {
    e.preventDefault();
    if (withdrawValidationError) return;

    try {
      setWithdrawSubmitting(true);
      setWithdrawError(null);

      const res = await walletApi.createWithdrawal({
        amount: withdrawAmount,
        bankName: withdrawBank.trim(),
        accountNumber: withdrawAccount.trim(),
        accountHolderName: withdrawHolder.trim().toUpperCase(),
      });

      setIsWithdrawModalOpen(false);
      setWithdrawSuccess(`Yêu cầu rút tiền ${res.data.amount.toLocaleString('vi-VN')} đ (Mã: ${res.data.requestCode}) đã được tạo thành công và đang chờ ban quản trị phê duyệt.`);

      // Refresh wallet balances, transactions, and withdrawal list
      loadWallet();
      loadTransactions(0);
      loadWithdrawals(0);
    } catch (err) {
      const apiErr = getApiError(err);
      setWithdrawError(apiErr.message || 'Không thể tạo yêu cầu rút tiền. Vui lòng kiểm tra lại số dư.');
    } finally {
      setWithdrawSubmitting(false);
    }
  };

  const handleCancelWithdrawal = async (id: number) => {
    if (!window.confirm('Bạn có chắc chắn muốn hủy yêu cầu rút tiền này? Số tiền sẽ được hoàn trả ngay lập tức về số dư khả dụng của ví.')) {
      return;
    }

    try {
      setCancellingId(id);
      const res = await walletApi.cancelWithdrawal(id);
      setWithdrawSuccess(`Đã hủy thành công yêu cầu rút tiền ${res.data.requestCode}. Số tiền ${res.data.amount.toLocaleString('vi-VN')} đ đã được hoàn về ví.`);

      // Refresh data
      loadWallet();
      loadTransactions(0);
      loadWithdrawals(wdrPage);
    } catch (err) {
      const apiErr = getApiError(err);
      setError(apiErr);
    } finally {
      setCancellingId(null);
    }
  };

  return (
    <div className="ui-stack" style={{ maxWidth: 1050, margin: '0 auto' }}>
      <PageHeader
        eyebrow="Tài chính & Ví"
        title="Ví tiền & Lịch sử giao dịch"
        description="Quản lý số dư, nạp tiền VNPay, rút tiền về ngân hàng và tra cứu biến động số dư (UC-34, UC-38, UC-40)."
        actions={
          <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
            <Button
              variant="primary"
              size="sm"
              onClick={handleOpenWithdrawModal}
              disabled={!wallet || wallet.available < MIN_WITHDRAWAL}
              icon={<Banknote size={15} />}
            >
              Rút tiền về ngân hàng
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={handleOpenStatementModal}
              icon={<FileSpreadsheet size={15} />}
            >
              Sao kê tháng
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                loadWallet();
                loadTransactions(txPage);
                loadWithdrawals(wdrPage);
              }}
              disabled={loading || txLoading || wdrLoading}
              icon={<RefreshCw size={15} />}
            >
              Làm mới
            </Button>
          </div>
        }
      />

      {error && <ApiErrorNotice error={error} />}

      {withdrawSuccess && (
        <Alert
          tone="success"
          action={<Button variant="ghost" size="sm" onClick={() => setWithdrawSuccess(null)}>Đóng</Button>}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <CheckCircle2 size={18} color="var(--accent-emerald)" />
            <span>{withdrawSuccess}</span>
          </div>
        </Alert>
      )}

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

      {/* 4 Dashboard Cards */}
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
            Sẵn sàng chi tiêu, ký quỹ hoặc rút tiền
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
            <span>Đang chờ rút (Pending)</span>
            <Clock size={20} />
          </div>
          <div className="wallet-stat-value">
            {wallet ? wallet.pendingWithdraw.toLocaleString('vi-VN') : '0'} <span style={{ fontSize: '1.1rem', fontWeight: 500 }}>đ</span>
          </div>
          <div className="wallet-stat-subtext">
            Đang trong quy trình xét duyệt chi trả ngân hàng
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
        title="Nạp tiền vào ví qua cổng VNPay (UC-34)"
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

      {/* Withdrawal Tracking List (UC-40) */}
      <SectionCard
        title="Yêu cầu rút tiền về ngân hàng (UC-40)"
        description="Theo dõi danh sách các yêu cầu rút tiền của bạn và trạng thái xét duyệt chi trả từ ban quản trị."
        actions={
          <Button
            variant="primary"
            size="sm"
            onClick={handleOpenWithdrawModal}
            disabled={!wallet || wallet.available < MIN_WITHDRAWAL}
            icon={<Banknote size={15} />}
          >
            Tạo yêu cầu rút
          </Button>
        }
      >
        {wdrLoading ? (
          <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>
            <RefreshCw className="spin" size={24} style={{ margin: '0 auto var(--space-2)' }} />
            <p>Đang tải danh sách yêu cầu rút tiền…</p>
          </div>
        ) : withdrawals.length === 0 ? (
          <EmptyState
            title="Chưa có yêu cầu rút tiền nào"
            description="Bạn chưa tạo yêu cầu rút tiền nào từ ví về tài khoản ngân hàng."
            icon={<Banknote size={32} />}
            action={
              wallet && wallet.available >= MIN_WITHDRAWAL ? (
                <Button variant="primary" size="sm" onClick={handleOpenWithdrawModal} style={{ marginTop: 'var(--space-3)' }}>
                  Rút tiền ngay
                </Button>
              ) : undefined
            }
          />
        ) : (
          <div className="ui-stack">
            <div className="tx-table-container">
              <table className="tx-table">
                <thead>
                  <tr>
                    <th>Mã yêu cầu</th>
                    <th>Thời gian tạo</th>
                    <th>Số tiền rút</th>
                    <th>Ngân hàng thụ hưởng</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {withdrawals.map((wdr) => {
                    const statusConfig = WDR_STATUS_CONFIG[wdr.status] || { label: wdr.status, tone: 'neutral' };
                    const isPending = wdr.status === 'PENDING_REVIEW';

                    return (
                      <tr key={wdr.id}>
                        <td>
                          <span className="tx-code">{wdr.requestCode}</span>
                        </td>
                        <td>
                          <span className="tx-date">{formatDateTime(wdr.createdAt)}</span>
                        </td>
                        <td>
                          <span className="tx-amount outflow">
                            -{wdr.amount.toLocaleString('vi-VN')} đ
                          </span>
                        </td>
                        <td>
                          <div>
                            <strong>{wdr.bankName}</strong> · {wdr.accountNumber}
                            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                              Chủ TK: {wdr.accountHolderName}
                            </div>
                          </div>
                        </td>
                        <td>
                          <Badge tone={statusConfig.tone} dot={isPending}>
                            {statusConfig.label}
                          </Badge>
                          {wdr.rejectionReason && (
                            <div style={{ fontSize: '0.75rem', color: 'var(--accent-rose)', marginTop: '2px' }}>
                              Lý do: {wdr.rejectionReason}
                            </div>
                          )}
                        </td>
                        <td>
                          {isPending ? (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="btn-cancel-wdr"
                              disabled={cancellingId === wdr.id}
                              onClick={() => handleCancelWithdrawal(wdr.id)}
                              icon={<XCircle size={14} />}
                            >
                              {cancellingId === wdr.id ? 'Đang hủy…' : 'Hủy yêu cầu'}
                            </Button>
                          ) : (
                            <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>—</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {wdrTotalPages > 1 && (
              <Pagination
                page={wdrPage}
                size={10}
                totalElements={wdrTotalElements}
                totalPages={wdrTotalPages}
                onPageChange={(p) => loadWithdrawals(p)}
                disabled={wdrLoading}
              />
            )}
          </div>
        )}
      </SectionCard>

      {/* Transaction History Section (UC-38) */}
      <SectionCard
        title="Lịch sử biến động số dư (UC-38)"
        description="Theo dõi toàn bộ dòng tiền nạp, ký quỹ đơn hàng và giải ngân vào ví cá nhân theo chuẩn sổ cái kép."
        actions={
          <Button
            variant="secondary"
            size="sm"
            onClick={handleOpenStatementModal}
            icon={<FileText size={15} />}
          >
            Bảng sao kê tháng
          </Button>
        }
      >
        {/* Filter Bar */}
        <form onSubmit={handleFilterSubmit} className="tx-filter-bar">
          <div className="tx-filter-item">
            <label htmlFor="tx-type-filter">Loại giao dịch:</label>
            <select
              id="tx-type-filter"
              className="ui-control ui-select"
              value={txType}
              onChange={(e) => setTxType(e.target.value as LedgerTransactionType | '')}
            >
              <option value="">Tất cả giao dịch</option>
              <option value="WALLET_TOPUP">Nạp tiền vào ví</option>
              <option value="ORDER_ESCROW_HOLD">Ký quỹ đơn hàng</option>
              <option value="ESCROW_RELEASE">Nhận giải ngân</option>
              <option value="ESCROW_REFUND">Hoàn tiền ký quỹ</option>
              <option value="WITHDRAWAL_REQUEST">Yêu cầu rút tiền</option>
              <option value="WITHDRAWAL_CANCEL">Hoàn trả rút tiền</option>
            </select>
          </div>

          <div className="tx-filter-item">
            <label htmlFor="tx-from-date">Từ ngày:</label>
            <input
              id="tx-from-date"
              type="date"
              className="ui-control ui-input"
              value={txFromDate}
              onChange={(e) => setTxFromDate(e.target.value)}
            />
          </div>

          <div className="tx-filter-item">
            <label htmlFor="tx-to-date">Đến ngày:</label>
            <input
              id="tx-to-date"
              type="date"
              className="ui-control ui-input"
              value={txToDate}
              onChange={(e) => setTxToDate(e.target.value)}
            />
          </div>

          <div className="tx-filter-actions">
            <Button
              type="submit"
              variant="primary"
              size="sm"
              disabled={txLoading}
              icon={<Filter size={15} />}
            >
              Lọc
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={handleResetFilter}
              icon={<RotateCcw size={15} />}
            >
              Đặt lại
            </Button>
          </div>
        </form>

        {/* Transactions Table */}
        {txLoading ? (
          <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>
            <RefreshCw className="spin" size={24} style={{ margin: '0 auto var(--space-2)' }} />
            <p>Đang tải danh sách giao dịch…</p>
          </div>
        ) : transactions.length === 0 ? (
          <EmptyState
            title="Chưa có giao dịch nào"
            description="Không tìm thấy biến động số dư nào phù hợp với bộ lọc đã chọn."
            icon={<History size={32} />}
          />
        ) : (
          <div className="ui-stack">
            <div className="tx-table-container">
              <table className="tx-table">
                <thead>
                  <tr>
                    <th>Mã chứng từ</th>
                    <th>Thời gian</th>
                    <th>Loại giao dịch</th>
                    <th>Biến động số dư</th>
                    <th>Nội dung / Tham chiếu</th>
                    <th>Trạng thái</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map((tx) => {
                    const isInflow = tx.balanceDirection === 'IN';
                    const typeConfig = TX_TYPE_LABELS[tx.type] || { label: tx.type, tone: 'neutral' };

                    return (
                      <tr key={tx.transactionCode}>
                        <td>
                          <span className="tx-code">{tx.transactionCode}</span>
                        </td>
                        <td>
                          <span className="tx-date">{formatDateTime(tx.createdAt)}</span>
                        </td>
                        <td>
                          <Badge tone={typeConfig.tone}>{typeConfig.label}</Badge>
                        </td>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                            {isInflow ? (
                              <ArrowDownLeft size={16} color="#10b981" />
                            ) : (
                              <ArrowUpRight size={16} color="#f43f5e" />
                            )}
                            <span className={`tx-amount ${isInflow ? 'inflow' : 'outflow'}`}>
                              {isInflow ? '+' : '-'}{tx.amount.toLocaleString('vi-VN')} đ
                            </span>
                          </div>
                        </td>
                        <td>
                          <div>
                            <div>{tx.description}</div>
                            {tx.orderId && (
                              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                                Đơn hàng ID: #{tx.orderId}
                              </div>
                            )}
                          </div>
                        </td>
                        <td>
                          <Badge tone="success" dot>Hoàn thành</Badge>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            {txTotalPages > 1 && (
              <Pagination
                page={txPage}
                size={10}
                totalElements={txTotalElements}
                totalPages={txTotalPages}
                onPageChange={(p) => loadTransactions(p)}
                disabled={txLoading}
              />
            )}
          </div>
        )}
      </SectionCard>

      {/* Withdrawal Request Modal (UC-40, API-097) */}
      <Modal
        isOpen={isWithdrawModalOpen}
        onClose={() => setIsWithdrawModalOpen(false)}
        title="Rút tiền từ ví về tài khoản ngân hàng (UC-40)"
        description="Tiền sẽ được chuyển từ số dư khả dụng sang tạm giữ để chờ xét duyệt và chi trả."
        size="md"
      >
        <form onSubmit={handleCreateWithdrawal} className="ui-stack">
          {withdrawError && (
            <Alert tone="danger">
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                <AlertCircle size={18} />
                <span>{withdrawError}</span>
              </div>
            </Alert>
          )}

          <div style={{
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-md)',
            padding: 'var(--space-3) var(--space-4)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Số dư khả dụng hiện tại:</span>
            <span style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--accent-emerald)' }}>
              {wallet ? wallet.available.toLocaleString('vi-VN') : '0'} đ
            </span>
          </div>

          <div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 'var(--space-1)' }}>
              {wallet && wallet.available > 0 && (
                <button
                  type="button"
                  className="withdrawal-quick-max-btn"
                  onClick={handleSetMaxWithdraw}
                >
                  Rút toàn bộ ({wallet.available.toLocaleString('vi-VN')} đ)
                </button>
              )}
            </div>
            <Input
              id={withdrawAmountInputId}
              label="Số tiền muốn rút (VNĐ):"
              type="text"
              inputMode="numeric"
              value={withdrawAmountInput}
              onChange={handleWithdrawAmountChange}
              hint={`Tối thiểu ${MIN_WITHDRAWAL.toLocaleString('vi-VN')} đ`}
              placeholder="VD: 100000"
              error={withdrawValidationError?.includes('Số tiền') ? withdrawValidationError : undefined}
            />
          </div>

          <div>
            <label htmlFor="wdr-bank-select" className="ui-label" style={{ marginBottom: 'var(--space-1)' }}>
              Ngân hàng thụ hưởng:
            </label>
            <select
              id="wdr-bank-select"
              className="ui-control ui-select"
              value={withdrawBank}
              onChange={(e) => setWithdrawBank(e.target.value)}
            >
              {POPULAR_BANKS.map((b) => (
                <option key={b} value={b}>{b}</option>
              ))}
            </select>
          </div>

          <Input
            id={withdrawAccountInputId}
            label="Số tài khoản ngân hàng:"
            type="text"
            value={withdrawAccount}
            onChange={(e) => setWithdrawAccount(e.target.value)}
            placeholder="VD: 0123456789"
            required
            error={withdrawValidationError?.includes('tài khoản') ? withdrawValidationError : undefined}
          />

          <Input
            id={withdrawHolderInputId}
            label="Họ và tên chủ tài khoản thụ hưởng:"
            hint="Vui lòng nhập họ tên in hoa không dấu hoặc có dấu đúng với thẻ ngân hàng"
            type="text"
            value={withdrawHolder}
            onChange={(e) => setWithdrawHolder(e.target.value.toUpperCase())}
            placeholder="VD: NGUYEN VAN A"
            required
            error={withdrawValidationError?.includes('chủ tài khoản') ? withdrawValidationError : undefined}
          />

          {/* Breakdown Card */}
          <div className="withdrawal-summary-card">
            <div className="withdrawal-summary-row">
              <span style={{ color: 'var(--text-muted)' }}>Số tiền yêu cầu rút:</span>
              <span>{withdrawAmount > 0 ? `${withdrawAmount.toLocaleString('vi-VN')} đ` : '0 đ'}</span>
            </div>
            <div className="withdrawal-summary-row">
              <span style={{ color: 'var(--text-muted)' }}>Phí xử lý giao dịch:</span>
              <span style={{ color: 'var(--accent-emerald)', fontWeight: 600 }}>0 đ (Miễn phí)</span>
            </div>
            <div className="withdrawal-summary-row total">
              <span>Thực nhận về tài khoản:</span>
              <span>{withdrawAmount > 0 ? `${withdrawAmount.toLocaleString('vi-VN')} đ` : '0 đ'}</span>
            </div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              ⚡ Thời gian xử lý: Sau khi gửi yêu cầu, ban quản trị sẽ đối soát và chi trả trong vòng 1 - 24 giờ làm việc.
            </div>
          </div>

          <div className="ui-form-actions" style={{ marginTop: 'var(--space-4)' }}>
            <Button
              type="button"
              variant="secondary"
              onClick={() => setIsWithdrawModalOpen(false)}
            >
              Đóng
            </Button>
            <Button
              type="submit"
              variant="primary"
              loading={withdrawSubmitting}
              loadingText="Đang tạo yêu cầu…"
              disabled={!!withdrawValidationError || withdrawSubmitting}
              icon={<Send size={15} />}
            >
              Xác nhận rút {withdrawAmount > 0 ? `${withdrawAmount.toLocaleString('vi-VN')} đ` : ''}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Monthly Statement Modal (UC-38, API-090) */}
      <Modal
        isOpen={isStatementOpen}
        onClose={() => setIsStatementOpen(false)}
        title="Bảng sao kê biến động tài khoản ví (UC-38)"
        description="Tổng hợp dòng tiền vào/ra và các giao dịch phát sinh trong kỳ kế toán theo tháng."
        size="lg"
      >
        <div className="ui-stack">
          {/* Period selector */}
          <div className="statement-selector-bar">
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
              <Calendar size={18} color="var(--primary)" />
              <label style={{ fontSize: '0.9rem', fontWeight: 600 }}>Kỳ sao kê:</label>
              <select
                className="ui-control ui-select"
                style={{ width: 'auto' }}
                value={statementMonth}
                onChange={(e) => {
                  const m = Number(e.target.value);
                  setStatementMonth(m);
                  loadStatement(m, statementYear);
                }}
              >
                {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                  <option key={m} value={m}>Tháng {m}</option>
                ))}
              </select>
              <select
                className="ui-control ui-select"
                style={{ width: 'auto' }}
                value={statementYear}
                onChange={(e) => {
                  const y = Number(e.target.value);
                  setStatementYear(y);
                  loadStatement(statementMonth, y);
                }}
              >
                {[currentDate.getFullYear() - 1, currentDate.getFullYear(), currentDate.getFullYear() + 1].map((y) => (
                  <option key={y} value={y}>Năm {y}</option>
                ))}
              </select>
            </div>

            <Button
              variant="secondary"
              size="sm"
              onClick={() => loadStatement(statementMonth, statementYear)}
              disabled={statementLoading}
              icon={<RefreshCw size={14} />}
            >
              Tải lại
            </Button>
          </div>

          {statementLoading ? (
            <div style={{ padding: 'var(--space-8)', textAlign: 'center', color: 'var(--text-muted)' }}>
              <RefreshCw className="spin" size={28} style={{ margin: '0 auto var(--space-2)' }} />
              <p>Đang tính toán và xuất dữ liệu sao kê…</p>
            </div>
          ) : statement ? (
            <>
              {/* Statement Summary Cards */}
              <div className="statement-summary-grid">
                <div className="statement-card">
                  <span className="statement-card-label">Số dư đầu kỳ</span>
                  <span className="statement-card-value">
                    {statement.startingBalance.toLocaleString('vi-VN')} đ
                  </span>
                </div>

                <div className="statement-card">
                  <span className="statement-card-label">Tổng tiền vào (+)</span>
                  <span className="statement-card-value inflow">
                    +{statement.totalInflow.toLocaleString('vi-VN')} đ
                  </span>
                </div>

                <div className="statement-card">
                  <span className="statement-card-label">Tổng tiền ra (-)</span>
                  <span className="statement-card-value outflow">
                    -{statement.totalOutflow.toLocaleString('vi-VN')} đ
                  </span>
                </div>

                <div className="statement-card">
                  <span className="statement-card-label">Biến động thuần</span>
                  <span className={`statement-card-value ${statement.netChange >= 0 ? 'inflow' : 'outflow'}`}>
                    {statement.netChange >= 0 ? '+' : ''}{statement.netChange.toLocaleString('vi-VN')} đ
                  </span>
                </div>

                <div className="statement-card" style={{ borderColor: 'var(--brand-border)' }}>
                  <span className="statement-card-label">Số dư cuối kỳ</span>
                  <span className="statement-card-value" style={{ color: 'var(--primary)' }}>
                    {statement.endingBalance.toLocaleString('vi-VN')} đ
                  </span>
                </div>
              </div>

              {/* Transactions in statement */}
              <div style={{ marginTop: 'var(--space-2)' }}>
                <h4 style={{ marginBottom: 'var(--space-2)', fontSize: '0.95rem' }}>
                  Danh sách giao dịch trong kỳ ({statement.transactionCount} giao dịch):
                </h4>

                {statement.transactions.length === 0 ? (
                  <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', fontStyle: 'italic' }}>
                    Không có phát sinh giao dịch nào trong tháng {statement.period}.
                  </p>
                ) : (
                  <div className="tx-table-container" style={{ maxHeight: 320, overflowY: 'auto' }}>
                    <table className="tx-table">
                      <thead>
                        <tr>
                          <th>Thời gian</th>
                          <th>Mã GD</th>
                          <th>Loại</th>
                          <th>Số tiền</th>
                          <th>Diễn giải</th>
                        </tr>
                      </thead>
                      <tbody>
                        {statement.transactions.map((tx) => {
                          const isInflow = tx.balanceDirection === 'IN';
                          const typeConfig = TX_TYPE_LABELS[tx.type] || { label: tx.type, tone: 'neutral' };

                          return (
                            <tr key={tx.transactionCode}>
                              <td>
                                <span className="tx-date">{formatDateTime(tx.createdAt)}</span>
                              </td>
                              <td>
                                <span className="tx-code">{tx.transactionCode}</span>
                              </td>
                              <td>
                                <Badge tone={typeConfig.tone}>{typeConfig.label}</Badge>
                              </td>
                              <td>
                                <span className={`tx-amount ${isInflow ? 'inflow' : 'outflow'}`}>
                                  {isInflow ? '+' : '-'}{tx.amount.toLocaleString('vi-VN')} đ
                                </span>
                              </td>
                              <td style={{ fontSize: '0.85rem' }}>{tx.description}</td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </>
          ) : (
            <p className="ui-muted">Chưa có thông tin sao kê.</p>
          )}

          <div className="ui-form-actions" style={{ marginTop: 'var(--space-4)' }}>
            <Button variant="secondary" onClick={() => setIsStatementOpen(false)}>
              Đóng sao kê
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
