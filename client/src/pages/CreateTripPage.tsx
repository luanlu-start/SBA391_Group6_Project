import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  Navigation,
  MapPin,
  Clock,
  Bike,
  Car,
  Package,
  Calendar,
  DollarSign,
  ChevronLeft,
  AlertCircle,
  CheckCircle2,
  LocateFixed,
} from 'lucide-react';
import { tripApi } from '../api/tripApi';
import type { VehicleType, TripRecurrence } from '../types/trip';
import TripRouteMap from '../features/trip/components/TripRouteMap';
import '../styles/trip.css';

export default function CreateTripPage() {
  const navigate = useNavigate();

  // Form State
  const [origin, setOrigin] = useState('Đại học FPT Cần Thơ, 600 Nguyễn Văn Cừ');
  const [originLat, setOriginLat] = useState<number | null>(10.0299);
  const [originLng, setOriginLng] = useState<number | null>(105.7705);

  const [destination, setDestination] = useState('');
  const [destinationLat, setDestinationLat] = useState<number | null>(null);
  const [destinationLng, setDestinationLng] = useState<number | null>(null);

  // Default to 1 hour from now formatted for datetime-local
  const getDefaultDepartTime = () => {
    const d = new Date(Date.now() + 60 * 60 * 1000);
    d.setMinutes(Math.ceil(d.getMinutes() / 15) * 15, 0, 0);
    const offset = d.getTimezoneOffset() * 60000;
    return new Date(d.getTime() - offset).toISOString().slice(0, 16);
  };

  const [departTime, setDepartTime] = useState<string>(getDefaultDepartTime());
  const [vehicleType, setVehicleType] = useState<VehicleType>('MOTORBIKE');
  const [capacityKg, setCapacityKg] = useState<number>(10);
  const [floorPrice, setFloorPrice] = useState<string>('');
  const [recurrence, setRecurrence] = useState<TripRecurrence>('NONE');

  // Calculated route info
  const [distanceKm, setDistanceKm] = useState<number | null>(null);
  const [durationMinutes, setDurationMinutes] = useState<number | null>(null);
  const [routePolyline, setRoutePolyline] = useState<string | undefined>(undefined);

  // UI State
  const [activePickingMode, setActivePickingMode] = useState<'origin' | 'destination' | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Fast preset capacity selection
  const capacityPresets = [3, 5, 10, 15, 20];

  // Pick on map handler
  const handleMapSelect = (
    type: 'origin' | 'destination',
    lat: number,
    lng: number,
    address?: string
  ) => {
    if (type === 'origin') {
      setOriginLat(lat);
      setOriginLng(lng);
      if (address) setOrigin(address);
    } else {
      setDestinationLat(lat);
      setDestinationLng(lng);
      if (address) setDestination(address);
    }
    setActivePickingMode(null);
  };

  // Get current GPS location for origin
  const handleUseCurrentLocation = () => {
    if (!navigator.geolocation) {
      alert('Trình duyệt không hỗ trợ định vị GPS.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const lat = Number(pos.coords.latitude.toFixed(6));
        const lng = Number(pos.coords.longitude.toFixed(6));
        setOriginLat(lat);
        setOriginLng(lng);

        try {
          const res = await fetch(`https://photon.komoot.io/reverse?lat=${lat}&lon=${lng}`);
          if (res.ok) {
            const data = await res.json();
            if (data?.features?.[0]?.properties) {
              const props = data.features[0].properties;
              const parts = [
                props.name,
                props.housenumber ? `${props.housenumber} ${props.street || ''}`.trim() : props.street,
                props.district,
                props.city,
              ].filter(Boolean);
              if (parts.length > 0) {
                setOrigin(parts.join(', '));
              }
            }
          }
        } catch {
          setOrigin(`Tọa độ: ${lat}, ${lng}`);
        }
      },
      (err) => {
        alert('Không thể lấy vị trí hiện tại: ' + err.message);
      },
      { timeout: 7000 }
    );
  };

  // Submit Handler
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setSuccessMessage(null);

    // Form Validations
    if (!origin.trim() || !originLat || !originLng) {
      setErrorMessage('Vui lòng nhập và xác định điểm xuất phát trên bản đồ.');
      return;
    }
    if (!destination.trim() || !destinationLat || !destinationLng) {
      setErrorMessage('Vui lòng nhập và chọn điểm đến trên bản đồ.');
      return;
    }

    const selectedDate = new Date(departTime);
    if (isNaN(selectedDate.getTime()) || selectedDate.getTime() <= Date.now()) {
      setErrorMessage('Thời gian khởi hành phải ở tương lai.');
      return;
    }

    if (capacityKg <= 0) {
      setErrorMessage('Sức chứa vận chuyển phải lớn hơn 0 kg.');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        origin: origin.trim(),
        originLat,
        originLng,
        destination: destination.trim(),
        destinationLat,
        destinationLng,
        departAt: selectedDate.toISOString(),
        vehicleType,
        capacityKg: Number(capacityKg),
        floorPrice: floorPrice ? Number(floorPrice) : undefined,
        recurrence,
        routePolyline,
        distanceKm: distanceKm ?? undefined,
        estimatedDurationMinutes: durationMinutes ?? undefined,
      };

      await tripApi.create(payload);
      setSuccessMessage('Đăng chuyến đi thành công! Hệ thống đang kích hoạt gợi ý ghép đơn tiện đường.');
      setTimeout(() => {
        navigate('/courier/trips');
      }, 1500);
    } catch (err: unknown) {
      const message =
        err && typeof err === 'object' && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : 'Đã có lỗi xảy ra khi tạo chuyến đi. Vui lòng kiểm tra lại thông tin.';
      setErrorMessage(message || 'Không thể đăng chuyến đi. Vui lòng thử lại sau.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="trip-page-container">
      {/* Navigation Header */}
      <div className="trip-header-bar">
        <Link to="/courier/trips" className="trip-back-btn">
          <ChevronLeft size={20} />
          <span>Danh sách chuyến đi</span>
        </Link>
        <div className="trip-header-title">
          <h1>Đăng chuyến đi tiện đường</h1>
          <p>Chia sẻ hành trình của bạn để hệ thống tự động gợi ý các đơn hàng cùng tuyến, tăng thu nhập.</p>
        </div>
      </div>

      {errorMessage && (
        <div className="trip-alert-box error" role="alert">
          <AlertCircle size={20} />
          <span>{errorMessage}</span>
        </div>
      )}

      {successMessage && (
        <div className="trip-alert-box success" role="status">
          <CheckCircle2 size={20} />
          <span>{successMessage}</span>
        </div>
      )}

      <form onSubmit={handleSubmit} className="trip-create-layout">
        {/* Left Column: Form Inputs */}
        <div className="trip-form-card">
          <div className="trip-form-section">
            <h2 className="section-title">
              <Navigation size={18} className="section-icon" />
              Lộ trình di chuyển
            </h2>

            {/* Origin Field */}
            <div className="trip-field-group">
              <label htmlFor="origin-input" className="trip-label">
                Điểm xuất phát <span className="required">*</span>
              </label>
              <div className="trip-input-wrapper">
                <MapPin size={18} className="input-icon origin" />
                <input
                  id="origin-input"
                  type="text"
                  className="trip-input"
                  placeholder="Nhập địa chỉ hoặc nhấp nút ghim..."
                  value={origin}
                  onChange={(e) => setOrigin(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="trip-action-btn"
                  title="Lấy vị trí hiện tại của bạn"
                  onClick={handleUseCurrentLocation}
                >
                  <LocateFixed size={16} />
                  <span>Vị trí của tôi</span>
                </button>
                <button
                  type="button"
                  className={`trip-action-btn ${activePickingMode === 'origin' ? 'active' : ''}`}
                  onClick={() =>
                    setActivePickingMode(activePickingMode === 'origin' ? null : 'origin')
                  }
                >
                  <MapPin size={16} />
                  <span>Ghim bản đồ</span>
                </button>
              </div>
            </div>

            {/* Destination Field */}
            <div className="trip-field-group">
              <label htmlFor="destination-input" className="trip-label">
                Điểm đến <span className="required">*</span>
              </label>
              <div className="trip-input-wrapper">
                <MapPin size={18} className="input-icon destination" />
                <input
                  id="destination-input"
                  type="text"
                  className="trip-input"
                  placeholder="Nhập địa chỉ điểm đến hoặc ghim trên bản đồ..."
                  value={destination}
                  onChange={(e) => setDestination(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className={`trip-action-btn ${activePickingMode === 'destination' ? 'active' : ''}`}
                  onClick={() =>
                    setActivePickingMode(activePickingMode === 'destination' ? null : 'destination')
                  }
                >
                  <MapPin size={16} />
                  <span>Ghim bản đồ</span>
                </button>
              </div>
            </div>

            {/* Departure Time */}
            <div className="trip-field-group">
              <label htmlFor="depart-time-input" className="trip-label">
                Thời gian khởi hành dự kiến <span className="required">*</span>
              </label>
              <div className="trip-input-wrapper">
                <Clock size={18} className="input-icon" />
                <input
                  id="depart-time-input"
                  type="datetime-local"
                  className="trip-input"
                  value={departTime}
                  min={new Date().toISOString().slice(0, 16)}
                  onChange={(e) => setDepartTime(e.target.value)}
                  required
                />
              </div>
            </div>
          </div>

          {/* Vehicle & Capacity Section */}
          <div className="trip-form-section">
            <h2 className="section-title">
              <Package size={18} className="section-icon" />
              Phương tiện & Khả năng vận chuyển
            </h2>

            {/* Vehicle Selection Cards */}
            <div className="trip-field-group">
              <label className="trip-label">
                Phương tiện di chuyển <span className="required">*</span>
              </label>
              <div className="trip-vehicle-grid">
                <button
                  type="button"
                  className={`vehicle-card ${vehicleType === 'MOTORBIKE' ? 'selected' : ''}`}
                  onClick={() => setVehicleType('MOTORBIKE')}
                >
                  <Bike size={24} />
                  <span className="vehicle-name">Xe máy</span>
                  <span className="vehicle-sub">Tối đa 20 kg</span>
                </button>

                <button
                  type="button"
                  className={`vehicle-card ${vehicleType === 'CAR' ? 'selected' : ''}`}
                  onClick={() => setVehicleType('CAR')}
                >
                  <Car size={24} />
                  <span className="vehicle-name">Ô tô / Xe bán tải</span>
                  <span className="vehicle-sub">Tối đa 50 kg</span>
                </button>

                <button
                  type="button"
                  className={`vehicle-card ${vehicleType === 'BICYCLE' ? 'selected' : ''}`}
                  onClick={() => setVehicleType('BICYCLE')}
                >
                  <Navigation size={24} />
                  <span className="vehicle-name">Xe đạp / Đi bộ</span>
                  <span className="vehicle-sub">Gọn nhẹ ≤ 5 kg</span>
                </button>
              </div>
            </div>

            {/* Capacity Field */}
            <div className="trip-field-group">
              <label htmlFor="capacity-input" className="trip-label">
                Khối lượng có thể nhận chở (kg) <span className="required">*</span>
              </label>
              <div className="capacity-control-row">
                <div className="trip-input-wrapper">
                  <Package size={18} className="input-icon" />
                  <input
                    id="capacity-input"
                    type="number"
                    step="0.5"
                    min="1"
                    max="50"
                    className="trip-input"
                    value={capacityKg}
                    onChange={(e) => setCapacityKg(parseFloat(e.target.value) || 0)}
                    required
                  />
                  <span className="input-suffix">kg</span>
                </div>
                <div className="presets-wrapper">
                  {capacityPresets.map((preset) => (
                    <button
                      key={preset}
                      type="button"
                      className={`preset-pill ${capacityKg === preset ? 'active' : ''}`}
                      onClick={() => setCapacityKg(preset)}
                    >
                      {preset} kg
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* Floor Price */}
            <div className="trip-field-group">
              <label htmlFor="floor-price-input" className="trip-label">
                Giá sàn mong muốn cho mỗi đơn (tùy chọn)
              </label>
              <div className="trip-input-wrapper">
                <DollarSign size={18} className="input-icon" />
                <input
                  id="floor-price-input"
                  type="number"
                  step="1000"
                  min="0"
                  placeholder="Ví dụ: 30000 (để trống nếu nhận mọi mức giá phù hợp)"
                  className="trip-input"
                  value={floorPrice}
                  onChange={(e) => setFloorPrice(e.target.value)}
                />
                <span className="input-suffix">₫</span>
              </div>
              <p className="trip-helper-text">
                Hệ thống sẽ ưu tiên gợi ý các đơn hàng có giá cước bằng hoặc cao hơn mức này.
              </p>
            </div>

            {/* Recurrence Selection */}
            <div className="trip-field-group">
              <label htmlFor="recurrence-select" className="trip-label">
                Lặp lại chuyến đi
              </label>
              <div className="trip-input-wrapper">
                <Calendar size={18} className="input-icon" />
                <select
                  id="recurrence-select"
                  className="trip-select"
                  value={recurrence}
                  onChange={(e) => setRecurrence(e.target.value as TripRecurrence)}
                >
                  <option value="NONE">Chuyến một lần (Không lặp lại)</option>
                  <option value="DAILY">Hàng ngày (Mỗi ngày vào giờ này)</option>
                  <option value="WEEKDAYS">Các ngày trong tuần (Thứ 2 đến Thứ 6)</option>
                  <option value="WEEKENDS">Cuối tuần (Thứ 7 & Chủ Nhật)</option>
                  <option value="WEEKLY">Hàng tuần (Cùng ngày này mỗi tuần)</option>
                </select>
              </div>
            </div>
          </div>

          {/* Submit Actions */}
          <div className="trip-form-actions">
            <button
              type="submit"
              className="trip-submit-btn"
              disabled={submitting}
            >
              {submitting ? (
                <span>Đang xử lý đăng chuyến...</span>
              ) : (
                <>
                  <Navigation size={18} />
                  <span>Xác nhận đăng chuyến đi</span>
                </>
              )}
            </button>
            <Link to="/courier/trips" className="trip-cancel-btn">
              Hủy bỏ
            </Link>
          </div>
        </div>

        {/* Right Column: Interactive Route Map */}
        <div className="trip-map-card">
          <div className="trip-map-header">
            <h3>Bản đồ lộ trình tiện đường</h3>
            <span className="map-badge">Trực quan hóa</span>
          </div>

          <TripRouteMap
            originLat={originLat}
            originLng={originLng}
            destinationLat={destinationLat}
            destinationLng={destinationLng}
            activePickingMode={activePickingMode}
            onSelectPoint={handleMapSelect}
            onRouteCalculated={(dist, dur, polyline) => {
              setDistanceKm(dist);
              setDurationMinutes(dur);
              setRoutePolyline(polyline);
            }}
          />

          {/* Route Summary Statistics */}
          <div className="trip-route-metrics">
            <div className="metric-box">
              <span className="metric-label">Quãng đường</span>
              <span className="metric-value">
                {distanceKm !== null ? `${distanceKm} km` : '---'}
              </span>
            </div>
            <div className="metric-box">
              <span className="metric-label">Thời gian</span>
              <span className="metric-value">
                {durationMinutes !== null ? `~${durationMinutes} phút` : '---'}
              </span>
            </div>
            <div className="metric-box">
              <span className="metric-label">Trạng thái</span>
              <span className="metric-value highlight">
                {originLat && destinationLat ? 'Đã thiết lập' : 'Chờ chọn điểm'}
              </span>
            </div>
          </div>

          <div className="trip-matching-tip">
            <p>
              💡 <strong>Gợi ý thông minh:</strong> Khi chuyến đi được đăng, hệ thống sẽ tự động quét
              và thông báo cho bạn các đơn hàng có điểm gửi và điểm giao nằm dọc lộ trình (độ lệch
              tối đa 3 km).
            </p>
          </div>
        </div>
      </form>
    </div>
  );
}
