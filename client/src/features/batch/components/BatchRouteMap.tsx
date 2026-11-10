import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { getMapTileConfig } from '../../../utils/mapConfig';
import type { BatchStop } from '../../../types/batch';

interface BatchRouteMapProps {
  stops: BatchStop[];
  activeStopIndex?: number | null;
  onSelectStop?: (stop: BatchStop) => void;
  startLat?: number | null;
  startLng?: number | null;
  onRoadRouteCalculated?: (distanceKm: number, durationMinutes: number) => void;
}

export default function BatchRouteMap({
  stops,
  activeStopIndex,
  onSelectStop,
  startLat,
  startLng,
  onRoadRouteCalculated,
}: BatchRouteMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [mapInstance, setMapInstance] = useState<L.Map | null>(null);
  const markersLayerRef = useRef<L.LayerGroup | null>(null);
  const routeLayerGroupRef = useRef<L.LayerGroup | null>(null);
  const [loadingRoadRoute, setLoadingRoadRoute] = useState(false);

  // 1. Khởi tạo Leaflet Map
  useEffect(() => {
    if (!containerRef.current) return;

    const defaultLat = startLat ?? (stops.length > 0 ? stops[0].latitude : 10.0299);
    const defaultLng = startLng ?? (stops.length > 0 ? stops[0].longitude : 105.7705);

    const map = L.map(containerRef.current, {
      zoomControl: true,
      attributionControl: true,
    }).setView([defaultLat, defaultLng], 13);

    const tileConfig = getMapTileConfig();
    L.tileLayer(tileConfig.url, tileConfig.options).addTo(map);

    const markersGroup = L.layerGroup().addTo(map);
    markersLayerRef.current = markersGroup;

    const routeGroup = L.layerGroup().addTo(map);
    routeLayerGroupRef.current = routeGroup;

    setMapInstance(map);

    let resizeObserver: ResizeObserver | null = null;
    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => {
        map.invalidateSize();
      });
      resizeObserver.observe(containerRef.current);
    }

    const timer = setTimeout(() => {
      map.invalidateSize();
    }, 200);

    return () => {
      clearTimeout(timer);
      if (resizeObserver) {
        resizeObserver.disconnect();
      }
      map.remove();
      setMapInstance(null);
    };
  }, []);

  // 2. Vẽ Markers và Lộ trình đường bộ (OSRM Road Routing)
  useEffect(() => {
    if (!mapInstance || !markersLayerRef.current || !routeLayerGroupRef.current) return;

    markersLayerRef.current.clearLayers();
    routeLayerGroupRef.current.clearLayers();

    if (!stops || stops.length === 0) return;

    interface WaypointCoord {
      lat: number;
      lng: number;
    }

    const waypoints: WaypointCoord[] = [];

    // Nếu có điểm xuất phát của tài xế/chuyến đi
    if (startLat != null && startLng != null) {
      waypoints.push({ lat: startLat, lng: startLng });
      const startIcon = L.divIcon({
        className: 'batch-map-marker-start',
        html: `
          <div style="
            background: #10b981;
            color: #ffffff;
            width: 32px;
            height: 32px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 700;
            font-size: 11px;
            border: 2px solid #ffffff;
            box-shadow: 0 4px 12px rgba(16, 185, 129, 0.4);
          ">
            Bắt đầu
          </div>
        `,
        iconSize: [32, 32],
        iconAnchor: [16, 16],
      });

      L.marker([startLat, startLng], { icon: startIcon, zIndexOffset: 2000 })
        .bindPopup(`<strong>Vị trí xuất phát</strong>`)
        .addTo(markersLayerRef.current);
    }

    // Vẽ từng điểm dừng
    stops.forEach((stop, idx) => {
      const lat = Number(stop.latitude);
      const lng = Number(stop.longitude);
      if (isNaN(lat) || isNaN(lng)) return;

      waypoints.push({ lat, lng });

      const isPickup = stop.stopType === 'PICKUP';
      const bgColor = isPickup ? '#f37021' : '#3b82f6';
      const labelText = `${stop.stopOrder}`;
      const isActive = activeStopIndex === idx;

      const stopIcon = L.divIcon({
        className: `batch-stop-marker ${isPickup ? 'pickup' : 'delivery'} ${isActive ? 'active' : ''}`,
        html: `
          <div style="
            background: ${bgColor};
            color: #ffffff;
            width: ${isActive ? '36px' : '30px'};
            height: ${isActive ? '36px' : '30px'};
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 800;
            font-size: ${isActive ? '14px' : '12px'};
            border: 2px solid ${isActive ? '#ffedd5' : '#ffffff'};
            box-shadow: 0 4px 12px ${isPickup ? 'rgba(243, 112, 33, 0.5)' : 'rgba(59, 130, 246, 0.5)'};
            transition: transform 0.2s;
            cursor: pointer;
          ">
            ${labelText}
          </div>
        `,
        iconSize: [isActive ? 36 : 30, isActive ? 36 : 30],
        iconAnchor: [isActive ? 18 : 15, isActive ? 18 : 15],
      });

      const marker = L.marker([lat, lng], { icon: stopIcon, zIndexOffset: 1000 + idx }).addTo(markersLayerRef.current!);

      const popupHtml = `
        <div style="color: #111827; font-family: sans-serif; font-size: 13px; line-height: 1.5; min-width: 200px;">
          <div style="font-weight: 700; color: ${bgColor}; margin-bottom: 4px; font-size: 14px;">
            Chặng #${stop.stopOrder}: ${isPickup ? 'Lấy hàng' : 'Giao hàng'}
          </div>
          <div><strong>Đơn hàng:</strong> ${stop.orderCode}</div>
          <div><strong>Địa chỉ:</strong> ${stop.address}</div>
          ${stop.contactName ? `<div><strong>Liên hệ:</strong> ${stop.contactName} ${stop.contactPhone ? `(${stop.contactPhone})` : ''}</div>` : ''}
          ${stop.itemDescription ? `<div><strong>Hàng hóa:</strong> ${stop.itemDescription}</div>` : ''}
          ${stop.distanceFromPrevKm != null ? `<div style="margin-top: 4px; color: #4b5563;">Cách chặng trước: <strong>${stop.distanceFromPrevKm} km</strong></div>` : ''}
        </div>
      `;

      marker.bindPopup(popupHtml);

      marker.on('click', () => {
        if (onSelectStop) {
          onSelectStop(stop);
        }
      });
    });

    if (waypoints.length < 2) return;

    // Helper vẽ polyline đôi mịn màng
    const drawPolyline = (coords: [number, number][]) => {
      if (!routeLayerGroupRef.current || !mapInstance) return;
      routeLayerGroupRef.current.clearLayers();

      // Lớp phát sáng nền (Outer Glow)
      L.polyline(coords, {
        color: '#f37021',
        weight: 8,
        opacity: 0.35,
        lineCap: 'round',
        lineJoin: 'round',
      }).addTo(routeLayerGroupRef.current);

      // Lớp đường chính uốn theo tuyến đường bộ (Core Road Line)
      L.polyline(coords, {
        color: '#ff8a42',
        weight: 4,
        opacity: 0.95,
        lineCap: 'round',
        lineJoin: 'round',
      }).addTo(routeLayerGroupRef.current);

      const bounds = L.latLngBounds(coords);
      mapInstance.invalidateSize();
      mapInstance.fitBounds(bounds, {
        padding: [50, 50],
        maxZoom: 16,
      });
    };

    // 3. TÍNH TOÁN TUYẾN ĐƯỜNG BỘ NGẮN NHẤT (Leg-by-Leg Shortest Distance Optimizer)
    let isCancelled = false;
    setLoadingRoadRoute(true);

    // Helper tính khoảng cách chim bay nếu API offline
    const getHaversineMeters = (lat1: number, lon1: number, lat2: number, lon2: number) => {
      const R = 6371000;
      const dLat = ((lat2 - lat1) * Math.PI) / 180;
      const dLon = ((lon2 - lon1) * Math.PI) / 180;
      const a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2);
      const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      return R * c * 1.35; // Nhân hệ số mạng đường bộ đô thị
    };

    interface LegResult {
      coords: [number, number][]; // [lat, lng]
      distanceMeters: number;
      durationSeconds: number;
    }

    const fetchSingleLeg = async (
      w1: WaypointCoord,
      w2: WaypointCoord
    ): Promise<LegResult> => {
      const coordsQuery = `${w1.lng},${w1.lat};${w2.lng},${w2.lat}`;

      // URLs cho 2 Routing Engine (Đường xe máy / đô thị và Trục đường chính)
      const bikeUrl = `https://routing.openstreetmap.de/routed-bike/route/v1/driving/${coordsQuery}?overview=full&geometries=geojson&continue_straight=false`;
      const carUrl = `https://router.project-osrm.org/route/v1/driving/${coordsQuery}?overview=full&geometries=geojson&continue_straight=false`;

      const candidateResults: LegResult[] = [];

      try {
        const responses = await Promise.allSettled([
          fetch(bikeUrl, { signal: AbortSignal.timeout(4000) }),
          fetch(carUrl, { signal: AbortSignal.timeout(4000) }),
        ]);

        for (const res of responses) {
          if (res.status === 'fulfilled' && res.value.ok) {
            try {
              const data = await res.value.json();
              if (data?.routes?.[0]?.geometry?.coordinates) {
                const rawCoords: [number, number][] = data.routes[0].geometry.coordinates;
                const leafletCoords: [number, number][] = rawCoords.map(c => [c[1], c[0]]);
                candidateResults.push({
                  coords: leafletCoords,
                  distanceMeters: data.routes[0].distance ?? 0,
                  durationSeconds: data.routes[0].duration ?? 0,
                });
              }
            } catch {
              // Parse JSON lỗi, bỏ qua candidate này
            }
          }
        }
      } catch {
        // Mạng timeout hoặc lỗi ngoài dự kiến
      }

      // LUÔN ƯU TIÊN TUYẾN ĐƯỜNG CÓ QUÃNG ĐƯỜNG NGẮN NHẤT
      if (candidateResults.length > 0) {
        let best = candidateResults[0];
        for (let i = 1; i < candidateResults.length; i++) {
          if (candidateResults[i].distanceMeters < best.distanceMeters) {
            best = candidateResults[i];
          }
        }
        return best;
      }

      // Dự phòng nếu cả 2 routing engine đều không phản hồi: đường nối thẳng
      const fallbackDist = getHaversineMeters(w1.lat, w1.lng, w2.lat, w2.lng);
      return {
        coords: [
          [w1.lat, w1.lng],
          [w2.lat, w2.lng],
        ],
        distanceMeters: fallbackDist,
        durationSeconds: Math.round((fallbackDist / 1000 / 28) * 3600),
      };
    };

    const fetchRoadRouting = async () => {
      try {
        const legPromises: Promise<LegResult>[] = [];
        for (let i = 0; i < waypoints.length - 1; i++) {
          legPromises.push(fetchSingleLeg(waypoints[i], waypoints[i + 1]));
        }

        const legResults = await Promise.all(legPromises);
        if (isCancelled) return;

        // Gộp toàn bộ chặng thành 1 đường polyline liên tục
        const mergedCoords: [number, number][] = [];
        let totalDistMeters = 0;
        let totalDurationSec = 0;

        legResults.forEach((leg, index) => {
          totalDistMeters += leg.distanceMeters;
          totalDurationSec += leg.durationSeconds;

          if (index === 0) {
            mergedCoords.push(...leg.coords);
          } else {
            // Tránh lặp lại điểm nối giữa 2 chặng
            mergedCoords.push(...leg.coords.slice(1));
          }
        });

        if (mergedCoords.length > 0) {
          drawPolyline(mergedCoords);

          if (onRoadRouteCalculated) {
            const roadDistKm = Number((totalDistMeters / 1000).toFixed(1));
            const roadDurationMin = Math.round(totalDurationSec / 60) + (stops.length * 5);
            onRoadRouteCalculated(roadDistKm, roadDurationMin);
          }
        }
      } catch (err) {
        console.warn('Lỗi khi tính toán lộ trình ngắn nhất, fallback sang nối điểm:', err);
        if (!isCancelled) {
          const fallbackCoords: [number, number][] = waypoints.map(w => [w.lat, w.lng]);
          drawPolyline(fallbackCoords);
        }
      } finally {
        if (!isCancelled) {
          setLoadingRoadRoute(false);
        }
      }
    };

    fetchRoadRouting();

    return () => {
      isCancelled = true;
    };
  }, [stops, mapInstance, activeStopIndex, startLat, startLng]);

  return (
    <div className="batch-map-wrapper">
      <div
        ref={containerRef}
        className="batch-map-container"
        style={{ width: '100%', height: '100%', minHeight: '380px' }}
      />

      {loadingRoadRoute && (
        <div
          style={{
            position: 'absolute',
            top: 12,
            right: 12,
            background: 'rgba(24, 30, 39, 0.92)',
            backdropFilter: 'blur(8px)',
            border: '1px solid #333d4c',
            borderRadius: '8px',
            padding: '6px 12px',
            fontSize: '0.8rem',
            color: '#ffb17d',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            zIndex: 1000,
            boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
          }}
        >
          <span
            style={{
              width: '8px',
              height: '8px',
              borderRadius: '50%',
              background: '#f37021',
              display: 'inline-block',
              animation: 'pulse 1.5s infinite',
            }}
          />
          Đang tối ưu lộ trình ngắn nhất…
        </div>
      )}

      <div className="batch-map-legend">
        <span className="legend-item">
          <span className="legend-dot pickup-dot" /> Lấy hàng
        </span>
        <span className="legend-item">
          <span className="legend-dot delivery-dot" /> Giao hàng
        </span>
        <span className="legend-item">
          <span className="legend-line" /> Lộ trình ngắn nhất
        </span>
      </div>
    </div>
  );
}
