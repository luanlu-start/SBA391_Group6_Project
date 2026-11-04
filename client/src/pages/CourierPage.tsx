import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
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
  const courier = useCourier(user?.id); const location = useLocation(); const navigate = useNavigate();
  if (initializing) return <p role="status">Đang kiểm tra phiên đăng nhập…</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (!courier.loading && !courier.error && !courier.status?.profile) return <Navigate to="/me/courier-register" replace />;
  return <div className="ui-stack account-page"><PageHeader eyebrow="Người giao" title="Không gian người giao" description="Thông tin hoạt động và hồ sơ người giao của bạn."
    actions={<Button variant="secondary" onClick={() => { selectMode('SENDER'); navigate('/sender/orders'); }}>Chuyển sang người gửi</Button>} />
    {location.state?.registered && <Alert tone="success">Đăng ký người giao thành công. Bạn đã có thể chuyển giữa hai chế độ mà không cần đăng nhập lại.</Alert>}
    {mode !== 'COURIER' && <Alert tone="info"><Button variant="secondary" onClick={() => selectMode('COURIER')}>Dùng chế độ người giao</Button></Alert>}
    {courier.loading ? <p role="status">Đang tải hồ sơ người giao…</p> : courier.error ? <><ApiErrorNotice error={courier.error} /><Button onClick={courier.reload}>Thử lại</Button></> : courier.status?.profile && <>
      <SectionCard title="Hồ sơ người giao"><CourierProfileCard profile={courier.status.profile} /></SectionCard>
      {!courier.status.eligible && <Alert tone="warning">Vui lòng kiểm tra lại hồ sơ xác minh trước khi nhận đơn.</Alert>}
      <div className="ui-form-actions"><Link className="ui-button ui-button-secondary" to="/me/kyc">Hồ sơ xác minh</Link><Link className="ui-button ui-button-secondary" to="/wallet">Ví tiền</Link><Link className="ui-button ui-button-secondary" to="/me/security">Bảo mật tài khoản</Link></div>
    </>}
  </div>;
}
