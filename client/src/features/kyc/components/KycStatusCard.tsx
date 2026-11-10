import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { BadgeCheck, Clock3, Eye, FileImage, FilePenLine, ShieldCheck } from 'lucide-react';
import type { KycSnapshot } from '../../../types/kyc';
import { useAuthentication } from '../../../auth/useAuthentication';
import { kycService } from '../../../services/kycService';
import { useProfileAction } from '../../profile/hooks/useProfileAction';
import Badge from '../../../components/ui/Badge';
import Button from '../../../components/ui/Button';
import Modal from '../../../components/ui/Modal';
import Alert from '../../../components/ui/Alert';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

const states = {
  NOT_SUBMITTED: { label: 'Chưa nộp hồ sơ', title: 'Bắt đầu xác minh danh tính', description: 'Chuẩn bị CCCD còn hiệu lực và ảnh chụp rõ thông tin để gửi xét duyệt.', tone: 'neutral', icon: ShieldCheck },
  PENDING: { label: 'Chờ xét duyệt', title: 'Hồ sơ đang được xét duyệt', description: 'Bạn đã gửi hồ sơ. Kết quả sẽ được thông báo sau khi bộ phận kiểm duyệt xử lý.', tone: 'warning', icon: Clock3 },
  APPROVED: { label: 'Đã xác minh', title: 'Danh tính đã được xác minh', description: 'Thông tin danh tính của bạn đã được kiểm duyệt.', tone: 'success', icon: BadgeCheck },
  REJECTED: { label: 'Cần bổ sung', title: 'Hồ sơ cần được bổ sung', description: 'Kiểm tra lý do bên dưới, cập nhật thông tin hoặc ảnh và gửi lại hồ sơ.', tone: 'danger', icon: FilePenLine },
} as const;
const files = { ID_FRONT: 'Mặt trước CCCD', ID_BACK: 'Mặt sau CCCD', SELFIE_WITH_ID: 'Ảnh chân dung cầm CCCD' };
const date = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeZone: 'UTC' });
const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' });
const formatDate = (value: string) => date.format(new Date(`${value}T00:00:00Z`));

export default function KycStatusCard({ snapshot }: { snapshot: KycSnapshot }) {
  const { user } = useAuthentication();
  const { run, busy, error } = useProfileAction();
  const [image, setImage] = useState<{ url: string; title: string }>();
  useEffect(() => () => { if (image) URL.revokeObjectURL(image.url); }, [image]);
  const request = snapshot.request;
  const state = states[snapshot.status];
  const Icon = state.icon;
  async function view(id: string, title: string) {
    const result = await run(() => kycService.image(id));
    if (result) setImage({ url: URL.createObjectURL(result.data), title });
  }

  return <div className="ui-stack">
    <div className="kyc-status-heading">
      <span className={`kyc-status-icon kyc-tone-${state.tone}`}><Icon size={26} aria-hidden="true" /></span>
      <div className="kyc-status-copy"><h2>{state.title}</h2><p>{state.description}</p></div>
      <Badge tone={state.tone} dot>{state.label}</Badge>
    </div>
    {request?.rejectReason && <Alert tone="warning" title="Lý do cần bổ sung">{request.rejectReason}</Alert>}
    {request && <dl className="kyc-request-details">
      <div><dt>Họ và tên</dt><dd>{request.fullName}</dd></div>
      <div><dt>Số CCCD</dt><dd>{request.idNumberMasked}</dd></div>
      <div><dt>Ngày sinh</dt><dd>{formatDate(request.dob)}</dd></div>
      <div><dt>Ngày gửi hồ sơ</dt><dd>{dateTime.format(new Date(request.submittedAt))}</dd></div>
      <div><dt>Ngày cấp CCCD</dt><dd>{formatDate(request.issuedDate)}</dd></div>
      <div><dt>Ngày hết hạn CCCD</dt><dd>{formatDate(request.expiryDate)}</dd></div>
    </dl>}
    {error && <ApiErrorNotice error={error} />}
    {!!request?.files.length && <div className="kyc-submitted-images"><h3>Ảnh đã nộp</h3>
      <div className="kyc-file-list">{request.files.map(file => <div key={file.id} className="kyc-file-card">
        <FileImage size={22} aria-hidden="true" /><strong>{files[file.type]}</strong>
        <Button size="sm" variant="secondary" icon={<Eye size={15} />} disabled={busy}
          aria-label={`Xem ${files[file.type].toLowerCase()}`} onClick={() => void view(file.id, files[file.type])}>Xem ảnh</Button>
      </div>)}</div>
    </div>}
    {snapshot.status === 'APPROVED' && <div className="kyc-next-action">
      <ShieldCheck size={17} aria-hidden="true" /><span>Hồ sơ xác minh được dùng khi xét điều kiện hoạt động của người giao.</span>
      <Link className="ui-button ui-button-secondary ui-button-sm" to={user?.roles.includes('COURIER') ? '/courier' : '/me/courier-register'}>
        {user?.roles.includes('COURIER') ? 'Hồ sơ người giao' : 'Đăng ký người giao'}
      </Link>
    </div>}
    <Modal isOpen={!!image} onClose={() => setImage(undefined)} title={image?.title} size="lg">
      {image && <img className="kyc-private-image" src={image.url} alt={image.title} />}
    </Modal>
  </div>;
}
