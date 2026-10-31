import { useEffect, useState, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Navigation,
  Plus,
  Clock,
  Bike,
  Car,
  Package,
  Calendar,
  DollarSign,
  AlertCircle,
  XCircle,
  Search,
  CheckCircle2,
  PauseCircle,
  PlayCircle,
  Edit3,
  ExternalLink,
  MapPin,
  X,
  Layers,
  ArrowRight,
  TrendingUp,
} from 'lucide-react';
import { tripApi } from '../api/tripApi';
import type {
  Trip,
  TripStatus,
  TripAttachedOrder,
  TripUpdateRequest,
  VehicleType,
  TripRecurrence,
} from '../types/trip';
import TripRouteMap from '../features/trip/components/TripRouteMap';
import '../styles/trip.css';

export default function CourierTripsPage() {
  const navigate = useNavigate();

  // Trips state
  const [trips, setTrips] = useState<Trip[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<TripStatus | 'ALL'>('ALL');
  const [feedbackMessage, setFeedbackMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Action Loading States
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);

  // Detail Modal State
  const [selectedTrip, setSelectedTrip] = useState<Trip | null>(null);
  const [detailModalTab, setDetailModalTab] = useState<'map' | 'orders' | 'suggestions'>('orders');
  const [attachedOrders, setAttachedOrders] = useState<TripAttachedOrder[]>([]);
  const [suggestedOrders, setSuggestedOrders] = useState<TripAttachedOrder[]>([]);
  const [loadingOrders, setLoadingOrders] = useState(false);
  const [loadingSuggestions, setLoadingSuggestions] = useState(false);

  // Edit Modal State
  const [editingTrip, setEditingTrip] = useState<Trip | null>(null);
  const [editDepartAt, setEditDepartAt] = useState('');
  const [editVehicleType, setEditVehicleType] = useState<VehicleType>('MOTORBIKE');
  const [editCapacityKg, setEditCapacityKg] = useState<number>(10);
  const [editFloorPrice, setEditFloorPrice] = useState<string>('');
  const [editRecurrence, setEditRecurrence] = useState<TripRecurrence>('NONE');
  const [editOrigin, setEditOrigin] = useState('');
  const [editDestination, setEditDestination] = useState('');
  const [savingEdit, setSavingEdit] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  // Cancel Confirmation Modal State
  const [cancellingTrip, setCancellingTrip] = useState<Trip | null>(null);

  const fetchTrips = useCallback(async () => {
    setLoading(true);
    try {
      const params = statusFilter !== 'ALL' ? { status: statusFilter } : undefined;
      const res = await tripApi.getMyTrips(params);
      if (res.data && res.data.content) {
        setTrips(res.data.content);
      } else {
        setTrips([]);
      }
    } catch {
      setTrips([]);
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    fetchTrips();
  }, [fetchTrips]);

  // Load attached orders and suggestions when detail modal opens
  const handleOpenDetailModal = async (trip: Trip, defaultTab: 'map' | 'orders' | 'suggestions' = 'orders') => {
    setSelectedTrip(trip);
    setDetailModalTab(defaultTab);
    setAttachedOrders(trip.attachedOrders || []);
    setLoadingOrders(true);
    setLoadingSuggestions(true);

    try {
      const [detailRes, ordersRes, suggestionsRes] = await Promise.allSettled([
        tripApi.getById(trip.id),
        tripApi.getAttachedOrders(trip.id),
        tripApi.getSuggestedOrders(trip.id),
      ]);

      if (detailRes.status === 'fulfilled' && detailRes.value.data) {
        setSelectedTrip(detailRes.value.data);
        if (detailRes.value.data.attachedOrders) {
          setAttachedOrders(detailRes.value.data.attachedOrders);
        }
      }

      if (ordersRes.status === 'fulfilled' && ordersRes.value.data) {
        setAttachedOrders(ordersRes.value.data);
      }
      if (suggestionsRes.status === 'fulfilled' && suggestionsRes.value.data) {
        setSuggestedOrders(suggestionsRes.value.data);
      }
    } catch {
      // fallback to existing
    } finally {
      setLoadingOrders(false);
      setLoadingSuggestions(false);
    }
  };

  // Toggle Pause/Resume Accepting Orders
  const handleTogglePause = async (trip: Trip) => {
    setActionLoadingId(trip.id);
    setFeedbackMessage(null);
    try {
      const res = await tripApi.togglePause(trip.id);
      const updated = res.data;
      const newStatus = updated.status;
      setFeedbackMessage({
        type: 'success',
        text:
          newStatus === 'PAUSED'
            ? 'Đã tạm dừng nhận đơn cho chuyến đi.'
            : 'Đã mở lại trạng thái sẵn sàng nhận đơn.',
      });
      fetchTrips();
      if (selectedTrip?.id === trip.id) {
        setSelectedTrip(updated);
      }
    } catch (err: unknown) {
      const message =
        err && typeof err === 'object' && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : 'Không thể thay đổi trạng thái chuyến đi lúc này.';
      setFeedbackMessage({ type: 'error', text: message || 'Lỗi khi cập nhật trạng thái.' });
    } finally {
      setActionLoadingId(null);
    }
  };

  // Complete Trip
  const handleCompleteTrip = async (trip: Trip) => {
    if (!window.confirm('Xác nhận bạn đã kết thúc chuyến đi này và hoàn tất hành trình?')) return;

    setActionLoadingId(trip.id);
    setFeedbackMessage(null);
    try {
      await tripApi.complete(trip.id);
      setFeedbackMessage({ type: 'success', text: 'Chuyến đi đã được đánh dấu hoàn thành.' });
      fetchTrips();
      if (selectedTrip?.id === trip.id) {
        setSelectedTrip({ ...selectedTrip, status: 'COMPLETED' });
      }
    } catch (err: unknown) {
      const message =
        err && typeof err === 'object' && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : 'Không thể hoàn tất chuyến đi lúc này.';
      setFeedbackMessage({ type: 'error', text: message || 'Lỗi khi kết thúc chuyến đi.' });
    } finally {
      setActionLoadingId(null);
    }
  };

  // Initiate Cancel Trip Modal
  const handleInitiateCancel = (trip: Trip) => {
    setCancellingTrip(trip);
  };

  // Execute Cancel Trip
  const handleConfirmCancelTrip = async () => {
    if (!cancellingTrip) return;

    setActionLoadingId(cancellingTrip.id);
    setFeedbackMessage(null);
    try {
      await tripApi.cancel(cancellingTrip.id);
      setFeedbackMessage({ type: 'success', text: 'Đã hủy chuyến đi thành công.' });
      setCancellingTrip(null);
      fetchTrips();
      if (selectedTrip?.id === cancellingTrip.id) {
        setSelectedTrip(null);
      }
    } catch (err: unknown) {
      const message =
        err && typeof err === 'object' && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : 'Không thể hủy chuyến đi.';
      setFeedbackMessage({ type: 'error', text: message || 'Lỗi khi hủy chuyến đi.' });
    } finally {
      setActionLoadingId(null);
    }
  };

  // Open Edit Modal
  const handleOpenEditModal = (trip: Trip) => {
    setEditingTrip(trip);
    setEditOrigin(trip.origin);
    setEditDestination(trip.destination);
    setEditVehicleType(trip.vehicleType);
    setEditCapacityKg(trip.capacityKg);
    setEditFloorPrice(trip.floorPrice ? String(trip.floorPrice) : '');
    setEditRecurrence(trip.recurrence);
    setEditError(null);

    // Format departAt to local ISO string for datetime-local input
    try {
      const d = new Date(trip.departAt);
      const offset = d.getTimezoneOffset() * 60000;
      setEditDepartAt(new Date(d.getTime() - offset).toISOString().slice(0, 16));
    } catch {
      setEditDepartAt('');
    }
  };

  // Submit Edit
  const handleSaveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingTrip) return;

    if (!editDepartAt) {
      setEditError('Vui lòng chọn thời gian khởi hành.');
      return;
    }

    const departDate = new Date(editDepartAt);
    if (isNaN(departDate.getTime())) {
      setEditError('Thời gian khởi hành không hợp lệ.');
      return;
    }

    if (editCapacityKg <= 0) {
      setEditError('Sức chứa vận chuyển phải lớn hơn 0 kg.');
      return;
    }

    setSavingEdit(true);
    setEditError(null);

    try {
      const updateData: TripUpdateRequest = {
        origin: editOrigin.trim(),
        originLat: editingTrip.originLat,
        originLng: editingTrip.originLng,
        destination: editDestination.trim(),
        destinationLat: editingTrip.destinationLat,
        destinationLng: editingTrip.destinationLng,
        departAt: departDate.toISOString(),
        vehicleType: editVehicleType,
        capacityKg: Number(editCapacityKg),
        floorPrice: editFloorPrice ? Number(editFloorPrice) : undefined,
        recurrence: editRecurrence,
        routePolyline: editingTrip.routePolyline,
        distanceKm: editingTrip.distanceKm,
        estimatedDurationMinutes: editingTrip.estimatedDurationMinutes,
      };

      const res = await tripApi.update(editingTrip.id, updateData);
      setFeedbackMessage({ type: 'success', text: 'Thông tin chuyến đi đã được cập nhật thành công.' });
      setEditingTrip(null);
      fetchTrips();
      if (selectedTrip?.id === editingTrip.id) {
        setSelectedTrip(res.data);
      }
    } catch (err: unknown) {
      const message =
        err && typeof err === 'object' && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : 'Không thể cập nhật chuyến đi.';
      setEditError(message || 'Lỗi khi lưu cập nhật.');
    } finally {
      setSavingEdit(false);
    }
  };

  const formatDateTime = (iso: string) => {
    try {
      const d = new Date(iso);
      return d.toLocaleString('vi-VN', {
        hour: '2-digit',
        minute: '2-digit',
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
      });
    } catch {
      return iso;
    }
  };

  const getStatusBadge = (status: TripStatus) => {
    switch (status) {
      case 'SCHEDULED':
        return <span className="trip-status-badge scheduled">Đã lên lịch</span>;
      case 'ACTIVE':
        return <span className="trip-status-badge active">Đang di chuyển</span>;
      case 'PAUSED':
        return <span className="trip-status-badge paused">Tạm dừng nhận</span>;
      case 'COMPLETED':
        return <span className="trip-status-badge completed">Đã hoàn thành</span>;
      case 'CANCELLED':
        return <span className="trip-status-badge cancelled">Đã hủy</span>;
      default:
        return <span className="trip-status-badge">{status}</span>;
    }
  };

  const getVehicleLabel = (vehicle: string) => {
    switch (vehicle) {
      case 'MOTORBIKE':
        return (
          <span className="vehicle-pill">
            <Bike size={14} /> Xe máy
          </span>
        );
      case 'CAR':
        return (
          <span className="vehicle-pill">
            <Car size={14} /> Ô tô
          </span>
        );
      case 'BICYCLE':
        return (
          <span className="vehicle-pill">
            <Navigation size={14} /> Xe đạp / Đi bộ
          </span>
        );
      default:
        return vehicle;
    }
  };

  const getRecurrenceLabel = (rec: string) => {
    switch (rec) {
      case 'DAILY':
        return 'Hàng ngày';
      case 'WEEKDAYS':
        return 'Thứ 2 - Thứ 6';
      case 'WEEKENDS':
        return 'Cuối tuần';
      case 'WEEKLY':
        return 'Hàng tuần';
      default:
        return 'Một lần';
    }
  };

  const getOrderStatusBadge = (status: string) => {
    switch (status) {
      case 'FINDING_SHIPPER':
        return <span className="order-status-pill" style={{ color: '#60a5fa' }}>Đang mở nhận</span>;
      case 'AWAITING_PAYMENT':
        return <span className="order-status-pill" style={{ color: '#f5cb76' }}>Chờ ký quỹ</span>;
      case 'SHIPPER_ASSIGNED':
      case 'ASSIGNED':
      case 'ESCROWED':
        return <span className="order-status-pill" style={{ color: '#34d399' }}>Đã xác nhận</span>;
      case 'IN_DELIVERY':
      case 'PICKED_UP':
      case 'IN_TRANSIT':
        return <span className="order-status-pill" style={{ color: '#ffb17d' }}>Đang giao hàng</span>;
      case 'COMPLETED':
        return <span className="order-status-pill" style={{ color: '#a7f3d0' }}>Hoàn tất</span>;
      default:
        return <span className="order-status-pill">{status}</span>;
    }
  };

  return (
    <div className="trip-page-container">
      {/* Page Header */}
      <div className="trip-header-bar flex-between">
        <div className="trip-header-title">
          <h1>Chuyến đi của tôi</h1>
          <p>Quản lý lộ trình di chuyển, tạm dừng hoặc mở nhận đơn và ghép các đơn hàng tiện đường.</p>
        </div>

        <Link to="/courier/trips/new" className="trip-create-btn">
          <Plus size={18} />
          <span>Đăng chuyến mới</span>
        </Link>
      </div>

      {/* Feedback Alert */}
      {feedbackMessage && (
        <div className={`trip-alert-box ${feedbackMessage.type}`} role="alert">
          {feedbackMessage.type === 'success' ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
          <span>{feedbackMessage.text}</span>
        </div>
      )}

      {/* Filter Tabs */}
      <div className="trip-tabs-row">
        <div className="trip-tabs-group">
          <button
            type="button"
            className={`trip-tab-btn ${statusFilter === 'ALL' ? 'active' : ''}`}
            onClick={() => setStatusFilter('ALL')}
          >
            Tất cả
          </button>
          <button
            type="button"
            className={`trip-tab-btn ${statusFilter === 'SCHEDULED' ? 'active' : ''}`}
            onClick={() => setStatusFilter('SCHEDULED')}
          >
            Đã lên lịch
          </button>
          <button
            type="button"
            className={`trip-tab-btn ${statusFilter === 'ACTIVE' ? 'active' : ''}`}
            onClick={() => setStatusFilter('ACTIVE')}
          >
            Đang đi
          </button>
          <button
            type="button"
            className={`trip-tab-btn ${statusFilter === 'PAUSED' ? 'active' : ''}`}
            onClick={() => setStatusFilter('PAUSED')}
          >
            Tạm dừng
          </button>
          <button
            type="button"
            className={`trip-tab-btn ${statusFilter === 'COMPLETED' ? 'active' : ''}`}
            onClick={() => setStatusFilter('COMPLETED')}
          >
            Đã hoàn thành
          </button>
          <button
            type="button"
            className={`trip-tab-btn ${statusFilter === 'CANCELLED' ? 'active' : ''}`}
            onClick={() => setStatusFilter('CANCELLED')}
          >
            Đã hủy
          </button>
        </div>
      </div>

      {/* Trips Content */}
      {loading ? (
        <div className="trip-loading-state">
          <div className="loading-spinner" />
          <p>Đang tải danh sách chuyến đi...</p>
        </div>
      ) : trips.length === 0 ? (
        <div className="trip-empty-state">
          <div className="empty-icon-bubble">
            <Navigation size={36} />
          </div>
          <h3>Chưa có chuyến đi nào phù hợp</h3>
          <p>Bạn chưa có chuyến đi nào trong danh mục này. Hãy chia sẻ lộ trình di chuyển thường lệ để nhận ghép đơn tiện đường!</p>
          <Link to="/courier/trips/new" className="trip-create-btn-primary">
            <Plus size={18} />
            <span>Đăng chuyến đi ngay</span>
          </Link>
        </div>
      ) : (
        <div className="trip-cards-grid">
          {trips.map((trip) => {
            const hasAttachedOrders = (trip.ordersCount ?? 0) > 0 || (trip.attachedOrders && trip.attachedOrders.length > 0);
            const attachedCount = trip.ordersCount ?? trip.attachedOrders?.length ?? 0;

            return (
              <div key={trip.id} className="trip-card">
                {/* Card Header */}
                <div className="trip-card-header">
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    {getStatusBadge(trip.status)}
                    <span className={`trip-card-attached-badge ${hasAttachedOrders ? 'has-orders' : ''}`}>
                      <Package size={12} />
                      {attachedCount > 0 ? `${attachedCount} đơn ghép` : 'Chưa có đơn'}
                    </span>
                  </div>
                  <div className="trip-card-recurrence">
                    <Calendar size={13} />
                    <span>{getRecurrenceLabel(trip.recurrence)}</span>
                  </div>
                </div>

                {/* Route Body */}
                <div className="trip-route-display">
                  <div className="route-step">
                    <div className="step-dot origin" />
                    <div className="step-info">
                      <span className="step-tag">Điểm đi</span>
                      <strong className="step-address">{trip.origin}</strong>
                    </div>
                  </div>

                  <div className="route-line-connector" />

                  <div className="route-step">
                    <div className="step-dot destination" />
                    <div className="step-info">
                      <span className="step-tag">Điểm đến</span>
                      <strong className="step-address">{trip.destination}</strong>
                    </div>
                  </div>
                </div>

                {/* Details Grid */}
                <div className="trip-details-grid">
                  <div className="detail-item">
                    <Clock size={15} className="detail-icon" />
                    <div>
                      <span className="detail-label">Khởi hành</span>
                      <strong className="detail-value">{formatDateTime(trip.departAt)}</strong>
                    </div>
                  </div>

                  <div className="detail-item">
                    <Package size={15} className="detail-icon" />
                    <div>
                      <span className="detail-label">Sức chứa</span>
                      <strong className="detail-value">{trip.capacityKg} kg</strong>
                    </div>
                  </div>

                  <div className="detail-item">
                    <div className="detail-icon">{getVehicleLabel(trip.vehicleType)}</div>
                  </div>

                  {trip.distanceKm && (
                    <div className="detail-item">
                      <Navigation size={15} className="detail-icon" />
                      <div>
                        <span className="detail-label">Quãng đường</span>
                        <strong className="detail-value">{trip.distanceKm} km</strong>
                      </div>
                    </div>
                  )}

                  {trip.floorPrice && (
                    <div className="detail-item">
                      <DollarSign size={15} className="detail-icon" />
                      <div>
                        <span className="detail-label">Giá sàn nhận</span>
                        <strong className="detail-value highlight">
                          {Number(trip.floorPrice).toLocaleString('vi-VN')} ₫
                        </strong>
                      </div>
                    </div>
                  )}
                </div>

                {/* Sub Controls: Edit, Pause, Complete */}
                {(trip.status === 'SCHEDULED' || trip.status === 'ACTIVE' || trip.status === 'PAUSED') && (
                  <div className="trip-card-actions-row">
                    {/* Edit button */}
                    <button
                      type="button"
                      className="trip-card-sub-btn"
                      title="Chỉnh sửa thông tin chuyến đi"
                      onClick={() => handleOpenEditModal(trip)}
                    >
                      <Edit3 size={13} />
                      <span>Sửa</span>
                    </button>

                    {/* Pause / Resume toggle button */}
                    <button
                      type="button"
                      className={`trip-card-sub-btn pause`}
                      disabled={actionLoadingId === trip.id}
                      title={trip.status === 'PAUSED' ? 'Mở lại nhận đơn' : 'Tạm dừng nhận đơn'}
                      onClick={() => handleTogglePause(trip)}
                    >
                      {trip.status === 'PAUSED' ? <PlayCircle size={13} /> : <PauseCircle size={13} />}
                      <span>{trip.status === 'PAUSED' ? 'Mở nhận đơn' : 'Tạm dừng'}</span>
                    </button>

                    {/* Complete button */}
                    <button
                      type="button"
                      className="trip-card-sub-btn complete"
                      disabled={actionLoadingId === trip.id}
                      title="Hoàn tất chuyến đi"
                      onClick={() => handleCompleteTrip(trip)}
                    >
                      <CheckCircle2 size={13} />
                      <span>Hoàn tất</span>
                    </button>

                    {/* Cancel button */}
                    <button
                      type="button"
                      className="trip-card-sub-btn danger"
                      disabled={actionLoadingId === trip.id}
                      title="Hủy chuyến đi"
                      onClick={() => handleInitiateCancel(trip)}
                    >
                      <XCircle size={13} />
                      <span>Hủy</span>
                    </button>
                  </div>
                )}

                {/* Card Main Footer */}
                <div className="trip-card-footer">
                  <button
                    type="button"
                    className="trip-card-action-btn primary"
                    onClick={() => handleOpenDetailModal(trip, 'orders')}
                  >
                    <Layers size={15} />
                    <span>Chi tiết & Đơn ghép ({attachedCount})</span>
                  </button>

                  <Link
                    to={`/courier/market?tripId=${trip.id}`}
                    className="trip-card-action-btn"
                    style={{
                      backgroundColor: 'var(--bg-tertiary, #242d39)',
                      color: 'var(--text-secondary, #b0bdd2)',
                      border: '1px solid var(--border-subtle, #333d4c)',
                    }}
                    title="Tìm đơn mở trên sàn"
                  >
                    <Search size={15} />
                    <span>Sàn đơn</span>
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* =========================================================================
          MODAL 1: TRIP DETAILS & MATCHING ORDERS (Attached + Suggested + Route Map)
          ========================================================================= */}
      {selectedTrip && (
        <div className="trip-modal-overlay" onClick={() => setSelectedTrip(null)}>
          <div className="trip-modal-container" onClick={(e) => e.stopPropagation()}>
            {/* Modal Header */}
            <div className="trip-modal-header">
              <h2>
                <Navigation size={20} style={{ color: 'var(--primary, #f37021)' }} />
                <span>Chi tiết chuyến đi #{selectedTrip.id}</span>
              </h2>
              <button
                type="button"
                className="trip-modal-close-btn"
                onClick={() => setSelectedTrip(null)}
                aria-label="Đóng"
              >
                <X size={20} />
              </button>
            </div>

            {/* Modal Body */}
            <div className="trip-modal-body">
              {/* Tab navigation */}
              <div className="trip-modal-tabs">
                <button
                  type="button"
                  className={`trip-modal-tab ${detailModalTab === 'orders' ? 'active' : ''}`}
                  onClick={() => setDetailModalTab('orders')}
                >
                  <Package size={16} />
                  <span>Đơn hàng đã ghép</span>
                  <span className="tab-badge">{attachedOrders.length}</span>
                </button>

                <button
                  type="button"
                  className={`trip-modal-tab ${detailModalTab === 'suggestions' ? 'active' : ''}`}
                  onClick={() => setDetailModalTab('suggestions')}
                >
                  <TrendingUp size={16} />
                  <span>Gợi ý tiện đường</span>
                  <span className="tab-badge">{suggestedOrders.length}</span>
                </button>

                <button
                  type="button"
                  className={`trip-modal-tab ${detailModalTab === 'map' ? 'active' : ''}`}
                  onClick={() => setDetailModalTab('map')}
                >
                  <MapPin size={16} />
                  <span>Bản đồ lộ trình</span>
                </button>
              </div>

              {/* Tab 1: Attached Orders */}
              {detailModalTab === 'orders' && (
                <div>
                  {loadingOrders ? (
                    <div className="trip-loading-state" style={{ padding: '30px 0' }}>
                      <div className="loading-spinner" />
                      <p>Đang tải danh sách đơn hàng đã ghép...</p>
                    </div>
                  ) : attachedOrders.length === 0 ? (
                    <div className="trip-empty-state" style={{ padding: '36px 16px', margin: '10px 0' }}>
                      <Package size={32} style={{ color: 'var(--text-muted, #8e9eb8)', marginBottom: '10px' }} />
                      <h4 style={{ color: 'var(--text-primary, #f1f4fb)', margin: '0 0 6px 0' }}>
                        Chưa có đơn hàng nào ghép vào chuyến này
                      </h4>
                      <p style={{ fontSize: '0.9rem', marginBottom: '18px' }}>
                        Khám phá các đơn hàng đang tìm người giao phù hợp với lộ trình của bạn để gia tăng thu nhập.
                      </p>
                      <button
                        type="button"
                        className="trip-modal-btn primary"
                        onClick={() => setDetailModalTab('suggestions')}
                      >
                        <TrendingUp size={15} style={{ marginRight: '6px' }} />
                        Xem gợi ý tiện đường
                      </button>
                    </div>
                  ) : (
                    <div className="trip-orders-list">
                      {attachedOrders.map((ord) => (
                        <div key={ord.id} className="trip-order-item-card">
                          <div className="order-item-header">
                            <span className="order-code-badge">{ord.orderCode}</span>
                            {getOrderStatusBadge(ord.status)}
                          </div>

                          <div className="order-item-product">{ord.productName}</div>

                          <div className="order-route-compact">
                            <div className="order-route-point">
                              <span className="order-point-dot origin" />
                              <span>{ord.pickupAddress}</span>
                            </div>
                            <div className="order-route-point">
                              <span className="order-point-dot dest" />
                              <span>{ord.deliveryAddress}</span>
                            </div>
                          </div>

                          <div className="order-item-footer">
                            <div>
                              <span>Khối lượng: <strong>{ord.weightKg} kg</strong></span>
                              {ord.receiverName && (
                                <span style={{ marginLeft: '12px' }}>
                                  Người nhận: <strong>{ord.receiverName}</strong>
                                </span>
                              )}
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                              {ord.priceAgreed && (
                                <span className="order-price-tag">
                                  {Number(ord.priceAgreed).toLocaleString('vi-VN')} ₫
                                </span>
                              )}
                              <button
                                type="button"
                                className="trip-card-sub-btn"
                                onClick={() => navigate(`/courier/market/${ord.id}`)}
                              >
                                <ExternalLink size={13} />
                                <span>Xem đơn</span>
                              </button>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Tab 2: Suggested Orders Along Route */}
              {detailModalTab === 'suggestions' && (
                <div>
                  {selectedTrip.status === 'PAUSED' && (
                    <div className="trip-alert-box warning" style={{ backgroundColor: 'var(--warning-bg, #352d1c)', color: '#f5cb76' }}>
                      <AlertCircle size={16} />
                      <span>Chuyến đi đang tạm dừng nhận đơn. Vui lòng mở lại nhận đơn để tự động cập nhật gợi ý mới nhất.</span>
                    </div>
                  )}

                  {loadingSuggestions ? (
                    <div className="trip-loading-state" style={{ padding: '30px 0' }}>
                      <div className="loading-spinner" />
                      <p>Đang tìm kiếm đơn hàng tiện đường phù hợp...</p>
                    </div>
                  ) : suggestedOrders.length === 0 ? (
                    <div className="trip-empty-state" style={{ padding: '36px 16px', margin: '10px 0' }}>
                      <Navigation size={32} style={{ color: 'var(--text-muted, #8e9eb8)', marginBottom: '10px' }} />
                      <h4 style={{ color: 'var(--text-primary, #f1f4fb)', margin: '0 0 6px 0' }}>
                        Hiện chưa có đơn mở tiện đường
                      </h4>
                      <p style={{ fontSize: '0.9rem', marginBottom: '18px' }}>
                        Hệ thống sẽ tiếp tục rà soát các đơn hàng mới đăng dọc tuyến đường này. Bạn có thể kiểm tra trực tiếp trên sàn đơn.
                      </p>
                      <Link to={`/courier/market?tripId=${selectedTrip.id}`} className="trip-modal-btn primary">
                        <Search size={15} style={{ marginRight: '6px' }} />
                        Mở sàn đơn hàng
                      </Link>
                    </div>
                  ) : (
                    <div className="trip-orders-list">
                      <div className="trip-matching-tip" style={{ marginTop: 0, marginBottom: '14px' }}>
                        <p>
                          Các đơn hàng dưới đây được tối ưu có điểm lấy và giao lệch không quá 3 km so với lộ trình di chuyển của bạn.
                        </p>
                      </div>

                      {suggestedOrders.map((ord) => (
                        <div key={ord.id} className="trip-order-item-card">
                          <div className="order-item-header">
                            <span className="order-code-badge">{ord.orderCode}</span>
                            {ord.detourKm !== undefined && (
                              <span className="order-detour-tag">
                                <TrendingUp size={12} />
                                Lệch ~{ord.detourKm} km
                              </span>
                            )}
                          </div>

                          <div className="order-item-product">{ord.productName}</div>

                          <div className="order-route-compact">
                            <div className="order-route-point">
                              <span className="order-point-dot origin" />
                              <span>{ord.pickupAddress}</span>
                            </div>
                            <div className="order-route-point">
                              <span className="order-point-dot dest" />
                              <span>{ord.deliveryAddress}</span>
                            </div>
                          </div>

                          <div className="order-item-footer">
                            <div>
                              <span>Khối lượng: <strong>{ord.weightKg} kg</strong></span>
                              {ord.distanceKm && (
                                <span style={{ marginLeft: '12px' }}>
                                  Cự ly: <strong>{ord.distanceKm} km</strong>
                                </span>
                              )}
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                              {ord.priceSuggested && (
                                <span className="order-price-tag">
                                  {Number(ord.priceSuggested).toLocaleString('vi-VN')} ₫
                                </span>
                              )}
                              <button
                                type="button"
                                className="trip-modal-btn primary"
                                style={{ padding: '6px 12px', fontSize: '0.82rem' }}
                                onClick={() => navigate(`/courier/market/${ord.id}?tripId=${selectedTrip.id}`)}
                              >
                                <span>Nhận / Chào giá</span>
                                <ArrowRight size={13} style={{ marginLeft: '4px' }} />
                              </button>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Tab 3: Interactive Route Map */}
              {detailModalTab === 'map' && (
                <div>
                  <div style={{ height: '360px', borderRadius: '12px', overflow: 'hidden', border: '1px solid var(--border-subtle, #333d4c)' }}>
                    <TripRouteMap
                      originLat={selectedTrip.originLat}
                      originLng={selectedTrip.originLng}
                      destinationLat={selectedTrip.destinationLat}
                      destinationLng={selectedTrip.destinationLng}
                      routePolyline={selectedTrip.routePolyline}
                    />
                  </div>

                  <div className="trip-route-metrics" style={{ marginTop: '16px' }}>
                    <div className="metric-box">
                      <span className="metric-label">Quãng đường</span>
                      <strong className="metric-value">{selectedTrip.distanceKm ?? '--'} km</strong>
                    </div>
                    <div className="metric-box">
                      <span className="metric-label">Thời gian dự kiến</span>
                      <strong className="metric-value">{selectedTrip.estimatedDurationMinutes ?? '--'} phút</strong>
                    </div>
                    <div className="metric-box">
                      <span className="metric-label">Phương tiện</span>
                      <strong className="metric-value">{getVehicleLabel(selectedTrip.vehicleType)}</strong>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div className="trip-modal-footer">
              <button
                type="button"
                className="trip-modal-btn secondary"
                onClick={() => setSelectedTrip(null)}
              >
                Đóng
              </button>

              {(selectedTrip.status === 'SCHEDULED' || selectedTrip.status === 'ACTIVE' || selectedTrip.status === 'PAUSED') && (
                <>
                  <button
                    type="button"
                    className="trip-modal-btn secondary"
                    onClick={() => {
                      setSelectedTrip(null);
                      handleOpenEditModal(selectedTrip);
                    }}
                  >
                    <Edit3 size={15} style={{ marginRight: '6px' }} />
                    Chỉnh sửa
                  </button>

                  <button
                    type="button"
                    className="trip-modal-btn primary"
                    disabled={actionLoadingId === selectedTrip.id}
                    onClick={() => handleTogglePause(selectedTrip)}
                  >
                    {selectedTrip.status === 'PAUSED' ? (
                      <>
                        <PlayCircle size={15} style={{ marginRight: '6px' }} />
                        Mở lại nhận đơn
                      </>
                    ) : (
                      <>
                        <PauseCircle size={15} style={{ marginRight: '6px' }} />
                        Tạm dừng nhận đơn
                      </>
                    )}
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {/* =========================================================================
          MODAL 2: EDIT TRIP (Departure, Capacity, Floor Price, Vehicle, Recurrence)
          ========================================================================= */}
      {editingTrip && (
        <div className="trip-modal-overlay" onClick={() => setEditingTrip(null)}>
          <div className="trip-modal-container" style={{ maxWidth: '640px' }} onClick={(e) => e.stopPropagation()}>
            <form onSubmit={handleSaveEdit}>
              <div className="trip-modal-header">
                <h2>
                  <Edit3 size={18} style={{ color: 'var(--primary, #f37021)' }} />
                  <span>Chỉnh sửa thông tin chuyến đi #{editingTrip.id}</span>
                </h2>
                <button
                  type="button"
                  className="trip-modal-close-btn"
                  onClick={() => setEditingTrip(null)}
                  aria-label="Đóng"
                >
                  <X size={18} />
                </button>
              </div>

              <div className="trip-modal-body">
                {editError && (
                  <div className="trip-alert-box error" role="alert">
                    <AlertCircle size={16} />
                    <span>{editError}</span>
                  </div>
                )}

                <div className="trip-edit-grid">
                  {/* Origin */}
                  <div className="trip-field-group" style={{ gridColumn: 'span 2' }}>
                    <label className="trip-label">
                      Điểm khởi hành <span className="required">*</span>
                    </label>
                    <div className="trip-input-wrapper">
                      <MapPin size={16} className="input-icon origin" />
                      <input
                        type="text"
                        className="trip-input"
                        value={editOrigin}
                        onChange={(e) => setEditOrigin(e.target.value)}
                        required
                      />
                    </div>
                  </div>

                  {/* Destination */}
                  <div className="trip-field-group" style={{ gridColumn: 'span 2' }}>
                    <label className="trip-label">
                      Điểm đến <span className="required">*</span>
                    </label>
                    <div className="trip-input-wrapper">
                      <MapPin size={16} className="input-icon destination" />
                      <input
                        type="text"
                        className="trip-input"
                        value={editDestination}
                        onChange={(e) => setEditDestination(e.target.value)}
                        required
                      />
                    </div>
                  </div>

                  {/* Depart At */}
                  <div className="trip-field-group">
                    <label className="trip-label">
                      Thời gian khởi hành <span className="required">*</span>
                    </label>
                    <div className="trip-input-wrapper">
                      <Clock size={16} className="input-icon" />
                      <input
                        type="datetime-local"
                        className="trip-input"
                        value={editDepartAt}
                        onChange={(e) => setEditDepartAt(e.target.value)}
                        required
                      />
                    </div>
                  </div>

                  {/* Vehicle Type */}
                  <div className="trip-field-group">
                    <label className="trip-label">
                      Phương tiện di chuyển <span className="required">*</span>
                    </label>
                    <select
                      className="trip-select"
                      value={editVehicleType}
                      onChange={(e) => setEditVehicleType(e.target.value as VehicleType)}
                      required
                    >
                      <option value="MOTORBIKE">Xe máy</option>
                      <option value="CAR">Ô tô</option>
                      <option value="BICYCLE">Xe đạp / Đi bộ</option>
                    </select>
                  </div>

                  {/* Capacity Kg */}
                  <div className="trip-field-group">
                    <label className="trip-label">
                      Sức chứa tối đa (kg) <span className="required">*</span>
                    </label>
                    <div className="trip-input-wrapper">
                      <Package size={16} className="input-icon" />
                      <input
                        type="number"
                        step="0.5"
                        min="1"
                        max="50"
                        className="trip-input"
                        value={editCapacityKg}
                        onChange={(e) => setEditCapacityKg(Number(e.target.value))}
                        required
                      />
                      <span className="input-suffix">kg</span>
                    </div>
                  </div>

                  {/* Floor Price */}
                  <div className="trip-field-group">
                    <label className="trip-label">Giá sàn nhận tối thiểu</label>
                    <div className="trip-input-wrapper">
                      <DollarSign size={16} className="input-icon" />
                      <input
                        type="number"
                        step="5000"
                        min="0"
                        placeholder="Để trống nếu không yêu cầu"
                        className="trip-input"
                        value={editFloorPrice}
                        onChange={(e) => setEditFloorPrice(e.target.value)}
                      />
                      <span className="input-suffix">₫</span>
                    </div>
                  </div>

                  {/* Recurrence */}
                  <div className="trip-field-group" style={{ gridColumn: 'span 2' }}>
                    <label className="trip-label">Tần suất lặp lại</label>
                    <select
                      className="trip-select"
                      value={editRecurrence}
                      onChange={(e) => setEditRecurrence(e.target.value as TripRecurrence)}
                    >
                      <option value="NONE">Chuyến đi một lần</option>
                      <option value="DAILY">Hàng ngày</option>
                      <option value="WEEKDAYS">Thứ 2 đến Thứ 6</option>
                      <option value="WEEKENDS">Cuối tuần</option>
                      <option value="WEEKLY">Hàng tuần</option>
                    </select>
                  </div>
                </div>
              </div>

              <div className="trip-modal-footer">
                <button
                  type="button"
                  className="trip-modal-btn secondary"
                  onClick={() => setEditingTrip(null)}
                  disabled={savingEdit}
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  className="trip-modal-btn primary"
                  disabled={savingEdit}
                >
                  {savingEdit ? 'Đang lưu...' : 'Lưu thay đổi'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* =========================================================================
          MODAL 3: CANCEL TRIP CONFIRMATION (WITH ATTACHED ORDERS CHECK)
          ========================================================================= */}
      {cancellingTrip && (
        <div className="trip-modal-overlay" onClick={() => setCancellingTrip(null)}>
          <div className="trip-modal-container" style={{ maxWidth: '520px' }} onClick={(e) => e.stopPropagation()}>
            <div className="trip-modal-header">
              <h2>
                <AlertCircle size={20} style={{ color: '#f87171' }} />
                <span>Xác nhận hủy chuyến đi #{cancellingTrip.id}</span>
              </h2>
              <button
                type="button"
                className="trip-modal-close-btn"
                onClick={() => setCancellingTrip(null)}
                aria-label="Đóng"
              >
                <X size={18} />
              </button>
            </div>

            <div className="trip-modal-body">
              {(cancellingTrip.ordersCount ?? cancellingTrip.attachedOrders?.length ?? 0) > 0 ? (
                <div className="trip-alert-box error" style={{ margin: 0 }}>
                  <AlertCircle size={20} style={{ flexShrink: 0 }} />
                  <div>
                    <strong style={{ display: 'block', marginBottom: '4px' }}>
                      Không thể hủy trực tiếp chuyến đi này!
                    </strong>
                    <span>
                      Chuyến đi đang có đơn hàng được ghép nối. Để đảm bảo an toàn cho quá trình giao nhận,
                      bạn cần hủy hoặc xử lý từng đơn hàng trước khi có thể hủy chuyến đi.
                    </span>
                  </div>
                </div>
              ) : (
                <p style={{ margin: 0, color: 'var(--text-secondary, #b0bdd2)', lineHeight: 1.6 }}>
                  Bạn có chắc chắn muốn hủy chuyến đi từ <strong style={{ color: 'var(--text-primary, #f1f4fb)' }}>{cancellingTrip.origin}</strong> đến{' '}
                  <strong style={{ color: 'var(--text-primary, #f1f4fb)' }}>{cancellingTrip.destination}</strong> không?
                  Sau khi hủy, chuyến đi sẽ không nhận thêm bất kỳ gợi ý đơn hàng nào.
                </p>
              )}
            </div>

            <div className="trip-modal-footer">
              <button
                type="button"
                className="trip-modal-btn secondary"
                onClick={() => setCancellingTrip(null)}
              >
                {(cancellingTrip.ordersCount ?? cancellingTrip.attachedOrders?.length ?? 0) > 0 ? 'Đã hiểu' : 'Quay lại'}
              </button>

              {(cancellingTrip.ordersCount ?? cancellingTrip.attachedOrders?.length ?? 0) === 0 && (
                <button
                  type="button"
                  className="trip-modal-btn"
                  style={{ backgroundColor: '#ef4444', color: '#fff' }}
                  disabled={actionLoadingId === cancellingTrip.id}
                  onClick={handleConfirmCancelTrip}
                >
                  {actionLoadingId === cancellingTrip.id ? 'Đang hủy...' : 'Xác nhận hủy'}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
