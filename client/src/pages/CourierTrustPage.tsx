import { useEffect, useState } from 'react';
import { trustApi } from '../api/trustApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { MyTrust } from '../types/trust';
import ApiErrorNotice from '../components/ApiErrorNotice';
import LoadingSpinner from '../components/common/LoadingSpinner';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Badge from '../components/ui/Badge';
import Alert from '../components/ui/Alert';
import SignedIn from '../features/complaints/components/SignedIn';
import {
  componentLabels, formatMoney, formatPercent, formatScore, formatTime, reasonLabels, tierLabels, tierTones,
} from '../features/trust/trustPresentation';
import '../styles/trust.css';

function Meter({ value, label }: { value: number; label: string }) {
  const width = Math.max(0, Math.min(100, value));
  return <span className="trust-meter" role="img" aria-label={label}><span style={{ width: `${width}%` }} /></span>;
}

function TrustOverview() {
  const [trust, setTrust] = useState<MyTrust | null>(null);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    void trustApi.mine(controller.signal)
      .then(response => { if (!controller.signal.aborted) setTrust(response.data); })
      .catch(cause => { if (!controller.signal.aborted) setError(getApiError(cause)); });
    return () => controller.abort();
  }, []);

  if (error) return <ApiErrorNotice message={error.message} correlationId={error.correlationId} />;
  if (!trust) return <LoadingSpinner text="Đang tải điểm uy tín…" />;

  const restricted = trust.restrictedUntil !== null && new Date(trust.restrictedUntil) > new Date();
  const next = trust.nextTier;

  return <div className="ui-stack trust-page">
    <PageHeader title="Uy tín và hạng" eyebrow="Người giao"
      description={`Điểm tính trên thang 0–100, cập nhật sau mỗi đơn và hằng đêm. Lần tính gần nhất: ${formatTime(trust.calculatedAt)}.`} />
    {restricted && <Alert tone="danger" title="Tạm khóa nhận đơn">Bạn chưa thể nhận đơn mới đến {formatTime(trust.restrictedUntil)}.</Alert>}
    {trust.belowRestrictThreshold && <Alert tone="warning" title="Điểm dưới ngưỡng">Bạn tạm thời chỉ nhận được đơn giá trị thấp cho tới khi điểm hồi phục.</Alert>}

    <div className="trust-summary">
      <SectionCard title="Điểm uy tín">
        <div className="trust-score">
          <strong>{formatScore(trust.score)}</strong><span>/ 100</span>
        </div>
        <Meter value={trust.score} label={`Điểm ${formatScore(trust.score)} trên 100`} />
        <p className="trust-muted">{trust.completedOrders} đơn hoàn tất · {trust.recentCancellations}/{trust.cancelLimit} lần hủy được phép trong 30 ngày</p>
      </SectionCard>
      <SectionCard title="Hạng hiện tại">
        <Badge dot tone={tierTones[trust.tier]}>{tierLabels[trust.tier]}</Badge>
        <dl className="trust-benefits">
          <div><dt>Đơn giao đồng thời</dt><dd>{trust.benefits.maxConcurrentOrders}</dd></div>
          <div><dt>Tổng giá trị đang giữ</dt><dd>{formatMoney(trust.benefits.maxTotalDeclaredValue)}</dd></div>
          <div><dt>Nhận đơn giá trị cao</dt><dd>{trust.benefits.canAcceptHighValue ? 'Có' : 'Chưa'}</dd></div>
          <div><dt>Phí nền tảng</dt><dd>{formatPercent(trust.benefits.commissionRate)}</dd></div>
        </dl>
      </SectionCard>
      <SectionCard title={next ? `Lên hạng ${next.name}` : 'Hạng cao nhất'}>
        {next ? <div className="ui-stack">
          <div className="trust-progress">
            <span>Điểm {formatScore(trust.score)} / {formatScore(next.minScore)}</span>
            <Meter value={(trust.score / next.minScore) * 100} label="Tiến độ điểm lên hạng" />
          </div>
          <div className="trust-progress">
            <span>Đơn hoàn tất {trust.completedOrders} / {next.minOrders}</span>
            <Meter value={(trust.completedOrders / next.minOrders) * 100} label="Tiến độ số đơn lên hạng" />
          </div>
        </div> : <p className="trust-muted">Bạn đang ở hạng Kim cương. Giữ điểm và số đơn để không bị xuống hạng.</p>}
      </SectionCard>
    </div>

    <SectionCard title="Thành phần điểm" description="Mỗi chỉ số được làm mượt về điểm khởi tạo khi còn ít dữ liệu.">
      <ul className="trust-components">
        {trust.components.map(item => <li key={item.key}>
          <div>
            <strong>{componentLabels[item.key].title}</strong>
            <span className="trust-muted">{componentLabels[item.key].hint} · trọng số {formatPercent(item.weight)}</span>
          </div>
          <Meter value={item.value * 100} label={`${componentLabels[item.key].title} ${formatPercent(item.value)}`} />
          <span className="trust-value">{item.hasData ? formatPercent(item.value) : 'Chưa có dữ liệu'}</span>
        </li>)}
      </ul>
    </SectionCard>

    <SectionCard title="Lịch sử thay đổi">
      {trust.history.length === 0 ? <p className="trust-muted">Điểm chưa thay đổi kể từ khi đăng ký.</p>
        : <ol className="trust-history">{trust.history.map((item, index) => <li key={`${item.calculatedAt}-${index}`}>
          <span className="trust-muted">{formatTime(item.calculatedAt)}</span>
          <span>{item.previousScore !== null ? `${formatScore(item.previousScore)} → ` : ''}<strong>{formatScore(item.score)}</strong>
            {item.previousTier && item.previousTier !== item.tier ? ` · ${tierLabels[item.previousTier]} → ${tierLabels[item.tier]}` : ''}</span>
          <span className="trust-muted">{reasonLabels[item.reason]}{item.note ? ` · ${item.note}` : ''}</span>
        </li>)}</ol>}
    </SectionCard>
  </div>;
}

export default function CourierTrustPage() {
  return <SignedIn><TrustOverview /></SignedIn>;
}
