import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowRightLeft,
  CheckCircle2,
  Clock,
  DollarSign,
  MapPin,
  MessageCircle,
  Package,
  RefreshCw,
  RotateCcw,
  Search,
  Wallet,
  Weight,
  Zap,
} from 'lucide-react';

import { orderApi } from '../api/orderApi';
import type { Order } from '../types/order';
import { ORDER_STATUS_MAP, DELIVERY_URGENCY_MAP } from '../types/order';
import { getApiError, type ApiError } from '../api/ApiError';
import { useAuthentication } from '../auth/useAuthentication';
import { type DetailedOrderLeg, isLegAssignedToUser } from '../types/relay';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import EmptyState from '../components/ui/EmptyState';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ConfirmPickupPanel from '../components/order/ConfirmPickupPanel';
import DeliveryIncidentPanel from '../components/order/DeliveryIncidentPanel';
import '../styles/courier.css';

type FilterTab = 'ALL' | 'NEED_PICKUP' | 'DELIVERING' | 'COMPLETED' | 'ISSUES';

export default function CourierAssignedOrdersPage() {
  const navigate = useNavigate();
  const { user } = useAuthentication();

  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [activeTab, setActiveTab] = useState<FilterTab>('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  async function loadOrders() {
    setLoading(true);
    setError(null);

    try {
      const response = await orderApi.getAssignedToMe();
      setOrders(response.data || []);
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadOrders();
  }, []);

  // Hàm kiểm tra xem chặng tiếp sức của tài xế này đã bàn giao thành công hay chưa
  const isOrderLegCompletedForMe = (order: Order): boolean => {
    if (!order.isRelay || !order.legs || !user) return false;
    const myLegs = (order.legs as DetailedOrderLeg[]).filter(l => isLegAssignedToUser(l, user));
    return myLegs.length > 0 && myLegs.every(l => l.status === 'COMPLETED');
  };

  // Đếm số lượng theo trạng thái
  const counts = useMemo(() => {
    return {
      ALL: orders.length,
      NEED_PICKUP: orders.filter(o => {
        if (isOrderLegCompletedForMe(o)) return false;
        return ['SHIPPER_ASSIGNED', 'PICKING_UP', 'SHIPPER_ACCEPTED'].includes(o.status);
      }).length,
      DELIVERING: orders.filter(o => {
        if (isOrderLegCompletedForMe(o)) return false;
        return ['IN_DELIVERY', 'IN_TRANSIT'].includes(o.status);
      }).length,
      COMPLETED: orders.filter(o => {
        return isOrderLegCompletedForMe(o) || ['COMPLETED', 'DELIVERED'].includes(o.status);
      }).length,
      ISSUES: orders.filter(o =>
        ['DISPUTED', 'DELIVERY_FAILED', 'RETURNING', 'RETURNED'].includes(o.status)
      ).length,
    };
  }, [orders, user]);

  // Lọc theo tab và tìm kiếm
  const filteredOrders = useMemo(() => {
    return orders.filter(order => {
      const legCompletedForMe = isOrderLegCompletedForMe(order);

      // 1. Lọc theo tab
      if (activeTab === 'NEED_PICKUP') {
        if (legCompletedForMe) return false;
        if (!['SHIPPER_ASSIGNED', 'PICKING_UP', 'SHIPPER_ACCEPTED'].includes(order.status)) {
          return false;
        }
      }
      if (activeTab === 'DELIVERING') {
        if (legCompletedForMe) return false;
        if (!['IN_DELIVERY', 'IN_TRANSIT'].includes(order.status)) {
          return false;
        }
      }
      if (activeTab === 'COMPLETED') {
        if (!legCompletedForMe && !['COMPLETED', 'DELIVERED'].includes(order.status)) {
          return false;
        }
      }
      if (
        activeTab === 'ISSUES' &&
        !['DISPUTED', 'DELIVERY_FAILED', 'RETURNING', 'RETURNED'].includes(order.status)
      ) {
        return false;
      }

      // 2. Lọc theo từ khóa tìm kiếm
      if (searchTerm.trim()) {
        const query = searchTerm.toLowerCase().trim();
        const matchCode = order.orderCode?.toLowerCase().includes(query);
        const matchName = order.productName?.toLowerCase().includes(query);
        const matchPickup = order.pickupAddress?.toLowerCase().includes(query);
        const matchDelivery = order.deliveryAddress?.toLowerCase().includes(query);
        if (!matchCode && !matchName && !matchPickup && !matchDelivery) {
          return false;
        }
      }

      return true;
    });
  }, [orders, activeTab, searchTerm, user]);

  return (
    <div className="assigned-orders-page">
      <PageHeader
        title="Đơn hàng được phân công"
        description="Quản lý và thực hiện giao nhận các đơn hàng được giao cho bạn."
        actions={
          <Button
            variant="secondary"
            icon={<RefreshCw size={16} className={loading ? 'ui-spin' : ''} />}
            onClick={() => void loadOrders()}
            disabled={loading}
          >
            Làm mới
          </Button>
        }
      />

      {error && <ApiErrorNotice error={error} />}

      {/* Toolbar: Tìm kiếm & Các tab lọc */}
      <div className="assigned-toolbar">
        <div className="assigned-search-row">
          <div className="assigned-search-wrap">
            <Search size={16} className="assigned-search-icon" />
            <input
              type="text"
              className="ui-control assigned-search-input"
              placeholder="Tìm kiếm theo mã đơn, tên hàng hóa, địa chỉ..."
              value={searchTerm}
              onChange={e => setSearchTerm(e.target.value)}
            />
          </div>
        </div>

        <div className="assigned-filter-tabs">
          <button
            type="button"
            className={`assigned-tab-btn ${activeTab === 'ALL' ? 'is-active' : ''}`}
            onClick={() => setActiveTab('ALL')}
          >
            <span>Tất cả</span>
            <span className="assigned-tab-count">{counts.ALL}</span>
          </button>

          <button
            type="button"
            className={`assigned-tab-btn ${activeTab === 'NEED_PICKUP' ? 'is-active' : ''}`}
            onClick={() => setActiveTab('NEED_PICKUP')}
          >
            <span>Cần lấy hàng</span>
            <span className="assigned-tab-count">{counts.NEED_PICKUP}</span>
          </button>

          <button
            type="button"
            className={`assigned-tab-btn ${activeTab === 'DELIVERING' ? 'is-active' : ''}`}
            onClick={() => setActiveTab('DELIVERING')}
          >
            <span>Đang giao</span>
            <span className="assigned-tab-count">{counts.DELIVERING}</span>
          </button>

          <button
            type="button"
            className={`assigned-tab-btn ${activeTab === 'COMPLETED' ? 'is-active' : ''}`}
            onClick={() => setActiveTab('COMPLETED')}
          >
            <span>Hoàn thành</span>
            <span className="assigned-tab-count">{counts.COMPLETED}</span>
          </button>

          <button
            type="button"
            className={`assigned-tab-btn ${activeTab === 'ISSUES' ? 'is-active' : ''}`}
            onClick={() => setActiveTab('ISSUES')}
          >
            <span>Sự cố / Hoàn hàng</span>
            <span className="assigned-tab-count">{counts.ISSUES}</span>
          </button>
        </div>
      </div>

      {/* Danh sách đơn hàng */}
      {loading ? (
        <LoadingSpinner text="Đang tải danh sách đơn hàng..." />
      ) : orders.length === 0 && !error ? (
        <EmptyState
          title="Chưa có đơn hàng nào"
          description="Bạn chưa được phân công đơn hàng nào vào thời điểm này."
          icon={<Package size={32} />}
        />
      ) : filteredOrders.length === 0 ? (
        <EmptyState
          title="Không tìm thấy đơn hàng"
          description="Không có đơn hàng nào khớp với điều kiện tìm kiếm hoặc bộ lọc hiện tại."
          icon={<Search size={32} />}
          action={
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                setActiveTab('ALL');
                setSearchTerm('');
              }}
            >
              Xóa bộ lọc
            </Button>
          }
        />
      ) : (
        <div className="assigned-orders-list">
          {filteredOrders.map(order => {
            const myLegs = (order.isRelay && order.legs && user)
              ? (order.legs as DetailedOrderLeg[]).filter(l => isLegAssignedToUser(l, user))
              : [];
            const isMyLegCompleted = myLegs.length > 0 && myLegs.every(l => l.status === 'COMPLETED');
            const myLegFees = myLegs.reduce((sum, l) => sum + Number(l.legFee || 0), 0);
            const displayPrice = myLegFees > 0 ? myLegFees : (order.priceAgreed ?? order.priceSuggested);

            const statusConfig = (order.status && ORDER_STATUS_MAP[order.status]) ?? {
              label: order.status,
              tone: 'neutral' as const,
            };

            return (
              <article key={order.id} className="assigned-order-card">
                {/* Header thẻ: Mã đơn, Tag tiếp sức, Urgency, Thời gian, Badge trạng thái */}
                <div className="assigned-order-header">
                  <div className="assigned-order-header-left">
                    <span className="assigned-order-code">#{order.orderCode}</span>

                    {order.isRelay && (
                      <span className="assigned-badge-relay">
                        <ArrowRightLeft size={12} /> Tiếp sức
                      </span>
                    )}

                    {order.urgency && order.urgency !== 'STANDARD' && (
                      <span className="assigned-badge-urgency">
                        <Zap size={12} /> {DELIVERY_URGENCY_MAP[order.urgency]?.label || order.urgency}
                      </span>
                    )}
                  </div>

                  <div className="assigned-order-header-right">
                    {isMyLegCompleted ? (
                      <Badge tone="success">
                        <CheckCircle2 size={12} style={{ marginRight: '4px' }} />
                        Đã bàn giao chặng {myLegs.map(l => l.legNo).join(', ')} • Đã cộng ví
                      </Badge>
                    ) : (
                      <Badge dot tone={statusConfig.tone}>
                        {statusConfig.label}
                      </Badge>
                    )}

                    {order.createdAt && (
                      <span className="assigned-order-time">
                        <Clock size={12} />
                        {new Date(order.createdAt).toLocaleTimeString('vi-VN', {
                          hour: '2-digit',
                          minute: '2-digit',
                        })}{' '}
                        {new Date(order.createdAt).toLocaleDateString('vi-VN')}
                      </span>
                    )}
                  </div>
                </div>

                {/* Thân thẻ: Thông tin sản phẩm, Lộ trình giao nhận, Khung xác nhận lấy hàng */}
                <div className="assigned-order-body">
                  <div className="assigned-product-section">
                    <h3 className="assigned-product-title">
                      <Package size={18} />
                      <span>{order.productName}</span>
                    </h3>

                    <div className="assigned-meta-pills">
                      {order.quantity > 0 && (
                        <span className="assigned-pill">
                          {order.quantity} kiện
                        </span>
                      )}

                      {order.weightKg != null && (
                        <span className="assigned-pill">
                          <Weight size={13} /> {order.weightKg} kg
                        </span>
                      )}

                      {displayPrice != null && (
                        <span className="assigned-pill assigned-pill-price" title={myLegFees > 0 ? 'Cước phí chặng của bạn' : 'Cước phí đơn hàng'}>
                          <DollarSign size={13} /> {Number(displayPrice).toLocaleString('vi-VN')} đ
                          {myLegFees > 0 && <span style={{ fontSize: '11px', opacity: 0.85, marginLeft: '4px' }}>(cước chặng)</span>}
                        </span>
                      )}

                      {order.distanceKm != null && (
                        <span className="assigned-pill">
                          <MapPin size={13} /> ~{order.distanceKm} km
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Lộ trình giao nhận (nếu có địa chỉ) */}
                  {(order.pickupAddress || order.deliveryAddress) && (
                    <div className="assigned-route-section">
                      {order.pickupAddress && (
                        <div className="assigned-route-row">
                          <span className="assigned-route-dot pickup" />
                          <div className="assigned-route-text">
                            <span className="assigned-route-label">Điểm lấy:</span>
                            <span className="assigned-route-val">{order.pickupAddress}</span>
                          </div>
                        </div>
                      )}

                      {order.deliveryAddress && (
                        <div className="assigned-route-row">
                          <span className="assigned-route-dot dropoff" />
                          <div className="assigned-route-text">
                            <span className="assigned-route-label">Điểm giao:</span>
                            <span className="assigned-route-val">{order.deliveryAddress}</span>
                            {order.receiverName && (
                              <span className="assigned-receiver-info">
                                (Người nhận: {order.receiverName}
                                {order.receiverPhone ? ` - ${order.receiverPhone}` : ''})
                              </span>
                            )}
                          </div>
                        </div>
                      )}
                    </div>
                  )}

                  {/* UC-25: Xác nhận lấy hàng bằng QR và ảnh (khi SHIPPER_ASSIGNED) */}
                  {order.status === 'SHIPPER_ASSIGNED' && !isMyLegCompleted && (
                    <ConfirmPickupPanel
                      orderId={order.id}
                      onSuccess={() => void loadOrders()}
                    />
                  )}
                </div>

                {/* Footer thẻ: Ghi chú bên trái, Các nút thao tác nhóm bên phải */}
                <div className="assigned-order-footer">
                  <div className="assigned-footer-hint">
                    {order.deliveryNote && (
                      <span className="assigned-note-text" title={order.deliveryNote}>
                        Ghi chú: {order.deliveryNote}
                      </span>
                    )}
                  </div>

                  <div className="assigned-order-actions">
                    {/* Lịch sử sự cố / Báo cáo sự cố */}
                    <DeliveryIncidentPanel
                      orderId={order.id}
                      canReport={order.status === 'IN_DELIVERY'}
                      size="sm"
                    />

                    {/* Theo dõi hoàn hàng / Giao thất bại */}
                    <Button
                      variant="secondary"
                      size="sm"
                      icon={<RotateCcw size={14} />}
                      onClick={() => navigate(`/orders/${order.id}/return`)}
                    >
                      {order.status === 'IN_DELIVERY'
                        ? 'Giao thất bại / hoàn hàng'
                        : 'Theo dõi hoàn hàng'}
                    </Button>

                    {/* Bàn giao giữa các chặng tiếp sức */}
                    {order.isRelay && (
                      <Button
                        variant="secondary"
                        size="sm"
                        icon={<ArrowRightLeft size={14} />}
                        onClick={() => navigate(`/courier/orders/${order.id}/handover`)}
                        style={{
                          borderColor: 'var(--primary)',
                          color: 'var(--primary)',
                        }}
                      >
                        {isMyLegCompleted ? 'Tiến độ tiếp sức' : 'Bàn giao chặng'}
                      </Button>
                    )}

                    {isMyLegCompleted && (
                      <Button
                        variant="secondary"
                        size="sm"
                        icon={<Wallet size={14} />}
                        onClick={() => navigate('/wallet')}
                        style={{
                          borderColor: 'var(--accent-emerald, #10b981)',
                          color: 'var(--accent-emerald, #10b981)',
                        }}
                      >
                        Ví (+{Number(myLegFees).toLocaleString('vi-VN')} đ)
                      </Button>
                    )}

                    {/* Chat Sender - Courier */}
                    <Button
                      variant="primary"
                      size="sm"
                      icon={<MessageCircle size={15} />}
                      onClick={() => navigate(`/orders/${order.id}/chat`)}
                    >
                      Nhắn tin
                    </Button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
