import React, { useEffect, useState, useCallback, useRef } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import {
  ArrowLeft,
  CheckCircle2,
  Clock,
  MapPin,
  Package,
  QrCode,
  RefreshCw,
  ShieldCheck,
  AlertTriangle,
  Camera,
  User,
  Check,
  X,
  Copy,
  ClipboardPaste,
  ArrowRight
} from 'lucide-react';

import { orderApi } from '../api/orderApi';
import { relayApi } from '../api/relayApi';
import { ApiError, getApiError } from '../api/ApiError';
import type { Order } from '../types/order';
import {
  type DetailedOrderLeg,
  type HandoverQrData,
  type RelayMeetingPoint,
  type RelayHandoverResult,
  isLegAssignedToUser
} from '../types/relay';
import { useAuthentication } from '../auth/useAuthentication';

import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ApiErrorNotice from '../components/ApiErrorNotice';
import Modal from '../components/ui/Modal';

import '../styles/relay-handover.css';

const numberFormat = new Intl.NumberFormat('vi-VN');

export default function RelayHandoverPage(): React.ReactElement {
  const { orderId, legNo: legNoParam } = useParams<{ orderId: string; legNo?: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user } = useAuthentication();

  const [order, setOrder] = useState<Order | null>(null);
  const [legs, setLegs] = useState<DetailedOrderLeg[]>([]);
  const [meetingPoints, setMeetingPoints] = useState<RelayMeetingPoint[]>([]);
  const [activeLegNo, setActiveLegNo] = useState<number>(legNoParam ? parseInt(legNoParam, 10) : 1);

  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<ApiError | null>(null);

  // Tab mode: 'give' (tài xế giao hàng chặng trước) hoặc 'receive' (tài xế nhận hàng chặng sau)
  const [mode, setMode] = useState<'give' | 'receive'>('give');

  // Dữ liệu mã QR bàn giao (cho tab 'give')
  const [qrData, setQrData] = useState<HandoverQrData | null>(null);
  const [qrLoading, setQrLoading] = useState<boolean>(false);
  const [qrError, setQrError] = useState<ApiError | null>(null);
  const [secondsLeft, setSecondsLeft] = useState<number>(0);
  const [copyFeedback, setCopyFeedback] = useState<string | null>(null);

  // Dữ liệu xác nhận bàn giao (cho tab 'receive')
  const [inputToken, setInputToken] = useState<string>('');
  const [selectedMeetingPoint, setSelectedMeetingPoint] = useState<string>('');
  const [handoverNote, setHandoverNote] = useState<string>('Kiện hàng nguyên vẹn, đã đối chiếu mã xác nhận');
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const [handoverResult, setHandoverResult] = useState<RelayHandoverResult | null>(null);

  // Nhận chặng vận chuyển
  const [claimingLeg, setClaimingLeg] = useState<boolean>(false);

  // Modal Báo sự cố bàn giao
  const [isIncidentModalOpen, setIsIncidentModalOpen] = useState<boolean>(false);
  const [incidentType, setIncidentType] = useState<string>('PARTNER_ABSENT');
  const [incidentDescription, setIncidentDescription] = useState<string>('');
  const [incidentSubmitted, setIncidentSubmitted] = useState<boolean>(false);

  const fileInputRef = useRef<HTMLInputElement>(null);

  // 1. Tự động nhận diện token hoặc OTP từ URL query param khi quét QR bằng điện thoại
  useEffect(() => {
    const urlToken = searchParams.get('token');
    const urlOtp = searchParams.get('otp');
    if (urlToken || urlOtp) {
      setInputToken(urlOtp || urlToken || '');
      setMode('receive');
    }
  }, [searchParams]);

  // 2. Tải thông tin đơn hàng và danh sách chặng
  const loadData = useCallback(async () => {
    if (!orderId) return;
    setLoading(true);
    setError(null);
    try {
      const [orderRes, legsRes, pointsRes] = await Promise.all([
        orderApi.getById(orderId),
        relayApi.getOrderLegsDetailed(orderId),
        relayApi.getSafeMeetingPoints(),
      ]);

      const orderData = orderRes.data;
      const legsData = legsRes.data || [];
      setOrder(orderData);
      setLegs(legsData);
      setMeetingPoints(pointsRes.data || []);

      // Xác định chặng hiện tại cần thao tác nếu chưa chọn
      if (!legNoParam && legsData.length > 0) {
        const pendingLeg = legsData.find(l => l.status !== 'COMPLETED');
        if (pendingLeg) {
          setActiveLegNo(pendingLeg.legNo);
        } else {
          setActiveLegNo(legsData[0].legNo);
        }
      }
    } catch (err) {
      setError(getApiError(err));
    } finally {
      setLoading(false);
    }
  }, [orderId, legNoParam]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  // Các đối tượng chặng
  const currentLeg = legs.find(l => l.legNo === activeLegNo);
  const nextLeg = legs.find(l => l.legNo === activeLegNo + 1);
  const isLastLeg = activeLegNo >= legs.length;

  const isCurrentLegCourier = Boolean(
    isLegAssignedToUser(currentLeg, user) || user?.roles?.includes('ADMIN')
  );
  const isNextLegCourier = Boolean(
    isLegAssignedToUser(nextLeg, user)
  );
  const isCurrentLegUnassigned = !currentLeg?.courierId && !currentLeg?.courierName;
  const isNextLegUnassigned = Boolean(nextLeg && !nextLeg.courierId && !nextLeg.courierName);

  // Tự động chuyển mode phù hợp với quyền hạn của tài xế đang đăng nhập
  useEffect(() => {
    if (currentLeg) {
      if (isCurrentLegCourier) {
        // Tài xế chặng 1 -> Mặc định vào tab Tôi bàn giao
        setMode('give');
      } else if (isNextLegCourier || isCurrentLegUnassigned === false) {
        // Tài xế chặng 2 hoặc người khác -> Mặc định vào tab Tôi tiếp nhận
        setMode('receive');
      }
    }
  }, [currentLeg?.courierId, isCurrentLegCourier, isNextLegCourier, isCurrentLegUnassigned]);

  // 3. Tự động sinh mã QR bàn giao khi ở tab 'give' và ĐÚNG là tài xế phụ trách chặng hiện tại
  const loadHandoverQr = useCallback(async () => {
    if (!orderId || !activeLegNo) return;
    setQrLoading(true);
    setQrError(null);
    try {
      const res = await relayApi.generateHandoverQr(orderId, activeLegNo);
      setQrData(res.data);
      setSecondsLeft(res.data.remainingSeconds || 600);
      if (res.data.meetingPoint) {
        setSelectedMeetingPoint(res.data.meetingPoint);
      }
    } catch (err) {
      setQrError(getApiError(err));
    } finally {
      setQrLoading(false);
    }
  }, [orderId, activeLegNo]);

  useEffect(() => {
    if (mode === 'give' && orderId && activeLegNo) {
      if (isCurrentLegCourier || user?.roles?.includes('ADMIN')) {
        void loadHandoverQr();
      }
    }
  }, [mode, orderId, activeLegNo, isCurrentLegCourier, user?.roles, loadHandoverQr]);

  // 4. Đếm ngược thời gian mã QR (10 phút theo PRM-097)
  useEffect(() => {
    if (!qrData || secondsLeft <= 0) return;
    const interval = setInterval(() => {
      setSecondsLeft(prev => {
        if (prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [qrData, secondsLeft]);

  // Format thời gian mm:ss
  const formatTime = (totalSecs: number) => {
    const mins = Math.floor(totalSecs / 60);
    const secs = totalSecs % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  // 5. Xử lý nhận chặng tiếp sức
  const handleClaimLeg = async (legNoToClaim: number) => {
    if (!orderId) return;
    setClaimingLeg(true);
    setError(null);
    try {
      await relayApi.acceptLeg(orderId, legNoToClaim);
      await loadData();
      setActiveLegNo(legNoToClaim);
      setCopyFeedback(`Bạn đã tiếp nhận phụ trách Chặng ${legNoToClaim} thành công!`);
      setTimeout(() => setCopyFeedback(null), 4000);
    } catch (err) {
      setError(getApiError(err));
    } finally {
      setClaimingLeg(false);
    }
  };

  // 6. Xử lý ảnh chụp bằng chứng bàn giao
  const handlePhotoSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      setPhotoFile(file);
      setPhotoPreview(URL.createObjectURL(file));
      setSubmitError(null);
    }
  };

  const handleRemovePhoto = () => {
    setPhotoFile(null);
    if (photoPreview) {
      URL.revokeObjectURL(photoPreview);
      setPhotoPreview(null);
    }
  };

  // 7. Xác nhận bàn giao kiện hàng (Tab 'receive')
  const handleConfirmHandover = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!orderId) return;

    if (!inputToken.trim()) {
      setSubmitError(new ApiError('Vui lòng quét mã QR hoặc nhập mã số 6 chữ số do tài xế chặng trước cung cấp.'));
      return;
    }

    if (!photoFile) {
      setSubmitError(new ApiError('Bắt buộc chụp ảnh kiện hàng tại điểm hẹn để làm bằng chứng chuyển giao trách nhiệm.'));
      return;
    }

    setSubmitting(true);
    setSubmitError(null);

    try {
      const formData = new FormData();
      formData.append('qrToken', inputToken.trim());
      formData.append('photo', photoFile);
      if (selectedMeetingPoint) {
        formData.append('meetingPoint', selectedMeetingPoint);
      }
      if (handoverNote) {
        formData.append('note', handoverNote.trim());
      }

      const res = await relayApi.confirmHandover(orderId, activeLegNo, formData);
      setHandoverResult(res.data);
      if (res.data.legs) {
        setLegs(res.data.legs);
      } else {
        void loadData();
      }
    } catch (err) {
      setSubmitError(getApiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  // Sao chép clipboard
  const handleCopyText = (text: string, label: string) => {
    void navigator.clipboard.writeText(text);
    setCopyFeedback(`Đã sao chép ${label}!`);
    setTimeout(() => setCopyFeedback(null), 3000);
  };

  // Dán clipboard vào ô mã
  const handlePasteClipboard = async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (text) {
        setInputToken(text.trim());
        setCopyFeedback('Đã dán mã bàn giao!');
        setTimeout(() => setCopyFeedback(null), 3000);
      }
    } catch {
      // Clipboard access denied
    }
  };

  // 8. Xử lý báo sự cố tại điểm hẹn
  const handleSubmitIncident = () => {
    setIncidentSubmitted(true);
    setTimeout(() => {
      setIsIncidentModalOpen(false);
      setIncidentSubmitted(false);
      setIncidentDescription('');
    }, 2000);
  };

  if (loading) {
    return (
      <div className="relay-handover-container" style={{ textAlign: 'center', padding: '60px 0' }}>
        <LoadingSpinner />
        <p style={{ marginTop: '16px', color: 'var(--text-secondary)' }}>Đang tải thông tin lộ trình chặng tiếp sức...</p>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="relay-handover-container">
        <PageHeader
          title="Bàn giao chặng tiếp sức"
          description="Tiếp sức vận chuyển hàng hóa qua các điểm hẹn an toàn"
          actions={
            <Button variant="secondary" icon={<ArrowLeft size={16} />} onClick={() => navigate(-1)}>
              Quay lại
            </Button>
          }
        />
        <ApiErrorNotice error={error || new ApiError('Đơn hàng không tồn tại hoặc bạn không có quyền truy cập.')} />
      </div>
    );
  }

  // URL nhúng vào mã QR để điện thoại Shipper 2 quét mở trực tiếp
  const directHandoverUrl = qrData
    ? `${window.location.origin}/courier/orders/${order.id}/legs/${activeLegNo}/handover?token=${encodeURIComponent(qrData.qrToken)}&otp=${qrData.otpCode}`
    : '';

  return (
    <div className="relay-handover-container">
      {/* Hero Banner Header */}
      <div className="relay-hero-card">
        <div className="relay-hero-header">
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
              <Button
                variant="ghost"
                icon={<ArrowLeft size={18} />}
                onClick={() => navigate('/courier/orders')}
                aria-label="Quay lại danh sách đơn"
              >
                Danh sách đơn
              </Button>
              <div className="relay-hero-badge">
                <ShieldCheck size={16} />
                Bàn giao tiếp sức an toàn
              </div>
            </div>
            <h1 className="relay-hero-title">
              Bàn giao chặng tiếp sức: {order.orderCode}
            </h1>
            <p className="relay-hero-desc">
              Phân chia lộ trình đa chặng. Hai tài xế gặp mặt tại điểm hẹn an toàn đã kiểm duyệt, xuất trình mã bảo mật và lưu ảnh kiểm tra ngoại quan kiện hàng để chuyển giao nghĩa vụ vận chuyển.
            </p>
          </div>

          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <Button
              variant="secondary"
              icon={<AlertTriangle size={16} />}
              onClick={() => setIsIncidentModalOpen(true)}
              style={{ color: 'var(--accent-amber)', borderColor: 'rgba(245, 203, 118, 0.4)' }}
            >
              Báo sự cố tại điểm hẹn
            </Button>
            <Button
              variant="secondary"
              icon={<RefreshCw size={16} />}
              onClick={() => void loadData()}
            >
              Cập nhật
            </Button>
          </div>
        </div>

        {/* Thông tin đơn hàng tóm tắt */}
        <div className="relay-order-strip">
          <div className="relay-strip-item">
            <span className="relay-strip-label">Kiện hàng</span>
            <span className="relay-strip-value">{order.productName} ({order.quantity || 1} kiện)</span>
          </div>
          <div className="relay-strip-item">
            <span className="relay-strip-label">Khối lượng</span>
            <span className="relay-strip-value">{order.weightKg} kg</span>
          </div>
          <div className="relay-strip-item">
            <span className="relay-strip-label">Tổng cước phí</span>
            <span className="relay-strip-value" style={{ color: 'var(--accent-emerald)' }}>
              {numberFormat.format(order.priceAgreed || order.priceSuggested || 0)} đ
            </span>
          </div>
          <div className="relay-strip-item">
            <span className="relay-strip-label">Số chặng phân bổ</span>
            <span className="relay-strip-value">{legs.length} chặng tiếp sức</span>
          </div>
        </div>
      </div>

      {copyFeedback && (
        <div style={{ padding: '12px 16px', background: 'rgba(108, 222, 180, 0.15)', border: '1px solid var(--accent-emerald)', borderRadius: 'var(--radius-md)', color: 'var(--accent-emerald)', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <CheckCircle2 size={18} />
          <span>{copyFeedback}</span>
        </div>
      )}

      {/* Thông báo kết quả bàn giao thành công */}
      {handoverResult && (
        <div className="relay-success-card">
          <div className="relay-success-icon">
            <CheckCircle2 size={36} />
          </div>
          <h2 style={{ fontSize: '1.375rem', fontWeight: 700, margin: '0 0 8px', color: 'var(--accent-emerald)' }}>
            Xác nhận bàn giao kiện hàng thành công!
          </h2>
          <p style={{ color: 'var(--text-secondary)', maxWidth: '600px', margin: '0 auto 16px' }}>
            {handoverResult.message}
          </p>
          <div style={{ display: 'inline-flex', gap: '12px' }}>
            <Button variant="primary" onClick={() => setHandoverResult(null)}>
              Tiếp tục theo dõi lộ trình
            </Button>
            <Button variant="secondary" onClick={() => navigate('/courier/orders')}>
              Về danh sách đơn nhận
            </Button>
          </div>
        </div>
      )}

      {/* Tiến độ các chặng (Multi-leg Timeline) */}
      <SectionCard
        title="Lộ trình các chặng vận chuyển"
        description="Mỗi tài xế đảm nhiệm vận chuyển an toàn trên một đoạn đường và bàn giao tại điểm hẹn công cộng."
      >
        <div className="relay-legs-stepper">
          {legs.map((leg) => {
            const isCompleted = leg.status === 'COMPLETED';
            const isActive = leg.legNo === activeLegNo;
            const isMyLeg = isLegAssignedToUser(leg, user);
            const isUnassigned = !leg.courierId && !leg.courierName;

            return (
              <div
                key={leg.id}
                className={`relay-leg-step-card ${isCompleted ? 'completed' : ''} ${isActive ? 'active' : ''}`}
                onClick={() => setActiveLegNo(leg.legNo)}
                style={{ cursor: 'pointer' }}
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
                          background: isActive ? 'var(--primary)' : 'var(--bg-tertiary)',
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
                      ? 'Bạn phụ trách'
                      : isUnassigned
                      ? 'Chưa có tài xế'
                      : 'Tài xế khác'}
                  </span>
                </div>

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

                <div className="relay-leg-meta">
                  <span>{leg.distanceKm} km</span>
                  <span className="relay-leg-fee">{numberFormat.format(leg.legFee)} đ</span>
                </div>

                {leg.courierName && (
                  <div style={{ fontSize: '0.813rem', color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <User size={14} style={{ color: 'var(--text-muted)' }} />
                    <span>Tài xế: <strong>{leg.courierName}</strong></span>
                    {isMyLeg && <span style={{ color: 'var(--primary)', fontWeight: 600 }}>(Tôi)</span>}
                  </div>
                )}

                {isUnassigned && !isCompleted && (
                  <Button
                    variant="primary"
                    size="sm"
                    loading={claimingLeg}
                    onClick={(e) => {
                      e.stopPropagation();
                      void handleClaimLeg(leg.legNo);
                    }}
                    style={{ marginTop: '8px' }}
                  >
                    Nhận chặng này ({numberFormat.format(leg.legFee)} đ)
                  </Button>
                )}

                {leg.handoverPhotoUrl && (
                  <div style={{ marginTop: '4px', fontSize: '0.75rem', color: 'var(--accent-emerald)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <Check size={14} /> Có ảnh bằng chứng bàn giao
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </SectionCard>

      {/* Khu vực thao tác chính (Hai cột tương tác) */}
      <div className="relay-workspace-grid">
        {/* Cột trái: Thao tác Chuyển giao hoặc Tiếp nhận */}
        <SectionCard
          title={`Thao tác chặng ${activeLegNo} ${nextLeg ? `→ Chặng ${nextLeg.legNo}` : '(Chặng cuối)'}`}
          description={
            isLastLeg
              ? 'Đây là chặng giao hàng cuối cùng. Kiện hàng sẽ được giao trực tiếp cho người nhận cuối cùng.'
              : 'Chọn vai trò của bạn để xuất trình mã QR hoặc xác nhận tiếp nhận kiện hàng.'
          }
        >
          {isLastLeg ? (
            <div style={{ padding: '24px', textAlign: 'center', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)' }}>
              <Package size={40} style={{ color: 'var(--primary)', margin: '0 auto 12px' }} />
              <h3 style={{ fontSize: '1.125rem', fontWeight: 600, marginBottom: '8px' }}>Chặng vận chuyển về đích</h3>
              <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', lineHeight: 1.5 }}>
                Chặng {activeLegNo} là chặng cuối cùng đến địa chỉ người nhận <strong>{order.receiverName}</strong> ({order.receiverPhone}).
                Sau khi đến điểm giao, vui lòng sử dụng mã OTP giao hàng của người nhận để hoàn tất đơn.
              </p>
              <Button
                variant="primary"
                style={{ marginTop: '16px' }}
                onClick={() => navigate('/courier/orders')}
              >
                Quay lại đơn đang giao
              </Button>
            </div>
          ) : (
            <>
              {/* Tab chuyển đổi vai trò */}
              <div className="relay-mode-tabs">
                <button
                  type="button"
                  className={`relay-tab-button ${mode === 'give' ? 'active' : ''}`}
                  onClick={() => setMode('give')}
                >
                  <QrCode size={18} />
                  Tôi bàn giao (Xuất trình QR)
                </button>
                <button
                  type="button"
                  className={`relay-tab-button ${mode === 'receive' ? 'active' : ''}`}
                  onClick={() => setMode('receive')}
                >
                  <Camera size={18} />
                  Tôi tiếp nhận (Quét & Chụp ảnh)
                </button>
              </div>

              {/* NỘI DUNG TAB 1: TÀI XẾ BÀN GIAO (XUẤT TRÌNH MÃ QR ĐỘNG) */}
              {mode === 'give' && (
                <div>
                  {/* Trường hợp 1: Chặng 1 chưa có ai nhận */}
                  {isCurrentLegUnassigned ? (
                    <div style={{ padding: '24px', textAlign: 'center', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)', border: '1px dashed var(--border-subtle)' }}>
                      <AlertTriangle size={36} style={{ color: 'var(--accent-amber)', margin: '0 auto 12px' }} />
                      <h4 style={{ fontSize: '1.05rem', fontWeight: 600, marginBottom: '8px' }}>
                        Chặng {activeLegNo} hiện chưa có tài xế phụ trách
                      </h4>
                      <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', maxWidth: '420px', margin: '0 auto 16px', lineHeight: 1.5 }}>
                        Để xuất trình mã QR bàn giao cho tài xế tiếp theo, bạn cần bấm tiếp nhận vận chuyển chặng này trước.
                      </p>
                      <Button
                        variant="primary"
                        loading={claimingLeg}
                        onClick={() => handleClaimLeg(activeLegNo)}
                      >
                        Nhận phụ trách Chặng {activeLegNo} ({numberFormat.format(currentLeg?.legFee || 0)} đ)
                      </Button>
                    </div>
                  ) : !isCurrentLegCourier ? (
                    /* Trường hợp 2: Tài khoản đang đăng nhập KHÔNG PHẢI tài xế chặng này */
                    <div style={{ padding: '24px', textAlign: 'center', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
                      <User size={36} style={{ color: 'var(--primary)', margin: '0 auto 12px' }} />
                      <h4 style={{ fontSize: '1.05rem', fontWeight: 600, marginBottom: '8px' }}>
                        Chặng {activeLegNo} do tài xế {currentLeg?.courierName || 'Shipper 1'} phụ trách
                      </h4>
                      <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', maxWidth: '460px', margin: '0 auto 16px', lineHeight: 1.5 }}>
                        Tài xế chặng {activeLegNo} là người có trách nhiệm xuất trình mã QR và đọc mã 6 chữ số tại điểm hẹn.
                        Nếu bạn là tài xế tiếp nhận chặng {nextLeg?.legNo}, vui lòng chuyển sang tab <strong>Tôi tiếp nhận</strong> để quét mã hoặc nhập mã số.
                      </p>
                      <div style={{ display: 'flex', justifyContent: 'center', gap: '12px', flexWrap: 'wrap' }}>
                        <Button
                          variant="primary"
                          icon={<ArrowRight size={16} />}
                          onClick={() => setMode('receive')}
                        >
                          Chuyển sang "Tôi tiếp nhận (Quét & Chụp ảnh)"
                        </Button>
                        <Button
                          variant="secondary"
                          icon={<QrCode size={16} />}
                          onClick={() => void loadHandoverQr()}
                        >
                          Xuất trình mã QR bàn giao
                        </Button>
                      </div>
                    </div>
                  ) : (
                    /* Trường hợp 3: ĐÚNG TÀI XẾ CHẶNG NÀY -> HIỂN THỊ MÃ QR ĐỘNG */
                    <div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                        <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                          Mã xác nhận bàn giao cho: <strong>Chặng {activeLegNo + 1}</strong>
                        </span>
                        <Button
                          variant="ghost"
                          size="sm"
                          icon={<RefreshCw size={14} className={qrLoading ? 'animate-spin' : ''} />}
                          onClick={() => void loadHandoverQr()}
                          disabled={qrLoading}
                        >
                          Làm mới mã
                        </Button>
                      </div>

                      {qrError && <ApiErrorNotice error={qrError} />}

                      {qrLoading ? (
                        <div style={{ padding: '48px 0', textAlign: 'center' }}>
                          <LoadingSpinner />
                          <p style={{ marginTop: '12px', color: 'var(--text-secondary)' }}>Đang sinh mã bàn giao có chữ ký bảo mật...</p>
                        </div>
                      ) : qrData ? (
                        <div style={{ textAlign: 'center' }}>
                          {/* Thẻ QR trắng hiển thị đẹp rõ */}
                          <div className="relay-qr-box">
                            <div className="relay-qr-wrapper">
                              <QRCodeSVG
                                value={directHandoverUrl || qrData.qrToken}
                                size={200}
                                level="M"
                                includeMargin={false}
                              />
                            </div>
                            <span style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '8px' }}>
                              Quét bằng camera điện thoại để mở thẳng trang tiếp nhận
                            </span>
                          </div>

                          {/* Đếm ngược 10 phút */}
                          <div className="relay-timer-wrapper" style={{ maxWidth: '340px', margin: '0 auto' }}>
                            <div className="relay-timer-bar-bg">
                              <div
                                className="relay-timer-bar-fill"
                                style={{ width: `${Math.min(100, Math.max(0, (secondsLeft / 600) * 100))}%` }}
                              />
                            </div>
                            <div className="relay-timer-text">
                              <span>Thời hạn hiệu lực mã (10 phút):</span>
                              <span className="relay-timer-value" style={{ color: secondsLeft < 60 ? '#ef4444' : 'var(--accent-cyan)' }}>
                                {formatTime(secondsLeft)}
                              </span>
                            </div>
                          </div>

                          {secondsLeft === 0 && (
                            <div style={{ marginTop: '12px', color: '#ef4444', fontSize: '0.875rem' }}>
                              Mã QR đã hết hạn. Vui lòng bấm <strong>Làm mới mã</strong> để tạo mã mới cho tài xế kế tiếp.
                            </div>
                          )}

                          {/* Mã OTP số dự phòng */}
                          <div className="relay-qr-otp-box">
                            <small style={{ color: 'var(--text-muted)' }}>Mã số bàn giao 6 chữ số (đọc cho đồng nghiệp nếu không quét QR):</small>
                            <div className="relay-qr-otp-code">{qrData.otpCode}</div>
                            <div style={{ display: 'flex', justifyContent: 'center', gap: '10px', marginTop: '8px' }}>
                              <Button
                                variant="secondary"
                                size="sm"
                                icon={<Copy size={13} />}
                                onClick={() => handleCopyText(qrData.otpCode, 'mã 6 số')}
                              >
                                Sao chép mã
                              </Button>
                              <Button
                                variant="secondary"
                                size="sm"
                                icon={<Copy size={13} />}
                                onClick={() => handleCopyText(directHandoverUrl, 'liên kết bàn giao')}
                              >
                                Sao chép liên kết
                              </Button>
                            </div>
                          </div>

                          {/* Quy trình hướng dẫn ngắn gọn */}
                          <div className="relay-guide-steps" style={{ textAlign: 'left', marginTop: '20px' }}>
                            <div className="relay-guide-step-item">
                              <span className="relay-guide-step-badge">1</span>
                              <span>Gặp tài xế kế tiếp tại điểm hẹn an toàn đã thỏa thuận.</span>
                            </div>
                            <div className="relay-guide-step-item">
                              <span className="relay-guide-step-badge">2</span>
                              <span>Đưa mã QR trên màn hình này để tài xế kế tiếp quét bằng camera hoặc đọc mã số 6 chữ số.</span>
                            </div>
                            <div className="relay-guide-step-item">
                              <span className="relay-guide-step-badge">3</span>
                              <span>Để tài xế kế tiếp chụp ảnh kiểm tra kiện hàng. Khi đối phương xác nhận, trách nhiệm chặng của bạn sẽ tự động hoàn tất.</span>
                            </div>
                          </div>
                        </div>
                      ) : null}
                    </div>
                  )}
                </div>
              )}

              {/* NỘI DUNG TAB 2: TÀI XẾ TIẾP NHẬN (QUÉT QR / NHẬP MÃ & CHỤP ẢNH) */}
              {mode === 'receive' && (
                <form onSubmit={handleConfirmHandover}>
                  {submitError && <ApiErrorNotice error={submitError} />}

                  {/* Thông báo tiếp nhận chặng tiếp theo */}
                  {isNextLegUnassigned && (
                    <div style={{ padding: '12px 14px', background: 'rgba(96, 165, 250, 0.12)', border: '1px solid var(--secondary)', borderRadius: 'var(--radius-sm)', marginBottom: '16px', fontSize: '0.875rem' }}>
                      <div style={{ fontWeight: 600, color: 'var(--secondary)', marginBottom: '4px' }}>
                        Chặng {nextLeg?.legNo} đang tìm tài xế
                      </div>
                      <span style={{ color: 'var(--text-secondary)' }}>
                        Khi bạn nhập mã bàn giao và chụp ảnh xác nhận từ tài xế chặng trước, bạn sẽ <strong>tự động nhận phụ trách Chặng {nextLeg?.legNo}</strong> và nhận toàn bộ cước phí <strong>{numberFormat.format(nextLeg?.legFee || 0)} đ</strong>!
                      </span>
                    </div>
                  )}

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    {/* Trường nhập mã QR Token hoặc OTP */}
                    <div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                        <label style={{ fontSize: '0.875rem', fontWeight: 600 }}>
                          Mã bàn giao (Mã 6 số hoặc QR Token) <span style={{ color: 'var(--primary)' }}>*</span>
                        </label>
                        <Button
                          variant="ghost"
                          size="sm"
                          icon={<ClipboardPaste size={14} />}
                          onClick={() => void handlePasteClipboard()}
                        >
                          Dán từ bộ nhớ tạm
                        </Button>
                      </div>

                      <input
                        type="text"
                        value={inputToken}
                        onChange={(e) => setInputToken(e.target.value)}
                        placeholder="Nhập mã 6 chữ số (ví dụ: 839201) hoặc dán mã QR Token..."
                        style={{
                          width: '100%',
                          padding: '12px 14px',
                          background: 'var(--bg-tertiary)',
                          border: '1px solid var(--border-subtle)',
                          borderRadius: 'var(--radius-sm)',
                          color: 'var(--text-primary)',
                          fontFamily: 'var(--font-mono)',
                          fontSize: '1rem',
                          fontWeight: 600,
                          letterSpacing: inputToken.length === 6 && /^\d+$/.test(inputToken) ? '4px' : 'normal',
                        }}
                      />
                      <small style={{ color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
                        Tài xế chặng trước xuất trình mã QR trên màn hình hoặc đọc mã 6 chữ số cho bạn.
                      </small>
                    </div>

                    {/* Điểm hẹn bàn giao */}
                    <div>
                      <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}>
                        Điểm hẹn bàn giao an toàn
                      </label>
                      <input
                        type="text"
                        value={selectedMeetingPoint}
                        onChange={(e) => setSelectedMeetingPoint(e.target.value)}
                        placeholder="Ví dụ: Cổng chính Đại học FPT, Thư viện Alpha..."
                        style={{
                          width: '100%',
                          padding: '10px 14px',
                          background: 'var(--bg-tertiary)',
                          border: '1px solid var(--border-subtle)',
                          borderRadius: 'var(--radius-sm)',
                          color: 'var(--text-primary)',
                          fontSize: '0.875rem',
                        }}
                      />
                      <small style={{ color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
                        Chọn nhanh từ danh sách điểm hẹn an toàn ở cột bên phải.
                      </small>
                    </div>

                    {/* Bắt buộc chụp ảnh kiện hàng lúc nhận */}
                    <div>
                      <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}>
                        Ảnh chụp kiểm tra kiện hàng tại điểm hẹn <span style={{ color: 'var(--primary)' }}>*</span>
                      </label>

                      {photoPreview ? (
                        <div className="relay-photo-preview-box">
                          <img src={photoPreview} alt="Bằng chứng bàn giao" className="relay-photo-preview-img" />
                          <button
                            type="button"
                            className="relay-photo-remove-btn"
                            onClick={handleRemovePhoto}
                            aria-label="Xóa ảnh và chụp lại"
                          >
                            <X size={16} />
                          </button>
                        </div>
                      ) : (
                        <div
                          className="relay-photo-dropzone"
                          onClick={() => fileInputRef.current?.click()}
                        >
                          <Camera size={32} style={{ color: 'var(--primary)' }} />
                          <div style={{ fontWeight: 600, fontSize: '0.938rem', color: 'var(--text-primary)' }}>
                            Chụp ảnh hoặc chọn ảnh kiện hàng tại điểm hẹn
                          </div>
                          <span style={{ fontSize: '0.813rem', color: 'var(--text-muted)' }}>
                            Chụp rõ ngoại quan kiện hàng, tem niêm phong để xác thực trách nhiệm và bảo vệ quyền lợi của bạn.
                          </span>
                        </div>
                      )}

                      <input
                        ref={fileInputRef}
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        capture="environment"
                        onChange={handlePhotoSelect}
                        style={{ display: 'none' }}
                      />
                    </div>

                    {/* Ghi chú tình trạng hàng */}
                    <div>
                      <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}>
                        Ghi chú tình trạng kiện hàng
                      </label>
                      <textarea
                        rows={2}
                        value={handoverNote}
                        onChange={(e) => setHandoverNote(e.target.value)}
                        placeholder="Mô tả hiện trạng: kiện hàng nguyên vẹn, bao bì không rách móp..."
                        style={{
                          width: '100%',
                          padding: '10px 14px',
                          background: 'var(--bg-tertiary)',
                          border: '1px solid var(--border-subtle)',
                          borderRadius: 'var(--radius-sm)',
                          color: 'var(--text-primary)',
                          fontSize: '0.875rem',
                          resize: 'vertical',
                        }}
                      />
                    </div>

                    {/* Nút submit xác nhận tiếp nhận */}
                    <Button
                      type="submit"
                      variant="primary"
                      disabled={submitting}
                      style={{ padding: '14px', fontSize: '1rem', fontWeight: 700, width: '100%' }}
                    >
                      {submitting ? 'Đang xác thực & chuyển giao...' : 'Xác nhận tiếp nhận kiện hàng'}
                    </Button>
                  </div>
                </form>
              )}
            </>
          )}
        </SectionCard>

        {/* Cột phải: Điểm hẹn an toàn được gợi ý & Hướng dẫn an ninh */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
          {/* Danh mục Điểm hẹn an toàn */}
          <SectionCard
            title="Điểm hẹn bàn giao an toàn"
            description="Các địa điểm công cộng đã được xác minh quanh Campus FPT có bảo vệ 24/7 và hệ thống camera giám sát."
          >
            <div className="relay-meeting-points-grid">
              {meetingPoints.map((point) => (
                <div
                  key={point.id}
                  className={`relay-point-card ${selectedMeetingPoint === point.name ? 'selected' : ''}`}
                  onClick={() => setSelectedMeetingPoint(point.name)}
                >
                  <div className="relay-point-name">
                    <ShieldCheck size={16} style={{ color: 'var(--accent-emerald)' }} />
                    {point.name}
                  </div>
                  <div className="relay-point-addr">{point.address}</div>
                  <div className="relay-point-sec">
                    <Check size={12} />
                    {point.securityFeatures}
                  </div>
                </div>
              ))}
            </div>
          </SectionCard>

          {/* Quy tắc bảo vệ trách nhiệm */}
          <SectionCard
            title="Quy tắc bảo vệ trách nhiệm vận chuyển"
            description="Nguyên tắc phân định rủi ro và chuyển giao nghĩa vụ bảo quản hàng hóa."
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', fontSize: '0.875rem', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
                <ShieldCheck size={18} style={{ color: 'var(--primary)', flexShrink: 0, marginTop: '2px' }} />
                <span>
                  <strong>Trách nhiệm theo từng chặng:</strong> Mỗi tài xế chịu trách nhiệm toàn vẹn hàng hóa trong suốt chặng của mình cho đến khi tài xế kế tiếp hoàn tất quét mã bàn giao và chụp ảnh xác nhận.
                </span>
              </div>
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
                <Clock size={18} style={{ color: 'var(--accent-cyan)', flexShrink: 0, marginTop: '2px' }} />
                <span>
                  <strong>Mã QR tự hủy sau 10 phút:</strong> Để bảo đảm an toàn giao dịch tại chỗ, mã QR bàn giao chỉ có giá trị trong vòng 10 phút. Nếu quá hạn, tài xế bàn giao cần bấm làm mới.
                </span>
              </div>
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
                <Camera size={18} style={{ color: 'var(--accent-emerald)', flexShrink: 0, marginTop: '2px' }} />
                <span>
                  <strong>Bằng chứng ảnh thực tế:</strong> Ảnh chụp ngoại quan kiện hàng tại điểm hẹn là căn cứ duy nhất giải quyết khiếu nại nếu xảy ra tranh chấp hoặc hư hao.
                </span>
              </div>
            </div>
          </SectionCard>
        </div>
      </div>

      {/* Modal Báo sự cố tại điểm hẹn */}
      <Modal
        isOpen={isIncidentModalOpen}
        onClose={() => setIsIncidentModalOpen(false)}
        title="Báo sự cố tại điểm hẹn bàn giao"
      >
        {incidentSubmitted ? (
          <div style={{ textAlign: 'center', padding: '24px 0' }}>
            <CheckCircle2 size={48} style={{ color: 'var(--accent-emerald)', margin: '0 auto 12px' }} />
            <h4 style={{ fontSize: '1.125rem', fontWeight: 600 }}>Đã ghi nhận sự cố bàn giao</h4>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
              Hệ thống đã thông báo cho bộ phận hỗ trợ và các bên liên quan để kịp thời phối hợp xử lý.
            </p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}>
                Loại sự cố gặp phải
              </label>
              <select
                value={incidentType}
                onChange={(e) => setIncidentType(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 12px',
                  background: 'var(--bg-tertiary)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-sm)',
                  color: 'var(--text-primary)',
                  fontSize: '0.875rem',
                }}
              >
                <option value="PARTNER_ABSENT">Tài xế chặng trước / kế tiếp không đến điểm hẹn</option>
                <option value="DAMAGED_PACKAGE">Kiện hàng bị hư hỏng / móp méo / rách niêm phong</option>
                <option value="WRONG_PACKAGE_INFO">Sai lệch thông tin kiện hàng hoặc không khớp mã</option>
                <option value="WEATHER_OR_LOCATION">Điểm hẹn bị cản trở thời tiết / không thể tiếp cận</option>
                <option value="OTHER">Lý do khác</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}>
                Mô tả chi tiết sự cố
              </label>
              <textarea
                rows={3}
                value={incidentDescription}
                onChange={(e) => setIncidentDescription(e.target.value)}
                placeholder="Nhập chi tiết tình huống tại điểm hẹn để bộ phận hỗ trợ xác minh..."
                style={{
                  width: '100%',
                  padding: '10px 12px',
                  background: 'var(--bg-tertiary)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-sm)',
                  color: 'var(--text-primary)',
                  fontSize: '0.875rem',
                  resize: 'vertical',
                }}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
              <Button variant="ghost" onClick={() => setIsIncidentModalOpen(false)}>
                Hủy
              </Button>
              <Button
                variant="primary"
                onClick={handleSubmitIncident}
                disabled={!incidentDescription.trim()}
              >
                Gửi báo cáo sự cố
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
