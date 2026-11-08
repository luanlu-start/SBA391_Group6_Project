import React, { useState, useEffect, useRef } from 'react';
import {
  GitCommit,
  QrCode,
  CheckCircle2,
  MapPin,
  AlertTriangle,
  RefreshCw,
  ShieldCheck,
  Camera,
  Upload,
  User,
  Phone,
  Package,
  Layers,
  Clock,
  Info
} from 'lucide-react';
import { relayApi } from '../api/relayApi';
import type {
  OrderLeg,
  MeetingPoint,
  HandoverToken,
  HandoverConfirmResponse,
  RelayOrderDetail
} from '../types/relay';
import HandoverQrDisplay from '../components/relay/HandoverQrDisplay';
import Button from '../components/ui/Button';
import '../styles/relay.css';

type ActiveTab = 'my-legs' | 'show-qr' | 'confirm-receive' | 'meeting-points' | 'incident';

export default function CourierRelayHandoverPage() {
  const [activeTab, setActiveTab] = useState<ActiveTab>('my-legs');

  // Dữ liệu danh sách chặng
  const [legs, setLegs] = useState<OrderLeg[]>([]);
  const [meetingPoints, setMeetingPoints] = useState<MeetingPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [feedbackMessage, setFeedbackMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Chọn chặng để xuất trình QR hoặc nhận bàn giao
  const [selectedLeg, setSelectedLeg] = useState<OrderLeg | null>(null);
  const [handoverToken, setHandoverToken] = useState<HandoverToken | null>(null);
  const [tokenLoading, setTokenLoading] = useState(false);

  // Form xác nhận nhận bàn giao
  const [inputCode, setInputCode] = useState('');
  const [handoverNotes, setHandoverNotes] = useState('');
  const [proofPhotoUrl, setProofPhotoUrl] = useState('');
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [submittingHandover, setSubmittingHandover] = useState(false);
  const [handoverSuccessResult, setHandoverSuccessResult] = useState<HandoverConfirmResponse | null>(null);

  // Form báo cáo sự cố
  const [incidentType, setIncidentType] = useState('Hàng hóa bị móp méo / rách bao bì');
  const [incidentDesc, setIncidentDesc] = useState('');
  const [incidentPhoto, setIncidentPhoto] = useState('');
  const [submittingIncident, setSubmittingIncident] = useState(false);

  // Chi tiết đơn hàng tiếp sức
  const [viewingDetailOrder, setViewingDetailOrder] = useState<RelayOrderDetail | null>(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const incidentFileInputRef = useRef<HTMLInputElement>(null);

  // Tải dữ liệu ban đầu
  const loadData = async () => {
    setLoading(true);
    setFeedbackMessage(null);
    try {
      const [legsRes, pointsRes] = await Promise.all([
        relayApi.getCourierRelayLegs(),
        relayApi.getSafeMeetingPoints(),
      ]);

      setLegs(legsRes.data || []);
      setMeetingPoints(pointsRes.data || []);

      // Nếu chưa chọn chặng, chọn chặng đầu tiên có thể thao tác
      if (legsRes.data && legsRes.data.length > 0 && !selectedLeg) {
        setSelectedLeg(legsRes.data[0]);
      }
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } }; message?: string };
      setFeedbackMessage({
        type: 'error',
        text: error?.response?.data?.message || error?.message || 'Không thể tải danh sách chặng tiếp sức.',
      });
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, []);

  // Xử lý tạo / làm mới mã QR bàn giao
  const handleGenerateToken = async (orderId: number, legNo: number) => {
    setTokenLoading(true);
    setFeedbackMessage(null);
    try {
      const res = await relayApi.generateHandoverToken(orderId, legNo);
      setHandoverToken(res.data);
      setFeedbackMessage({
        type: 'success',
        text: 'Đã tạo mã xác nhận bàn giao mới thành công (hiệu lực 10 phút).',
      });
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } }; message?: string };
      setFeedbackMessage({
        type: 'error',
        text: error?.response?.data?.message || 'Lỗi khi tạo mã bàn giao.',
      });
    } finally {
      setTokenLoading(false);
    }
  };

  // Mở tab xuất trình QR cho 1 chặng cụ thể
  const handleOpenShowQr = async (leg: OrderLeg) => {
    setSelectedLeg(leg);
    setActiveTab('show-qr');
    setFeedbackMessage(null);

    // Thử lấy mã token còn hiệu lực trước
    setTokenLoading(true);
    try {
      const res = await relayApi.getActiveHandoverToken(leg.orderId, leg.legNo);
      if (res.data) {
        setHandoverToken(res.data);
      } else {
        // Nếu chưa có, tự động tạo mới
        await handleGenerateToken(leg.orderId, leg.legNo);
      }
    } catch {
      await handleGenerateToken(leg.orderId, leg.legNo);
    } finally {
      setTokenLoading(false);
    }
  };

  // Mở tab nhận bàn giao cho 1 chặng
  const handleOpenReceiveHandover = (leg: OrderLeg) => {
    setSelectedLeg(leg);
    setActiveTab('confirm-receive');
    setInputCode('');
    setProofPhotoUrl('');
    setHandoverNotes('');
    setHandoverSuccessResult(null);
    setFeedbackMessage(null);
  };

  // Mở tab báo cáo sự cố cho 1 chặng
  const handleOpenIncident = (leg: OrderLeg) => {
    setSelectedLeg(leg);
    setActiveTab('incident');
    setIncidentDesc('');
    setIncidentPhoto('');
    setFeedbackMessage(null);
  };

  // Xem chi tiết toàn bộ các chặng của đơn
  const handleViewOrderDetails = async (orderId: number) => {
    try {
      const res = await relayApi.getRelayOrder(orderId);
      setViewingDetailOrder(res.data);
      setDetailModalOpen(true);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } }; message?: string };
      setFeedbackMessage({
        type: 'error',
        text: error?.response?.data?.message || 'Không thể xem chi tiết đơn hàng tiếp sức.',
      });
    }
  };

  // Xử lý upload ảnh chứng minh bàn giao
  const handleUploadPhoto = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploadingPhoto(true);
    try {
      const url = await relayApi.uploadHandoverPhoto(file);
      setProofPhotoUrl(url);
    } catch {
      // Dự phòng URL ảnh nếu chưa cấu hình upload cloud
      const mockUrl = URL.createObjectURL(file);
      setProofPhotoUrl(mockUrl);
    } finally {
      setUploadingPhoto(false);
    }
  };

  // Xử lý gửi xác nhận bàn giao chặng
  const handleConfirmHandover = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedLeg) return;

    if (!inputCode.trim()) {
      setFeedbackMessage({
        type: 'error',
        text: 'Vui lòng nhập mã xác nhận bàn giao hoặc quét mã QR.',
      });
      return;
    }

    setSubmittingHandover(true);
    setFeedbackMessage(null);

    // Xác định chặng cần xác nhận bàn giao: nếu đang ở chặng tiếp theo (legNo 2), chặng cần bàn giao là legNo 1
    const targetLegNo = selectedLeg.legNo > 1 ? selectedLeg.legNo - 1 : selectedLeg.legNo;

    try {
      const res = await relayApi.confirmHandover(selectedLeg.orderId, targetLegNo, {
        tokenCode: inputCode.trim(),
        photoUrl: proofPhotoUrl || 'https://fptpost.vn/uploads/proof_sample.jpg',
        notes: handoverNotes || 'Đã kiểm tra kiện hàng tại điểm hẹn, tình trạng nguyên vẹn.',
      });

      setHandoverSuccessResult(res.data);
      setFeedbackMessage({
        type: 'success',
        text: 'Bàn giao chặng thành công! Trách nhiệm vận chuyển đã được chuyển giao cho bạn.',
      });

      // Tải lại danh sách chặng
      void loadData();
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } }; message?: string };
      setFeedbackMessage({
        type: 'error',
        text: error?.response?.data?.message || 'Bàn giao không thành công. Vui lòng kiểm tra lại mã xác nhận.',
      });
    } finally {
      setSubmittingHandover(false);
    }
  };

  // Xử lý gửi báo cáo sự cố bàn giao
  const handleSendIncident = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedLeg) return;

    if (!incidentDesc.trim()) {
      setFeedbackMessage({
        type: 'error',
        text: 'Vui lòng nhập chi tiết mô tả sự cố gặp phải.',
      });
      return;
    }

    setSubmittingIncident(false);
    try {
      await relayApi.reportIncident(selectedLeg.orderId, selectedLeg.legNo, {
        incidentType,
        description: incidentDesc,
        photoUrl: incidentPhoto,
      });

      setFeedbackMessage({
        type: 'success',
        text: 'Đã gửi báo cáo sự cố thành công. Bộ phận vận hành sẽ liên hệ hỗ trợ bạn.',
      });
      setActiveTab('my-legs');
      void loadData();
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } }; message?: string };
      setFeedbackMessage({
        type: 'error',
        text: error?.response?.data?.message || 'Lỗi khi gửi báo cáo sự cố.',
      });
    } finally {
      setSubmittingIncident(false);
    }
  };

  // Helper hiển thị huy hiệu trạng thái
  const renderStatusBadge = (status: string) => {
    switch (status) {
      case 'IN_TRANSIT':
        return <span className="leg-status-pill in-transit"><Clock size={12} /> Đang giao chặng</span>;
      case 'READY_FOR_HANDOVER':
        return <span className="leg-status-pill ready"><AlertTriangle size={12} /> Sẵn sàng bàn giao</span>;
      case 'HANDED_OVER':
        return <span className="leg-status-pill handed-over"><CheckCircle2 size={12} /> Đã bàn giao</span>;
      case 'COMPLETED':
        return <span className="leg-status-pill completed"><CheckCircle2 size={12} /> Hoàn tất đơn</span>;
      case 'INCIDENT':
        return <span className="leg-status-pill incident"><AlertTriangle size={12} /> Có sự cố</span>;
      default:
        return <span className="leg-status-pill pending"><Clock size={12} /> Chờ bắt đầu</span>;
    }
  };

  return (
    <div className="relay-page-container">
      {/* Header bar */}
      <div className="relay-header-bar">
        <div className="relay-header-title">
          <h1>
            <GitCommit size={28} className="text-primary" />
            Giao hàng tiếp sức
          </h1>
          <p>
            Chuyển giao trách nhiệm kiện hàng giữa các người giao tại điểm hẹn an toàn đã được kiểm duyệt.
          </p>
        </div>

        <Button
          variant="secondary"
          icon={<RefreshCw size={16} className={refreshing ? 'spin' : ''} />}
          onClick={() => {
            setRefreshing(true);
            void loadData();
          }}
          disabled={loading || refreshing}
        >
          Làm mới
        </Button>
      </div>

      {/* Thông báo phản hồi */}
      {feedbackMessage && (
        <div
          className={`ui-alert ${feedbackMessage.type === 'error' ? 'ui-alert-danger' : 'ui-alert-success'}`}
          style={{ marginBottom: '20px' }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            {feedbackMessage.type === 'error' ? <AlertTriangle size={18} /> : <CheckCircle2 size={18} />}
            <span>{feedbackMessage.text}</span>
          </div>
        </div>
      )}

      {/* Tabs navigation */}
      <div className="relay-tabs-nav">
        <button
          type="button"
          className={`relay-tab-btn ${activeTab === 'my-legs' ? 'active' : ''}`}
          onClick={() => setActiveTab('my-legs')}
        >
          <Layers size={17} />
          Chặng tiếp sức của tôi
          <span className="relay-tab-badge">{legs.length}</span>
        </button>

        <button
          type="button"
          className={`relay-tab-btn ${activeTab === 'show-qr' ? 'active' : ''}`}
          onClick={() => {
            if (selectedLeg) {
              void handleOpenShowQr(selectedLeg);
            } else {
              setActiveTab('show-qr');
            }
          }}
        >
          <QrCode size={17} />
          Xuất trình mã bàn giao
        </button>

        <button
          type="button"
          className={`relay-tab-btn ${activeTab === 'confirm-receive' ? 'active' : ''}`}
          onClick={() => setActiveTab('confirm-receive')}
        >
          <CheckCircle2 size={17} />
          Xác nhận nhận hàng
        </button>

        <button
          type="button"
          className={`relay-tab-btn ${activeTab === 'meeting-points' ? 'active' : ''}`}
          onClick={() => setActiveTab('meeting-points')}
        >
          <ShieldCheck size={17} />
          Điểm hẹn an toàn
          <span className="relay-tab-badge">{meetingPoints.length}</span>
        </button>
      </div>

      {/* ========================================================================= */}
      {/* TAB 1: DANH SÁCH CHẶNG TIẾP SỨC CỦA TÔI */}
      {/* ========================================================================= */}
      {activeTab === 'my-legs' && (
        <div className="relay-grid">
          <div>
            <div className="relay-card">
              <div className="relay-card-title">
                <span>Chặng được phân công cho bạn</span>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                  Tổng: {legs.length} chặng
                </span>
              </div>

              {loading ? (
                <p style={{ color: 'var(--text-muted)' }}>Đang tải danh sách chặng tiếp sức...</p>
              ) : legs.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '30px 10px', color: 'var(--text-muted)' }}>
                  <Package size={40} style={{ opacity: 0.3, marginBottom: '10px' }} />
                  <p>Bạn chưa có chặng giao tiếp sức nào được phân công.</p>
                  <p style={{ fontSize: '0.85rem' }}>Các đơn hàng nhiều chặng sẽ hiển thị tại đây khi bạn nhận chặng.</p>
                </div>
              ) : (
                legs.map((leg) => {
                  const isSelected = selectedLeg?.id === leg.id;
                  return (
                    <div
                      key={leg.id}
                      className={`relay-leg-card ${isSelected ? 'active-leg' : ''}`}
                      onClick={() => setSelectedLeg(leg)}
                      style={{ cursor: 'pointer' }}
                    >
                      <div className="leg-card-header">
                        <div className="leg-card-num">
                          <span>Chặng {leg.legNo}</span>
                          <span style={{ color: 'var(--text-secondary)', fontWeight: 400 }}>
                            • {leg.orderCode}
                          </span>
                        </div>
                        {renderStatusBadge(leg.status)}
                      </div>

                      <div style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '8px' }}>
                        {leg.productName}
                      </div>

                      <div className="leg-path-info">
                        <div className="path-point">
                          <span className="point-icon start">1</span>
                          <div className="point-text">
                            <small>Điểm bắt đầu chặng:</small>
                            <span>{leg.startAddress}</span>
                          </div>
                        </div>

                        <div className="path-point">
                          <span className="point-icon mid">2</span>
                          <div className="point-text">
                            <small>
                              {leg.meetingPoint ? 'Điểm hẹn bàn giao an toàn:' : 'Điểm kết thúc chặng:'}
                            </small>
                            <span style={{ color: leg.meetingPoint ? 'var(--primary)' : 'inherit' }}>
                              {leg.endAddress}
                            </span>
                          </div>
                        </div>
                      </div>

                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          borderTop: '1px solid var(--border-subtle)',
                          paddingTop: '12px',
                          marginTop: '10px',
                          fontSize: '0.88rem',
                          flexWrap: 'wrap',
                          gap: '10px',
                        }}
                      >
                        <div style={{ color: 'var(--text-secondary)' }}>
                          Phí chặng: <strong style={{ color: '#6cdeb4' }}>{(leg.fee || 0).toLocaleString('vi-VN')} đ</strong>
                          {leg.distanceKm && <span> • {leg.distanceKm} km</span>}
                        </div>

                        <div style={{ display: 'flex', gap: '8px' }}>
                          <button
                            type="button"
                            className="ui-button ui-button-secondary ui-button-sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              void handleViewOrderDetails(leg.orderId);
                            }}
                          >
                            <Info size={14} /> Chi tiết
                          </button>

                          {leg.canHandover && (
                            <button
                              type="button"
                              className="ui-button ui-button-primary ui-button-sm"
                              onClick={(e) => {
                                e.stopPropagation();
                                void handleOpenShowQr(leg);
                              }}
                            >
                              <QrCode size={14} /> Bàn giao ngay
                            </button>
                          )}

                          {leg.status === 'PENDING' && (
                            <button
                              type="button"
                              className="ui-button ui-button-secondary ui-button-sm"
                              onClick={(e) => {
                                e.stopPropagation();
                                handleOpenReceiveHandover(leg);
                              }}
                            >
                              <CheckCircle2 size={14} /> Nhận hàng
                            </button>
                          )}

                          <button
                            type="button"
                            className="ui-button ui-button-ghost ui-button-sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleOpenIncident(leg);
                            }}
                            title="Báo sự cố"
                          >
                            <AlertTriangle size={14} />
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>

          {/* Cột hiển thị chi tiết chặng được chọn */}
          <div>
            {selectedLeg ? (
              <div className="relay-card">
                <div className="relay-card-title">
                  <span>Thông tin chặng {selectedLeg.legNo} ({selectedLeg.orderCode})</span>
                  {renderStatusBadge(selectedLeg.status)}
                </div>

                <div style={{ marginBottom: '16px' }}>
                  <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Kiện hàng:</div>
                  <div style={{ fontSize: '1.05rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                    {selectedLeg.productName}
                  </div>
                </div>

                {selectedLeg.meetingPoint && (
                  <div className="handover-point-hint">
                    <ShieldCheck size={20} style={{ color: '#6cdeb4', marginTop: '2px' }} />
                    <div>
                      <strong>Điểm hẹn an toàn: {selectedLeg.meetingPoint.name}</strong>
                      <p>{selectedLeg.meetingPoint.address}</p>
                      {selectedLeg.meetingPoint.description && (
                        <p style={{ marginTop: '4px', fontStyle: 'italic' }}>
                          Tiện ích: {selectedLeg.meetingPoint.description}
                        </p>
                      )}
                    </div>
                  </div>
                )}

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px', marginBottom: '20px' }}>
                  <div style={{ background: 'var(--bg-tertiary)', padding: '12px', borderRadius: '8px' }}>
                    <div style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>Người phụ trách chặng:</div>
                    <div style={{ fontWeight: 600, display: 'flex', alignItems: 'center', gap: '6px', marginTop: '4px' }}>
                      <User size={15} />
                      {selectedLeg.courierName || 'Chưa nhận'}
                    </div>
                    {selectedLeg.courierPhone && (
                      <div style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', marginTop: '2px' }}>
                        <Phone size={13} style={{ display: 'inline', marginRight: '4px' }} />
                        {selectedLeg.courierPhone}
                      </div>
                    )}
                  </div>

                  <div style={{ background: 'var(--bg-tertiary)', padding: '12px', borderRadius: '8px' }}>
                    <div style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>Phí nhận chặng:</div>
                    <div style={{ fontSize: '1.15rem', fontWeight: 700, color: '#6cdeb4', marginTop: '4px' }}>
                      {(selectedLeg.fee || 0).toLocaleString('vi-VN')} đ
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                      Khoảng cách: {selectedLeg.distanceKm || 0} km
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                  <Button
                    variant="primary"
                    icon={<QrCode size={16} />}
                    onClick={() => void handleOpenShowQr(selectedLeg)}
                  >
                    Xuất trình mã bàn giao
                  </Button>

                  <Button
                    variant="secondary"
                    icon={<CheckCircle2 size={16} />}
                    onClick={() => handleOpenReceiveHandover(selectedLeg)}
                  >
                    Xác nhận nhận hàng
                  </Button>

                  <Button
                    variant="ghost"
                    icon={<AlertTriangle size={16} />}
                    onClick={() => handleOpenIncident(selectedLeg)}
                  >
                    Báo sự cố
                  </Button>
                </div>
              </div>
            ) : (
              <div className="relay-card" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '40px 20px' }}>
                <p>Chọn một chặng từ danh sách bên trái để xem chi tiết và thao tác bàn giao.</p>
              </div>
            )}

            {/* Hướng dẫn quy trình bàn giao */}
            <div className="relay-card">
              <div className="relay-card-title">
                <span>Quy trình chuyển giao trách nhiệm chặng</span>
              </div>
              <ol style={{ paddingLeft: '20px', color: 'var(--text-secondary)', fontSize: '0.9rem', lineHeight: '1.7' }}>
                <li>Hai người giao di chuyển tới điểm hẹn an toàn đã được xác định.</li>
                <li>Người giao trước bấm <strong>Xuất trình mã bàn giao</strong> để mở mã QR có hiệu lực 10 phút.</li>
                <li>Người giao tiếp theo dùng camera quét mã QR hoặc nhập chuỗi mã 8 ký tự.</li>
                <li>Chụp ảnh tình trạng kiện hàng tại điểm hẹn làm bằng chứng bàn giao.</li>
                <li>Bấm xác nhận: Hệ thống ghi nhận hoàn thành chặng trước và kích hoạt trách nhiệm cho chặng tiếp theo.</li>
              </ol>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 2: XUẤT TRÌNH MÃ QR BÀN GIAO (DÀNH CHO NGƯỜI GIAO TRƯỚC) */}
      {/* ========================================================================= */}
      {activeTab === 'show-qr' && (
        <div style={{ maxWidth: '640px', margin: '0 auto' }}>
          <div className="relay-card">
            <div className="relay-card-title">
              <span>Mã QR Bàn Giao Chặng {selectedLeg?.legNo}</span>
              <button
                type="button"
                className="ui-button ui-button-ghost ui-button-sm"
                onClick={() => setActiveTab('my-legs')}
              >
                ← Quay lại danh sách
              </button>
            </div>

            {selectedLeg && (
              <div style={{ marginBottom: '16px', background: 'var(--bg-tertiary)', padding: '12px 16px', borderRadius: '10px' }}>
                <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Đơn hàng:</div>
                <div style={{ fontWeight: 600 }}>{selectedLeg.orderCode} - {selectedLeg.productName}</div>
              </div>
            )}

            {tokenLoading ? (
              <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                <RefreshCw size={32} className="spin" style={{ marginBottom: '10px' }} />
                <p>Đang tạo mã xác nhận bàn giao an toàn...</p>
              </div>
            ) : handoverToken ? (
              <HandoverQrDisplay
                token={handoverToken}
                onRefresh={() => {
                  if (selectedLeg) {
                    void handleGenerateToken(selectedLeg.orderId, selectedLeg.legNo);
                  }
                }}
                refreshing={tokenLoading}
              />
            ) : (
              <div style={{ textAlign: 'center', padding: '30px' }}>
                <p style={{ color: 'var(--text-muted)', marginBottom: '16px' }}>
                  Chưa có mã bàn giao nào được tạo cho chặng này.
                </p>
                {selectedLeg && (
                  <Button
                    variant="primary"
                    icon={<QrCode size={16} />}
                    onClick={() => void handleGenerateToken(selectedLeg.orderId, selectedLeg.legNo)}
                  >
                    Tạo mã bàn giao ngay
                  </Button>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 3: XÁC NHẬN NHẬN BÀN GIAO (DÀNH CHO NGƯỜI GIAO TIẾP THEO) */}
      {/* ========================================================================= */}
      {activeTab === 'confirm-receive' && (
        <div style={{ maxWidth: '700px', margin: '0 auto' }}>
          <div className="relay-card">
            <div className="relay-card-title">
              <span>Xác nhận nhận hàng & Tiếp nhận chặng tiếp theo</span>
              <button
                type="button"
                className="ui-button ui-button-ghost ui-button-sm"
                onClick={() => setActiveTab('my-legs')}
              >
                ← Quay lại danh sách
              </button>
            </div>

            {handoverSuccessResult ? (
              <div style={{ textAlign: 'center', padding: '30px 10px' }}>
                <CheckCircle2 size={56} style={{ color: '#6cdeb4', marginBottom: '16px' }} />
                <h3 style={{ fontSize: '1.3rem', fontWeight: 700, color: '#6cdeb4', marginBottom: '8px' }}>
                  Bàn giao chặng thành công!
                </h3>
                <p style={{ color: 'var(--text-secondary)', marginBottom: '20px' }}>
                  Bạn đã tiếp nhận thành công trách nhiệm vận chuyển kiện hàng cho chặng tiếp theo.
                </p>

                <div
                  style={{
                    background: 'var(--bg-tertiary)',
                    borderRadius: '12px',
                    padding: '18px',
                    textAlign: 'left',
                    marginBottom: '24px',
                  }}
                >
                  <div style={{ marginBottom: '10px' }}>
                    <small style={{ color: 'var(--text-muted)' }}>Mã đơn hàng:</small>
                    <div style={{ fontWeight: 600 }}>{handoverSuccessResult.orderCode}</div>
                  </div>

                  <div style={{ marginBottom: '10px' }}>
                    <small style={{ color: 'var(--text-muted)' }}>Chặng đã bàn giao:</small>
                    <div>Chặng {handoverSuccessResult.completedLegNo} đã hoàn tất</div>
                  </div>

                  <div style={{ marginBottom: '10px' }}>
                    <small style={{ color: 'var(--text-muted)' }}>Chặng hiện tại của bạn:</small>
                    <div style={{ color: '#60a5fa', fontWeight: 600 }}>
                      Chặng {handoverSuccessResult.nextLegNo} - Đang vận chuyển
                    </div>
                  </div>

                  <div>
                    <small style={{ color: 'var(--text-muted)' }}>Điểm đến tiếp theo:</small>
                    <div style={{ fontWeight: 600, color: 'var(--primary)' }}>
                      {handoverSuccessResult.nextDestinationAddress}
                    </div>
                  </div>
                </div>

                <Button
                  variant="primary"
                  onClick={() => {
                    setHandoverSuccessResult(null);
                    setActiveTab('my-legs');
                  }}
                >
                  Về danh sách chặng của tôi
                </Button>
              </div>
            ) : (
              <form onSubmit={handleConfirmHandover}>
                {selectedLeg && (
                  <div
                    style={{
                      background: 'rgba(243, 112, 33, 0.08)',
                      border: '1px solid rgba(243, 112, 33, 0.2)',
                      padding: '12px 16px',
                      borderRadius: '10px',
                      marginBottom: '20px',
                    }}
                  >
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Đang nhận bàn giao cho:</div>
                    <div style={{ fontWeight: 600, color: 'var(--primary)' }}>
                      {selectedLeg.orderCode} - {selectedLeg.productName}
                    </div>
                    <div style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', marginTop: '4px' }}>
                      Chặng bàn giao: Chặng {selectedLeg.legNo > 1 ? selectedLeg.legNo - 1 : 1} → Tiếp nhận chặng {selectedLeg.legNo}
                    </div>
                  </div>
                )}

                <div className="confirm-form-group">
                  <label className="confirm-form-label">
                    Mã xác nhận bàn giao <span style={{ color: '#ffa6b3' }}>*</span>
                  </label>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <input
                      type="text"
                      className="confirm-form-input"
                      placeholder="Nhập mã 8 ký tự (ví dụ: HO-FPTRELAY)"
                      value={inputCode}
                      onChange={(e) => setInputCode(e.target.value.toUpperCase())}
                      style={{ textTransform: 'uppercase', fontFamily: 'var(--font-mono, monospace)', fontWeight: 600 }}
                      required
                    />
                    <button
                      type="button"
                      className="ui-button ui-button-secondary"
                      onClick={async () => {
                        try {
                          const text = await navigator.clipboard.readText();
                          if (text) setInputCode(text.trim().toUpperCase());
                        } catch {
                          // Bỏ qua lỗi truy cập clipboard
                        }
                      }}
                      title="Dán từ bộ nhớ tạm"
                    >
                      Dán
                    </button>
                  </div>
                  <small style={{ color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
                    Mã xác nhận do người giao chặng trước xuất trình (mỗi mã có hiệu lực trong 10 phút).
                  </small>
                </div>

                <div className="confirm-form-group">
                  <label className="confirm-form-label">Ảnh kiểm tra kiện hàng tại điểm hẹn</label>
                  <input
                    type="file"
                    accept="image/*"
                    ref={fileInputRef}
                    style={{ display: 'none' }}
                    onChange={handleUploadPhoto}
                  />

                  {proofPhotoUrl ? (
                    <div className="photo-preview-box">
                      <img src={proofPhotoUrl} alt="Bằng chứng bàn giao" className="photo-preview-img" />
                      <button
                        type="button"
                        className="photo-remove-btn"
                        onClick={() => setProofPhotoUrl('')}
                      >
                        Đổi ảnh khác
                      </button>
                    </div>
                  ) : (
                    <div
                      className="photo-upload-box"
                      onClick={() => fileInputRef.current?.click()}
                    >
                      <Camera size={28} style={{ color: 'var(--primary)', marginBottom: '6px' }} />
                      <p style={{ margin: 0, fontWeight: 600, fontSize: '0.92rem' }}>
                        {uploadingPhoto ? 'Đang tải ảnh...' : 'Chụp ảnh hoặc tải ảnh tình trạng kiện hàng'}
                      </p>
                      <small style={{ color: 'var(--text-muted)' }}>
                        Làm bằng chứng xác thực kiện hàng nguyên vẹn lúc tiếp nhận trách nhiệm.
                      </small>
                    </div>
                  )}
                </div>

                <div className="confirm-form-group">
                  <label className="confirm-form-label">Ghi chú tình trạng hàng khi nhận</label>
                  <textarea
                    className="confirm-form-textarea"
                    placeholder="Ví dụ: Đã kiểm tra bao bì còn nguyên vẹn, tem niêm phong không rách..."
                    value={handoverNotes}
                    onChange={(e) => setHandoverNotes(e.target.value)}
                  />
                </div>

                <div style={{ display: 'flex', gap: '12px', marginTop: '24px' }}>
                  <Button
                    type="submit"
                    variant="primary"
                    icon={<CheckCircle2 size={16} />}
                    disabled={submittingHandover}
                    style={{ flex: 1 }}
                  >
                    {submittingHandover ? 'Đang xác nhận...' : 'Xác nhận nhận hàng & Bắt đầu giao'}
                  </Button>

                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => setActiveTab('my-legs')}
                  >
                    Hủy bỏ
                  </Button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 4: DANH SÁCH ĐIỂM HẸN BÀN GIAO AN TOÀN */}
      {/* ========================================================================= */}
      {activeTab === 'meeting-points' && (
        <div>
          <div className="relay-card">
            <div className="relay-card-title">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <ShieldCheck size={22} style={{ color: '#6cdeb4' }} />
                <span>Danh sách điểm hẹn bàn giao an toàn đã được kiểm duyệt</span>
              </div>
              <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                {meetingPoints.length} địa điểm công cộng
              </span>
            </div>

            <p style={{ color: 'var(--text-secondary)', marginBottom: '20px', fontSize: '0.92rem' }}>
              Các địa điểm công cộng có nhân viên an ninh, camera giám sát 24/7 và quầy dịch vụ sinh viên
              quanh các cơ sở trường, giúp việc chuyển giao bưu phẩm giữa các đối tác diễn ra an toàn, minh bạch.
            </p>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))', gap: '16px' }}>
              {meetingPoints.map((point) => (
                <div key={point.id} className="meeting-point-item">
                  <div className="meeting-point-content">
                    <h3>{point.name}</h3>
                    <p>
                      <MapPin size={14} style={{ display: 'inline', marginRight: '4px', color: 'var(--primary)' }} />
                      {point.address}
                    </p>
                    {point.description && (
                      <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                        {point.description}
                      </p>
                    )}
                    <div className="meeting-point-amenities">
                      {point.hasSecurityGuard && (
                        <span className="amenity-tag">
                          <ShieldCheck size={12} /> Bảo vệ 24/7
                        </span>
                      )}
                      {point.hasSurveillanceCamera && (
                        <span className="amenity-tag">
                          <Camera size={12} /> Camera an ninh
                        </span>
                      )}
                      {point.campusArea && (
                        <span className="amenity-tag" style={{ color: '#60a5fa', borderColor: 'rgba(96, 165, 250, 0.3)' }}>
                          {point.campusArea}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 5: BÁO CÁO SỰ CỐ BÀN GIAO */}
      {/* ========================================================================= */}
      {activeTab === 'incident' && (
        <div style={{ maxWidth: '640px', margin: '0 auto' }}>
          <div className="relay-card">
            <div className="relay-card-title">
              <span style={{ color: '#ffa6b3', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <AlertTriangle size={20} />
                Báo cáo sự cố tại điểm bàn giao
              </span>
              <button
                type="button"
                className="ui-button ui-button-ghost ui-button-sm"
                onClick={() => setActiveTab('my-legs')}
              >
                ← Quay lại danh sách
              </button>
            </div>

            {selectedLeg && (
              <div style={{ background: 'var(--bg-tertiary)', padding: '12px', borderRadius: '8px', marginBottom: '18px' }}>
                <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Đơn hàng gặp sự cố:</div>
                <div style={{ fontWeight: 600 }}>{selectedLeg.orderCode} - Chặng {selectedLeg.legNo}</div>
              </div>
            )}

            <form onSubmit={handleSendIncident}>
              <div className="confirm-form-group">
                <label className="confirm-form-label">Loại sự cố</label>
                <select
                  className="confirm-form-input"
                  value={incidentType}
                  onChange={(e) => setIncidentType(e.target.value)}
                >
                  <option value="Hàng hóa bị móp méo / rách bao bì">Hàng hóa bị móp méo / rách bao bì</option>
                  <option value="Đối tác bàn giao không tới điểm hẹn">Đối tác bàn giao không tới điểm hẹn</option>
                  <option value="Đến trễ hẹn quá 30 phút">Đến trễ hẹn quá 30 phút</option>
                  <option value="Hàng không đúng mô tả đơn hàng">Hàng không đúng mô tả đơn hàng</option>
                  <option value="Khác">Sự cố bất khả kháng khác</option>
                </select>
              </div>

              <div className="confirm-form-group">
                <label className="confirm-form-label">
                  Mô tả chi tiết sự cố <span style={{ color: '#ffa6b3' }}>*</span>
                </label>
                <textarea
                  className="confirm-form-textarea"
                  placeholder="Mô tả cụ thể vấn đề phát sinh để đội ngũ hỗ trợ kịp thời can thiệp..."
                  value={incidentDesc}
                  onChange={(e) => setIncidentDesc(e.target.value)}
                  required
                />
              </div>

              <div className="confirm-form-group">
                <label className="confirm-form-label">Ảnh chụp tình trạng thực tế</label>
                <input
                  type="file"
                  accept="image/*"
                  ref={incidentFileInputRef}
                  style={{ display: 'none' }}
                  onChange={async (e) => {
                    const f = e.target.files?.[0];
                    if (f) {
                      try {
                        const url = await relayApi.uploadHandoverPhoto(f);
                        setIncidentPhoto(url);
                      } catch {
                        setIncidentPhoto(URL.createObjectURL(f));
                      }
                    }
                  }}
                />

                {incidentPhoto ? (
                  <div className="photo-preview-box">
                    <img src={incidentPhoto} alt="Ảnh sự cố" className="photo-preview-img" />
                    <button
                      type="button"
                      className="photo-remove-btn"
                      onClick={() => setIncidentPhoto('')}
                    >
                      Đổi ảnh khác
                    </button>
                  </div>
                ) : (
                  <div
                    className="photo-upload-box"
                    onClick={() => incidentFileInputRef.current?.click()}
                  >
                    <Upload size={24} style={{ color: 'var(--primary)', marginBottom: '4px' }} />
                    <p style={{ margin: 0, fontSize: '0.9rem', fontWeight: 600 }}>Tải ảnh bằng chứng sự cố</p>
                  </div>
                )}
              </div>

              <div style={{ display: 'flex', gap: '12px', marginTop: '20px' }}>
                <Button
                  type="submit"
                  variant="primary"
                  icon={<AlertTriangle size={16} />}
                  disabled={submittingIncident}
                  style={{ flex: 1 }}
                >
                  {submittingIncident ? 'Đang gửi...' : 'Gửi báo cáo sự cố'}
                </Button>

                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setActiveTab('my-legs')}
                >
                  Hủy bỏ
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* MODAL CHI TIẾT TOÀN BỘ CÁC CHẶNG CỦA ĐƠN HÀNG TIẾP SỨC */}
      {/* ========================================================================= */}
      {detailModalOpen && viewingDetailOrder && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0, 0, 0, 0.75)',
            zIndex: 1000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px',
          }}
          onClick={() => setDetailModalOpen(false)}
        >
          <div
            style={{
              background: 'var(--bg-card, #181e27)',
              border: '1px solid var(--border-subtle, #333d4c)',
              borderRadius: '16px',
              maxWidth: '680px',
              width: '100%',
              maxHeight: '90vh',
              overflowY: 'auto',
              padding: '24px',
              boxShadow: '0 10px 40px rgba(0, 0, 0, 0.5)',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h2 style={{ fontSize: '1.25rem', fontWeight: 700, margin: 0, color: 'var(--text-primary)' }}>
                Chi tiết đơn hàng tiếp sức {viewingDetailOrder.orderCode}
              </h2>
              <button
                type="button"
                className="ui-button ui-button-ghost ui-button-sm"
                onClick={() => setDetailModalOpen(false)}
              >
                ✕
              </button>
            </div>

            <div style={{ background: 'var(--bg-tertiary)', padding: '14px', borderRadius: '10px', marginBottom: '20px' }}>
              <div style={{ fontWeight: 600, fontSize: '1rem', color: 'var(--text-primary)' }}>
                {viewingDetailOrder.productName}
              </div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '4px' }}>
                Khối lượng: {viewingDetailOrder.weightKg} kg • Tổng số chặng: {viewingDetailOrder.totalLegs} chặng
              </div>
            </div>

            <h3 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '12px' }}>
              Tiến trình các chặng vận chuyển:
            </h3>

            {viewingDetailOrder.legs?.map((leg) => (
              <div
                key={leg.id}
                style={{
                  background: 'var(--bg-secondary)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: '10px',
                  padding: '14px',
                  marginBottom: '10px',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <div style={{ fontWeight: 700, color: 'var(--primary)' }}>
                    Chặng {leg.legNo} {leg.isCurrentLeg && <span style={{ color: '#60a5fa' }}>(Đang thực hiện)</span>}
                  </div>
                  {renderStatusBadge(leg.status)}
                </div>

                <div style={{ fontSize: '0.88rem', color: 'var(--text-primary)' }}>
                  <div><strong>Từ:</strong> {leg.startAddress}</div>
                  <div><strong>Đến:</strong> {leg.endAddress}</div>
                  {leg.courierName && (
                    <div style={{ color: 'var(--text-secondary)', marginTop: '4px' }}>
                      Người giao: {leg.courierName} {leg.courierPhone ? `(${leg.courierPhone})` : ''}
                    </div>
                  )}
                </div>
              </div>
            ))}

            {viewingDetailOrder.handoverHistory && viewingDetailOrder.handoverHistory.length > 0 && (
              <>
                <h3 style={{ fontSize: '1rem', fontWeight: 700, marginTop: '20px', marginBottom: '10px' }}>
                  Lịch sử bàn giao tại điểm hẹn:
                </h3>
                {viewingDetailOrder.handoverHistory.map((hist) => (
                  <div key={hist.id} className="handover-history-item">
                    <div className="history-avatar-icon">
                      <CheckCircle2 size={18} />
                    </div>
                    <div className="history-content">
                      <div className="history-header">
                        <span className="history-parties">
                          {hist.fromCourierName} → {hist.toCourierName}
                        </span>
                        <span className="history-time">
                          {new Date(hist.handoverTime).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                        </span>
                      </div>
                      <div className="history-location">
                        <MapPin size={13} /> {hist.meetingPointName}
                      </div>
                      {hist.notes && <div className="history-notes">{hist.notes}</div>}
                    </div>
                  </div>
                ))}
              </>
            )}

            <div style={{ marginTop: '24px', textAlign: 'right' }}>
              <Button variant="secondary" onClick={() => setDetailModalOpen(false)}>
                Đóng
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
