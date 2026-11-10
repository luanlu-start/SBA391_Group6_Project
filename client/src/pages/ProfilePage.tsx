import { Navigate } from 'react-router-dom';
import { ChevronDown, FileLock2, Mail, Phone } from 'lucide-react';
import { useProfile } from '../features/profile/hooks/useProfile';
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

  return <div className="ui-stack account-page profile-page">
    <PageHeader eyebrow="Tài khoản" title="Hồ sơ cá nhân" description="Cập nhật ảnh đại diện, thông tin cá nhân và liên hệ của bạn." />
    {loading ? <p role="status">Đang tải hồ sơ…</p> : error ? <>
      <ApiErrorNotice error={error} /><Button onClick={reload}>Thử lại</Button>
    </> : <>
      <SectionCard className="profile-identity-card"><AvatarForm user={user} /></SectionCard>
      <SectionCard title="Thông tin cá nhân" description="Giới thiệu về bạn và thông tin dùng để đối chiếu hồ sơ.">
        <ProfileForm user={user} />
      </SectionCard>
      <SectionCard title="Thông tin liên hệ" description="Email và số điện thoại dùng để đăng nhập và nhận thông báo.">
        <dl className="profile-contact-grid">
          <div><dt><Mail size={16} aria-hidden="true" />Email</dt><dd>{user.email}</dd>
            {user.emailVerifiedAt && <Badge tone="success" dot>Đã xác thực</Badge>}
          </div>
          <div><dt><Phone size={16} aria-hidden="true" />Số điện thoại</dt><dd>{user.phone || 'Chưa cập nhật'}</dd></div>
        </dl>
        <details className="profile-contact-change">
          <summary>Thay đổi thông tin liên hệ<ChevronDown size={17} aria-hidden="true" /></summary>
          <div className="profile-disclosure-body"><ContactChangeForm /></div>
        </details>
      </SectionCard>
      <details className="ui-card profile-data-panel">
        <summary><FileLock2 size={21} aria-hidden="true" /><span><strong>Dữ liệu và quyền riêng tư</strong>
          <span>Yêu cầu bản sao dữ liệu hoặc xóa tài khoản.</span></span><ChevronDown size={18} aria-hidden="true" /></summary>
        <div className="ui-card-body"><PrivacyRequests /></div>
      </details>
    </>}
  </div>;
}
