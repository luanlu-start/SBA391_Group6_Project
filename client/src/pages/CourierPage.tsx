import { Navigate, useLocation } from 'react-router-dom';
import { useAuthentication } from '../auth/useAuthentication';
import { useAccountMode } from '../auth/useAccountMode';
import { useCourier } from '../features/courier/hooks/useCourier';
import CourierProfileCard from '../features/courier/components/CourierProfileCard';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Alert from '../components/ui/Alert';
import Button from '../components/ui/Button';
import ApiErrorNotice from '../components/ApiErrorNotice';
import '../styles/profile.css';
import '../styles/courier.css';

export default function CourierPage() {
  const { user, initializing } = useAuthentication(); const { mode, selectMode } = useAccountMode();
  const courier = useCourier(user?.id); const location = useLocation();
  if (initializing) return <p role="status">Đang kiểm tra phiên đăng nhập…</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (!courier.loading && !courier.error && !courier.status?.profile) return <Navigate to="/me/courier-register" replace />;
  return <div className="ui-stack account-page"><PageHeader eyebrow="Người giao" title="Hồ sơ người giao" description="Thông tin phương tiện, khu vực và điều kiện hoạt động của bạn." />
    {location.state?.registered && <Alert tone="success">Đăng ký người giao thành công. Bạn đã có thể chuyển giữa hai chế độ mà không cần đăng nhập lại.</Alert>}
    {mode !== 'COURIER' && <Alert tone="info"><Button variant="secondary" onClick={() => selectMode('COURIER')}>Dùng chế độ người giao</Button></Alert>}
    {courier.loading ? <p role="status">Đang tải hồ sơ người giao…</p> : courier.error ? <><ApiErrorNotice error={courier.error} /><Button onClick={courier.reload}>Thử lại</Button></> : courier.status?.profile && <>
      <SectionCard title="Hồ sơ người giao"><CourierProfileCard profile={courier.status.profile} /></SectionCard>
      {!courier.status.eligible && <Alert tone="warning">Vui lòng kiểm tra lại hồ sơ xác minh trước khi nhận đơn.</Alert>}
    </>}
  </div>;
}
