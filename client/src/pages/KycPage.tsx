import { Link, Navigate } from 'react-router-dom';
import { useProfile } from '../features/profile/hooks/useProfile';
import { useKyc } from '../features/kyc/hooks/useKyc';
import AccountNavigation from '../features/profile/components/AccountNavigation';
import KycForm from '../features/kyc/components/KycForm';
import KycStatusCard from '../features/kyc/components/KycStatusCard';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Alert from '../components/ui/Alert';
import ApiErrorNotice from '../components/ApiErrorNotice';
import '../styles/profile.css';
import '../styles/kyc.css';

export default function KycPage() {
  const profile = useProfile(); const kyc = useKyc(profile.user?.id);
  if (profile.initializing) return <p role="status">Đang kiểm tra phiên đăng nhập…</p>;
  if (!profile.user) return <Navigate to="/login" replace />;
  const error = profile.error ?? kyc.error;
  const canSubmit = kyc.snapshot && ['NOT_SUBMITTED', 'REJECTED'].includes(kyc.snapshot.status);
  return <div className="ui-stack account-page"><PageHeader eyebrow="Tài khoản" title="Xác minh danh tính" description="Xác minh CCCD để sẵn sàng trở thành người giao trên FPost."
    actions={<Link className="ui-button ui-button-secondary" to="/account">Tài khoản</Link>} /><AccountNavigation />
    {profile.loading || kyc.loading ? <p role="status">Đang tải hồ sơ xác minh…</p> : error ? <><ApiErrorNotice error={error} /><Button onClick={() => { profile.reload(); kyc.reload(); }}>Thử lại</Button></> : kyc.snapshot && <>
      <SectionCard title="Trạng thái hồ sơ" actions={<Button variant="secondary" onClick={kyc.reload}>Tải lại</Button>}><KycStatusCard snapshot={kyc.snapshot} /></SectionCard>
      {canSubmit && (kyc.snapshot.submissionsRemaining > 0 ? <SectionCard title={kyc.snapshot.status === 'REJECTED' ? 'Nộp lại hồ sơ' : 'Thông tin xác minh'}
        description={`Bạn còn ${kyc.snapshot.submissionsRemaining} lượt nộp trong 7 ngày.`}><KycForm user={profile.user} snapshot={kyc.snapshot} onSubmitted={kyc.setSnapshot} /></SectionCard>
        : <Alert tone="warning">Bạn đã dùng hết lượt nộp hồ sơ. Có thể nộp lại từ {kyc.snapshot.nextSubmissionAt && new Date(kyc.snapshot.nextSubmissionAt).toLocaleString('vi-VN')}.</Alert>)}
    </>}
  </div>;
}
