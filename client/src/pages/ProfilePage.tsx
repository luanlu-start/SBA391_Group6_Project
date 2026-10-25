import { Link, Navigate } from 'react-router-dom';
import { useProfile } from '../features/profile/hooks/useProfile';
import AccountNavigation from '../features/profile/components/AccountNavigation';
import ProfileForm from '../features/profile/components/ProfileForm';
import AvatarForm from '../features/profile/components/AvatarForm';
import ContactChangeForm from '../features/profile/components/ContactChangeForm';
import PrivacyRequests from '../features/profile/components/PrivacyRequests';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Badge from '../components/ui/Badge';
import Button from '../components/ui/Button';
import ApiErrorNotice from '../components/ApiErrorNotice';
import '../styles/profile.css';

export default function ProfilePage() {
  const { user, initializing, loading, error, reload } = useProfile();
  if (initializing) return <p role="status">Đang kiểm tra phiên đăng nhập…</p>;
  if (!user) return <Navigate to="/login" replace />;
  return <div className="ui-stack account-page"><PageHeader eyebrow="Tài khoản" title="Hồ sơ cá nhân" description="Quản lý thông tin, ảnh đại diện và liên hệ của bạn."
    actions={<Link className="ui-button ui-button-secondary" to="/account">Tài khoản</Link>} /><AccountNavigation />
    {loading ? <p role="status">Đang tải hồ sơ…</p> : error ? <><ApiErrorNotice error={error} /><Button onClick={reload}>Thử lại</Button></> : <>
      <SectionCard title="Thông tin cá nhân" actions={user.fptBadge ? <Badge tone="success">Cộng đồng FPT</Badge> : undefined}><ProfileForm user={user} /></SectionCard>
      <SectionCard title="Ảnh đại diện"><AvatarForm user={user} /></SectionCard>
      <SectionCard title="Thông tin liên hệ" description={`Email: ${user.email} · SĐT: ${user.phone}`}><ContactChangeForm /></SectionCard>
      <SectionCard title="Dữ liệu cá nhân"><PrivacyRequests /></SectionCard>
    </>}
  </div>;
}
