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

const TX_TYPE_LABELS: Record<LedgerTransactionType, { label: string; tone: 'success' | 'warning' | 'info' | 'neutral' }> = {
  WALLET_TOPUP: { label: 'Nạp tiền ví', tone: 'success' },
  ORDER_ESCROW_HOLD: { label: 'Ký quỹ đơn hàng', tone: 'warning' },
  ESCROW_RELEASE: { label: 'Nhận giải ngân', tone: 'success' },
  ESCROW_REFUND: { label: 'Hoàn tiền ký quỹ', tone: 'info' },
};

const MIN_TOPUP = 20_000;
const MAX_TOPUP = 10_000_000;
const MAX_BALANCE = 20_000_000;

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

  useEffect(() => {
    if (user) {
      loadWallet();
      loadTransactions(0);
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
          // Refresh wallet balance and transactions after return
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

  return (
    <div className="ui-stack" style={{ maxWidth: 1050, margin: '0 auto' }}>
      <PageHeader
        eyebrow="Tài chính & Ví"
        title="Ví tiền & Lịch sử giao dịch"
        description="Quản lý số dư, nạp tiền VNPay, tra cứu biến động số dư và sao kê định kỳ (UC-34, UC-38)."
        actions={
          <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
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
              }}
              disabled={loading || txLoading}
              icon={<RefreshCw size={15} />}
            >
              Làm mới
            </Button>
          </div>
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
