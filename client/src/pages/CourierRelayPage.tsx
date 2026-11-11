import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowRightLeft,
  CheckCircle2,
  MapPin,
  Package,
  RefreshCw,
  ShieldCheck,
  User,
  ExternalLink
} from 'lucide-react';

import { relayApi } from '../api/relayApi';
import { getApiError, type ApiError } from '../api/ApiError';
import type { RelayOrderResponse } from '../types/order';
import { type RelayMeetingPoint, type DetailedOrderLeg, isLegAssignedToUser } from '../types/relay';
import { useAuthentication } from '../auth/useAuthentication';

import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ApiErrorNotice from '../components/ApiErrorNotice';

import '../styles/relay-handover.css';

const numberFormat = new Intl.NumberFormat('vi-VN');

export default function CourierRelayPage(): React.ReactElement {
  const navigate = useNavigate();
  const { user } = useAuthentication();

  const [orders, setOrders] = useState<RelayOrderResponse[]>([]);
  const [meetingPoints, setMeetingPoints] = useState<RelayMeetingPoint[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<ApiError | null>(null);

  // Tab filter: 'available' (chặng đang tìm tài xế), 'my' (chặng của tôi), 'all' (tất cả)
  const [activeTab, setActiveTab] = useState<'available' | 'my' | 'all'>('available');
  const [claimingLegId, setClaimingLegId] = useState<number | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [ordersRes, pointsRes] = await Promise.all([
        relayApi.getRelayOrders(activeTab),
        relayApi.getSafeMeetingPoints(),
      ]);
      setOrders(ordersRes.data || []);
      setMeetingPoints(pointsRes.data || []);
    } catch (err) {
      setError(getApiError(err));
    } finally {
      setLoading(false);
    }
  }, [activeTab]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  // Xử lý nhận chặng tiếp sức
  const handleAcceptLeg = async (orderId: number, legNo: number) => {
    setClaimingLegId(orderId * 100 + legNo);
    try {
      await relayApi.acceptLeg(orderId, legNo);
      // Chuyển ngay sang trang bàn giao của chặng đó
      navigate(`/courier/orders/${orderId}/legs/${legNo}/handover`);
    } catch (err) {
      setError(getApiError(err));
      setClaimingLegId(null);
    }
  };

  return (
    <div className="relay-handover-container">
      {/* Hero Header */}
      <div className="relay-hero-card">
        <div className="relay-hero-header">
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
              <div className="relay-hero-badge">
                <ShieldCheck size={16} />
                Mạng lưới giao hàng tiếp sức
              </div>
            </div>
            <h1 className="relay-hero-title">
              Sàn chặng tiếp sức & Bàn giao hàng hóa
            </h1>
            <p className="relay-hero-desc">
              Khám phá các chặng vận chuyển đang cần tài xế tiếp sức tại các điểm hẹn công cộng hoặc quản lý các chặng bạn đang phụ trách. Nhận chặng tiện đường và thực hiện bàn giao hàng hóa bảo đảm.
            </p>
          </div>

          <Button
            variant="secondary"
            icon={<RefreshCw size={16} className={loading ? 'animate-spin' : ''} />}
            onClick={() => void loadData()}
            disabled={loading}
          >
            Làm mới danh sách
          </Button>
        </div>
      </div>

      {error && <ApiErrorNotice error={error} />}

      {/* Tabs Filter */}
      <div className="relay-mode-tabs" style={{ maxWidth: '600px', margin: '0 auto var(--space-4)' }}>
        <button
          type="button"
          className={`relay-tab-button ${activeTab === 'available' ? 'active' : ''}`}
          onClick={() => setActiveTab('available')}
        >
          <Package size={18} />
          Chặng tìm tài xế
        </button>
        <button
          type="button"
          className={`relay-tab-button ${activeTab === 'my' ? 'active' : ''}`}
          onClick={() => setActiveTab('my')}
        >
          <User size={18} />
          Chặng tôi phụ trách
        </button>
        <button
          type="button"
          className={`relay-tab-button ${activeTab === 'all' ? 'active' : ''}`}
          onClick={() => setActiveTab('all')}
        >
          <ArrowRightLeft size={18} />
          Tất cả đơn tiếp sức
        </button>
      </div>

      {/* Danh sách đơn và chặng */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <LoadingSpinner />
          <p style={{ marginTop: '16px', color: 'var(--text-secondary)' }}>Đang tải danh sách chặng tiếp sức...</p>
        </div>
      ) : orders.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 20px', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
          <Package size={48} style={{ color: 'var(--text-muted)', margin: '0 auto 16px' }} />
          <h3 style={{ fontSize: '1.125rem', fontWeight: 600, margin: '0 0 8px' }}>
            {activeTab === 'available'
              ? 'Hiện chưa có chặng tiếp sức nào cần tìm tài xế'
              : activeTab === 'my'
              ? 'Bạn chưa nhận chặng tiếp sức nào'
              : 'Không có đơn tiếp sức nào trong hệ thống'}
          </h3>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', maxWidth: '500px', margin: '0 auto 20px' }}>
            {activeTab === 'available'
              ? 'Các đơn tiếp sức mới sẽ xuất hiện tại đây khi người gửi tạo đơn hoặc khi chặng trước sắp đến điểm hẹn bàn giao.'
              : 'Hãy chuyển sang tab "Chặng tìm tài xế" để tiếp nhận các chặng vận chuyển phù hợp với lộ trình di chuyển của bạn.'}
          </p>
          {activeTab !== 'available' && (
            <Button variant="primary" onClick={() => setActiveTab('available')}>
              Xem các chặng đang tìm tài xế
            </Button>
          )}
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
          {orders.map((order) => {
            const legs = (order.legs || []) as DetailedOrderLeg[];
            return (
              <SectionCard
                key={order.id}
                title={`Đơn hàng tiếp sức: ${order.orderCode}`}
                description={`Kiện hàng: ${order.productName} • Khối lượng: ${order.weightKg} kg • Tổng cước: ${numberFormat.format(order.priceAgreed || order.priceSuggested || 0)} đ`}
                actions={
                  <Button
                    variant="secondary"
                    size="sm"
                    icon={<ExternalLink size={14} />}
                    onClick={() => navigate(`/courier/orders/${order.id}/handover`)}
                  >
                    Vào màn hình bàn giao
                  </Button>
                }
              >
                {/* Lộ trình các chặng của đơn này */}
                <div className="relay-legs-stepper" style={{ marginTop: '8px' }}>
                  {legs.map((leg) => {
                    const isMyLeg = isLegAssignedToUser(leg, user);
                    const isUnassigned = !leg.courierId && !leg.courierName;
                    const isCompleted = leg.status === 'COMPLETED';
                    const isClaimingThis = claimingLegId === (order.id * 100 + leg.legNo);

                    return (
                      <div
                        key={leg.id}
                        className={`relay-leg-step-card ${isCompleted ? 'completed' : ''} ${isMyLeg ? 'active' : ''}`}
                      >
                        <div className="relay-leg-step-header">
                          <span className="relay-leg-number">
                            {isCompleted ? (
                              <CheckCircle2 size={18} style={{ color: 'var(--accent-emerald)' }} />
                            ) : (
                              <span
                                style={{
                                  width: '22px',
                                  height: '22px',
                                  borderRadius: '50%',
                                  background: isMyLeg ? 'var(--primary)' : 'var(--bg-tertiary)',
                                  color: '#fff',
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontSize: '12px',
                                  fontWeight: 700,
                                }}
                              >
                                {leg.legNo}
                              </span>
                            )}
                            Chặng {leg.legNo}
                          </span>

                          <span
                            className="relay-leg-badge"
                            style={{
                              background: isCompleted
                                ? 'rgba(108, 222, 180, 0.15)'
                                : isMyLeg
                                ? 'rgba(243, 112, 33, 0.2)'
                                : isUnassigned
                                ? 'rgba(96, 165, 250, 0.15)'
                                : 'var(--bg-tertiary)',
                              color: isCompleted
                                ? 'var(--accent-emerald)'
                                : isMyLeg
                                ? 'var(--primary)'
                                : isUnassigned
                                ? 'var(--secondary)'
                                : 'var(--text-muted)',
                              fontWeight: 600,
                            }}
                          >
                            {isCompleted
                              ? 'Đã hoàn tất'
                              : isMyLeg
                              ? 'Bạn đang phụ trách'
                              : isUnassigned
                              ? 'Đang tìm tài xế'
                              : 'Tài xế khác'}
                          </span>
                        </div>

                        {/* Điểm xuất phát & điểm hẹn */}
                        <div className="relay-leg-locations">
                          <div className="relay-leg-loc-row">
                            <MapPin size={15} style={{ color: 'var(--primary)', flexShrink: 0, marginTop: '2px' }} />
                            <div>
                              <small style={{ color: 'var(--text-muted)' }}>Xuất phát:</small>
                              <div>{leg.pickupAddress}</div>
                            </div>
                          </div>
                          <div className="relay-leg-loc-row">
                            <MapPin size={15} style={{ color: 'var(--accent-cyan)', flexShrink: 0, marginTop: '2px' }} />
                            <div>
                              <small style={{ color: 'var(--text-muted)' }}>
                                {leg.legNo < legs.length ? 'Điểm hẹn bàn giao an toàn:' : 'Điểm giao đích:'}
                              </small>
                              <div><strong>{leg.deliveryAddress}</strong></div>
                            </div>
                          </div>
                        </div>

                        {/* Thông tin cước & km */}
                        <div className="relay-leg-meta">
                          <span>{leg.distanceKm} km</span>
                          <span className="relay-leg-fee">{numberFormat.format(leg.legFee)} đ</span>
                        </div>

                        {/* Thông tin tài xế */}
                        {leg.courierName && (
                          <div style={{ fontSize: '0.813rem', color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                            <User size={14} style={{ color: 'var(--text-muted)' }} />
                            <span>Tài xế: <strong>{leg.courierName}</strong></span>
                            {isMyLeg && <span style={{ color: 'var(--primary)', fontWeight: 600 }}>(Tôi)</span>}
                          </div>
                        )}

                        {/* Nút hành động cho chặng */}
                        <div style={{ marginTop: 'auto', paddingTop: '8px' }}>
                          {isUnassigned && !isCompleted ? (
                            <Button
                              variant="primary"
                              size="sm"
                              fullWidth
                              loading={isClaimingThis}
                              onClick={() => handleAcceptLeg(order.id, leg.legNo)}
                            >
                              Nhận chặng này ({numberFormat.format(leg.legFee)} đ)
                            </Button>
                          ) : isMyLeg && !isCompleted ? (
                            <Button
                              variant="secondary"
                              size="sm"
                              fullWidth
                              onClick={() => navigate(`/courier/orders/${order.id}/legs/${leg.legNo}/handover`)}
                              style={{ borderColor: 'var(--primary)', color: 'var(--primary)' }}
                            >
                              Bàn giao / Tiếp nhận
                            </Button>
                          ) : (
                            <Button
                              variant="ghost"
                              size="sm"
                              fullWidth
                              onClick={() => navigate(`/courier/orders/${order.id}/legs/${leg.legNo}/handover`)}
                            >
                              Xem tiến trình chặng
                            </Button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </SectionCard>
            );
          })}
        </div>
      )}

      {/* Điểm hẹn bàn giao an toàn quanh Campus */}
      <SectionCard
        title="Mạng lưới điểm hẹn bàn giao an toàn FPT"
        description="Các địa điểm bàn giao công cộng bảo đảm an ninh 24/7 với camera giám sát phục vụ kết nối tiếp sức giữa các tài xế."
      >
        <div className="relay-meeting-points-grid">
          {meetingPoints.map((point) => (
            <div key={point.id} className="relay-point-card">
              <div className="relay-point-name">
                <ShieldCheck size={16} style={{ color: 'var(--accent-emerald)' }} />
                {point.name}
              </div>
              <div className="relay-point-addr">{point.address}</div>
              <div className="relay-point-sec">
                <CheckCircle2 size={12} />
                {point.securityFeatures}
              </div>
            </div>
          ))}
        </div>
      </SectionCard>
    </div>
  );
}
