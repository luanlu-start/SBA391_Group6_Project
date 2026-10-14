import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  MapPin,
  User,
  Package,
  Calendar,
  Truck,
  Image as ImageIcon,
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
import LoadingSpinner from '../components/common/LoadingSpinner';
import ApiErrorNotice from '../components/ApiErrorNotice';

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    if (!id) return;

    let mounted = true;
    setLoading(true);
    setError(null);

    orderApi
      .getById(id)
      .then(res => {
        if (mounted) setOrder(res.data);
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
              <ApiErrorNotice message={error.message} correlationId={error.correlationId} />
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

  return (
    <div className="ui-stack">
      <PageHeader
        title={`Vận đơn: ${order.orderCode}`}
        eyebrow={`Chi tiết đơn hàng #${order.id}`}
        description={`Đơn được tiếp nhận trên hệ thống lúc ${new Date(order.createdAt).toLocaleString('vi-VN')}`}
        actions={
          <div className="ui-row" style={{ gap: '12px' }}>
            <Badge dot tone={statusConfig.tone}>
              {statusConfig.label}
            </Badge>
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
                  <strong>{order.receiverName}</strong> ({order.receiverPhone})
                </dd>
              </div>

              <div>
                <dt className="ui-row" style={{ gap: '6px' }}>
                  <User size={16} />
                  <span>Người gửi ID:</span>
                </dt>
                <dd>#{order.senderId}</dd>
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
        <div className="ui-stack">
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
        </div>
      </div>
    </div>
  );
}
