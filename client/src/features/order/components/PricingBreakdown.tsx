import { Calculator, ShieldCheck, Zap, Navigation, Loader2 } from 'lucide-react';
import type { PricingEstimateResponse } from '../../../types/order';
import { DELIVERY_URGENCY_MAP } from '../../../types/order';

interface PricingBreakdownProps {
  pricing: PricingEstimateResponse | null;
  loading: boolean;
  error?: string | null;
}

export default function PricingBreakdown({ pricing, loading, error }: PricingBreakdownProps) {
  if (loading) {
    return (
      <div
        style={{
          padding: '1.25rem',
          borderRadius: 'var(--radius-md, 10px)',
          background: 'var(--bg-secondary, #181e27)',
          border: '1px solid var(--border-subtle, #333d4c)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '0.75rem',
          color: 'var(--text-secondary, #b0bdd2)',
        }}
      >
        <Loader2 size={20} className="ui-spin" style={{ color: 'var(--primary, #f37021)' }} />
        <span>Đang tính toán khoảng cách và cước phí ước tính…</span>
      </div>
    );
  }

  if (error) {
    return (
      <div
        style={{
          padding: '1rem',
          borderRadius: 'var(--radius-md, 10px)',
          background: 'var(--danger-bg, #36202b)',
          border: '1px solid var(--accent-rose, #ffa6b3)',
          color: 'var(--accent-rose, #ffa6b3)',
          fontSize: '0.9rem',
        }}
      >
        {error}
      </div>
    );
  }

  if (!pricing) {
    return (
      <div
        style={{
          padding: '1.25rem',
          borderRadius: 'var(--radius-md, 10px)',
          background: 'var(--bg-secondary, #181e27)',
          border: '1px dashed var(--border-subtle, #333d4c)',
          color: 'var(--text-muted, #8e9eb8)',
          textAlign: 'center',
          fontSize: '0.925rem',
        }}
      >
        <Calculator size={24} style={{ margin: '0 auto 0.5rem', color: 'var(--primary, #f37021)', opacity: 0.8 }} />
        <p style={{ margin: 0, color: 'var(--text-secondary, #b0bdd2)' }}>
          Chọn hoặc nhập địa chỉ gửi & nhận để xem khoảng cách và bảng tính cước chi tiết tự động.
        </p>
      </div>
    );
  }

  const urgencyInfo = DELIVERY_URGENCY_MAP[pricing.urgency] || DELIVERY_URGENCY_MAP.STANDARD;

  return (
    <div
      style={{
        borderRadius: 'var(--radius-md, 10px)',
        border: '1px solid var(--border-subtle, #333d4c)',
        borderLeft: '4px solid var(--primary, #f37021)',
        background: 'var(--bg-secondary, #181e27)',
        padding: '1.25rem',
        boxShadow: 'var(--shadow-md, 0 8px 24px rgba(0,0,0,0.2))',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'baseline',
          justifyContent: 'space-between',
          borderBottom: '1px solid var(--border-subtle, #333d4c)',
          paddingBottom: '0.85rem',
          marginBottom: '1rem',
        }}
      >
        <div>
          <span
            style={{
              fontSize: '0.8rem',
              color: 'var(--text-accent, #ffb17d)',
              fontWeight: 600,
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
            }}
          >
            Tổng cước tạm tính (UC-11)
          </span>
          <div
            style={{
              fontSize: '1.75rem',
              fontWeight: 700,
              color: 'var(--primary, #f37021)',
              marginTop: '2px',
            }}
          >
            {pricing.totalEstimatedPrice.toLocaleString('vi-VN')} đ
          </div>
        </div>

        <div style={{ textAlign: 'right' }}>
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '0.25rem 0.65rem',
              borderRadius: 'var(--radius-full, 999px)',
              background: 'var(--brand-soft, rgba(243, 112, 33, 0.1))',
              border: '1px solid var(--brand-border, rgba(243, 112, 33, 0.3))',
              color: 'var(--text-accent, #ffb17d)',
              fontSize: '0.85rem',
              fontWeight: 600,
            }}
          >
            <Navigation size={13} style={{ color: 'var(--primary, #f37021)' }} />
            <span>{pricing.distanceKm} km</span>
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted, #8e9eb8)', marginTop: '4px' }}>
            {pricing.calculationMethod === 'OSRM_ROUTING'
              ? 'Định tuyến đường bộ OSRM'
              : pricing.calculationMethod === 'HAVERSINE_FALLBACK'
              ? 'Khoảng cách tọa độ x1.3'
              : 'Ước tính tiêu chuẩn'}
          </div>
        </div>
      </div>

      {/* Chi tiết từng mục cước phí */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.65rem', fontSize: '0.875rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--text-secondary, #b0bdd2)' }}>
          <span>Cước khoảng cách cơ bản ({pricing.distanceKm} km):</span>
          <strong style={{ color: 'var(--text-primary, #f1f4fb)' }}>
            {pricing.baseDistanceFee.toLocaleString('vi-VN')} đ
          </strong>
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--text-secondary, #b0bdd2)' }}>
          <span>Phụ phí khối lượng:</span>
          <strong style={{ color: 'var(--text-primary, #f1f4fb)' }}>
            {pricing.weightSurcharge > 0 ? `+${pricing.weightSurcharge.toLocaleString('vi-VN')} đ` : 'Miễn phí (≤ 2kg)'}
          </strong>
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--text-secondary, #b0bdd2)' }}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
            <ShieldCheck size={14} style={{ color: 'var(--accent-emerald, #6cdeb4)' }} />
            Phí bảo hiểm hàng hóa:
          </span>
          <strong style={{ color: 'var(--text-primary, #f1f4fb)' }}>
            {pricing.insuranceFee > 0 ? `+${pricing.insuranceFee.toLocaleString('vi-VN')} đ` : '0 đ'}
          </strong>
        </div>

        {pricing.urgencyMultiplier > 1 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--accent-amber, #f5cb76)' }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
              <Zap size={14} />
              Gói ưu tiên ({urgencyInfo.label}):
            </span>
            <strong style={{ color: 'var(--accent-amber, #f5cb76)' }}>
              x{pricing.urgencyMultiplier}
            </strong>
          </div>
        )}
      </div>

      <div
        style={{
          marginTop: '0.85rem',
          paddingTop: '0.65rem',
          borderTop: '1px dashed var(--border-subtle, #333d4c)',
          fontSize: '0.75rem',
          color: 'var(--text-muted, #8e9eb8)',
          display: 'flex',
          justifyContent: 'space-between',
        }}
      >
        <span>* Đã làm tròn lên bội số 500đ theo quy tắc FPTPost.</span>
        <span>Cước tối thiểu 10.000đ</span>
      </div>
    </div>
  );
}
