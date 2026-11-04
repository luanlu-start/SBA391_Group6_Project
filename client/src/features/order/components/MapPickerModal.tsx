import { useState, useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { MapPin, Check, Loader2, Navigation } from 'lucide-react';
import Modal from '../../../components/ui/Modal';
import Button from '../../../components/ui/Button';

interface MapPickerModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  initialLat?: number | null;
  initialLng?: number | null;
  onConfirm: (lat: number, lng: number, addressText?: string) => void;
}

export default function MapPickerModal({
  isOpen,
  onClose,
  title,
  initialLat,
  initialLng,
  onConfirm,
}: MapPickerModalProps) {
  const [selectedLat, setSelectedLat] = useState<number>(initialLat || 10.0299);
  const [selectedLng, setSelectedLng] = useState<number>(initialLng || 105.7705);
  const [addressPreview, setAddressPreview] = useState<string>('');
  const [isGeocoding, setIsGeocoding] = useState<boolean>(false);

  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<L.Map | null>(null);
  const markerRef = useRef<L.CircleMarker | null>(null);

  useEffect(() => {
    if (isOpen) {
      const lat = initialLat && Number.isFinite(initialLat) ? initialLat : 10.0299;
      const lng = initialLng && Number.isFinite(initialLng) ? initialLng : 105.7705;
      setSelectedLat(lat);
      setSelectedLng(lng);
      setAddressPreview('');
    }
  }, [isOpen, initialLat, initialLng]);

  useEffect(() => {
    if (!isOpen) return;

    let isMounted = true;
    const timers: number[] = [];

    // Initialize map after modal is in the DOM
    const initTimer = window.setTimeout(() => {
      if (!isMounted || !containerRef.current || mapRef.current) return;

      const map = L.map(containerRef.current, {
        zoomControl: true,
        attributionControl: true,
      }).setView([selectedLat, selectedLng], 14);
      mapRef.current = map;

      // Use Google Maps raster tiles: Free, ultra-fast CDN in VN, no API key required, highly detailed Vietnamese street names
      L.tileLayer(
        'https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}',
        {
          attribution: '&copy; Google Maps',
          subdomains: ['0', '1', '2', '3'],
          maxZoom: 20,
        }
      ).addTo(map);

      // Distinct, visible FPTPost brand SVG circle marker
      const marker = L.circleMarker([selectedLat, selectedLng], {
        color: '#f37021',
        fillColor: '#ff8a42',
        fillOpacity: 0.95,
        weight: 3,
        radius: 11,
      }).addTo(map);

      marker.bindTooltip('Vị trí đã chọn', {
        permanent: true,
        direction: 'top',
        offset: [0, -12],
        className: 'map-marker-tooltip',
      });
      markerRef.current = marker;

      const updateCoordinates = (lat: number, lng: number) => {
        const roundedLat = Number(lat.toFixed(6));
        const roundedLng = Number(lng.toFixed(6));
        setSelectedLat(roundedLat);
        setSelectedLng(roundedLng);
        reverseGeocode(roundedLat, roundedLng);
      };

      map.on('click', (e: L.LeafletMouseEvent) => {
        marker.setLatLng(e.latlng);
        map.panTo(e.latlng);
        updateCoordinates(e.latlng.lat, e.latlng.lng);
      });

      // Periodic invalidateSize to guarantee layout inside <dialog>
      [50, 150, 300, 500].forEach(delay => {
        timers.push(window.setTimeout(() => {
          if (mapRef.current) {
            mapRef.current.invalidateSize();
          }
        }, delay));
      });
    }, 100);
    timers.push(initTimer);

    return () => {
      isMounted = false;
      timers.forEach(t => window.clearTimeout(t));
      if (mapRef.current) {
        mapRef.current.remove();
        mapRef.current = null;
        markerRef.current = null;
      }
    };
  }, [isOpen]);

  // Synchronize marker when coordinates update externally
  useEffect(() => {
    if (mapRef.current && markerRef.current) {
      markerRef.current.setLatLng([selectedLat, selectedLng]);
      mapRef.current.panTo([selectedLat, selectedLng]);
    }
  }, [selectedLat, selectedLng]);

  const reverseGeocode = async (lat: number, lng: number) => {
    setIsGeocoding(true);
    try {
      // Using photon.komoot.io which works reliably across Vietnamese ISPs
      const res = await fetch(
        `https://photon.komoot.io/reverse?lat=${lat}&lon=${lng}`
      );
      if (res.ok) {
        const data = await res.json();
        if (data && data.features && data.features.length > 0) {
          const props = data.features[0].properties;
          const parts = [
            props.name,
            props.housenumber ? `${props.housenumber} ${props.street || ''}`.trim() : props.street,
            props.district,
            props.city,
            props.country,
          ].filter(Boolean);

          if (parts.length > 0) {
            setAddressPreview(parts.join(', '));
            return;
          }
        }
      }
    } catch {
      // Fallback silently if offline
    } finally {
      setIsGeocoding(false);
    }
  };

  const handleGetCurrentLocation = () => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      pos => {
        const lat = Number(pos.coords.latitude.toFixed(6));
        const lng = Number(pos.coords.longitude.toFixed(6));
        setSelectedLat(lat);
        setSelectedLng(lng);
        if (mapRef.current && markerRef.current) {
          markerRef.current.setLatLng([lat, lng]);
          mapRef.current.setView([lat, lng], 15);
        }
        reverseGeocode(lat, lng);
      },
      () => {},
      { timeout: 5000 }
    );
  };

  const handleConfirm = () => {
    onConfirm(selectedLat, selectedLng, addressPreview || undefined);
    onClose();
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title} size="lg">
      <div className="ui-stack" style={{ gap: '1rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <p style={{ margin: 0, fontSize: '0.9rem', color: 'var(--text-secondary, #b0bdd2)' }}>
            Nhấp chuột vào bất kỳ vị trí nào trên bản đồ để ghim tọa độ lấy / giao hàng.
          </p>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            icon={<Navigation size={14} style={{ color: 'var(--primary, #f37021)' }} />}
            onClick={handleGetCurrentLocation}
          >
            Vị trí của tôi
          </Button>
        </div>

        {/* Bản đồ Leaflet với chiều cao cố định và nền tối */}
        <div
          ref={containerRef}
          style={{
            height: '380px',
            width: '100%',
            borderRadius: 'var(--radius-md, 10px)',
            border: '1px solid var(--border-subtle, #333d4c)',
            backgroundColor: 'var(--bg-tertiary, #242d39)',
            position: 'relative',
            zIndex: 1,
            overflow: 'hidden',
          }}
        />

        {/* Thông tin tọa độ & địa chỉ gợi ý */}
        <div
          style={{
            padding: '0.85rem 1rem',
            background: 'var(--bg-secondary, #181e27)',
            borderRadius: 'var(--radius-md, 10px)',
            border: '1px solid var(--border-subtle, #333d4c)',
            fontSize: '0.875rem',
            color: 'var(--text-primary, #f1f4fb)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
            <MapPin size={16} style={{ color: 'var(--primary, #f37021)', flexShrink: 0 }} />
            <span>
              <strong style={{ color: 'var(--text-primary, #f1f4fb)' }}>Tọa độ ghim: </strong>
              <span style={{ color: 'var(--text-accent, #ffb17d)', fontFamily: 'var(--font-mono)' }}>
                [{selectedLat}, {selectedLng}]
              </span>
            </span>
          </div>
          {isGeocoding ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-muted, #8e9eb8)', marginTop: '4px' }}>
              <Loader2 size={14} className="ui-spin" style={{ color: 'var(--primary, #f37021)' }} />
              <span>Đang tra cứu tên địa điểm tương ứng…</span>
            </div>
          ) : addressPreview ? (
            <div style={{ color: 'var(--text-secondary, #b0bdd2)', marginTop: '4px' }}>
              <strong style={{ color: 'var(--text-primary, #f1f4fb)' }}>Gợi ý địa chỉ: </strong>
              {addressPreview}
            </div>
          ) : null}
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="button" icon={<Check size={16} />} onClick={handleConfirm}>
            Xác nhận vị trí này
          </Button>
        </div>
      </div>
    </Modal>
  );
}
