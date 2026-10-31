import { useEffect, useState } from 'react';
import type { ChangeEvent } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { 
  CreditCard, 
  Wallet as WalletIcon, 
  Package, 
  ShieldCheck, 
  CheckCircle2, 
  ExternalLink, 
  PlusCircle, 
  Truck 
} from 'lucide-react';
import { useAuthentication } from '../auth/useAuthentication';
import { orderApi } from '../api/orderApi';
import { walletApi } from '../api/walletApi';
import { getApiError } from '../api/ApiError';
import type { OrderResponse } from '../types/order';
import type { WalletResponse, EscrowPaymentMethod, OrderPaymentResponse } from '../types/payment';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import Alert from '../components/ui/Alert';
import ApiErrorNotice from '../components/ApiErrorNotice';
import '../styles/payment.css';

const BANK_OPTIONS = [
  { code: '', label: 'Tất cả phương thức', sub: 'Cổng VNPay' },
  { code: 'VNPAYQR', label: 'VNPAY-QR', sub: 'Quét mã QR' },
  { code: 'VNBANK', label: 'Thẻ ATM / Tài khoản', sub: 'Nội địa' },
  { code: 'INTCARD', label: 'Thẻ quốc tế', sub: 'Visa, Master, JCB' },
  { code: 'NCB', label: 'Ngân hàng NCB', sub: 'Thẻ ATM demo' },
];

