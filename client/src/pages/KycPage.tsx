import { Link, Navigate } from 'react-router-dom';
import { ArrowRight, IdCard, Images, RefreshCw, UserRound } from 'lucide-react';
import { useProfile } from '../features/profile/hooks/useProfile';
import { useKyc } from '../features/kyc/hooks/useKyc';
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
  const profile = useProfile();
  const kyc = useKyc(profile.user?.id);
  if (profile.initializing) return <p role="status">Đang kiểm tra phiên đăng nhập…</p>;
  if (!profile.user) return <Navigate to="/login" replace />;
  const error = profile.error ?? kyc.error;
  const loading = profile.loading || kyc.loading;
  const snapshot = kyc.snapshot;
  const canSubmit = snapshot && ['NOT_SUBMITTED', 'REJECTED'].includes(snapshot.status);

  return <div className="ui-stack account-page kyc-page">
    <PageHeader eyebrow="Tài khoản" title="Xác minh danh tính"
      description="Hoàn tất hồ sơ CCCD để đăng ký trở thành người giao trên FPost."
      actions={!loading && !error && snapshot && !canSubmit
        ? <Button variant="secondary" size="sm" icon={<RefreshCw size={16} />} onClick={kyc.reload}>Cập nhật trạng thái</Button> : undefined} />
    {loading ? <p className="ui-muted" role="status">Đang tải hồ sơ xác minh…</p> : error ? <>
      <ApiErrorNotice error={error} /><Button variant="secondary" onClick={() => { profile.reload(); kyc.reload(); }}>Thử lại</Button>
    </> : snapshot && <>
      <SectionCard className={`kyc-status-card kyc-status-${snapshot.status.toLowerCase()}`}>
        <KycStatusCard snapshot={snapshot} />
      </SectionCard>
      {canSubmit && (snapshot.submissionsRemaining <= 0
        ? <Alert tone="warning" title="Đã hết lượt nộp hồ sơ">
          Bạn đã dùng hết lượt nộp trong 7 ngày.{snapshot.nextSubmissionAt
            ? ` Có thể nộp lại từ ${new Date(snapshot.nextSubmissionAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}.`
            : ' Vui lòng kiểm tra lại sau.'}
        </Alert>
        : !profile.user.dob ? <SectionCard title="Bổ sung thông tin cá nhân"
          description="Bạn cần cập nhật ngày sinh trong hồ sơ trước khi gửi yêu cầu xác minh.">
          <div className="ui-stack">
            <div className="kyc-prerequisite"><span className="kyc-status-icon"><UserRound size={24} aria-hidden="true" /></span>
              <div><strong>{profile.user.fullName}</strong><p>Ngày sinh: Chưa cập nhật</p></div>
              <Link className="ui-button ui-button-primary ui-button-sm" to="/me/profile">Cập nhật hồ sơ<ArrowRight size={16} aria-hidden="true" /></Link>
            </div>
            <div className="kyc-preparation"><p>Bạn sẽ cần chuẩn bị</p><ul>
              <li><IdCard size={18} aria-hidden="true" /><span>CCCD còn hiệu lực, thông tin trùng với hồ sơ cá nhân.</span></li>
              <li><Images size={18} aria-hidden="true" /><span>Ảnh mặt trước, mặt sau và ảnh chân dung cầm CCCD.</span></li>
            </ul><p className="ui-muted">JPG, PNG hoặc WEBP · Tối đa 5 MB mỗi ảnh.</p></div>
          </div>
        </SectionCard> : <SectionCard title={snapshot.status === 'REJECTED' ? 'Bổ sung và gửi lại hồ sơ' : 'Thông tin và giấy tờ xác minh'}
          description={`Còn ${snapshot.submissionsRemaining} lượt nộp trong 7 ngày. Kiểm tra đầy đủ thông tin trước khi gửi.`}>
          <KycForm user={profile.user} snapshot={snapshot} onSubmitted={kyc.setSnapshot} />
        </SectionCard>)}
    </>}
  </div>;
}
