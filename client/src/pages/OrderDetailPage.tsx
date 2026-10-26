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
import type { Order } from '../types/order';
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
import type { EscrowResponse } from '../types/payment';
import { getSessionState } from '../auth/authSession';
import { ShieldCheck, CheckCircle, AlertTriangle } from 'lucide-react';

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [order, setOrder] = useState<Order | null>(null);
  const [escrow, setEscrow] = useState<EscrowResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [releasing, setReleasing] = useState(false);
  const [releaseSuccess, setReleaseSuccess] = useState<string | null>(null);
  const [releaseError, setReleaseError] = useState<string | null>(null);

  const currentUser = getSessionState().user;
  const isAdmin = currentUser?.roles?.includes('ADMIN') ?? false;

  const fetchEscrow = (orderId: string | number) => {
    orderApi
      .getEscrow(orderId)
      .then(res => setEscrow(res.data))
      .catch(() => setEscrow(null));
  };

  // Trạng thái modal hủy đơn
  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);
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
          fetchEscrow(id);
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
    if (!id) return;
    if (!window.confirm('Bạn có chắc chắn muốn giải ngân tiền ký quỹ cho người giao hàng?')) {
      return;
    }
    setReleasing(true);
    setReleaseSuccess(null);
    setReleaseError(null);
    try {
      const res = await orderApi.releaseEscrow(id);
      setReleaseSuccess(res.data.message || 'Giải ngân tiền ký quỹ thành công!');
      fetchEscrow(id);
    } catch (err) {
      const apiErr = getApiError(err);
      setReleaseError(apiErr.message);
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
      setIsCancelModalOpen(false);
      setCancelReason('');
    } catch (cause) {
      setCancelError(getApiError(cause));
    } finally {
      setCancelling(false);
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

  const canEdit = order.status === 'PENDING';
  const canCancel = order.status === 'PENDING' || order.status === 'FINDING_SHIPPER';

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
            {canEdit && (
              <Button
                variant="secondary"
                size="sm"
                icon={<Pencil size={15} />}
                onClick={() => navigate(`/sender/orders/${order.id}/edit`)}
              >
                Chỉnh sửa đơn
              </Button>
            )}
            {canCancel && (
              <Button
                variant="secondary"
                size="sm"
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
                <Button
                  variant="primary"
                  icon={<DollarSign size={16} />}
                  onClick={() => navigate(`/sender/orders/${order.id}/offers`)}
                  style={{ width: '100%' }}
                >
                  Xem danh sách chào giá
                </Button>
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

                {/* Nút hành động giải ngân dành cho ADMIN */}
                {isAdmin && escrow.status === 'HELD' && !escrow.frozen && (
                  <div style={{ marginTop: '8px' }}>
                    <Button
                      variant="primary"
                      icon={<ShieldCheck size={16} />}
                      onClick={handleReleaseEscrow}
                      disabled={releasing || !order.courierId}
                      style={{ width: '100%' }}
                    >
                      {releasing ? 'Đang giải ngân…' : 'Giải ngân cho người giao'}
                    </Button>
                    {!order.courierId && (
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px', textAlign: 'center' }}>
                        * Cần chỉ định người giao trước khi thực hiện giải ngân
                      </p>
                    )}
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
    </div>
  );
}
