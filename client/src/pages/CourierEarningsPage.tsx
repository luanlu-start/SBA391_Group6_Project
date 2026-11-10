import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Award,
  BarChart3,
  Calendar,
  CheckCircle2,
  Clock,
  DollarSign,
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
import '../styles/wallet.css';

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
    } catch (err: any) {
      setError(err.message || 'Không thể tải dữ liệu thu nhập và thống kê.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
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

  const formatVND = (amount: number) =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount || 0);

  const formatPercent = (rate: number) => `${(rate * 100).toFixed(0)}%`;

  const maxChartGross = Math.max(
    1,
    ...(earnings?.chartPoints.map((pt) => pt.grossAmount) || [1])
  );

  return (
    <div className="wallet-container">
      <PageHeader
        eyebrow="Người giao • UC-61"
        title="Thu nhập & Thống kê hiệu suất"
        description="Theo dõi doanh thu gộp, phí hoa hồng nền tảng, thu nhập ròng theo từng đơn và tiến độ thăng hạng người giao."
        actions={
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <Link to="/wallet" className="ui-button ui-button-secondary ui-button-sm">
              <Wallet size={15} /> Ví tiền & Rút tiền
            </Link>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => loadData()}
              icon={<RefreshCw size={15} />}
            >
              Làm mới
            </Button>
          </div>
        }
      />

      {error && (
        <div style={{ marginBottom: '16px' }}>
          <Alert tone="danger">{error}</Alert>
        </div>
      )}

      {/* Bộ lọc kỳ thống kê */}
      <div
        className="stat-card"
        style={{
          marginBottom: '20px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '12px',
        }}
      >
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
          <span style={{ fontWeight: 600, fontSize: '0.9rem', marginRight: '4px' }}>
            <Calendar size={16} style={{ verticalAlign: 'middle', marginRight: '6px' }} />
            Kỳ thống kê:
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
          style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}
        >
          <input
            type="date"
            className="form-input"
            style={{ width: 'auto', padding: '6px 10px' }}
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            aria-label="Từ ngày"
          />
          <span>→</span>
          <input
            type="date"
            className="form-input"
            style={{ width: 'auto', padding: '6px 10px' }}
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            aria-label="Đến ngày"
          />
          <Button type="submit" size="sm" variant="secondary">
            Lọc ngày
          </Button>
        </form>
      </div>

      {loading ? (
        <div className="stat-card" style={{ textAlign: 'center', padding: '48px' }}>
          Đang tải thống kê thu nhập và hiệu suất...
        </div>
      ) : (
        <>
          {/* KPI Cards Thu nhập (BR-F-118) */}
          {earnings && (
            <div className="wallet-grid" style={{ marginBottom: '24px' }}>
              <div className="balance-card">
                <div>
                  <div className="balance-label">
                    Thu nhập ròng ({earnings.fromDate} → {earnings.toDate})
                  </div>
                  <div className="balance-amount">{formatVND(earnings.netEarnings)}</div>
                  <div style={{ fontSize: '0.88rem', opacity: 0.9, marginTop: '6px' }}>
                    Đã trừ hoa hồng nền tảng ({formatPercent(earnings.commissionRate)}) trên{' '}
                    <strong>{earnings.completedOrdersCount}</strong> đơn hoàn thành
                  </div>
                </div>
                <div className="balance-actions">
                  <div style={{ fontSize: '0.85rem' }}>
                    Hôm nay: <strong>{formatVND(earnings.todayNetEarnings)}</strong>
                  </div>
                  <div style={{ fontSize: '0.85rem' }}>
                    Đang giao (chờ giải ngân): <strong>{formatVND(earnings.pendingEscrowAmount)}</strong>
                  </div>
                </div>
              </div>

              <div className="stat-card">
                <div>
                  <div className="stat-header">
                    <div className="stat-icon">
                      <TrendingUp size={20} />
                    </div>
                  </div>
                  <div className="stat-title">Doanh thu gộp</div>
                  <div className="stat-value">{formatVND(earnings.grossEarnings)}</div>
                </div>
                <div className="stat-sub">
                  Tổng tiền cước trước khi khấu trừ hoa hồng
                </div>
              </div>

              <div className="stat-card">
                <div>
                  <div className="stat-header">
                    <div className="stat-icon" style={{ color: '#d97706' }}>
                      <DollarSign size={20} />
                    </div>
                  </div>
                  <div className="stat-title">Phí hoa hồng nền tảng</div>
                  <div className="stat-value" style={{ color: '#d97706' }}>
                    -{formatVND(earnings.commissionAmount)}
                  </div>
                </div>
                <div className="stat-sub">
                  Mức chiết khấu hạng hiện tại: <strong>{formatPercent(earnings.commissionRate)}</strong>
                </div>
              </div>
            </div>
          )}

          {/* Tiến độ thăng hạng & Chỉ số hiệu suất (API-159, BR-F-119, BR-R-073) */}
          {stats && (
            <div style={{ marginBottom: '24px' }}>
              <SectionCard
                title="Hạng người giao & Tiến độ thăng hạng"
                description="Thăng hạng cao hơn để giảm tỷ lệ hoa hồng nền tảng từ 8% xuống tối đa 4% và mở khóa đơn giá trị cao."
              >
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
                    gap: '20px',
                    alignItems: 'stretch',
                  }}
                >
                  {/* Hạng hiện tại */}
                  <div
                    style={{
                      padding: '16px',
                      borderRadius: '12px',
                      background: 'var(--bg-subtle, #f8fafc)',
                      border: '1px solid var(--border-subtle, #e2e8f0)',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Hạng hiện tại</span>
                      <Badge tone="info">{stats.currentTier}</Badge>
                    </div>
                    <div style={{ fontSize: '1.4rem', fontWeight: 700, margin: '8px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <Award size={22} style={{ color: '#ea580c' }} />
                      {stats.currentTierDisplayName}
                    </div>
                    <div style={{ fontSize: '0.88rem', color: 'var(--text-secondary)' }}>
                      Điểm uy tín: <strong>{stats.trustScore} / 100</strong> • Hoa hồng:{' '}
                      <strong>{formatPercent(stats.commissionRate)}</strong>
                    </div>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '8px' }}>
                      Đơn hoàn thành tích lũy: <strong>{stats.completedOrders} đơn</strong>
                    </div>
                  </div>

                  {/* Tiến độ lên hạng tiếp theo */}
                  <div
                    style={{
                      padding: '16px',
                      borderRadius: '12px',
                      background: 'var(--bg-subtle, #f8fafc)',
                      border: '1px solid var(--border-subtle, #e2e8f0)',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                      <span style={{ fontWeight: 600, fontSize: '0.92rem' }}>
                        Mục tiêu tiếp theo: Hạng {stats.nextTierDisplayName} (Hoa hồng {formatPercent(stats.nextTierCommissionRate)})
                      </span>
                    </div>

                    {/* Progress 1: Số đơn hoàn thành */}
                    <div style={{ marginBottom: '12px' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.83rem', marginBottom: '4px' }}>
                        <span>Đơn hoàn thành: {stats.completedOrders} / {stats.nextTierMinOrders}</span>
                        <span>
                          {stats.ordersNeededForNextTier > 0
                            ? `Còn thiếu ${stats.ordersNeededForNextTier} đơn`
                            : 'Đã đạt chuẩn'}
                        </span>
                      </div>
                      <div style={{ height: '8px', background: '#e2e8f0', borderRadius: '999px', overflow: 'hidden' }}>
                        <div
                          style={{
                            width: `${stats.ordersProgressPercent}%`,
                            height: '100%',
                            background: '#16a34a',
                            transition: 'width 0.3s ease',
                          }}
                        />
                      </div>
                    </div>

                    {/* Progress 2: Điểm uy tín */}
                    <div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.83rem', marginBottom: '4px' }}>
                        <span>Điểm uy tín: {stats.trustScore} / {stats.nextTierMinScore}</span>
                        <span>
                          {stats.scoreNeededForNextTier > 0
                            ? `Cần thêm +${stats.scoreNeededForNextTier} điểm`
                            : 'Đã đạt chuẩn'}
                        </span>
                      </div>
                      <div style={{ height: '8px', background: '#e2e8f0', borderRadius: '999px', overflow: 'hidden' }}>
                        <div
                          style={{
                            width: `${stats.scoreProgressPercent}%`,
                            height: '100%',
                            background: '#2563eb',
                            transition: 'width 0.3s ease',
                          }}
                        />
                      </div>
                    </div>
                  </div>

                  {/* 4 Chỉ số hiệu suất */}
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '1fr 1fr',
                      gap: '10px',
                    }}
                  >
                    <div
                      style={{
                        padding: '12px',
                        borderRadius: '10px',
                        background: 'var(--bg-subtle, #f8fafc)',
                        border: '1px solid var(--border-subtle, #e2e8f0)',
                      }}
                    >
                      <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Tỷ lệ hoàn thành</div>
                      <div style={{ fontSize: '1.2rem', fontWeight: 700, color: '#16a34a', marginTop: '4px' }}>
                        <CheckCircle2 size={15} style={{ verticalAlign: 'middle', marginRight: '4px' }} />
                        {stats.completionRate}%
                      </div>
                    </div>
                    <div
                      style={{
                        padding: '12px',
                        borderRadius: '10px',
                        background: 'var(--bg-subtle, #f8fafc)',
                        border: '1px solid var(--border-subtle, #e2e8f0)',
                      }}
                    >
                      <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Giao đúng hạn</div>
                      <div style={{ fontSize: '1.2rem', fontWeight: 700, color: '#2563eb', marginTop: '4px' }}>
                        <Clock size={15} style={{ verticalAlign: 'middle', marginRight: '4px' }} />
                        {stats.onTimeRate}%
                      </div>
                    </div>
                    <div
                      style={{
                        padding: '12px',
                        borderRadius: '10px',
                        background: 'var(--bg-subtle, #f8fafc)',
                        border: '1px solid var(--border-subtle, #e2e8f0)',
                      }}
                    >
                      <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Đánh giá TB</div>
                      <div style={{ fontSize: '1.2rem', fontWeight: 700, color: '#d97706', marginTop: '4px' }}>
                        <Star size={15} style={{ verticalAlign: 'middle', marginRight: '4px' }} />
                        {stats.ratingAvg} / 5.0
                      </div>
                    </div>
                    <div
                      style={{
                        padding: '12px',
                        borderRadius: '10px',
                        background: 'var(--bg-subtle, #f8fafc)',
                        border: '1px solid var(--border-subtle, #e2e8f0)',
                      }}
                    >
                      <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Tỷ lệ khiếu nại</div>
                      <div style={{ fontSize: '1.2rem', fontWeight: 700, marginTop: '4px' }}>
                        <ShieldCheck size={15} style={{ verticalAlign: 'middle', marginRight: '4px' }} />
                        {stats.complaintRate}%
                      </div>
                    </div>
                  </div>
                </div>
              </SectionCard>
            </div>
          )}

          {/* Biểu đồ thu nhập theo thời gian */}
          {earnings && earnings.chartPoints.length > 0 && (
            <div style={{ marginBottom: '24px' }}>
              <SectionCard
                title="Biểu đồ biến động thu nhập theo ngày"
                description="Cột xanh lá thể hiện thu nhập thực nhận (ròng), phần cam thể hiện phí hoa hồng nền tảng."
              >
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'flex-end',
                    gap: '10px',
                    height: '190px',
                    padding: '16px 8px 8px 8px',
                    overflowX: 'auto',
                    borderBottom: '1px solid var(--border-subtle, #e2e8f0)',
                  }}
                >
                  {earnings.chartPoints.map((pt) => {
                    const heightPct = Math.max(4, Math.round((pt.grossAmount / maxChartGross) * 140));
                    const netPct = pt.grossAmount > 0 ? Math.round((pt.netAmount / pt.grossAmount) * 100) : 100;
                    return (
                      <div
                        key={pt.date}
                        title={`${pt.date}: ${pt.orderCount} đơn • Gộp: ${formatVND(pt.grossAmount)} • Ròng: ${formatVND(pt.netAmount)}`}
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          minWidth: '38px',
                          flex: 1,
                        }}
                      >
                        <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginBottom: '4px' }}>
                          {pt.orderCount > 0 ? `${pt.orderCount}đ` : ''}
                        </div>
                        <div
                          style={{
                            width: '22px',
                            height: `${heightPct}px`,
                            borderRadius: '6px 6px 0 0',
                            background: pt.grossAmount > 0 ? '#f59e0b' : '#e2e8f0',
                            display: 'flex',
                            flexDirection: 'column',
                            justifyContent: 'flex-end',
                            overflow: 'hidden',
                          }}
                        >
                          {pt.grossAmount > 0 && (
                            <div
                              style={{
                                width: '100%',
                                height: `${netPct}%`,
                                background: '#16a34a',
                              }}
                            />
                          )}
                        </div>
                        <div style={{ fontSize: '0.72rem', marginTop: '6px', color: 'var(--text-secondary)' }}>
                          {pt.label}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </SectionCard>
            </div>
          )}

          {/* Bảng Phiếu thu nhập từng đơn (BR-F-074) */}
          {earnings && (
            <SectionCard
              title={`Phiếu thu nhập chi tiết theo đơn (${earnings.receipts.length})`}
              description="Chi tiết tiền cước gộp, mức hoa hồng áp dụng và số tiền thực nhận vào ví sau mỗi đơn hoàn thành."
            >
              {earnings.receipts.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '36px 16px' }}>
                  <BarChart3 size={40} style={{ color: 'var(--text-muted)', marginBottom: '10px' }} />
                  <h3 style={{ margin: '0 0 8px 0' }}>Chưa có đơn hoàn thành trong kỳ này</h3>
                  <p style={{ color: 'var(--text-muted)', marginBottom: '16px' }}>
                    Hãy nhận đơn trên Sàn đơn hoặc ghép chuyến đi để bắt đầu tích lũy thu nhập và nâng hạng người giao.
                  </p>
                  <Link to="/courier/market" className="ui-button ui-button-primary">
                    <Package size={16} /> Khám phá Sàn đơn ngay
                  </Link>
                </div>
              ) : (
                <div className="table-responsive">
                  <table className="wallet-table">
                    <thead>
                      <tr>
                        <th>Mã đơn</th>
                        <th>Hàng hóa</th>
                        <th>Thời gian hoàn thành</th>
                        <th>Doanh thu gộp</th>
                        <th>Hoa hồng nền tảng</th>
                        <th>Thu nhập ròng</th>
                        <th>Trạng thái</th>
                      </tr>
                    </thead>
                    <tbody>
                      {earnings.receipts.map((item) => (
                        <tr key={item.orderId}>
                          <td style={{ fontWeight: 700 }}>{item.orderCode}</td>
                          <td>{item.productName}</td>
                          <td>{new Date(item.completedAt).toLocaleString('vi-VN')}</td>
                          <td>{formatVND(item.grossAmount)}</td>
                          <td style={{ color: '#d97706' }}>
                            -{formatVND(item.commissionAmount)} ({formatPercent(item.commissionRate)})
                          </td>
                          <td className="amount-positive" style={{ fontWeight: 700 }}>
                            +{formatVND(item.netAmount)}
                          </td>
                          <td>
                            <Badge tone="success">Đã quyết toán</Badge>
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
