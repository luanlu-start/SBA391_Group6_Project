import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
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
} from 'lucide-react';
import { tripApi } from '../api/tripApi';
import type { Trip, TripStatus } from '../types/trip';
import '../styles/trip.css';

export default function CourierTripsPage() {
  const [trips, setTrips] = useState<Trip[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<TripStatus | 'ALL'>('ALL');
  const [cancellingId, setCancellingId] = useState<number | null>(null);
  const [feedbackMessage, setFeedbackMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const fetchTrips = async () => {
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
  };

  useEffect(() => {
    fetchTrips();
  }, [statusFilter]);

  const handleCancelTrip = async (tripId: number) => {
    if (!window.confirm('Bạn có chắc chắn muốn hủy chuyến đi này không?')) return;

    setCancellingId(tripId);
    setFeedbackMessage(null);
    try {
      await tripApi.cancel(tripId);
      setFeedbackMessage({ type: 'success', text: 'Đã hủy chuyến đi thành công.' });
      fetchTrips();
    } catch (err: unknown) {
      const message =
        err && typeof err === 'object' && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : 'Không thể hủy chuyến đi vào lúc này.';
      setFeedbackMessage({ type: 'error', text: message || 'Lỗi khi hủy chuyến đi.' });
    } finally {
      setCancellingId(null);
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
      case 'COMPLETED':
        return <span className="trip-status-badge completed">Đã hoàn thành</span>;
      case 'CANCELLED':
        return <span className="trip-status-badge cancelled">Đã hủy</span>;
      case 'PAUSED':
        return <span className="trip-status-badge paused">Tạm dừng nhận</span>;
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

  return (
    <div className="trip-page-container">
      {/* Page Header */}
      <div className="trip-header-bar flex-between">
        <div className="trip-header-title">
          <h1>Chuyến đi của tôi</h1>
          <p>Quản lý các lộ trình tiện đường đã đăng và nhận gợi ý đơn hàng ghép tuyến tự động.</p>
        </div>

        <Link to="/courier/trips/new" className="trip-create-btn">
          <Plus size={18} />
          <span>Đăng chuyến mới</span>
        </Link>
      </div>

      {feedbackMessage && (
        <div className={`trip-alert-box ${feedbackMessage.type}`} role="alert">
          {feedbackMessage.type === 'success' ? <Clock size={18} /> : <AlertCircle size={18} />}
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
          <p>Bạn chưa đăng chuyến đi nào trong danh mục này. Hãy chia sẻ lộ trình để bắt đầu ghép đơn tiện đường!</p>
          <Link to="/courier/trips/new" className="trip-create-btn-primary">
            <Plus size={18} />
            <span>Đăng chuyến đi ngay</span>
          </Link>
        </div>
      ) : (
        <div className="trip-cards-grid">
          {trips.map((trip) => (
            <div key={trip.id} className="trip-card">
              {/* Card Header */}
              <div className="trip-card-header">
                <div className="trip-card-status">{getStatusBadge(trip.status)}</div>
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
                      <span className="detail-label">Giá sàn</span>
                      <strong className="detail-value highlight">
                        {Number(trip.floorPrice).toLocaleString('vi-VN')} ₫
                      </strong>
                    </div>
                  </div>
                )}
              </div>

              {/* Card Actions */}
              <div className="trip-card-footer">
                <Link
                  to={`/courier/market?tripId=${trip.id}`}
                  className="trip-card-action-btn primary"
                >
                  <Search size={15} />
                  <span>Tìm đơn ghép cùng tuyến</span>
                </Link>

                {trip.status === 'SCHEDULED' && (
                  <button
                    type="button"
                    className="trip-card-action-btn danger"
                    disabled={cancellingId === trip.id}
                    onClick={() => handleCancelTrip(trip.id)}
                  >
                    <XCircle size={15} />
                    <span>{cancellingId === trip.id ? 'Đang hủy...' : 'Hủy chuyến'}</span>
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
