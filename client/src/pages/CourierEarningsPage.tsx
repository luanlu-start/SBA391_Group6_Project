import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Award,
  BarChart3,
  Calendar,
  CheckCircle2,
  Clock,
  DollarSign,
  Hourglass,
  Package,
  RefreshCw,
  ShieldCheck,
  Star,
  TrendingUp,
  Wallet,
} from 'lucide-react';
import { earningsApi } from '../api/earningsApi';
import type { CourierEarningsResponse, CourierStatsResponse } from '../types/payment';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import Alert from '../components/ui/Alert';
import EmptyState from '../components/ui/EmptyState';
import '../styles/payment.css';

const CourierEarningsPage: React.FC = () => {
  const [period, setPeriod] = useState<'DAY' | 'WEEK' | 'MONTH'>('MONTH');
  const [fromDate, setFromDate] = useState<string>('');
  const [toDate, setToDate] = useState<string>('');
  const [earnings, setEarnings] = useState<CourierEarningsResponse | null>(null);
  const [stats, setStats] = useState<CourierStatsResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = async (selectedPeriod = period, customFrom = fromDate, customTo = toDate) => {
    try {
      setLoading(true);
      setError(null);
      const [earningsRes, statsRes] = await Promise.all([
        earningsApi.getMyEarnings({
          period: selectedPeriod,
          from: customFrom || undefined,
          to: customTo || undefined,
        }),
        earningsApi.getMyStats(),
      ]);
      setEarnings(earningsRes.data);
      setStats(statsRes.data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Không thể tải dữ liệu thu nhập và thống kê.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handlePeriodChange = (nextPeriod: 'DAY' | 'WEEK' | 'MONTH') => {
    setPeriod(nextPeriod);
    setFromDate('');
    setToDate('');
    loadData(nextPeriod, '', '');
  };

  const handleApplyDateFilter = (e: React.FormEvent) => {
    e.preventDefault();
    loadData(period, fromDate, toDate);
  };

  const formatVND = (amount: number) => `${(amount || 0).toLocaleString('vi-VN')} đ`;

  const formatCompactVND = (amount: number) => {
    if (amount >= 1_000_000) return `${(amount / 1_000_000).toFixed(1).replace(/\.0$/, '')}tr`;
    if (amount >= 1_000) return `${Math.round(amount / 1_000)}k`;
    return `${amount}`;
  };

  const formatPercent = (rate: number) => `${Math.round((rate || 0) * 100)}%`;

  const maxChartGross = Math.max(1, ...(earnings?.chartPoints.map((pt) => pt.grossAmount) || [1]));

  return (
    <div className="ui-stack" style={{ maxWidth: 1120, margin: '0 auto' }}>
      <PageHeader
        eyebrow="Người giao"
        title="Thu nhập & Thống kê hiệu suất"
        description="Theo dõi doanh thu gộp, phí hoa hồng nền tảng, thu nhập ròng thực nhận theo từng đơn và tiến độ thăng hạng."
        actions={
          <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
            <Link to="/wallet" className="ui-button ui-button-secondary ui-button-sm">
              <Wallet size={15} /> Ví tiền &amp; Rút tiền
            </Link>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => loadData()}
              disabled={loading}
              icon={<RefreshCw size={15} />}
            >
              Làm mới
            </Button>
          </div>
        }
      />

      {error && <Alert tone="danger">{error}</Alert>}

      {/* Bộ lọc kỳ thống kê */}
      <SectionCard
        title="Bộ lọc kỳ thống kê"
        description={
          earnings
            ? `Đang hiển thị dữ liệu từ ${earnings.fromDate} đến ${earnings.toDate}`
            : 'Chọn nhanh khoảng thời gian hoặc lọc theo ngày cụ thể'
        }
      >
        <div className="tx-filter-bar" style={{ marginBottom: 0 }}>
          <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center', flexWrap: 'wrap' }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: '0.85rem', color: 'var(--text-secondary)', fontWeight: 600 }}>
              <Calendar size={15} color="var(--primary)" />
              Kỳ:
            </span>
            <Button
              size="sm"
              variant={period === 'DAY' ? 'primary' : 'secondary'}
              onClick={() => handlePeriodChange('DAY')}
            >
              7 ngày qua
            </Button>
            <Button
              size="sm"
              variant={period === 'WEEK' ? 'primary' : 'secondary'}
              onClick={() => handlePeriodChange('WEEK')}
            >
              4 tuần qua
            </Button>
            <Button
              size="sm"
              variant={period === 'MONTH' ? 'primary' : 'secondary'}
              onClick={() => handlePeriodChange('MONTH')}
            >
              Tháng này
            </Button>
          </div>

          <form
            onSubmit={handleApplyDateFilter}
            style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center', flexWrap: 'wrap', marginLeft: 'auto' }}
          >
            <input
              type="date"
              className="ui-control ui-input"
              style={{ width: 'auto' }}
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              aria-label="Từ ngày"
            />
            <span style={{ color: 'var(--text-muted)' }}>→</span>
            <input
              type="date"
              className="ui-control ui-input"
              style={{ width: 'auto' }}
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              aria-label="Đến ngày"
            />
            <Button type="submit" size="sm" variant="secondary">
              Lọc ngày
            </Button>
          </form>
        </div>
      </SectionCard>

      {loading ? (
        <SectionCard title="Đang tải dữ liệu">
          <div style={{ padding: 'var(--space-8)', textAlign: 'center', color: 'var(--text-muted)' }}>
            <RefreshCw className="spin" size={28} style={{ margin: '0 auto var(--space-2)' }} />
            <p>Đang tổng hợp thống kê thu nhập và hiệu suất…</p>
          </div>
        </SectionCard>
      ) : (
        <>
          {/* 4 KPI Cards Thu nhập (Dark Theme matching WalletPage) */}
          {earnings && (
            <div className="wallet-dashboard">
              <div className="wallet-stat-card primary">
                <div className="card-glow" />
                <div className="wallet-stat-header">
                  <span>Thu nhập ròng thực nhận</span>
                  <Wallet size={18} />
                </div>
                <div className="wallet-stat-value" style={{ color: 'var(--accent-emerald)' }}>
                  {formatVND(earnings.netEarnings)}
                </div>
                <div className="wallet-stat-subtext">
                  Hôm nay: <strong style={{ color: 'var(--text-primary)' }}>{formatVND(earnings.todayNetEarnings)}</strong>
                  {' · '}
                  <strong>{earnings.completedOrdersCount}</strong> đơn đã tất toán
                </div>
              </div>

              <div className="wallet-stat-card">
                <div className="wallet-stat-header">
                  <span>Doanh thu gộp</span>
                  <TrendingUp size={18} />
                </div>
                <div className="wallet-stat-value">{formatVND(earnings.grossEarnings)}</div>
                <div className="wallet-stat-subtext">
                  Tổng tiền cước đơn hàng trước khi trừ hoa hồng
                </div>
              </div>

              <div className="wallet-stat-card">
                <div className="wallet-stat-header">
                  <span>Phí hoa hồng nền tảng</span>
                  <DollarSign size={18} />
                </div>
                <div className="wallet-stat-value" style={{ color: 'var(--accent-amber)' }}>
                  -{formatVND(earnings.commissionAmount)}
                </div>
                <div className="wallet-stat-subtext">
                  Chiết khấu hạng hiện tại: <strong style={{ color: 'var(--text-primary)' }}>{formatPercent(earnings.commissionRate)}</strong>
                </div>
              </div>

              <div className="wallet-stat-card">
                <div className="wallet-stat-header">
                  <span>Đang giao (Chờ giải ngân)</span>
                  <Hourglass size={18} />
                </div>
                <div className="wallet-stat-value" style={{ color: 'var(--accent-cyan)' }}>
                  {formatVND(earnings.pendingEscrowAmount)}
                </div>
                <div className="wallet-stat-subtext">
                  Sẽ cộng vào thu nhập ròng khi hoàn tất giao &amp; giải ngân
                </div>
              </div>
            </div>
          )}

          {/* Hạng người giao & Tiến độ thăng hạng */}
          {stats && (
            <SectionCard
              title="Hạng người giao & Tiến độ thăng hạng"
              description="Thăng hạng cao hơn giúp giảm phí hoa hồng từ 8% xuống tối đa 4% và mở khóa đơn hàng giá trị cao."
              actions={
                <Badge tone="info" dot>
                  Hạng {stats.currentTierDisplayName} ({stats.currentTier})
                </Badge>
              }
            >
              <div className="earnings-tier-grid">
                {/* Cột 1: Thông tin hạng hiện tại */}
                <div className="earnings-panel">
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: '0.8rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--text-muted)' }}>
                      Cấp bậc hiện tại
                    </span>
                    <Badge tone="warning">Hoa hồng {formatPercent(stats.commissionRate)}</Badge>
                  </div>

                  <div className="earnings-tier-title">
                    <Award size={24} color="var(--primary)" />
                    <span>{stats.currentTierDisplayName}</span>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '0.88rem', color: 'var(--text-secondary)' }}>
                    <div>
                      Điểm uy tín hiện có:{' '}
                      <strong style={{ color: 'var(--accent-cyan)' }}>{stats.trustScore} / 100 điểm</strong>
                    </div>
                    <div>
                      Đơn hoàn thành tích lũy:{' '}
                      <strong style={{ color: 'var(--text-primary)' }}>{stats.completedOrders} đơn</strong>
                    </div>
                    <div>
                      Đơn đang phụ trách:{' '}
                      <strong style={{ color: 'var(--text-primary)' }}>{stats.inDeliveryOrders} đơn</strong>
                    </div>
                  </div>
                </div>

                {/* Cột 2: Tiến độ lên hạng tiếp theo */}
                <div className="earnings-panel">
                  <div>
                    <div style={{ fontSize: '0.8rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--text-muted)', marginBottom: '4px' }}>
                      Mục tiêu tiếp theo
                    </div>
                    <div style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--text-accent)' }}>
                      Hạng {stats.nextTierDisplayName} (Hoa hồng {formatPercent(stats.nextTierCommissionRate)})
                    </div>
                  </div>

                  <div className="earnings-progress-block">
                    <div className="earnings-progress-meta">
                      <span>Số đơn: <strong style={{ color: 'var(--text-primary)' }}>{stats.completedOrders} / {stats.nextTierMinOrders}</strong></span>
                      <span style={{ color: stats.ordersNeededForNextTier === 0 ? 'var(--accent-emerald)' : 'var(--text-muted)' }}>
                        {stats.ordersNeededForNextTier > 0
                          ? `Thiếu ${stats.ordersNeededForNextTier} đơn`
                          : 'Đã đạt chuẩn'}
                      </span>
                    </div>
                    <div className="earnings-progress-track">
                      <div
                        className="earnings-progress-fill orders"
                        style={{ width: `${stats.ordersProgressPercent}%` }}
                      />
                    </div>
                  </div>

                  <div className="earnings-progress-block">
                    <div className="earnings-progress-meta">
                      <span>Điểm uy tín: <strong style={{ color: 'var(--text-primary)' }}>{stats.trustScore} / {stats.nextTierMinScore}</strong></span>
                      <span style={{ color: stats.scoreNeededForNextTier <= 0 ? 'var(--accent-emerald)' : 'var(--text-muted)' }}>
                        {stats.scoreNeededForNextTier > 0
                          ? `Cần thêm +${stats.scoreNeededForNextTier} điểm`
                          : 'Đã đạt chuẩn'}
                      </span>
                    </div>
                    <div className="earnings-progress-track">
                      <div
                        className="earnings-progress-fill score"
                        style={{ width: `${stats.scoreProgressPercent}%` }}
                      />
                    </div>
                  </div>
                </div>

                {/* Cột 3: 4 Chỉ số hiệu suất */}
                <div className="earnings-metrics-grid">
                  <div className="earnings-metric-tile">
                    <span className="earnings-metric-label">Tỷ lệ hoàn thành</span>
                    <span className="earnings-metric-value" style={{ color: 'var(--accent-emerald)' }}>
                      <CheckCircle2 size={16} />
                      {stats.completionRate}%
                    </span>
                  </div>

                  <div className="earnings-metric-tile">
                    <span className="earnings-metric-label">Giao đúng hạn</span>
                    <span className="earnings-metric-value" style={{ color: 'var(--secondary)' }}>
                      <Clock size={16} />
                      {stats.onTimeRate == null ? 'Chưa có dữ liệu' : `${stats.onTimeRate}%`}
                    </span>
                  </div>

                  <div className="earnings-metric-tile">
                    <span className="earnings-metric-label">Đánh giá TB</span>
                    <span className="earnings-metric-value" style={{ color: 'var(--accent-amber)' }}>
                      <Star size={16} />
                      {stats.ratingAvg == null ? 'Chưa có đánh giá' : `${stats.ratingAvg} / 5.0`}
                    </span>
                  </div>

                  <div className="earnings-metric-tile">
                    <span className="earnings-metric-label">Tỷ lệ khiếu nại</span>
                    <span className="earnings-metric-value" style={{ color: stats.complaintRate > 0 ? 'var(--accent-rose)' : 'var(--text-primary)' }}>
                      <ShieldCheck size={16} />
                      {stats.complaintRate}%
                    </span>
                  </div>
                </div>
              </div>
            </SectionCard>
          )}

          {/* Biểu đồ thu nhập theo thời gian */}
          {earnings && earnings.chartPoints.length > 0 && (
            <SectionCard
              title="Biểu đồ biến động thu nhập theo ngày"
              description="Di chuột vào từng cột để xem chi tiết số đơn, doanh thu gộp và thu nhập ròng trong ngày."
              actions={
                <div className="earnings-chart-legend">
                  <span className="earnings-legend-item">
                    <span className="earnings-legend-dot" style={{ background: '#10b981' }} />
                    Thu nhập ròng
                  </span>
                  <span className="earnings-legend-item">
                    <span className="earnings-legend-dot" style={{ background: '#f59e0b' }} />
                    Hoa hồng nền tảng
                  </span>
                </div>
              }
            >
              <div className="earnings-chart-box">
                {earnings.chartPoints.map((pt) => {
                  const hasValue = pt.grossAmount > 0;
                  const barHeight = hasValue ? Math.max(18, Math.round((pt.grossAmount / maxChartGross) * 145)) : 4;
                  const netPct = hasValue ? Math.round((pt.netAmount / pt.grossAmount) * 100) : 0;

                  return (
                    <div
                      key={pt.date}
                      className="earnings-chart-col"
                      title={`${pt.date}: ${pt.orderCount} đơn · Gộp: ${formatVND(pt.grossAmount)} · Hoa hồng: -${formatVND(pt.commissionAmount)} · Ròng: ${formatVND(pt.netAmount)}`}
                    >
                      {hasValue && (
                        <div className="earnings-chart-top">
                          {formatCompactVND(pt.netAmount)}
                        </div>
                      )}
                      <div
                        className="earnings-chart-bar"
                        style={{
                          height: `${barHeight}px`,
                          background: hasValue ? '#f59e0b' : 'rgba(255, 255, 255, 0.08)',
                        }}
                      >
                        {hasValue && (
                          <div
                            style={{
                              width: '100%',
                              height: `${netPct}%`,
                              background: '#10b981',
                            }}
                          />
                        )}
                      </div>
                      <div className="earnings-chart-label">{pt.label}</div>
                    </div>
                  );
                })}
              </div>
            </SectionCard>
          )}

          {/* Bảng Phiếu thu nhập từng đơn */}
          {earnings && (
            <SectionCard
              title={`Phiếu thu nhập chi tiết theo đơn (${earnings.receipts.length})`}
              description="Chi tiết tiền cước gộp, tỷ lệ hoa hồng áp dụng theo hạng và thu nhập ròng được cộng vào ví."
            >
              {earnings.receipts.length === 0 ? (
                <EmptyState
                  icon={<BarChart3 size={32} />}
                  title="Chưa có đơn hoàn thành trong kỳ này"
                  description="Hãy nhận đơn trên Sàn đơn hoặc ghép chuyến đi để bắt đầu tích lũy thu nhập và nâng hạng người giao."
                  action={
                    <Link to="/courier/market" className="ui-button ui-button-primary">
                      <Package size={16} /> Khám phá Sàn đơn ngay
                    </Link>
                  }
                />
              ) : (
                <div className="tx-table-container">
                  <table className="tx-table">
                    <thead>
                      <tr>
                        <th>Mã đơn</th>
                        <th>Hàng hóa</th>
                        <th>Thời gian tất toán</th>
                        <th>Doanh thu gộp</th>
                        <th>Hoa hồng nền tảng</th>
                        <th>Thu nhập ròng</th>
                        <th>Trạng thái</th>
                      </tr>
                    </thead>
                    <tbody>
                      {earnings.receipts.map((item) => (
                        <tr key={item.orderId}>
                          <td>
                            <span className="tx-code">{item.orderCode}</span>
                          </td>
                          <td style={{ color: 'var(--text-primary)', fontWeight: 500 }}>
                            {item.productName}
                          </td>
                          <td>
                            <span className="tx-date">
                              {new Date(item.completedAt).toLocaleString('vi-VN')}
                            </span>
                          </td>
                          <td style={{ color: 'var(--text-primary)', fontWeight: 600 }}>
                            {formatVND(item.grossAmount)}
                          </td>
                          <td style={{ color: 'var(--accent-amber)', fontWeight: 600 }}>
                            -{formatVND(item.commissionAmount)} ({formatPercent(item.commissionRate)})
                          </td>
                          <td>
                            <span className="tx-amount inflow">+{formatVND(item.netAmount)}</span>
                          </td>
                          <td>
                            <Badge tone="success" dot>
                              Đã quyết toán
                            </Badge>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </SectionCard>
          )}
        </>
      )}
    </div>
  );
};

export default CourierEarningsPage;
