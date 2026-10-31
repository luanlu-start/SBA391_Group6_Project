import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { getMapTileConfig } from '../../../utils/mapConfig';
import { MapPin, Compass } from 'lucide-react';

interface TripRouteMapProps {
  originLat: number | null;
  originLng: number | null;
  destinationLat: number | null;
  destinationLng: number | null;
  onSelectPoint?: (type: 'origin' | 'destination', lat: number, lng: number, address?: string) => void;
  onRouteCalculated?: (distanceKm: number, durationMinutes: number, polylineGeoJson?: string) => void;
  activePickingMode?: 'origin' | 'destination' | null;
}

export default function TripRouteMap({
  originLat,
  originLng,
  destinationLat,
  destinationLng,
  onSelectPoint,
  onRouteCalculated,
  activePickingMode,
}: TripRouteMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<L.Map | null>(null);
  const originMarkerRef = useRef<L.CircleMarker | null>(null);
  const destMarkerRef = useRef<L.CircleMarker | null>(null);
  const routeLineRef = useRef<L.Polyline | null>(null);
  const [calculatingRoute, setCalculatingRoute] = useState(false);

  // Initialize Map
  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;

    const defaultLat = originLat || 10.0299;
    const defaultLng = originLng || 105.7705;

    const map = L.map(containerRef.current, {
      zoomControl: true,
      attributionControl: true,
    }).setView([defaultLat, defaultLng], 13);
    mapRef.current = map;

    const tileConfig = getMapTileConfig();
    L.tileLayer(tileConfig.url, tileConfig.options).addTo(map);

    const resizeTimer = setTimeout(() => {
      map.invalidateSize();
    }, 200);

    return () => {
      clearTimeout(resizeTimer);
      map.remove();
      mapRef.current = null;
    };
  }, []);

  // Map Click Listener
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

    // Origin Marker (Green)
    if (originLat && originLng) {
      if (!originMarkerRef.current) {
        originMarkerRef.current = L.circleMarker([originLat, originLng], {
          color: '#10b981',
          fillColor: '#34d399',
          fillOpacity: 0.9,
          weight: 3,
          radius: 9,
        }).addTo(map);
        originMarkerRef.current.bindTooltip('Điểm xuất phát', {
          permanent: true,
          direction: 'top',
          offset: [0, -10],
          className: 'trip-map-tooltip origin',
        });
      } else {
        originMarkerRef.current.setLatLng([originLat, originLng]);
      }
    } else if (originMarkerRef.current) {
      originMarkerRef.current.remove();
      originMarkerRef.current = null;
    }

    // Destination Marker (FPT Orange)
    if (destinationLat && destinationLng) {
      if (!destMarkerRef.current) {
        destMarkerRef.current = L.circleMarker([destinationLat, destinationLng], {
          color: '#f37021',
          fillColor: '#ff8a42',
          fillOpacity: 0.9,
          weight: 3,
          radius: 9,
        }).addTo(map);
        destMarkerRef.current.bindTooltip('Điểm đến', {
          permanent: true,
          direction: 'top',
          offset: [0, -10],
          className: 'trip-map-tooltip destination',
        });
      } else {
        destMarkerRef.current.setLatLng([destinationLat, destinationLng]);
      }
    } else if (destMarkerRef.current) {
      destMarkerRef.current.remove();
      destMarkerRef.current = null;
    }

    // Calculate Routing if both points exist
    if (originLat && originLng && destinationLat && destinationLng) {
      setCalculatingRoute(true);

      const fetchRoute = async () => {
        try {
          // OSRM Driving public route
          const osrmUrl = `https://router.project-osrm.org/route/v1/driving/${originLng},${originLat};${destinationLng},${destinationLat}?overview=full&geometries=geojson`;
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

              if (routeLineRef.current) {
                routeLineRef.current.setLatLngs(coordinates);
              } else {
                routeLineRef.current = L.polyline(coordinates, {
                  color: '#f37021',
                  weight: 5,
                  opacity: 0.85,
                  smoothFactor: 1,
                }).addTo(map);
              }

              // Fit map bounds
              const bounds = L.latLngBounds([
                [originLat, originLng],
                [destinationLat, destinationLng],
              ]);
              map.fitBounds(bounds, { padding: [50, 50] });

              if (onRouteCalculated) {
                onRouteCalculated(distKm, durMin, JSON.stringify(route.geometry));
              }
              return;
            }
          }
          throw new Error('OSRM routing unavailable');
        } catch {
          // Fallback according to BR-C-021: Haversine distance * 1.3
          const R = 6371; // km
          const dLat = ((destinationLat - originLat) * Math.PI) / 180;
          const dLon = ((destinationLng - originLng) * Math.PI) / 180;
          const a =
            Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos((originLat * Math.PI) / 180) *
              Math.cos((destinationLat * Math.PI) / 180) *
              Math.sin(dLon / 2) *
              Math.sin(dLon / 2);
          const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
          const directKm = R * c;
          const fallbackKm = Number((directKm * 1.3).toFixed(1));
          const fallbackMin = Math.round((fallbackKm / 30) * 60); // approx 30km/h

          const coords: [number, number][] = [
            [originLat, originLng],
            [destinationLat, destinationLng],
          ];

          if (routeLineRef.current) {
            routeLineRef.current.setLatLngs(coords);
          } else {
            routeLineRef.current = L.polyline(coords, {
              color: '#f37021',
              weight: 4,
              dashArray: '8, 8',
              opacity: 0.8,
            }).addTo(map);
          }

          const bounds = L.latLngBounds(coords);
          map.fitBounds(bounds, { padding: [50, 50] });

          if (onRouteCalculated) {
            onRouteCalculated(fallbackKm, fallbackMin, undefined);
          }
        } finally {
          setCalculatingRoute(false);
        }
      };

      fetchRoute();
    } else {
      if (routeLineRef.current) {
        routeLineRef.current.remove();
        routeLineRef.current = null;
      }
      if (originLat && originLng) {
        map.setView([originLat, originLng], 14);
      } else if (destinationLat && destinationLng) {
        map.setView([destinationLat, destinationLng], 14);
      }
    }
  }, [originLat, originLng, destinationLat, destinationLng]);

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
          <span>Đang tính toán tuyến đường tối ưu...</span>
        </div>
      )}
    </div>
  );
}
