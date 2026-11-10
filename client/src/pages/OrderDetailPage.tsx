import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  MapPin,
  User,
  Phone,
  Package,
  Calendar,
  Truck,
  DollarSign,
  Image as ImageIcon,
  Pencil,
  XCircle,
} from 'lucide-react';
import type { Order, OrderLeg } from '../types/order';
import { ORDER_STATUS_MAP } from '../types/order';
import { orderApi } from '../api/orderApi';
import type { ApiError } from '../api/ApiError';
import { getApiError } from '../api/ApiError';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import Modal from '../components/ui/Modal';
import Alert from '../components/ui/Alert';
import Input from '../components/ui/Input';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ApiErrorNotice from '../components/ApiErrorNotice';
import OrderModerationNotice from '../features/order-moderation/components/OrderModerationNotice';
import type { EscrowResponse, RefundToSourceResponse } from '../types/payment';
import { getSessionState } from '../auth/authSession';
import ReportViolationButton from '../features/violation-reports/components/ReportViolationButton';
import { ShieldCheck, CheckCircle, AlertTriangle, Send, GitFork, CreditCard } from 'lucide-react';
import InviteCourierModal from '../features/order/components/InviteCourierModal';
import SuggestedCouriersCard from '../features/matching/components/SuggestedCouriersCard';

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [order, setOrder] = useState<Order | null>(null);
  const [escrow, setEscrow] = useState<EscrowResponse | null>(null);
  const [refundRequests, setRefundRequests] = useState<RefundToSourceResponse[]>([]);
  const [refundingSource, setRefundingSource] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [releasing, setReleasing] = useState(false);
  const [releaseSuccess, setReleaseSuccess] = useState<string | null>(null);
  const [releaseError, setReleaseError] = useState<string | null>(null);
  const [legs, setLegs] = useState<OrderLeg[]>([]);
  const [loadingLegs, setLoadingLegs] = useState(false);

  const currentUser = getSessionState().user;
  const isAdmin = currentUser?.roles?.includes('ADMIN') ?? false;

  const fetchEscrow = (orderId: string | number) => {
    orderApi
      .getEscrow(orderId)
      .then(res => setEscrow(res.data))
      .catch(() => setEscrow(null));
    orderApi
      .getRefundRequests(orderId)
      .then(res => setRefundRequests(res.data || []))
      .catch(() => setRefundRequests([]));
  };

  // Trạng thái modal hủy đơn, giải ngân & hoàn tiền
  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);
  const [isReleaseModalOpen, setIsReleaseModalOpen] = useState(false);
  const [isRefundSourceModalOpen, setIsRefundSourceModalOpen] = useState(false);
  const [isInviteModalOpen, setIsInviteModalOpen] = useState(false);
  const [invitedCourierId, setInvitedCourierId] = useState<number | null>(null);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<ApiError | null>(null);

  useEffect(() => {
    if (!id) return;

    let mounted = true;
    setLoading(true);
    setError(null);

    orderApi
      .getById(id)
      .then(res => {
        if (mounted) {
          setOrder(res.data);
          fetchEscrow(res.data.id);
          if (res.data.isRelay) {
            setLoadingLegs(true);
            orderApi.getOrderLegs(res.data.id)
              .then(legsRes => { if (mounted) setLegs(legsRes.data); })
              .catch(e => console.error("Lỗi tải chặng tiếp sức:", e))
              .finally(() => { if (mounted) setLoadingLegs(false); });
          }
        }
      })
      .catch(cause => {
        if (mounted) setError(getApiError(cause));
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [id]);

  const handleReleaseEscrow = async () => {
    if (!order) return;
    setReleasing(true);
    setReleaseSuccess(null);
    setReleaseError(null);
    try {
      const res = await orderApi.releaseEscrow(order.id);
      setReleaseSuccess(res.data.message || 'Giải ngân tiền ký quỹ thành công!');
      setIsReleaseModalOpen(false);
      fetchEscrow(order.id);
    } catch (err) {
      const apiErr = getApiError(err);
      setReleaseError(apiErr.message);
      setIsReleaseModalOpen(false);
    } finally {
      setReleasing(false);
    }
  };

  async function handleConfirmCancel() {
    if (!order) return;
    setCancelling(true);
    setCancelError(null);
    try {
      const res = await orderApi.cancel(order.id, cancelReason.trim() || undefined);
      setOrder(res.data);
      fetchEscrow(order.id);
      setIsCancelModalOpen(false);
      setCancelReason('');
    } catch (cause) {
      setCancelError(getApiError(cause));
    } finally {
      setCancelling(false);
    }
  }

  async function handleRefundToSource() {
    if (!order) return;
    setRefundingSource(true);
    setReleaseSuccess(null);
    setReleaseError(null);
    try {
      const res = await orderApi.refundToSource(order.id, {
        reason: `Người gửi yêu cầu hoàn tiền đơn ${order.orderCode} về nguồn VNPay`,
      });
      setReleaseSuccess(res.data.message || 'Hoàn tiền về nguồn VNPay thành công!');
      setIsRefundSourceModalOpen(false);
      fetchEscrow(order.id);
    } catch (err) {
      const apiErr = getApiError(err);
      setReleaseError(apiErr.message);
      setIsRefundSourceModalOpen(false);
    } finally {
      setRefundingSource(false);
    }
  }

  if (loading) {
    return <LoadingSpinner text="Đang tải thông tin chi tiết đơn hàng…" />;
  }

  if (error || !order) {
    return (
      <div className="ui-stack">
        <PageHeader
          title="Không tìm thấy đơn hàng"
          eyebrow="Chi tiết đơn hàng"
          actions={
            <Button
              variant="secondary"
              icon={<ArrowLeft size={16} />}
              onClick={() => navigate('/sender/orders')}
            >
              Quay lại danh sách
            </Button>
          }
        />
        <SectionCard>
          <div className="ui-stack">
            {error ? (
              <ApiErrorNotice error={error} />
            ) : (
              <p>Đơn hàng không tồn tại hoặc đã bị gỡ bỏ khỏi hệ thống.</p>
            )}
            <div>
              <Button variant="secondary" onClick={() => navigate('/sender/orders')}>
                Về trang danh sách đơn
              </Button>
            </div>
          </div>
        </SectionCard>
      </div>
    );
  }

  const statusConfig = ORDER_STATUS_MAP[order.status] ?? {
    label: order.status,
    tone: 'neutral',
  };

  const canPayEscrow =
    !escrow &&
    (order.status === 'PENDING' ||
      order.status === 'PENDING_ESCROW' ||
      order.status === 'FINDING_SHIPPER' ||
      order.status === 'AWAITING_PAYMENT');

  const canEdit = order.status === 'PENDING' || (order.status === 'FINDING_SHIPPER' && !order.courierId);
  const canCancel =
    order.status === 'PENDING' ||
    order.status === 'PENDING_ESCROW' ||
    order.status === 'ESCROWED' ||
    order.status === 'FINDING_SHIPPER' ||
    order.status === 'AWAITING_PAYMENT' ||
    order.status === 'SHIPPER_ASSIGNED';

  return (
    <div className="ui-stack">
      <PageHeader
        title={`Vận đơn: ${order.orderCode}`}
        eyebrow={`Chi tiết đơn hàng #${order.id}`}
        description={`Đơn được tiếp nhận trên hệ thống lúc ${new Date(order.createdAt).toLocaleString('vi-VN')}`}
        actions={
          <div className="ui-row" style={{ gap: '12px', alignItems: 'center' }}>
            <Badge dot tone={statusConfig.tone}>
              {statusConfig.label}
            </Badge>
            {order.isRelay && (
              <Badge tone="info">
                TIẾP SỨC
              </Badge>
            )}
            {canPayEscrow && !isAdmin && (
              <Button
                variant="primary"
                size="md"
                icon={<CreditCard size={15} />}
                onClick={() => navigate(`/orders/${order.id}/payment`)}
              >
                Thanh toán &amp; Ký quỹ
              </Button>
            )}
            {canEdit && (
              <Button
                variant="secondary"
                size="md"
                icon={<Pencil size={15} />}
                onClick={() => navigate(`/sender/orders/${order.id}/edit`)}
              >
                Chỉnh sửa đơn
              </Button>
            )}
            {canCancel && (
              <Button
                variant="secondary"
                size="md"
                icon={<XCircle size={15} style={{ color: 'var(--accent-rose, #ffa6b3)' }} />}
                onClick={() => {
                  setCancelError(null);
                  setCancelReason('');
                  setIsCancelModalOpen(true);
                }}
              >
                Hủy đơn hàng
              </Button>
            )}
            {order.courierId && (
              <ReportViolationButton targetType="ORDER" targetId={String(order.id)}
                targetName={`Đơn ${order.orderCode}: báo cáo hành vi của người giao`} />
            )}
            <Button
              variant="ghost"
              icon={<ArrowLeft size={16} />}
              onClick={() => navigate('/sender/orders')}
            >
              Quay lại danh sách
            </Button>
          </div>
        }
      />

      <OrderModerationNotice orderId={order.id} status={order.status} />

      <div className="ui-showcase-grid">
        {/* Left Column: Shipment & Product Details */}
        <div className="ui-stack">
          {/* Thông tin vận chuyển */}
          <SectionCard
            title="Thông tin vận chuyển & Tuyến đường"
            description="Lộ trình giao nhận từ người gửi tới người nhận"
          >
            <div className="ui-summary-list">
              <div>
                <dt className="ui-row" style={{ gap: '6px' }}>
                  <MapPin size={16} style={{ color: 'var(--primary)' }} />
                  <span>Điểm lấy hàng:</span>
                </dt>
                <dd>{order.pickupAddress}</dd>
              </div>

              <div>
                <dt className="ui-row" style={{ gap: '6px' }}>
                  <MapPin size={16} style={{ color: 'var(--accent-emerald)' }} />
                  <span>Điểm giao hàng:</span>
                </dt>
                <dd>{order.deliveryAddress}</dd>
              </div>

              <div>
                <dt className="ui-row" style={{ gap: '6px' }}>
                  <User size={16} />
                  <span>Người nhận:</span>
                </dt>
                <dd>
                  <strong>{order.receiverName}</strong>
                </dd>
              </div>

              <div>
                <dt className="ui-row" style={{ gap: '6px' }}>
                  <Phone size={16} />
                  <span>Số điện thoại người nhận:</span>
                </dt>
                <dd>{order.receiverPhone}</dd>
              </div>
            </div>
          </SectionCard>

          {/* Hành trình tiếp sức (Relay Delivery - BR-F-062) */}
          {order.isRelay && (
            <SectionCard
              title="Hành trình giao tiếp sức (Relay Delivery)"
              description="Hành trình được chia thành các chặng giao tiếp sức độc lập qua điểm hẹn an toàn"
            >
              <div className="ui-stack" style={{ gap: '14px' }}>
                <div
                  style={{
                    padding: '8px 12px',
                    borderRadius: 'var(--radius-sm, 6px)',
                    background: 'var(--brand-soft, rgba(243, 112, 33, 0.12))',
                    border: '1px solid var(--brand-border, rgba(243, 112, 33, 0.3))',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    color: 'var(--text-accent, #ffb17d)',
                    fontSize: '0.85rem',
                  }}
                >
                  <GitFork size={16} style={{ color: 'var(--primary, #f37021)', flexShrink: 0 }} />
                  <span>
                    Đơn hàng chia thành <strong>{legs.length > 0 ? legs.length : 2} chặng</strong>. Cước phí được chia theo tỷ lệ quãng đường (BR-R-047).
                  </span>
                </div>

                {loadingLegs ? (
                  <div style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--text-muted)' }}>
                    <LoadingSpinner />
                    <span style={{ marginLeft: '8px' }}>Đang tải tiến trình từng chặng…</span>
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {legs.map(leg => {
                      const legStatus = ORDER_STATUS_MAP[leg.status] || {
                        label: leg.status,
                        tone: 'neutral' as const,
                      };
                      return (
                        <div
                          key={leg.id || leg.legNo}
                          style={{
                            padding: '12px 14px',
                            borderRadius: 'var(--radius-md, 10px)',
                            background: 'var(--bg-tertiary, #242d39)',
                            border: '1px solid var(--border-subtle, #333d4c)',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '8px',
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                              <span
                                style={{
                                  background: 'var(--primary, #f37021)',
                                  color: '#fff',
                                  fontSize: '0.75rem',
                                  fontWeight: 700,
                                  padding: '2px 8px',
                                  borderRadius: 'var(--radius-full, 999px)',
                                }}
                              >
                                Chặng {leg.legNo}
                              </span>
                              <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                                {leg.distanceKm} km
                              </span>
                            </div>
                            <Badge dot tone={legStatus.tone}>
                              {legStatus.label}
                            </Badge>
                          </div>

                          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '0.85rem' }}>
                            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '6px' }}>
                              <MapPin size={14} style={{ color: 'var(--primary)', marginTop: '2px', flexShrink: 0 }} />
                              <span style={{ color: 'var(--text-secondary)' }}>
                                <strong style={{ color: 'var(--text-primary)' }}>Điểm đi:</strong> {leg.pickupAddress}
                              </span>
                            </div>
                            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '6px' }}>
                              <MapPin size={14} style={{ color: 'var(--accent-emerald)', marginTop: '2px', flexShrink: 0 }} />
                              <span style={{ color: 'var(--text-secondary)' }}>
                                <strong style={{ color: 'var(--text-primary)' }}>Điểm đến:</strong> {leg.deliveryAddress}
                              </span>
                            </div>
                          </div>

                          <div
                            style={{
                              marginTop: '4px',
                              paddingTop: '6px',
                              borderTop: '1px dashed var(--border-subtle, #333d4c)',
                              display: 'flex',
                              justifyContent: 'space-between',
                              alignItems: 'center',
                              fontSize: '0.8rem',
                            }}
                          >
                            <span style={{ color: 'var(--text-muted)' }}>
                              Bưu tá chặng: {leg.courierId ? '#' + leg.courierId : 'Chưa có tài xế nhận'}
                            </span>
                            <span style={{ fontWeight: 600, color: 'var(--accent-emerald)' }}>
                              Cước chặng: {Number(leg.legFee).toLocaleString('vi-VN')} đ
                            </span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </SectionCard>
          )}

          {/* Gợi ý tài xế có chuyến đi tiện đường */}
          {order.status === 'FINDING_SHIPPER' && (
            <SuggestedCouriersCard
              orderId={order.id}
              isOrderOpen={true}
              onInviteCourier={(courierId) => {
                setInvitedCourierId(courierId);
                setIsInviteModalOpen(true);
              }}
            />
          )}

          {/* Chi tiết kiện hàng */}
          <SectionCard
            title="Chi tiết kiện hàng"
            description="Thông số vật lý và giá trị của bưu kiện"
          >
            <div className="ui-summary-list">
              <div>
                <dt className="ui-row" style={{ gap: '6px' }}>
                  <Package size={16} />
                  <span>Tên hàng hóa:</span>
                </dt>
                <dd><strong>{order.productName}</strong></dd>
              </div>

              <div>
                <dt>Số lượng:</dt>
                <dd>{order.quantity} kiện</dd>
              </div>

              <div>
                <dt>Khối lượng:</dt>
                <dd>{order.weightKg} kg</dd>
              </div>

              <div>
                <dt>Giá trị khai báo:</dt>
                <dd>
                  {order.declaredValue !== null && order.declaredValue !== undefined
                    ? `${Number(order.declaredValue).toLocaleString('vi-VN')} VNĐ`
                    : 'Không khai báo'}
                </dd>
              </div>

              <div>
                <dt>Mô tả hàng hóa:</dt>
                <dd>{order.productDescription || 'Không có mô tả chi tiết'}</dd>
              </div>

              <div>
                <dt>Ghi chú giao hàng:</dt>
                <dd>{order.deliveryNote || 'Không có ghi chú'}</dd>
              </div>
            </div>
          </SectionCard>

          {/* Hình ảnh đính kèm */}
          {order.imageUrls && order.imageUrls.length > 0 && (
            <SectionCard
              title="Hình ảnh kiện hàng"
              description={`${order.imageUrls.length} ảnh đính kèm`}
            >
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                  gap: '16px',
                }}
              >
                {order.imageUrls.map((url, idx) => (
                  <a
                    key={idx}
                    href={url}
                    target="_blank"
                    rel="noreferrer"
                    style={{
                      display: 'block',
                      borderRadius: 'var(--radius-md)',
                      overflow: 'hidden',
                      border: '1px solid var(--border-subtle)',
                      backgroundColor: 'var(--bg-tertiary)',
                      aspectRatio: '1 / 1',
                    }}
                  >
                    <img
                      src={url}
                      alt={`Kiện hàng ${idx + 1}`}
                      style={{
                        width: '100%',
                        height: '100%',
                        objectFit: 'cover',
                      }}
                      onError={e => {
                        (e.target as HTMLImageElement).style.display = 'none';
                      }}
                    />
                  </a>
                ))}
              </div>
            </SectionCard>
          )}
        </div>

        {/* Right Column: Order Summary & Meta */}
        <div className="ui-stack" style={{ gap: '20px' }}>
          {/* Card Quản lý chào giá */}
          {(order.status === 'PENDING' || order.status === 'FINDING_SHIPPER') && (
            <SectionCard title="Chào giá từ bưu tá">
              <div className="ui-stack" style={{ gap: '12px' }}>
                <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', margin: 0 }}>
                  Đơn hàng đang mở trên sàn. Bưu tá có thể gửi chào giá hoặc nhận đơn theo giá niêm yết.
                </p>
                <div className="ui-stack" style={{ gap: '8px' }}>
                  <Button
                    variant="primary"
                    icon={<Send size={16} />}
                    onClick={() => setIsInviteModalOpen(true)}
                    style={{ width: '100%' }}
                  >
                    Mời tài xế trực tiếp
                  </Button>
                  <Button
                    variant="secondary"
                    icon={<DollarSign size={16} />}
                    onClick={() => navigate(`/sender/orders/${order.id}/offers`)}
                    style={{ width: '100%' }}
                  >
                    Xem danh sách chào giá
                  </Button>
                </div>
              </div>
            </SectionCard>
          )}

          <SectionCard title="Trạng thái đơn hàng">
            <div className="ui-stack" style={{ gap: '16px' }}>
              <div className="ui-row" style={{ justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>Tình trạng:</span>
                <Badge dot tone={statusConfig.tone}>
                  {statusConfig.label}
                </Badge>
              </div>

              <div className="ui-summary-list">
                <div>
                  <dt className="ui-row" style={{ gap: '6px' }}>
                    <Truck size={14} />
                    <span>Mã vận đơn:</span>
                  </dt>
                  <dd className="font-mono">{order.orderCode}</dd>
                </div>

                <div>
                  <dt className="ui-row" style={{ gap: '6px' }}>
                    <Calendar size={14} />
                    <span>Thời gian tạo:</span>
                  </dt>
                  <dd>
                    {order.createdAt ? new Date(order.createdAt).toLocaleString('vi-VN') : '—'}
                  </dd>
                </div>

                {order.updatedAt && (
                  <div>
                    <dt className="ui-row" style={{ gap: '6px' }}>
                      <Calendar size={14} />
                      <span>Cập nhật gần nhất:</span>
                    </dt>
                    <dd>{new Date(order.updatedAt).toLocaleString('vi-VN')}</dd>
                  </div>
                )}

                <div>
                  <dt className="ui-row" style={{ gap: '6px' }}>
                    <ImageIcon size={14} />
                    <span>Ảnh đính kèm:</span>
                  </dt>
                  <dd>{order.imageUrls?.length || 0} ảnh</dd>
                </div>
              </div>
            </div>
          </SectionCard>

          {/* Thanh toán & Ký quỹ nếu đơn chưa ký quỹ (UC-35) */}
          {!escrow && canPayEscrow && (
            <SectionCard
              title="Thanh toán & Ký quỹ (Escrow)"
              description="Đơn hàng chưa được thanh toán ký quỹ — số dư ví của bạn chưa bị trừ hay tạm giữ."
              actions={<Badge dot tone="warning">CHƯA KÝ QUỸ</Badge>}
            >
              <div className="ui-stack" style={{ gap: '12px' }}>
                <Alert tone="info">
                  Hãy chọn phương thức thanh toán qua <strong>Ví FPTPost</strong> hoặc <strong>Cổng VNPay</strong> để ký quỹ bảo đảm an toàn cho đơn hàng này.
                </Alert>
                {!isAdmin && (
                  <Button
                    variant="primary"
                    icon={<CreditCard size={16} />}
                    onClick={() => navigate(`/orders/${order.id}/payment`)}
                    style={{ width: '100%' }}
                  >
                    Chọn phương thức &amp; Thanh toán ký quỹ
                  </Button>
                )}
              </div>
            </SectionCard>
          )}

          {/* Thông tin Ký quỹ & Giải ngân */}
          {escrow && (
            <SectionCard
              title="Ký quỹ an toàn (Escrow)"
              description="Thông tin bảo đảm tài chính và giải ngân bưu tá"
            >
              <div className="ui-stack" style={{ gap: '16px' }}>
                <div className="ui-row" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>Trạng thái ký quỹ:</span>
                  <Badge
                    dot
                    tone={
                      escrow.status === 'RELEASED'
                        ? 'success'
                        : escrow.status === 'HELD'
                        ? 'warning'
                        : 'danger'
                    }
                  >
                    {escrow.status === 'RELEASED'
                      ? 'ĐÃ GIẢI NGÂN'
                      : escrow.status === 'HELD'
                      ? 'ĐANG GIAM GIỮ'
                      : 'ĐÃ HOÀN TIỀN'}
                  </Badge>
                </div>

                {escrow.frozen && (
                  <div
                    style={{
                      padding: '8px 12px',
                      backgroundColor: 'rgba(239, 68, 68, 0.1)',
                      border: '1px solid rgba(239, 68, 68, 0.3)',
                      borderRadius: 'var(--radius-sm)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      color: 'var(--accent-ruby)',
                      fontSize: '0.85rem',
                    }}
                  >
                    <AlertTriangle size={16} />
                    <span>Khoản ký quỹ đang bị phong tỏa do tranh chấp/khiếu nại.</span>
                  </div>
                )}

                {releaseSuccess && (
                  <div
                    style={{
                      padding: '8px 12px',
                      backgroundColor: 'rgba(16, 185, 129, 0.1)',
                      border: '1px solid rgba(16, 185, 129, 0.3)',
                      borderRadius: 'var(--radius-sm)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      color: 'var(--accent-emerald)',
                      fontSize: '0.85rem',
                    }}
                  >
                    <CheckCircle size={16} />
                    <span>{releaseSuccess}</span>
                  </div>
                )}

                {releaseError && (
                  <div
                    style={{
                      padding: '8px 12px',
                      backgroundColor: 'rgba(239, 68, 68, 0.1)',
                      border: '1px solid rgba(239, 68, 68, 0.3)',
                      borderRadius: 'var(--radius-sm)',
                      color: 'var(--accent-ruby)',
                      fontSize: '0.85rem',
                    }}
                  >
                    {releaseError}
                  </div>
                )}

                <div className="ui-summary-list">
                  <div>
                    <dt className="ui-row" style={{ gap: '6px' }}>
                      <DollarSign size={14} />
                      <span>Số tiền ký quỹ:</span>
                    </dt>
                    <dd style={{ fontWeight: 600, color: 'var(--primary)' }}>
                      {Number(escrow.amount).toLocaleString('vi-VN')} VNĐ
                    </dd>
                  </div>

                  <div>
                    <dt>Phương thức ký quỹ:</dt>
                    <dd>
                      {escrow.paymentMethod === 'WALLET' ? 'Ví FPTPost' : 'Cổng VNPay'}
                    </dd>
                  </div>

                  {escrow.courierId && (
                    <div>
                      <dt>Bưu tá thụ hưởng:</dt>
                      <dd>#{escrow.courierId}</dd>
                    </div>
                  )}

                  {escrow.releaseAt && (
                    <div>
                      <dt>Thời gian giải ngân:</dt>
                      <dd>{new Date(escrow.releaseAt).toLocaleString('vi-VN')}</dd>
                    </div>
                  )}
                </div>

                {/* Thông tin & Nút yêu cầu hoàn tiền về nguồn VNPay (UC-37) */}
                {refundRequests.length > 0 && (
                  <div
                    style={{
                      padding: '10px 12px',
                      backgroundColor: 'rgba(59, 130, 246, 0.08)',
                      border: '1px solid rgba(59, 130, 246, 0.25)',
                      borderRadius: 'var(--radius-sm)',
                      fontSize: '0.85rem',
                    }}
                  >
                    <div style={{ fontWeight: 600, marginBottom: 4 }}>
                      Hoàn tiền về nguồn VNPay ({refundRequests[0].kind === 'PARTIAL' ? 'Một phần' : 'Toàn bộ'}):
                    </div>
                    <div>
                      Số tiền hoàn: <strong>{Number(refundRequests[0].amount).toLocaleString('vi-VN')} VNĐ</strong>
                      {refundRequests[0].cancellationFee > 0 && (
                        <span> (Phí hủy trả bưu tá: {Number(refundRequests[0].cancellationFee).toLocaleString('vi-VN')} VNĐ)</span>
                      )}
                    </div>
                    <div>
                      Trạng thái:{' '}
                      <strong>
                        {refundRequests[0].status === 'SUCCESS'
                          ? `Thành công (Mã: ${refundRequests[0].vnpayRefundRef})`
                          : refundRequests[0].status === 'FAILED'
                          ? 'VNPay từ chối — Tiền được giữ tại ví FPTPost'
                          : 'Đang xử lý'}
                      </strong>
                    </div>
                  </div>
                )}

                {escrow.paymentMethod === 'VNPAY' &&
                  (escrow.status === 'REFUNDED' ||
                    escrow.status === 'PARTIALLY_REFUNDED' ||
                    order.status === 'CANCELLED' ||
                    order.status === 'EXPIRED') &&
                  !refundRequests.some(r => r.status === 'SUCCESS' || r.status === 'PENDING') && (
                    <div style={{ marginTop: '8px' }}>
                      <Button
                        variant="secondary"
                        onClick={() => setIsRefundSourceModalOpen(true)}
                        disabled={refundingSource}
                        style={{ width: '100%' }}
                      >
                        {refundingSource ? 'Đang gửi yêu cầu hoàn VNPay…' : 'Hoàn tiền về nguồn VNPay (UC-37)'}
                      </Button>
                    </div>
                  )}

                {/* Nút hành động giải ngân dành cho ADMIN */}
                {isAdmin && escrow.status === 'HELD' && !escrow.frozen && (
                  <div style={{ marginTop: '8px' }}>
                    <Button
                      variant="primary"
                      icon={<ShieldCheck size={16} />}
                      onClick={() => setIsReleaseModalOpen(true)}
                      disabled={releasing || !order.courierId || order.status !== 'COMPLETED'}
                      style={{ width: '100%' }}
                    >
                      {releasing ? 'Đang giải ngân…' : 'Giải ngân cho người giao'}
                    </Button>
                    {!order.courierId ? (
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px', textAlign: 'center' }}>
                        * Cần chỉ định người giao trước khi thực hiện giải ngân
                      </p>
                    ) : order.status !== 'COMPLETED' ? (
                      <p style={{ fontSize: '0.75rem', color: 'var(--accent-amber)', marginTop: '6px', textAlign: 'center' }}>
                        * Chỉ giải ngân được sau khi đơn hàng đã hoàn tất giao hàng (COMPLETED) theo quy định BR-R-054.
                      </p>
                    ) : null}
                  </div>
                )}
              </div>
            </SectionCard>
          )}
        </div>
      </div>

      {/* Modal xác nhận hủy đơn hàng */}
      <Modal
        isOpen={isCancelModalOpen}
        onClose={() => !cancelling && setIsCancelModalOpen(false)}
        title="Xác nhận hủy đơn hàng"
        description={`Bạn có chắc chắn muốn hủy đơn hàng ${order.orderCode}?`}
        closeDisabled={cancelling}
      >
        <div className="ui-stack" style={{ gap: '16px' }}>
          {cancelError && <ApiErrorNotice error={cancelError} />}

          <Alert tone="warning" title="Lưu ý khi hủy đơn">
            Đơn hàng sau khi hủy sẽ chuyển sang trạng thái <strong>Đã hủy (CANCELLED)</strong> và không thể tiếp tục thực hiện giao dịch hay chỉnh sửa.
          </Alert>

          <Input
            label="Lý do hủy đơn (Tùy chọn)"
            placeholder="Ví dụ: Đổi ý không gửi nữa, sai thông tin hàng, tìm được phương án khác..."
            value={cancelReason}
            onChange={e => setCancelReason(e.target.value)}
            disabled={cancelling}
            maxLength={255}
          />

          <div className="ui-row" style={{ justifyContent: 'flex-end', gap: '8px', marginTop: '8px' }}>
            <Button
              variant="secondary"
              onClick={() => setIsCancelModalOpen(false)}
              disabled={cancelling}
            >
              Đóng
            </Button>
            <Button
              variant="primary"
              style={{ background: 'var(--accent-rose, #ffa6b3)', color: '#251207', borderColor: 'transparent' }}
              onClick={() => void handleConfirmCancel()}
              loading={cancelling}
              loadingText="Đang xử lý hủy đơn…"
            >
              Xác nhận hủy
            </Button>
          </div>
        </div>
      </Modal>

      {/* Modal xác nhận giải ngân cho người giao (UC-36) */}
      <Modal
        isOpen={isReleaseModalOpen}
        onClose={() => !releasing && setIsReleaseModalOpen(false)}
        closeDisabled={releasing}
        title="Xác nhận giải ngân tiền ký quỹ"
        description={`Giải ngân khoản tiền ký quỹ của đơn hàng ${order.orderCode} vào ví người giao?`}
        size="md"
      >
        <div className="ui-stack" style={{ gap: '12px' }}>
          <Alert tone="info">
            Số tiền ký quỹ sẽ được chuyển từ trạng thái giam giữ sang số dư khả dụng của người giao hàng.
          </Alert>
          <div className="ui-row" style={{ justifyContent: 'flex-end', gap: '8px' }}>
            <Button variant="secondary" disabled={releasing} onClick={() => setIsReleaseModalOpen(false)}>
              Đóng
            </Button>
            <Button
              variant="primary"
              loading={releasing}
              loadingText="Đang giải ngân…"
              onClick={() => void handleReleaseEscrow()}
            >
              Xác nhận giải ngân
            </Button>
          </div>
        </div>
      </Modal>

      {/* Modal xác nhận hoàn tiền về nguồn VNPay (UC-37) */}
      <Modal
        isOpen={isRefundSourceModalOpen}
        onClose={() => !refundingSource && setIsRefundSourceModalOpen(false)}
        closeDisabled={refundingSource}
        title="Xác nhận hoàn tiền về nguồn VNPay"
        description={`Yêu cầu hoàn khoản tiền của đơn hàng ${order.orderCode} từ ví FPTPost về cổng thanh toán VNPay gốc?`}
        size="md"
      >
        <div className="ui-stack" style={{ gap: '12px' }}>
          <Alert tone="warning">
            Sau khi hoàn về nguồn VNPay thành công, số tiền tương ứng sẽ được khấu trừ khỏi số dư ví FPTPost của bạn.
          </Alert>
          <div className="ui-row" style={{ justifyContent: 'flex-end', gap: '8px' }}>
            <Button variant="secondary" disabled={refundingSource} onClick={() => setIsRefundSourceModalOpen(false)}>
              Đóng
            </Button>
            <Button
              variant="primary"
              loading={refundingSource}
              loadingText="Đang gửi yêu cầu…"
              onClick={() => void handleRefundToSource()}
            >
              Xác nhận hoàn VNPay
            </Button>
          </div>
        </div>
      </Modal>

      {/* Modal mời tài xế giao hàng trực tiếp */}
      {id && order && (
        <InviteCourierModal
          isOpen={isInviteModalOpen}
          onClose={() => {
            setIsInviteModalOpen(false);
            setInvitedCourierId(null);
          }}
          orderId={id}
          orderCode={order.orderCode}
          onSuccess={() => {
            orderApi.getById(id).then(res => setOrder(res.data));
          }}
          initialCourierId={invitedCourierId}
        />
      )}
    </div>
  );
}