export default function OrderPaymentPage() {
  const { id } = useParams<{ id?: string }>();
  const { user, initializing } = useAuthentication();
  const navigate = useNavigate();

  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [ordersList, setOrdersList] = useState<OrderResponse[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState<string>(id || '');
  const [wallet, setWallet] = useState<WalletResponse | null>(null);

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [correlationId, setCorrelationId] = useState<string | undefined>();

  // Payment form choice
  const [paymentMethod, setPaymentMethod] = useState<EscrowPaymentMethod>('WALLET');
  const [selectedBank, setSelectedBank] = useState<string>('');

  // Payment success result
  const [paymentResult, setPaymentResult] = useState<OrderPaymentResponse | null>(null);

  // Load Order and Wallet
  const loadData = async (orderIdToLoad?: string) => {
    try {
      setLoading(true);
      setError(null);

      // Load wallet balance
      try {
        const walletRes = await walletApi.getWallet();
        setWallet(walletRes.data);
      } catch {
        // Wallet might be null if not initialized yet
      }

      const targetId = orderIdToLoad || id || selectedOrderId;

      if (targetId) {
        const orderRes = await orderApi.getById(targetId);
        setOrder(orderRes.data);
      } else {
        // Load all orders for the user to choose from
        const listRes = await orderApi.getAll();
        setOrdersList(listRes.data || []);
        if (listRes.data && listRes.data.length > 0) {
          const firstEligible = listRes.data.find(o => o.status !== 'ESCROWED') || listRes.data[0];
          setOrder(firstEligible);
          setSelectedOrderId(firstEligible.id.toString());
        }
      }
    } catch (err) {
      const apiErr = getApiError(err);
      setError(apiErr.message);
      setCorrelationId(apiErr.correlationId);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (user) {
      loadData(id);
    }
  }, [user, id]);

  const handleOrderChange = (e: ChangeEvent<HTMLSelectElement>) => {
    const val = e.target.value;
    setSelectedOrderId(val);
    if (val) {
      loadData(val);
    }
  };

  const handlePay = async () => {
    if (!order) return;

    try {
      setSubmitting(true);
      setError(null);
      const res = await orderApi.payOrder(order.id, {
        paymentMethod,
        bankCode: paymentMethod === 'VNPAY' ? selectedBank || undefined : undefined,
        language: 'vn',
      });

      if (paymentMethod === 'VNPAY' && res.data.paymentUrl) {
        // Redirect to VNPay
        window.location.href = res.data.paymentUrl;
      } else {
        // WALLET payment success
        setPaymentResult(res.data);
        // Refresh order status
        const refreshed = await orderApi.getById(order.id);
        setOrder(refreshed.data);
        // Refresh wallet
        const wRes = await walletApi.getWallet();
        setWallet(wRes.data);
      }
    } catch (err) {
      const apiErr = getApiError(err);
      setError(apiErr.message);
      setCorrelationId(apiErr.correlationId);
    } finally {
      setSubmitting(false);
    }
  };

  if (initializing) {
    return <section className="ui-stack"><p className="ui-muted">Đang kiểm tra phiên đăng nhập…</p></section>;
  }

  if (!user) {
    return (
      <div className="ui-stack">
        <PageHeader title="Thanh toán đơn và Ký quỹ" description="Vui lòng đăng nhập để thực hiện thanh toán." />
        <SectionCard title="Yêu cầu xác thực">
          <p className="ui-muted">Bạn cần đăng nhập tài khoản Người gửi (SENDER) để thanh toán và ký quỹ đơn hàng.</p>
          <div className="ui-form-actions" style={{ marginTop: 'var(--space-4)' }}>
            <Button variant="primary" onClick={() => navigate('/login')}>Đăng nhập ngay</Button>
          </div>
        </SectionCard>
      </div>
    );
  }

  const isSender = user.roles.includes('SENDER') || user.roles.includes('ADMIN');
  const requiredAmount = (order && order.declaredValue != null) ? order.declaredValue : 0;
  const availableBalance = wallet ? wallet.available : 0;
  const isWalletBalanceEnough = availableBalance >= requiredAmount;
  const isAlreadyEscrowed = order?.status === 'ESCROWED';

  return (
    <div className="ui-stack" style={{ maxWidth: 900, margin: '0 auto' }}>
      <PageHeader 
        eyebrow="Đơn hàng & Ký quỹ" 
        title="Thanh toán & Ký quỹ Đơn hàng" 
        description="Thanh toán tiền ký quỹ (Escrow) bảo đảm hàng hóa qua Ví nội bộ FPTPost hoặc Cổng VNPay."
        actions={
          <div className="ui-row">
            <Link to="/wallet" className="ui-button ui-button-secondary ui-button-sm">
              <WalletIcon size={15} /> Quản lý Ví
            </Link>
            <Link to="/courier/market" className="ui-button ui-button-secondary ui-button-sm">
              <Package size={15} /> Sàn đơn
            </Link>
            <Link to="/courier/trips" className="ui-button ui-button-secondary ui-button-sm">
              <Truck size={15} /> Chuyến đi tiện đường
            </Link>
          </div>
        }
      />

      {error && <ApiErrorNotice message={error} correlationId={correlationId} />}

      {!isSender && (
        <Alert tone="warning">
          Tài khoản của bạn hiện chưa có quyền Người gửi (SENDER). Chỉ Người gửi tạo đơn mới có quyền thực hiện ký quỹ cho đơn hàng này.
        </Alert>
      )}

      {/* Payment Success Banner */}
      {paymentResult && (
        <Alert tone="success">
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <CheckCircle2 size={20} color="var(--accent-emerald)" />
            <div>
              <strong>Thanh toán và ký quỹ thành công!</strong> {paymentResult.message}
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                Mã đơn hàng: {paymentResult.orderCode} | Số tiền đã ký quỹ: {paymentResult.amount.toLocaleString('vi-VN')} đ
              </div>
            </div>
          </div>
        </Alert>
      )}

      {/* If no specific ID in URL and multiple orders available, show order selector */}
      {!id && ordersList.length > 0 && (
        <SectionCard title="Chọn đơn hàng cần ký quỹ">
          <div className="ui-field">
            <label className="ui-label" htmlFor="order-selector">Danh sách đơn hàng của bạn:</label>
            <select
              id="order-selector"
              className="ui-select"
              value={selectedOrderId}
              onChange={handleOrderChange}
            >
              {ordersList.map(o => (
                <option key={o.id} value={o.id}>
                  {o.orderCode} - {o.productName} ({o.declaredValue?.toLocaleString('vi-VN')} đ) - [{o.status}]
                </option>
              ))}
            </select>
          </div>
        </SectionCard>
      )}

      {loading && <p className="ui-muted">Đang tải thông tin đơn hàng và ví…</p>}

      {!loading && !order && (
        <SectionCard title="Không tìm thấy đơn hàng">
          <p className="ui-muted">Không tìm thấy thông tin đơn hàng cần thanh toán.</p>
          <div className="ui-form-actions" style={{ marginTop: 'var(--space-4)' }}>
            <Link to="/products" className="ui-button ui-button-secondary">Quay lại trang sản phẩm</Link>
          </div>
        </SectionCard>
      )}

      {!loading && order && (
        <>
          {/* Order Details Summary Box */}
          <div className="order-summary-box">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                <Package size={22} color="var(--primary)" />
                <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700 }}>Đơn hàng #{order.orderCode}</h3>
              </div>
              <Badge 
                tone={
                  order.status === 'ESCROWED' ? 'success' : 
                  order.status === 'PENDING_ESCROW' ? 'warning' : 'neutral'
                }
                dot
              >
                {order.status}
              </Badge>
            </div>

            <div className="order-detail-grid">
              <div className="order-detail-item">
                <span className="order-detail-label">Sản phẩm</span>
                <span className="order-detail-value">{order.productName} (x{order.quantity})</span>
              </div>
              <div className="order-detail-item">
                <span className="order-detail-label">Trọng lượng</span>
                <span className="order-detail-value">{order.weightKg} kg</span>
              </div>
              <div className="order-detail-item">
                <span className="order-detail-label">Người nhận</span>
                <span className="order-detail-value">{order.receiverName} - {order.receiverPhone}</span>
              </div>
              <div className="order-detail-item">
                <span className="order-detail-label">Địa chỉ giao</span>
                <span className="order-detail-value" style={{ fontSize: '0.85rem' }}>{order.deliveryAddress}</span>
              </div>
            </div>

            <div className="order-total-highlight">
              <span className="order-total-label">Số tiền cần ký quỹ bảo đảm (Escrow):</span>
              <span className="order-total-amount">
                {order.declaredValue ? order.declaredValue.toLocaleString('vi-VN') : '0'} đ
              </span>
            </div>
          </div>

          {isAlreadyEscrowed ? (
            <SectionCard title="Trạng thái ký quỹ">
              <Alert tone="success">
                <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
                  <Truck size={28} color="var(--accent-emerald)" />
                  <div>
                    <h4 style={{ margin: 0, fontSize: '1.05rem' }}>Đơn hàng đã được Ký quỹ (ESCROWED)</h4>
                    <p style={{ margin: '4px 0 0 0', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                      Khoản tiền ký quỹ đã được hệ thống ghi nhận vào tài khoản ký quỹ trung gian an toàn. Hệ thống đang tiến hành điều phối tài xế tiếp nhận đơn hàng.
                    </p>
                  </div>
                </div>
              </Alert>
              <div className="ui-form-actions" style={{ marginTop: 'var(--space-4)' }}>
                <Link to="/wallet" className="ui-button ui-button-secondary">Kiểm tra Ví tiền</Link>
                <Link to="/" className="ui-button ui-button-primary">Về trang chủ</Link>
              </div>
            </SectionCard>
          ) : (
            <SectionCard 
              title="Chọn phương thức thanh toán ký quỹ" 
              description="Khoản tiền sẽ được giữ an toàn tại tài khoản Escrow cho đến khi hàng giao thành công."
            >
              {/* Payment Methods Selection */}
              <div className="payment-methods-grid">
                {/* Method 1: Internal Wallet */}
                <div 
                  className={`payment-method-card ${paymentMethod === 'WALLET' ? 'selected' : ''}`}
                  onClick={() => setPaymentMethod('WALLET')}
                >
                  <div className="payment-method-header">
                    <div className="payment-method-icon"><WalletIcon size={22} /></div>
                    <div>
                      <div className="payment-method-title">Ví FPTPost</div>
                      <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Thanh toán tức thì bằng số dư ví</div>
                    </div>
                  </div>
                  <div className="payment-method-desc">
                    Số dư khả dụng: <strong style={{ color: isWalletBalanceEnough ? 'var(--accent-emerald)' : 'var(--accent-rose)' }}>
                      {availableBalance.toLocaleString('vi-VN')} đ
                    </strong>
                  </div>

                  {!isWalletBalanceEnough && (
                    <div style={{ marginTop: 'var(--space-3)' }}>
                      <Alert tone="danger">
                        <div style={{ fontSize: '0.8rem' }}>
                          Số dư ví không đủ (cần thêm {(requiredAmount - availableBalance).toLocaleString('vi-VN')} đ).
                        </div>
                      </Alert>
                      <Link 
                        to="/wallet" 
                        className="ui-button ui-button-secondary ui-button-sm"
                        style={{ marginTop: 'var(--space-2)', width: '100%', justifyContent: 'center' }}
                      >
                        <PlusCircle size={14} /> Nạp thêm tiền vào ví ngay
                      </Link>
                    </div>
                  )}
                </div>

                {/* Method 2: VNPay */}
                <div 
                  className={`payment-method-card ${paymentMethod === 'VNPAY' ? 'selected' : ''}`}
                  onClick={() => setPaymentMethod('VNPAY')}
                >
                  <div className="payment-method-header">
                    <div className="payment-method-icon"><CreditCard size={22} /></div>
                    <div>
                      <div className="payment-method-title">Cổng thanh toán VNPay</div>
                      <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>ATM, QR Code, Thẻ quốc tế</div>
                    </div>
                  </div>
                  <div className="payment-method-desc">
                    Ký quỹ trực tiếp qua VNPay mà không cần có sẵn số dư trong ví.
                  </div>
                </div>
              </div>

              {/* Bank selector when VNPay is chosen */}
              {paymentMethod === 'VNPAY' && (
                <div style={{ marginBottom: 'var(--space-4)' }}>
                  <label className="ui-label" style={{ marginBottom: 'var(--space-1)' }}>Chọn ngân hàng / kênh VNPay:</label>
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
              )}

              {/* Security info */}
              <div style={{ 
                background: 'rgba(255, 255, 255, 0.02)', 
                border: '1px solid var(--border-subtle)', 
                borderRadius: 'var(--radius-md)', 
                padding: 'var(--space-3) var(--space-4)',
                marginBottom: 'var(--space-4)',
                display: 'flex',
                alignItems: 'center',
                gap: 'var(--space-2)'
              }}>
                <ShieldCheck size={18} color="var(--primary)" />
                <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                  Bảo đảm ký quỹ: Tiền của bạn được hệ thống giữ an toàn và chỉ giải ngân khi đơn hàng được giao thành công và xác nhận.
                </span>
              </div>

              {/* Submit Action */}
              <div className="ui-form-actions">
                <Button
                  variant="primary"
                  size="md"
                  loading={submitting}
                  loadingText="Đang xử lý ký quỹ…"
                  disabled={
                    submitting || 
                    (paymentMethod === 'WALLET' && !isWalletBalanceEnough) ||
                    !isSender
                  }
                  onClick={handlePay}
                  icon={paymentMethod === 'VNPAY' ? <ExternalLink size={18} /> : <ShieldCheck size={18} />}
                >
                  {paymentMethod === 'WALLET'
                    ? `Xác nhận Ký quỹ ${requiredAmount.toLocaleString('vi-VN')} đ từ Ví`
                    : `Thanh toán ${requiredAmount.toLocaleString('vi-VN')} đ qua VNPay`}
                </Button>
              </div>
            </SectionCard>
          )}
        </>
      )}
    </div>
  );
}
