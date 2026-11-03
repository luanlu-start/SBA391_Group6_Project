import React, { useEffect, useState } from 'react';
import {
  Navigation,
  Sparkles,
  Clock,
  Car,
  Bike,
  Send,
  Star,
  Award,
  TrendingUp,
  MapPin,
  ChevronRight,
} from 'lucide-react';
import { matchingApi } from '../../../api/matchingApi';
import type { OrderCourierMatch } from '../../../types/matching';
import Button from '../../../components/ui/Button';
import SectionCard from '../../../components/ui/SectionCard';
import Badge from '../../../components/ui/Badge';
import LoadingSpinner from '../../../components/common/LoadingSpinner';

interface SuggestedCouriersCardProps {
  orderId: number | string;
  isOrderOpen: boolean;
  onInviteCourier?: (courierId: number, courierName: string) => void;
}

export default function SuggestedCouriersCard({
  orderId,
  isOrderOpen,
  onInviteCourier,
}: SuggestedCouriersCardProps): React.ReactElement {
  const [matches, setMatches] = useState<OrderCourierMatch[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!orderId) return;

    let isMounted = true;
    setLoading(true);
    setError(null);

    matchingApi
      .getCouriersForOrder(orderId)
      .then((res) => {
        if (isMounted) {
          setMatches(res.data || []);
        }
      })
      .catch((err) => {
        if (isMounted) {
          console.warn('Không thể tải gợi ý người giao tiện đường:', err);
          setError('Không thể tải dữ liệu gợi ý tài xế lúc này.');
        }
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [orderId]);

  const getTierTone = (tier?: string): 'info' | 'warning' | 'neutral' => {
    switch (tier) {
      case 'DIAMOND':
        return 'info';
      case 'GOLD':
        return 'warning';
      case 'SILVER':
      case 'BRONZE':
      default:
        return 'neutral';
    }
  };

  const getTierLabel = (tier?: string) => {
    switch (tier) {
      case 'DIAMOND':
        return 'Kim cương';
      case 'GOLD':
        return 'Vàng';
      case 'SILVER':
        return 'Bạc';
      case 'BRONZE':
        return 'Đồng';
      default:
        return 'Thành viên';
    }
  };

  const formatDepartAt = (iso: string) => {
    try {
      const d = new Date(iso);
      return d.toLocaleString('vi-VN', {
        hour: '2-digit',
        minute: '2-digit',
        day: '2-digit',
        month: '2-digit',
      });
    } catch {
      return iso;
    }
  };

  const headerActions = matches.length > 0 ? (
    <Badge tone="info">
      {matches.length} chuyến phù hợp
    </Badge>
  ) : undefined;

  return (
    <SectionCard
      title="Tài xế tiện đường gợi ý"
      description="Hệ thống tự động phát hiện các tài xế có chuyến đi đi qua điểm lấy và giao hàng của bạn với độ lệch tuyến tối ưu."
      actions={headerActions}
    >
      {loading ? (
        <div style={{ padding: '24px 0', textAlign: 'center' }}>
          <LoadingSpinner text="Đang quét các hành trình tiện đường phù hợp..." />
        </div>
      ) : error ? (
        <div
          style={{
            padding: '16px',
            borderRadius: '10px',
            backgroundColor: 'var(--bg-secondary)',
            color: 'var(--text-muted)',
            fontSize: '0.875rem',
            textAlign: 'center',
          }}
        >
          {error}
        </div>
      ) : matches.length === 0 ? (
        <div
          style={{
            padding: '28px 16px',
            textAlign: 'center',
            backgroundColor: 'var(--bg-secondary)',
            borderRadius: '12px',
            border: '1px dashed var(--border-subtle, #333d4c)',
            color: 'var(--text-muted)',
          }}
        >
          <Navigation size={28} style={{ opacity: 0.4, marginBottom: '8px', color: 'var(--primary)' }} />
          <h4 style={{ margin: '0 0 6px 0', color: 'var(--text-primary, #f1f4fb)', fontSize: '0.95rem' }}>
            Chưa có chuyến đi nào khớp dọc tuyến đường này
          </h4>
          <p style={{ margin: 0, fontSize: '0.85rem', lineHeight: 1.5 }}>
            Khi các tài xế đăng chuyến đi tiện đường qua khu vực lấy và giao của bạn, gợi ý sẽ tự động cập nhật tại đây.
          </p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {matches.map((item) => (
            <div
              key={`${item.tripId}-${item.courierId}`}
              style={{
                backgroundColor: 'var(--bg-secondary, #1a2230)',
                border: '1px solid var(--border-subtle, #2a374a)',
                borderRadius: '12px',
                padding: '16px',
                transition: 'border-color 0.15s ease, transform 0.15s ease',
              }}
            >
              {/* Card Header: Driver profile & Match score */}
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'flex-start',
                  gap: '12px',
                  flexWrap: 'wrap',
                  marginBottom: '12px',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div
                    style={{
                      width: '42px',
                      height: '42px',
                      borderRadius: '50%',
                      backgroundColor: 'rgba(243, 112, 33, 0.15)',
                      color: 'var(--primary, #f37021)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 700,
                      fontSize: '1rem',
                      border: '1px solid rgba(243, 112, 33, 0.3)',
                      overflow: 'hidden',
                    }}
                  >
                    {item.courierAvatar ? (
                      <img
                        src={item.courierAvatar}
                        alt={item.courierName}
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      />
                    ) : (
                      item.courierName.charAt(0).toUpperCase()
                    )}
                  </div>

                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                      <span style={{ fontWeight: 600, color: 'var(--text-primary, #f1f4fb)', fontSize: '0.975rem' }}>
                        {item.courierName}
                      </span>
                      <Badge tone={getTierTone(item.courierTier)}>
                        <Award size={12} style={{ marginRight: '3px' }} />
                        {getTierLabel(item.courierTier)}
                      </Badge>
                    </div>

                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '10px',
                        fontSize: '0.8rem',
                        color: 'var(--text-muted)',
                        marginTop: '3px',
                      }}
                    >
                      <span style={{ display: 'flex', alignItems: 'center', gap: '3px', color: '#f5cb76' }}>
                        <Star size={12} fill="#f5cb76" />
                        {item.trustScore ?? 80} điểm uy tín
                      </span>
                      <span>•</span>
                      <span>{item.completedOrders ?? 0} đơn hoàn tất</span>
                      {item.courierPhone && (
                        <>
                          <span>•</span>
                          <span>{item.courierPhone}</span>
                        </>
                      )}
                    </div>
                  </div>
                </div>

                {/* Match Score & Detour Badges */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  {item.detourKm !== undefined && (
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '4px',
                        padding: '4px 8px',
                        borderRadius: '6px',
                        backgroundColor: 'rgba(96, 165, 246, 0.12)',
                        color: '#60a5fa',
                        fontSize: '0.78rem',
                        fontWeight: 600,
                      }}
                    >
                      <TrendingUp size={12} />
                      Lệch ~{item.detourKm} km
                    </span>
                  )}

                  {item.matchScore !== undefined && (
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '4px',
                        padding: '4px 10px',
                        borderRadius: '6px',
                        backgroundColor:
                          item.matchScore >= 85
                            ? 'rgba(16, 185, 129, 0.15)'
                            : 'rgba(245, 203, 118, 0.15)',
                        color: item.matchScore >= 85 ? '#34d399' : '#f5cb76',
                        fontSize: '0.82rem',
                        fontWeight: 700,
                        border: `1px solid ${
                          item.matchScore >= 85
                            ? 'rgba(16, 185, 129, 0.3)'
                            : 'rgba(245, 203, 118, 0.3)'
                        }`,
                      }}
                    >
                      <Sparkles size={12} />
                      {item.matchScore}% Phù hợp
                    </span>
                  )}
                </div>
              </div>

              {/* Trip Route Details */}
              <div
                style={{
                  padding: '10px 12px',
                  borderRadius: '8px',
                  backgroundColor: 'rgba(255, 255, 255, 0.03)',
                  border: '1px solid var(--border-subtle, #2a374a)',
                  marginBottom: '10px',
                  fontSize: '0.85rem',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                  <Clock size={14} style={{ color: 'var(--primary, #f37021)' }} />
                  <span style={{ color: 'var(--text-secondary)' }}>Khởi hành:</span>
                  <strong style={{ color: 'var(--text-primary)' }}>{formatDepartAt(item.departAt)}</strong>
                  <span style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    {item.vehicleType === 'CAR' ? <Car size={14} /> : <Bike size={14} />}
                    <span style={{ color: 'var(--text-muted)' }}>{item.vehicleLabel || 'Xe máy'}</span>
                  </span>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-secondary)' }}>
                  <MapPin size={13} style={{ color: '#34d399', flexShrink: 0 }} />
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '42%' }}>
                    {item.origin}
                  </span>
                  <ChevronRight size={14} style={{ color: 'var(--text-muted)', flexShrink: 0 }} />
                  <MapPin size={13} style={{ color: '#f43f5e', flexShrink: 0 }} />
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '42%' }}>
                    {item.destination}
                  </span>
                </div>
              </div>

              {/* Match Reasons Badges */}
              {item.matchReasons && item.matchReasons.length > 0 && (
                <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '12px' }}>
                  {item.matchReasons.map((reason, idx) => (
                    <span
                      key={idx}
                      style={{
                        fontSize: '0.75rem',
                        padding: '2px 8px',
                        borderRadius: '4px',
                        backgroundColor: 'rgba(255, 255, 255, 0.05)',
                        color: 'var(--text-secondary)',
                      }}
                    >
                      ✓ {reason}
                    </span>
                  ))}
                </div>
              )}

              {/* Action buttons */}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                {isOrderOpen && onInviteCourier && (
                  <Button
                    variant="primary"
                    icon={<Send size={14} />}
                    onClick={() => onInviteCourier(item.courierId, item.courierName)}
                  >
                    Mời tài xế này
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </SectionCard>
  );
}
