import type { CourierProfile } from '../../../types/courier';
import { vehicleNames, weekDays } from '../schemas/courierSchema';
import Badge from '../../../components/ui/Badge';

export default function CourierProfileCard({ profile }: { profile: CourierProfile }) {
  return <div className="ui-stack"><div><Badge tone="success">{profile.tier === 'NEW' ? 'Tân binh' : profile.tier}</Badge></div>
    <dl className="account-details"><dt>Phương tiện</dt><dd>{vehicleNames[profile.vehicleType]}</dd><dt>Khu vực hoạt động</dt><dd>{profile.areas.join(', ')}</dd>
      <dt>Điểm uy tín</dt><dd>{profile.trustScore}/100</dd><dt>Đơn đã hoàn tất</dt><dd>{profile.completedOrders}</dd></dl>
    <div><h3>Khung giờ thường rảnh</h3><ul className="courier-hours">{profile.availableHours.map((slot, index) => <li key={index}>{weekDays.find(day => day.value === slot.day)?.label}: {slot.start.slice(0, 5)} – {slot.end.slice(0, 5)}</li>)}</ul></div>
  </div>;
}
