import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Layers,
  Clock,
  Compass,
  CheckCircle2,
  AlertCircle,
  Package,
  Weight,
  ExternalLink,
  Navigation,
  RefreshCw,
  PlusCircle,
  ListOrdered,
  Truck,
} from 'lucide-react';
import { batchApi } from '../api/batchApi';
import { tripApi } from '../api/tripApi';
import type {
  DeliveryBatch,
  EligibleBatchOrder,
  RouteOptimizationResult,
  BatchStop,
} from '../types/batch';
import type { Trip } from '../types/trip';
import BatchRouteMap from '../features/batch/components/BatchRouteMap';
import '../styles/batch.css';

export default function CourierBatchPage() {
  const [activeTab, setActiveTab] = useState<'CREATE' | 'LIST'>('CREATE');

  // Dữ liệu đơn hàng khả dụng
  const [eligibleOrders, setEligibleOrders] = useState<EligibleBatchOrder[]>([]);
  const [loadingOrders, setLoadingOrders] = useState(false);
  const [selectedOrderIds, setSelectedOrderIds] = useState<number[]>([]);

  // Chuyến đi liên kết (tùy chọn)
  const [myTrips, setMyTrips] = useState<Trip[]>([]);
  const [selectedTripId, setSelectedTripId] = useState<number | null>(null);

  // Kết quả tối ưu lộ trình
  const [optResult, setOptResult] = useState<RouteOptimizationResult | null>(null);
  const [isOptimizing, setIsOptimizing] = useState(false);
  const [batchName, setBatchName] = useState('');
  const [batchNotes, setBatchNotes] = useState('');

  // Tạo đợt giao
  const [isCreating, setIsCreating] = useState(false);
  const [alertInfo, setAlertInfo] = useState<{ type: 'success' | 'danger' | 'warning'; text: string } | null>(null);

  // Danh sách lượt giao gộp đã tạo
  const [myBatches, setMyBatches] = useState<DeliveryBatch[]>([]);
  const [loadingBatches, setLoadingBatches] = useState(false);
  const [activeBatchDetail, setActiveBatchDetail] = useState<DeliveryBatch | null>(null);

  // 1. Tải danh sách đơn hàng đủ điều kiện & chuyến đi
  const fetchEligibleOrders = async () => {
    try {
      setLoadingOrders(true);
      const res = await batchApi.getEligibleOrders();
      if (res.data) {
        setEligibleOrders(res.data);
      }
    } catch {
      setAlertInfo({ type: 'danger', text: 'Không thể tải danh sách đơn hàng khả dụng. Vui lòng thử lại sau.' });
    } finally {
      setLoadingOrders(false);
    }
  };

  const fetchMyTrips = async () => {
    try {
      const res = await tripApi.getMyTrips({ size: 20 });
      if (res.data?.content) {
        // Lấy các chuyến đi đang hoạt động hoặc lên lịch
        const active = res.data.content.filter(t => t.status === 'SCHEDULED' || t.status === 'ACTIVE');
        setMyTrips(active);
      }
    } catch {
      // Bỏ qua nếu lỗi
    }
  };

  const fetchMyBatches = async () => {
    try {
      setLoadingBatches(true);
      const res = await batchApi.getMyBatches({ size: 20 });
      if (res.data?.content) {
        setMyBatches(res.data.content);
        if (res.data.content.length > 0 && !activeBatchDetail) {
          setActiveBatchDetail(res.data.content[0]);
        }
      }
    } catch {
      // Bỏ qua nếu lỗi
    } finally {
      setLoadingBatches(false);
    }
  };

  useEffect(() => {
    fetchEligibleOrders();
    fetchMyTrips();
    fetchMyBatches();
  }, []);

  // Chọn / bỏ chọn đơn hàng
  const toggleOrderSelection = (id: number) => {
    setSelectedOrderIds(prev => {
      let next: number[];
      if (prev.includes(id)) {
        next = prev.filter(item => item !== id);
      } else {
        if (prev.length >= 5) {
          setAlertInfo({ type: 'warning', text: 'Một lượt giao gộp tối đa 5 đơn hàng theo quy định.' });
          return prev;
        }
        next = [...prev, id];
      }
      setOptResult(null); // Reset kết quả tối ưu khi thay đổi tập đơn
      return next;
    });
  };

  const selectAllEligible = () => {
    const ids = eligibleOrders.slice(0, 5).map(o => o.id);
    setSelectedOrderIds(ids);
    setOptResult(null);
  };

  const clearSelection = () => {
    setSelectedOrderIds([]);
    setOptResult(null);
  };

  // Tính tổng tải trọng các đơn được chọn
  const selectedOrders = eligibleOrders.filter(o => selectedOrderIds.includes(o.id));
  const totalSelectedWeight = selectedOrders.reduce((sum, o) => sum + (Number(o.weightKg) || 0), 0);

  const selectedTrip = myTrips.find(t => t.id === selectedTripId);
  const maxCapacity = selectedTrip?.capacityKg ?? 20;
  const isWeightExceeded = totalSelectedWeight > maxCapacity;

  // 2. Gợi ý và tối ưu hóa lộ trình
  const handleOptimizeRoute = async () => {
    if (selectedOrderIds.length < 2) {
      setAlertInfo({ type: 'warning', text: 'Vui lòng chọn từ 2 đến 5 đơn hàng để thực hiện gộp lộ trình.' });
      return;
    }
    if (isWeightExceeded) {
      setAlertInfo({
        type: 'danger',
        text: `Tổng khối lượng (${totalSelectedWeight.toFixed(1)} kg) vượt quá sức chứa cho phép (${maxCapacity} kg). Vui lòng bớt đơn hàng.`,
      });
      return;
    }

    try {
      setIsOptimizing(true);
      setAlertInfo(null);

      const payload = {
        orderIds: selectedOrderIds,
        tripId: selectedTripId,
        currentLatitude: selectedTrip?.originLat ?? null,
        currentLongitude: selectedTrip?.originLng ?? null,
      };

      const res = await batchApi.optimizeRoute(payload);
      if (res.data) {
        setOptResult(res.data);
        setAlertInfo({ type: 'success', text: res.data.message || 'Lộ trình điểm dừng đã được tối ưu hóa thành công!' });
      }
    } catch (err: unknown) {
      const msg = (err as { message?: string })?.message || 'Có lỗi xảy ra khi tính toán lộ trình tối ưu.';
      setAlertInfo({ type: 'danger', text: msg });
    } finally {
      setIsOptimizing(false);
    }
  };

  // 3. Tạo lượt giao gộp
  const handleCreateBatch = async () => {
    if (!optResult || selectedOrderIds.length < 2) {
      setAlertInfo({ type: 'warning', text: 'Vui lòng tối ưu hóa lộ trình trước khi tạo lượt giao.' });
      return;
    }

    try {
      setIsCreating(true);
      setAlertInfo(null);

      const payload = {
        orderIds: selectedOrderIds,
        tripId: selectedTripId,
        batchName: batchName.trim() || undefined,
        notes: batchNotes.trim() || undefined,
      };

      const res = await batchApi.create(payload);
      if (res.data) {
        setAlertInfo({ type: 'success', text: `Tạo lượt giao gộp thành công! Mã lượt giao: ${res.data.batchCode}` });
        setSelectedOrderIds([]);
        setOptResult(null);
        setBatchName('');
        setBatchNotes('');
        // Chuyển tab sang quản lý lượt giao
        setActiveTab('LIST');
        fetchMyBatches();
        fetchEligibleOrders();
      }
    } catch (err: unknown) {
      const msg = (err as { message?: string })?.message || 'Không thể tạo lượt giao gộp. Vui lòng kiểm tra lại.';
      setAlertInfo({ type: 'danger', text: msg });
    } finally {
      setIsCreating(false);
    }
  };

  // Cập nhật khoảng cách & thời gian thực tế từ đường bộ OSRM
  const handleRoadRouteCalculated = (distKm: number, durationMin: number) => {
    setOptResult(prev => {
      if (!prev) return prev;
      return {
        ...prev,
        totalDistanceKm: distKm,
        estimatedDurationMinutes: durationMin,
      };
    });
  };

  // 4. Đánh dấu hoàn thành điểm dừng
  const handleCompleteStop = async (stopId: number) => {
    if (!activeBatchDetail) return;
    try {
      const res = await batchApi.completeStop(activeBatchDetail.id, stopId);
      if (res.data) {
        setActiveBatchDetail(res.data);
        fetchMyBatches();
      }
    } catch {
      setAlertInfo({ type: 'danger', text: 'Không thể cập nhật trạng thái điểm dừng.' });
    }
  };

  // 5. Mở chỉ đường Google Maps
  const openGoogleMapsRoute = (stops: BatchStop[]) => {
    if (!stops || stops.length === 0) return;
    const origin = `${stops[0].latitude},${stops[0].longitude}`;
    const destination = `${stops[stops.length - 1].latitude},${stops[stops.length - 1].longitude}`;

    if (stops.length <= 2) {
      window.open(`https://www.google.com/maps/dir/?api=1&origin=${origin}&destination=${destination}&travelmode=driving`, '_blank');
      return;
    }

    const waypoints = stops.slice(1, stops.length - 1)
      .map(s => `${s.latitude},${s.longitude}`)
      .join('|');

    window.open(`https://www.google.com/maps/dir/?api=1&origin=${origin}&destination=${destination}&waypoints=${waypoints}&travelmode=driving`, '_blank');
  };

  return (
    <div className="batch-page-container">
      {/* Header Bar */}
      <div className="batch-header-bar flex-between">
        <div className="batch-header-title">
          <h1>
            <Layers size={28} color="#f37021" aria-hidden="true" /> Gộp đơn & Tối ưu lộ trình
          </h1>
          <p>Gộp nhiều đơn hàng cùng tuyến để vận chuyển trong một lượt và tối ưu hóa thứ tự các điểm dừng lấy - giao hàng.</p>
        </div>

        <div className="batch-header-actions" style={{ display: 'flex', gap: '10px' }}>
          <Link to="/courier/market" className="batch-btn-secondary" style={{ textDecoration: 'none' }}>
            <Package size={16} /> Sàn đơn
          </Link>
          <Link to="/courier/trips" className="batch-btn-secondary" style={{ textDecoration: 'none' }}>
            <Navigation size={16} /> Chuyến đi của tôi
          </Link>
        </div>
      </div>

      {/* Tabs Điều Hướng */}
      <div className="batch-tabs-nav" role="tablist">
        <button
          type="button"
          className={`batch-tab-btn ${activeTab === 'CREATE' ? 'active' : ''}`}
          onClick={() => setActiveTab('CREATE')}
          role="tab"
          aria-selected={activeTab === 'CREATE'}
        >
          <PlusCircle size={18} /> Lập lộ trình gộp đơn mới
        </button>
        <button
          type="button"
          className={`batch-tab-btn ${activeTab === 'LIST' ? 'active' : ''}`}
          onClick={() => {
            setActiveTab('LIST');
            fetchMyBatches();
          }}
          role="tab"
          aria-selected={activeTab === 'LIST'}
        >
          <ListOrdered size={18} /> Lượt giao đang thực hiện ({myBatches.length})
        </button>
      </div>

      {/* Thông báo Alert */}
      {alertInfo && (
        <div
          className={`batch-alert ${alertInfo.type}`}
          style={{
            background: alertInfo.type === 'success' ? '#15332d' : alertInfo.type === 'danger' ? '#36202b' : '#352d1c',
            border: `1px solid ${alertInfo.type === 'success' ? '#6cdeb4' : alertInfo.type === 'danger' ? '#ffa6b3' : '#f5cb76'}`,
            color: '#f1f4fb',
            padding: '12px 18px',
            borderRadius: '10px',
            marginBottom: '20px',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
          }}
        >
          {alertInfo.type === 'success' ? <CheckCircle2 size={20} color="#6cdeb4" /> : <AlertCircle size={20} />}
          <span>{alertInfo.text}</span>
        </div>
      )}

      {/* TAB 1: TẠO LỘ TRÌNH GỘP MỚI */}
      {activeTab === 'CREATE' && (
        <div>
          {/* KPI Tóm Tắt Khi Chọn Đơn */}
          <div className="batch-kpi-grid">
            <div className="batch-kpi-card">
              <div className="batch-kpi-icon">
                <Package size={22} />
              </div>
              <div className="batch-kpi-content">
                <h4>Đơn hàng đã chọn</h4>
                <div className="kpi-value">{selectedOrderIds.length} / 5 đơn</div>
              </div>
            </div>

            <div className="batch-kpi-card">
              <div className={`batch-kpi-icon ${isWeightExceeded ? 'amber' : 'emerald'}`}>
                <Weight size={22} />
              </div>
              <div className="batch-kpi-content">
                <h4>Tổng khối lượng</h4>
                <div className="kpi-value" style={{ color: isWeightExceeded ? '#ffa6b3' : 'inherit' }}>
                  {totalSelectedWeight.toFixed(1)} / {maxCapacity} kg
                </div>
              </div>
            </div>

            <div className="batch-kpi-card">
              <div className="batch-kpi-icon blue">
                <Compass size={22} />
              </div>
              <div className="batch-kpi-content">
                <h4>Quãng đường tối ưu</h4>
                <div className="kpi-value">
                  {optResult ? `${optResult.totalDistanceKm} km` : '—'}
                </div>
              </div>
            </div>

            <div className="batch-kpi-card">
              <div className="batch-kpi-icon amber">
                <Clock size={22} />
              </div>
              <div className="batch-kpi-content">
                <h4>Thời gian dự kiến</h4>
                <div className="kpi-value">
                  {optResult ? `~${optResult.estimatedDurationMinutes} phút` : '—'}
                </div>
              </div>
            </div>
          </div>

          {/* Giao diện 2 cột */}
          <div className="batch-layout-split">
            {/* Cột Trái: Chọn đơn & Danh sách điểm dừng */}
            <div className="batch-main-column">
              {/* Bảng chọn đơn hàng */}
              <div className="batch-panel" style={{ marginBottom: '24px' }}>
                <div className="batch-panel-header">
                  <h3 className="batch-panel-title">
                    <Package size={20} color="#f37021" /> Chọn đơn hàng cần gộp
                  </h3>
                  <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                    <span className="batch-panel-badge">{eligibleOrders.length} đơn sẵn sàng</span>
                    {eligibleOrders.length > 0 && (
                      <button
                        type="button"
                        onClick={selectAllEligible}
                        className="batch-btn-secondary"
                        style={{ padding: '4px 10px', fontSize: '0.8rem' }}
                      >
                        Chọn 5 đơn đầu
                      </button>
                    )}
                    {selectedOrderIds.length > 0 && (
                      <button
                        type="button"
                        onClick={clearSelection}
                        className="batch-btn-secondary"
                        style={{ padding: '4px 10px', fontSize: '0.8rem' }}
                      >
                        Bỏ chọn
                      </button>
                    )}
                  </div>
                </div>

                {/* Chọn chuyến đi liên kết */}
                {myTrips.length > 0 && (
                  <div style={{ marginBottom: '16px', padding: '12px', background: 'rgba(255,255,255,0.02)', borderRadius: '8px', border: '1px solid #333d4c' }}>
                    <label style={{ display: 'block', fontSize: '0.85rem', color: '#b0bdd2', marginBottom: '6px' }}>
                      <Navigation size={14} style={{ display: 'inline', verticalAlign: 'middle', marginRight: '4px' }} />
                      Gắn với chuyến đi của bạn (để tối ưu theo tuyến):
                    </label>
                    <select
                      className="ui-control ui-select"
                      style={{ width: '100%', background: '#101319', color: '#f1f4fb', borderColor: '#333d4c' }}
                      value={selectedTripId ?? ''}
                      onChange={e => {
                        const val = e.target.value ? Number(e.target.value) : null;
                        setSelectedTripId(val);
                        setOptResult(null);
                      }}
                    >
                      <option value="">-- Không gắn chuyến đi riêng --</option>
                      {myTrips.map(trip => (
                        <option key={trip.id} value={trip.id}>
                          {trip.origin} → {trip.destination} (Sức chứa: {trip.capacityKg}kg)
                        </option>
                      ))}
                    </select>
                  </div>
                )}

                {/* Bảng đơn hàng */}
                {loadingOrders ? (
                  <div style={{ textAlign: 'center', padding: '40px 0', color: '#8e9eb8' }}>
                    <RefreshCw className="animate-spin" size={24} style={{ margin: '0 auto 10px' }} />
                    <p>Đang tải danh sách đơn hàng của bạn…</p>
                  </div>
                ) : eligibleOrders.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '40px 20px', color: '#8e9eb8' }}>
                    <Truck size={40} style={{ margin: '0 auto 12px', opacity: 0.5 }} />
                    <p style={{ margin: 0, fontWeight: 600 }}>Bạn chưa có đơn hàng nào đang nhận để gộp.</p>
                    <p style={{ fontSize: '0.85rem', marginTop: '6px' }}>Hãy ghé Sàn đơn để nhận các đơn hàng tiện đường trước khi lập lộ trình gộp.</p>
                    <Link to="/courier/market" className="batch-btn-primary" style={{ marginTop: '16px', textDecoration: 'none' }}>
                      Xem sàn đơn ngay
                    </Link>
                  </div>
                ) : (
                  <div className="batch-orders-table-wrapper">
                    <table className="batch-orders-table">
                      <thead>
                        <tr>
                          <th style={{ width: '40px' }}></th>
                          <th>Mã đơn</th>
                          <th>Hàng hóa</th>
                          <th>Khối lượng</th>
                          <th>Điểm lấy hàng</th>
                          <th>Điểm giao hàng</th>
                        </tr>
                      </thead>
                      <tbody>
                        {eligibleOrders.map(order => {
                          const isSelected = selectedOrderIds.includes(order.id);
                          return (
                            <tr
                              key={order.id}
                              className={isSelected ? 'selected' : ''}
                              onClick={() => toggleOrderSelection(order.id)}
                              style={{ cursor: 'pointer' }}
                            >
                              <td>
                                <input
                                  type="checkbox"
                                  className="batch-checkbox-custom"
                                  checked={isSelected}
                                  onChange={() => {}} // Đã xử lý ở tr click
                                />
                              </td>
                              <td style={{ fontWeight: 600, color: '#ffb17d', whiteSpace: 'nowrap' }}>
                                {order.orderCode}
                              </td>
                              <td>{order.productName}</td>
                              <td style={{ whiteSpace: 'nowrap' }}>{order.weightKg} kg</td>
                              <td style={{ maxWidth: '200px', fontSize: '0.85rem' }} title={order.pickupAddress}>
                                {order.pickupAddress}
                              </td>
                              <td style={{ maxWidth: '200px', fontSize: '0.85rem' }} title={order.deliveryAddress}>
                                {order.deliveryAddress}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}

                {/* Nút hành động tính toán */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
                  <div style={{ fontSize: '0.85rem', color: '#8e9eb8' }}>
                    Yêu cầu: Chọn từ <strong>2 đến 5 đơn</strong>. Tổng tải trọng <strong>≤ {maxCapacity} kg</strong>.
                  </div>
                  <button
                    type="button"
                    className="batch-btn-primary"
                    disabled={selectedOrderIds.length < 2 || isWeightExceeded || isOptimizing}
                    onClick={handleOptimizeRoute}
                  >
                    {isOptimizing ? (
                      <>
                        <RefreshCw className="animate-spin" size={16} /> Đang tính toán lộ trình…
                      </>
                    ) : (
                      <>
                        <Compass size={16} /> Tối ưu hóa thứ tự điểm dừng
                      </>
                    )}
                  </button>
                </div>
              </div>

              {/* Danh sách thứ tự các điểm dừng sau khi tối ưu */}
              {optResult && (
                <div className="batch-panel">
                  <div className="batch-panel-header">
                    <h3 className="batch-panel-title">
                      <ListOrdered size={20} color="#6cdeb4" /> Thứ tự các chặng di chuyển đề xuất
                    </h3>
                    <span className="batch-panel-badge" style={{ background: 'rgba(108, 222, 180, 0.15)', color: '#6cdeb4' }}>
                      {optResult.totalStops} điểm dừng
                    </span>
                  </div>

                  <p style={{ fontSize: '0.85rem', color: '#8e9eb8', marginBottom: '16px' }}>
                    Hệ thống đã tự động sắp xếp theo thứ tự tối ưu nhất, đảm bảo luôn ghé điểm lấy hàng trước khi đến điểm giao của cùng một đơn.
                  </p>

                  <div className="batch-stops-timeline">
                    {optResult.stops.map(stop => {
                      const isPickup = stop.stopType === 'PICKUP';
                      return (
                        <div key={`${stop.stopType}_${stop.orderId}`} className="batch-stop-item">
                          <div className={`batch-stop-badge-number ${isPickup ? 'pickup' : 'delivery'}`}>
                            {stop.stopOrder}
                          </div>
                          <div className="batch-stop-info">
                            <span className={`batch-stop-type-tag ${isPickup ? 'pickup' : 'delivery'}`}>
                              {isPickup ? 'Lấy hàng' : 'Giao hàng'}
                            </span>
                            <div className="batch-stop-address">{stop.address}</div>
                            <div className="batch-stop-meta">
                              <span><strong>Đơn:</strong> {stop.orderCode}</span>
                              {stop.contactName && <span><strong>Người liên hệ:</strong> {stop.contactName} {stop.contactPhone && `(${stop.contactPhone})`}</span>}
                              {stop.itemDescription && <span><strong>Kiện hàng:</strong> {stop.itemDescription} ({stop.weightKg} kg)</span>}
                              {stop.distanceFromPrevKm != null && <span><strong>Cách chặng trước:</strong> +{stop.distanceFromPrevKm} km</span>}
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>

                  {/* Nhập tên và ghi chú đợt gộp */}
                  <div style={{ marginTop: '20px', paddingTop: '16px', borderTop: '1px solid #333d4c' }}>
                    <div style={{ marginBottom: '12px' }}>
                      <label style={{ display: 'block', fontSize: '0.85rem', color: '#b0bdd2', marginBottom: '4px' }}>
                        Tên lượt giao gộp (tùy chọn):
                      </label>
                      <input
                        type="text"
                        className="ui-control ui-input"
                        placeholder="Ví dụ: Chuyến giao chiều 3 đơn trung tâm"
                        value={batchName}
                        onChange={e => setBatchName(e.target.value)}
                        style={{ width: '100%', background: '#101319', color: '#f1f4fb', borderColor: '#333d4c' }}
                      />
                    </div>

                    <div style={{ marginBottom: '16px' }}>
                      <label style={{ display: 'block', fontSize: '0.85rem', color: '#b0bdd2', marginBottom: '4px' }}>
                        Ghi chú thêm:
                      </label>
                      <input
                        type="text"
                        className="ui-control ui-input"
                        placeholder="Ví dụ: Mang theo thùng giữ nhiệt, giao trước 17h"
                        value={batchNotes}
                        onChange={e => setBatchNotes(e.target.value)}
                        style={{ width: '100%', background: '#101319', color: '#f1f4fb', borderColor: '#333d4c' }}
                      />
                    </div>

                    <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end' }}>
                      <button
                        type="button"
                        onClick={() => openGoogleMapsRoute(optResult.stops)}
                        className="batch-btn-secondary"
                      >
                        <ExternalLink size={16} /> Mở bản đồ Google Maps
                      </button>
                      <button
                        type="button"
                        className="batch-btn-primary"
                        onClick={handleCreateBatch}
                        disabled={isCreating}
                      >
                        {isCreating ? (
                          <>
                            <RefreshCw className="animate-spin" size={16} /> Đang lưu…
                          </>
                        ) : (
                          <>
                            <CheckCircle2 size={16} /> Xác nhận tạo lượt giao gộp
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Cột Phải: Bản đồ Lộ Trình Trực Quan */}
            <div className="batch-map-column">
              <div className="batch-panel batch-sticky-panel">
                <div className="batch-panel-header">
                  <h3 className="batch-panel-title">
                    <Compass size={20} color="#f37021" /> Bản đồ lộ trình
                  </h3>
                  {optResult && (
                    <span style={{ fontSize: '0.85rem', color: '#ffb17d', fontWeight: 600 }}>
                      {optResult.totalDistanceKm} km • ~{optResult.estimatedDurationMinutes} phút
                    </span>
                  )}
                </div>

                <div style={{ marginBottom: '16px' }}>
                  <BatchRouteMap
                    stops={optResult ? optResult.stops : []}
                    startLat={selectedTrip?.originLat ?? null}
                    startLng={selectedTrip?.originLng ?? null}
                    onRoadRouteCalculated={handleRoadRouteCalculated}
                  />
                </div>

                {optResult ? (
                  <div style={{ fontSize: '0.85rem', color: '#b0bdd2', lineHeight: 1.6, background: 'rgba(255,255,255,0.02)', padding: '12px', borderRadius: '8px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                      <span>Số đơn gộp:</span>
                      <strong style={{ color: '#f1f4fb' }}>{optResult.totalOrders} đơn</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                      <span>Tổng số chặng ghé:</span>
                      <strong style={{ color: '#f1f4fb' }}>{optResult.totalStops} điểm dừng</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                      <span>Tổng quãng đường:</span>
                      <strong style={{ color: '#ffb17d' }}>{optResult.totalDistanceKm} km</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <span>Thời gian giao ước tính:</span>
                      <strong style={{ color: '#6cdeb4' }}>~{optResult.estimatedDurationMinutes} phút</strong>
                    </div>
                  </div>
                ) : (
                  <p style={{ textAlign: 'center', color: '#8e9eb8', fontSize: '0.85rem', padding: '16px 0', margin: 0 }}>
                    Chọn các đơn hàng ở bảng bên trái và nhấn <strong>Tối ưu hóa thứ tự điểm dừng</strong> để hiển thị bản đồ trực quan.
                  </p>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: DANH SÁCH LƯỢT GIAO GỘP ĐÃ TẠO */}
      {activeTab === 'LIST' && (
        <div>
          {loadingBatches ? (
            <div style={{ textAlign: 'center', padding: '60px 0', color: '#8e9eb8' }}>
              <RefreshCw className="animate-spin" size={28} style={{ margin: '0 auto 12px' }} />
              <p>Đang tải danh sách các lượt giao gộp…</p>
            </div>
          ) : myBatches.length === 0 ? (
            <div className="batch-panel" style={{ textAlign: 'center', padding: '60px 20px', color: '#8e9eb8' }}>
              <Layers size={48} style={{ margin: '0 auto 16px', opacity: 0.4 }} />
              <h3 style={{ color: '#f1f4fb', marginBottom: '8px' }}>Bạn chưa có lượt giao gộp nào</h3>
              <p style={{ margin: '0 0 20px' }}>Hãy bắt đầu bằng cách gộp các đơn hàng cùng tuyến để tiết kiệm thời gian và nhiên liệu.</p>
              <button
                type="button"
                className="batch-btn-primary"
                onClick={() => setActiveTab('CREATE')}
              >
                <PlusCircle size={16} /> Lập lượt giao mới ngay
              </button>
            </div>
          ) : (
            <div className="batch-layout-split">
              {/* Danh sách các batch */}
              <div className="batch-main-column">
                <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '14px', color: '#f1f4fb' }}>
                  Lịch sử các đợt giao ({myBatches.length})
                </h3>

                {myBatches.map(batch => {
                  const isSelected = activeBatchDetail?.id === batch.id;
                  const completedStops = batch.stops.filter(s => s.status === 'COMPLETED').length;
                  const progressPct = batch.stops.length > 0 ? Math.round((completedStops / batch.stops.length) * 100) : 0;

                  return (
                    <div
                      key={batch.id}
                      className="batch-history-card"
                      style={{
                        cursor: 'pointer',
                        borderColor: isSelected ? 'var(--primary, #f37021)' : undefined,
                        boxShadow: isSelected ? '0 0 0 2px var(--primary-glow)' : undefined,
                      }}
                      onClick={() => setActiveBatchDetail(batch)}
                    >
                      <div className="batch-history-header">
                        <div>
                          <span className="batch-code-badge">{batch.batchCode}</span>
                          <span style={{ marginLeft: '10px', fontWeight: 600, color: '#f1f4fb' }}>
                            {batch.batchName}
                          </span>
                        </div>
                        <span className={`batch-status-badge ${batch.status}`}>
                          {batch.status === 'IN_PROGRESS' ? 'Đang thực hiện' : batch.status === 'COMPLETED' ? 'Đã hoàn thành' : batch.status}
                        </span>
                      </div>

                      <div style={{ display: 'flex', gap: '16px', fontSize: '0.85rem', color: '#b0bdd2', marginBottom: '8px' }}>
                        <span><strong>{batch.totalOrders}</strong> đơn hàng</span>
                        <span><strong>{batch.stops.length}</strong> điểm dừng</span>
                        <span><strong>{batch.totalDistanceKm}</strong> km</span>
                        <span><strong>~{batch.estimatedDurationMinutes}</strong> phút</span>
                      </div>

                      {/* Tiến độ */}
                      <div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.78rem', color: '#8e9eb8' }}>
                          <span>Tiến độ giao hàng</span>
                          <span>{completedStops}/{batch.stops.length} điểm ({progressPct}%)</span>
                        </div>
                        <div className="batch-progress-bar-bg">
                          <div className="batch-progress-bar-fill" style={{ width: `${progressPct}%` }} />
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Chi tiết đợt giao được chọn */}
              <div className="batch-map-column">
                {activeBatchDetail ? (
                  <div className="batch-panel batch-sticky-panel">
                    <div className="batch-panel-header">
                      <div>
                        <h3 className="batch-panel-title">
                          <Navigation size={18} color="#f37021" /> {activeBatchDetail.batchName}
                        </h3>
                        <span style={{ fontSize: '0.8rem', color: '#8e9eb8', fontFamily: 'monospace' }}>
                          Mã: {activeBatchDetail.batchCode}
                        </span>
                      </div>
                      <button
                        type="button"
                        onClick={() => openGoogleMapsRoute(activeBatchDetail.stops)}
                        className="batch-btn-secondary"
                        style={{ padding: '6px 12px', fontSize: '0.85rem' }}
                      >
                        <ExternalLink size={14} /> Chỉ đường
                      </button>
                    </div>

                    {/* Bản đồ của batch */}
                    <div style={{ marginBottom: '16px' }}>
                      <BatchRouteMap stops={activeBatchDetail.stops} />
                    </div>

                    {/* Danh sách các điểm dừng với nút hoàn thành */}
                    <h4 style={{ fontSize: '0.95rem', fontWeight: 700, margin: '0 0 12px', color: '#f1f4fb' }}>
                      Các điểm dừng trong lộ trình:
                    </h4>

                    <div className="batch-stops-timeline" style={{ maxHeight: '380px', overflowY: 'auto', paddingRight: '4px' }}>
                      {activeBatchDetail.stops.map(stop => {
                        const isPickup = stop.stopType === 'PICKUP';
                        const isCompleted = stop.status === 'COMPLETED';

                        return (
                          <div
                            key={stop.id ?? `${stop.stopType}_${stop.orderId}`}
                            className={`batch-stop-item ${isCompleted ? 'completed' : ''}`}
                          >
                            <div className={`batch-stop-badge-number ${isPickup ? 'pickup' : 'delivery'}`}>
                              {isCompleted ? <CheckCircle2 size={16} /> : stop.stopOrder}
                            </div>
                            <div className="batch-stop-info">
                              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                <span className={`batch-stop-type-tag ${isPickup ? 'pickup' : 'delivery'}`}>
                                  Chặng #{stop.stopOrder}: {isPickup ? 'Lấy hàng' : 'Giao hàng'}
                                </span>
                                {!isCompleted && (
                                  <button
                                    type="button"
                                    onClick={() => stop.id && handleCompleteStop(stop.id)}
                                    className="batch-btn-secondary"
                                    style={{ padding: '2px 8px', fontSize: '0.75rem', borderColor: '#6cdeb4', color: '#6cdeb4' }}
                                  >
                                    Đã xong
                                  </button>
                                )}
                              </div>
                              <div className="batch-stop-address">{stop.address}</div>
                              <div className="batch-stop-meta">
                                <span><strong>Đơn:</strong> {stop.orderCode}</span>
                                {stop.contactName && <span><strong>Liên hệ:</strong> {stop.contactName} {stop.contactPhone && `(${stop.contactPhone})`}</span>}
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                ) : (
                  <div className="batch-panel" style={{ textAlign: 'center', padding: '40px 20px', color: '#8e9eb8' }}>
                    <p>Chọn một lượt giao ở danh sách để xem chi tiết lộ trình và cập nhật tiến độ.</p>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
