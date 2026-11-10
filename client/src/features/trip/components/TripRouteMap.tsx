import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { getMapTileConfig } from '../../../utils/mapConfig';
import { MapPin, Compass } from 'lucide-react';

interface TripRouteMapProps {
  originLat: number | null | undefined;
  originLng: number | null | undefined;
  destinationLat: number | null | undefined;
  destinationLng: number | null | undefined;
  onSelectPoint?: (type: 'origin' | 'destination', lat: number, lng: number, address?: string) => void;
  onRouteCalculated?: (distanceKm: number, durationMinutes: number, polylineGeoJson?: string) => void;
  activePickingMode?: 'origin' | 'destination' | null;
  routePolyline?: string;
}

export default function TripRouteMap({
  originLat,
  originLng,
  destinationLat,
  destinationLng,
  onSelectPoint,
  onRouteCalculated,
  activePickingMode,
  routePolyline,
}: TripRouteMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [mapInstance, setMapInstance] = useState<L.Map | null>(null);
  const [calculatingRoute, setCalculatingRoute] = useState(false);

  // 1. Initialize Map
  useEffect(() => {
    if (!containerRef.current) return;

    const oLat = originLat != null && !isNaN(Number(originLat)) ? Number(originLat) : 10.0299;
    const oLng = originLng != null && !isNaN(Number(originLng)) ? Number(originLng) : 105.7705;

    const map = L.map(containerRef.current, {
      zoomControl: true,
      attributionControl: true,
    }).setView([oLat, oLng], 13);

    // Free Google Maps / OpenStreetMap Tile Layer
    const tileConfig = getMapTileConfig();
    L.tileLayer(tileConfig.url, tileConfig.options).addTo(map);

    setMapInstance(map);

    // Container resize observer for smooth tab transitions
    let resizeObserver: ResizeObserver | null = null;
    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => {
        map.invalidateSize();
      });
      resizeObserver.observe(containerRef.current);
    }

    const timer = setTimeout(() => {
      map.invalidateSize();
    }, 150);

    return () => {
      clearTimeout(timer);
      if (resizeObserver) {
        resizeObserver.disconnect();
      }
      map.remove();
      setMapInstance(null);
    };
  }, []);

  // 2. Map Click Listener for picking mode
  useEffect(() => {
    if (!mapInstance || !onSelectPoint || !activePickingMode) return;

    const handleClick = async (e: L.LeafletMouseEvent) => {
      const lat = Number(e.latlng.lat.toFixed(6));
      const lng = Number(e.latlng.lng.toFixed(6));

      let addressText = '';
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
            addressText = parts.join(', ');
          }
        }
      } catch {
        // Fallback silently if offline
      }

      onSelectPoint(activePickingMode, lat, lng, addressText);
    };

    mapInstance.on('click', handleClick);
    return () => {
      mapInstance.off('click', handleClick);
    };
  }, [mapInstance, activePickingMode, onSelectPoint]);

  // 3. Render Route and Markers
  useEffect(() => {
    if (!mapInstance) return;

    const oLat = originLat != null && !isNaN(Number(originLat)) ? Number(originLat) : null;
    const oLng = originLng != null && !isNaN(Number(originLng)) ? Number(originLng) : null;
    const dLat = destinationLat != null && !isNaN(Number(destinationLat)) ? Number(destinationLat) : null;
    const dLng = destinationLng != null && !isNaN(Number(destinationLng)) ? Number(destinationLng) : null;

    // Use a fresh layer group for this render cycle (100% clean, no leaks)
    const layerGroup = L.layerGroup().addTo(mapInstance);

    const createPinIcon = (type: 'origin' | 'destination') => {
      return L.divIcon({
        className: 'trip-custom-marker-wrapper',
        html: `
          <div class="trip-custom-marker">
            <div class="trip-marker-pulse ${type}"></div>
            <div class="trip-marker-pin ${type}">
              <div class="trip-marker-inner"></div>
            </div>
          </div>
        `,
        iconSize: [26, 26],
        iconAnchor: [13, 26],
      });
    };

    // Add Origin Marker
    if (oLat != null && oLng != null) {
      const originMarker = L.marker([oLat, oLng], {
        icon: createPinIcon('origin'),
        zIndexOffset: 1000,
      }).addTo(layerGroup);

      originMarker.bindTooltip('Điểm xuất phát', {
        permanent: true,
        direction: 'top',
        offset: [0, -28],
        className: 'trip-map-tooltip origin',
      });
    }

    // Add Destination Marker
    if (dLat != null && dLng != null) {
      const destMarker = L.marker([dLat, dLng], {
        icon: createPinIcon('destination'),
        zIndexOffset: 1000,
      }).addTo(layerGroup);

      destMarker.bindTooltip('Điểm đến', {
        permanent: true,
        direction: 'top',
        offset: [0, -28],
        className: 'trip-map-tooltip destination',
      });
    }

    // Helper to draw dual-stroke high-contrast route
    const drawRouteOnMap = (coords: [number, number][]) => {
      // Background Glow
      L.polyline(coords, {
        color: '#f37021',
        weight: 8,
        opacity: 0.35,
        lineCap: 'round',
        lineJoin: 'round',
      }).addTo(layerGroup);

      // Core Line
      L.polyline(coords, {
        color: '#ff8a42',
        weight: 4,
        opacity: 0.95,
        lineCap: 'round',
        lineJoin: 'round',
      }).addTo(layerGroup);

      const bounds = L.latLngBounds(coords);
      mapInstance.invalidateSize();
      mapInstance.fitBounds(bounds, { padding: [50, 50], maxZoom: 16 });

      setTimeout(() => {
        if (mapInstance) {
          mapInstance.invalidateSize();
          mapInstance.fitBounds(bounds, { padding: [50, 50], maxZoom: 16 });
        }
      }, 200);
    };

    // Draw route if both points exist
    if (oLat != null && oLng != null && dLat != null && dLng != null) {
      // 1. Try saved routePolyline
      if (routePolyline) {
        try {
          const parsed = JSON.parse(routePolyline);
          let rawCoordinates: [number, number][] | undefined;

          if (Array.isArray(parsed?.coordinates)) {
            rawCoordinates = parsed.coordinates;
          } else if (Array.isArray(parsed?.geometry?.coordinates)) {
            rawCoordinates = parsed.geometry.coordinates;
          } else if (Array.isArray(parsed) && parsed.length > 0) {
            rawCoordinates = parsed;
          }

          if (rawCoordinates && rawCoordinates.length > 0) {
            const coords: [number, number][] = rawCoordinates.map((c) => {
              if (c[0] > 90 || c[0] < -90) {
                return [c[1], c[0]]; // [lng, lat] -> [lat, lng]
              }
              return [c[0], c[1]];
            });

            drawRouteOnMap(coords);
            return () => {
              layerGroup.remove();
            };
          }
        } catch {
          // Fall through to OSRM
        }
      }

      // 2. Fetch driving route from OSRM
      let isCancelled = false;
      setCalculatingRoute(true);

      const fetchRoute = async () => {
        try {
          const osrmUrl = `https://router.project-osrm.org/route/v1/driving/${oLng},${oLat};${dLng},${dLat}?overview=full&geometries=geojson`;
          const response = await fetch(osrmUrl);

          if (!isCancelled && response.ok) {
            const data = await response.json();
            if (data?.routes?.[0]?.geometry?.coordinates) {
              const route = data.routes[0];
              const distKm = Number((route.distance / 1000).toFixed(1));
              const durMin = Math.round(route.duration / 60);
              const coordinates: [number, number][] = route.geometry.coordinates.map(
                (c: [number, number]) => [c[1], c[0]]
              );

              drawRouteOnMap(coordinates);

              if (onRouteCalculated) {
                onRouteCalculated(distKm, durMin, JSON.stringify(route.geometry));
              }
              return;
            }
          }
          throw new Error('OSRM routing unavailable');
        } catch {
          if (!isCancelled) {
            // Direct fallback connection
            const coords: [number, number][] = [
              [oLat, oLng],
              [dLat, dLng],
            ];

            L.polyline(coords, {
              color: '#f37021',
              weight: 4,
              dashArray: '8, 8',
              opacity: 0.9,
            }).addTo(layerGroup);

            const bounds = L.latLngBounds(coords);
            mapInstance.invalidateSize();
            mapInstance.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
          }
        } finally {
          if (!isCancelled) {
            setCalculatingRoute(false);
          }
        }
      };

      fetchRoute();

      return () => {
        isCancelled = true;
        layerGroup.remove();
      };
    } else {
      // Only 1 point or none
      if (oLat != null && oLng != null) {
        mapInstance.setView([oLat, oLng], 14);
      } else if (dLat != null && dLng != null) {
        mapInstance.setView([dLat, dLng], 14);
      }
      return () => {
        layerGroup.remove();
      };
    }
  }, [mapInstance, originLat, originLng, destinationLat, destinationLng, routePolyline]);

  return (
    <div className="trip-map-wrapper">
      <div ref={containerRef} className="trip-map-container" />
      {activePickingMode && (
        <div className="trip-map-hint-banner">
          <MapPin size={16} />
          <span>
            Nhấp chuột vào bất kỳ vị trí nào trên bản đồ để chọn{' '}
            <strong>{activePickingMode === 'origin' ? 'Điểm xuất phát' : 'Điểm đến'}</strong>
          </span>
        </div>
      )}
      {calculatingRoute && (
        <div className="trip-map-loading-banner">
          <Compass size={15} className="spin-icon" />
          <span>Đang tải lộ trình đường đi...</span>
        </div>
      )}
    </div>
  );
}
