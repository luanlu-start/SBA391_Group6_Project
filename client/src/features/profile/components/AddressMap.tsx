import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { getMapTileConfig } from '../../../utils/mapConfig';

export default function AddressMap({ lat, lng, onPick }: { lat: number; lng: number; onPick: (lat: number, lng: number) => void }) {
  const container = useRef<HTMLDivElement>(null), map = useRef<L.Map | null>(null), marker = useRef<L.CircleMarker | null>(null);
  const callback = useRef(onPick);
  useEffect(() => { callback.current = onPick; }, [onPick]);
  useEffect(() => {
    if (!container.current) return;
    const instance = L.map(container.current).setView([10.0299, 105.7705], 13);
    map.current = instance;
    const tileConfig = getMapTileConfig();
    L.tileLayer(tileConfig.url, tileConfig.options).addTo(instance);
    instance.on('click', (event: L.LeafletMouseEvent) => callback.current(event.latlng.lat, event.latlng.lng));
    const timer = window.setTimeout(() => instance.invalidateSize(), 100);
    return () => { window.clearTimeout(timer); instance.remove(); map.current = null; marker.current = null; };
  }, []);
  useEffect(() => {
    if (!map.current || !Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) return;
    if (!marker.current) marker.current = L.circleMarker([lat, lng], { color: '#f97316', fillColor: '#f97316', fillOpacity: .8, radius: 9 }).addTo(map.current);
    else marker.current.setLatLng([lat, lng]);
    map.current.panTo([lat, lng]);
  }, [lat, lng]);
  return <div ref={container} className="address-map" role="region" aria-label="Bản đồ chọn vị trí địa chỉ" />;
}
