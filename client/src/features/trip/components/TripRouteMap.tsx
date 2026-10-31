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
  const mapRef = useRef<L.Map | null>(null);
  const originMarkerRef = useRef<L.Marker | null>(null);
  const destMarkerRef = useRef<L.Marker | null>(null);
  const routeLineGlowRef = useRef<L.Polyline | null>(null);
  const routeLineRef = useRef<L.Polyline | null>(null);
  const [calculatingRoute, setCalculatingRoute] = useState(false);

  // Initialize Map
  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;

    const oLat = originLat != null ? Number(originLat) : 10.0299;
    const oLng = originLng != null ? Number(originLng) : 105.7705;

    const map = L.map(containerRef.current, {
      zoomControl: true,
      attributionControl: true,
    }).setView([oLat, oLng], 13);
    mapRef.current = map;

    // Dark Mode Tile Config
    const tileConfig = getMapTileConfig(true);
    L.tileLayer(tileConfig.url, tileConfig.options).addTo(map);

    // Initial resize
    const timer = setTimeout(() => {
      map.invalidateSize();
    }, 150);

    // Watch container resize (crucial for modal tabs / drawer transitions)
    let resizeObserver: ResizeObserver | null = null;
    if (typeof ResizeObserver !== 'undefined' && containerRef.current) {
      resizeObserver = new ResizeObserver(() => {
        if (mapRef.current) {
          mapRef.current.invalidateSize();
        }
      });
      resizeObserver.observe(containerRef.current);
    }

    return () => {
      clearTimeout(timer);
      if (resizeObserver) {
        resizeObserver.disconnect();
      }
      map.remove();
      mapRef.current = null;
    };
  }, []);

  // Map Click Listener (when selecting points)
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !onSelectPoint || !activePickingMode) return;

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

    map.on('click', handleClick);
    return () => {
      map.off('click', handleClick);
    };
  }, [activePickingMode, onSelectPoint]);

  // Update Markers & Route
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    const oLat = originLat != null && !isNaN(Number(originLat)) ? Number(originLat) : null;
    const oLng = originLng != null && !isNaN(Number(originLng)) ? Number(originLng) : null;
    const dLat = destinationLat != null && !isNaN(Number(destinationLat)) ? Number(destinationLat) : null;
    const dLng = destinationLng != null && !isNaN(Number(destinationLng)) ? Number(destinationLng) : null;

    // Helper to create HTML pin icons
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

    // 1. Origin Marker (Green Pin)
    if (oLat != null && oLng != null) {
      if (!originMarkerRef.current) {
        originMarkerRef.current = L.marker([oLat, oLng], {
          icon: createPinIcon('origin'),
          zIndexOffset: 1000,
        }).addTo(map);

        originMarkerRef.current.bindTooltip('Điểm xuất phát', {
          permanent: true,
          direction: 'top',
          offset: [0, -28],
          className: 'trip-map-tooltip origin',
        });
      } else {
        originMarkerRef.current.setLatLng([oLat, oLng]);
      }
    } else if (originMarkerRef.current) {
      originMarkerRef.current.remove();
      originMarkerRef.current = null;
    }

    // 2. Destination Marker (FPT Orange Pin)
    if (dLat != null && dLng != null) {
      if (!destMarkerRef.current) {
        destMarkerRef.current = L.marker([dLat, dLng], {
          icon: createPinIcon('destination'),
          zIndexOffset: 1000,
        }).addTo(map);

        destMarkerRef.current.bindTooltip('Điểm đến', {
          permanent: true,
          direction: 'top',
          offset: [0, -28],
          className: 'trip-map-tooltip destination',
        });
      } else {
        destMarkerRef.current.setLatLng([dLat, dLng]);
      }
    } else if (destMarkerRef.current) {
      destMarkerRef.current.remove();
      destMarkerRef.current = null;
    }

    // Helper to draw dual-layer glowing polyline
    const drawPolylines = (coords: [number, number][]) => {
      if (routeLineGlowRef.current) {
        routeLineGlowRef.current.setLatLngs(coords);
      } else {
        routeLineGlowRef.current = L.polyline(coords, {
          color: '#f37021',
          weight: 8,
          opacity: 0.35,
          lineCap: 'round',
          lineJoin: 'round',
        }).addTo(map);
      }

      if (routeLineRef.current) {
        routeLineRef.current.setLatLngs(coords);
      } else {
        routeLineRef.current = L.polyline(coords, {
          color: '#ff8a42',
          weight: 4,
          opacity: 0.95,
          lineCap: 'round',
          lineJoin: 'round',
        }).addTo(map);
      }

      const bounds = L.latLngBounds(coords);
      map.fitBounds(bounds, { padding: [45, 45], maxZoom: 16 });
    };

    // 3. Draw Route if both points exist
    if (oLat != null && oLng != null && dLat != null && dLng != null) {
      // Check if pre-calculated routePolyline is provided
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
            // GeoJSON coordinates are [longitude, latitude] -> Leaflet requires [latitude, longitude]
            const coords: [number, number][] = rawCoordinates.map((c) => {
              if (c[0] > 90 || c[0] < -90) {
                return [c[1], c[0]]; // [lng, lat] -> [lat, lng]
              }
              return [c[0], c[1]];
            });

            drawPolylines(coords);
            return;
          }
        } catch {
          // Fall through to OSRM fetch
        }
      }

      // Fetch from OSRM
      setCalculatingRoute(true);

      const fetchRoute = async () => {
        try {
          const osrmUrl = `https://router.project-osrm.org/route/v1/driving/${oLng},${oLat};${dLng},${dLat}?overview=full&geometries=geojson`;
          const response = await fetch(osrmUrl);

          if (response.ok) {
            const data = await response.json();
            if (data?.routes?.[0]) {
              const route = data.routes[0];
              const distKm = Number((route.distance / 1000).toFixed(1));
              const durMin = Math.round(route.duration / 60);
              const coordinates: [number, number][] = route.geometry.coordinates.map(
                (c: [number, number]) => [c[1], c[0]]
              );

              drawPolylines(coordinates);

              if (onRouteCalculated) {
                onRouteCalculated(distKm, durMin, JSON.stringify(route.geometry));
              }
              return;
            }
          }
          throw new Error('OSRM routing unavailable');
        } catch {
          // Direct straight-line fallback with dashed styling
          const coords: [number, number][] = [
            [oLat, oLng],
            [dLat, dLng],
          ];

          if (routeLineGlowRef.current) {
            routeLineGlowRef.current.remove();
            routeLineGlowRef.current = null;
          }

          if (routeLineRef.current) {
            routeLineRef.current.setLatLngs(coords);
          } else {
            routeLineRef.current = L.polyline(coords, {
              color: '#f37021',
              weight: 4,
              dashArray: '8, 8',
              opacity: 0.9,
            }).addTo(map);
          }

          const bounds = L.latLngBounds(coords);
          map.fitBounds(bounds, { padding: [45, 45], maxZoom: 15 });
        } finally {
          setCalculatingRoute(false);
        }
      };

      fetchRoute();
    } else {
      // Clear route lines if incomplete points
      if (routeLineGlowRef.current) {
        routeLineGlowRef.current.remove();
        routeLineGlowRef.current = null;
      }
      if (routeLineRef.current) {
        routeLineRef.current.remove();
        routeLineRef.current = null;
      }
      if (oLat != null && oLng != null) {
        map.setView([oLat, oLng], 14);
      } else if (dLat != null && dLng != null) {
        map.setView([dLat, dLng], 14);
      }
    }
  }, [originLat, originLng, destinationLat, destinationLng, routePolyline]);

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
