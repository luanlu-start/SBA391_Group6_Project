import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuthentication } from '../auth/useAuthentication';
import { useCourier } from '../features/courier/hooks/useCourier';
import CourierRegistrationForm from '../features/courier/components/CourierRegistrationForm';
import AccountNavigation from '../features/profile/components/AccountNavigation';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Alert from '../components/ui/Alert';
import Button from '../components/ui/Button';
import ApiErrorNotice from '../components/ApiErrorNotice';
import '../styles/profile.css';
import '../styles/courier.css';

export default function CourierRegisterPage() {
  const { user, initializing } = useAuthentication(); const courier = useCourier(user?.id); const navigate = useNavigate();
  if (initializing) return <p role="status">Đang kiểm tra phiên đăng nhập…</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (courier.status?.profile) return <Navigate to="/courier" replace />;
  return <div className="ui-stack account-page"><PageHeader eyebrow="Người giao" title="Trở thành người giao" description="Chọn phương tiện, khu vực và thời gian phù hợp với hành trình của bạn." /><AccountNavigation />
    {courier.loading ? <p role="status">Đang kiểm tra điều kiện đăng ký…</p> : courier.error ? <><ApiErrorNotice error={courier.error} /><Button onClick={courier.reload}>Thử lại</Button></> : courier.status &&
      (courier.status.eligible ? <SectionCard title="Thông tin hoạt động"><CourierRegistrationForm commitmentVersion={courier.status.commitmentVersion} onRegistered={() => navigate('/courier', { replace: true, state: { registered: true } })} /></SectionCard>
        : <SectionCard title="Xác minh danh tính trước khi đăng ký"><div className="ui-stack"><Alert tone="info">
          {courier.status.kycStatus === 'PENDING' ? 'Hồ sơ CCCD của bạn đang chờ xét duyệt. Bạn có thể đăng ký người giao khi hồ sơ được duyệt.'
            : courier.status.kycStatus === 'APPROVED' ? 'Hồ sơ đã duyệt hiện chưa đáp ứng điều kiện đăng ký. Vui lòng kiểm tra hiệu lực CCCD hoặc liên hệ hỗ trợ.'
              : 'Bạn cần hoàn tất xác minh CCCD trước khi đăng ký làm người giao.'}</Alert>
          <div className="ui-form-actions"><Link className="ui-button ui-button-primary" to="/me/kyc">Xem hồ sơ xác minh</Link><Button variant="secondary" onClick={courier.reload}>Kiểm tra lại</Button></div></div></SectionCard>)}
  </div>;
}
