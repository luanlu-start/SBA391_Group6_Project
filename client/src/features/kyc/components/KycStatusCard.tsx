import { useEffect, useState } from 'react';
import type { KycSnapshot } from '../../../types/kyc';
import { kycService } from '../../../services/kycService';
import { useProfileAction } from '../../profile/hooks/useProfileAction';
import Badge from '../../../components/ui/Badge';
import Button from '../../../components/ui/Button';
import Modal from '../../../components/ui/Modal';
import Alert from '../../../components/ui/Alert';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

const labels = { NOT_SUBMITTED: 'Chưa nộp hồ sơ', PENDING: 'Đang chờ xét duyệt', APPROVED: 'Đã xác minh', REJECTED: 'Cần bổ sung hồ sơ' };
const files = { ID_FRONT: 'Mặt trước CCCD', ID_BACK: 'Mặt sau CCCD', SELFIE_WITH_ID: 'Ảnh chân dung cầm CCCD' };
export default function KycStatusCard({ snapshot }: { snapshot: KycSnapshot }) {
  const { run, busy, error } = useProfileAction();
  const [image, setImage] = useState<{ url: string; title: string }>();
  useEffect(() => () => { if (image) URL.revokeObjectURL(image.url); }, [image]);
  const request = snapshot.request;
  async function view(id: string, title: string) {
    const result = await run(() => kycService.image(id));
    if (result) setImage({ url: URL.createObjectURL(result.data), title });
  }
  return <div className="ui-stack">
    <div><Badge tone={snapshot.status === 'APPROVED' ? 'success' : snapshot.status === 'REJECTED' ? 'danger' : 'warning'}>{labels[snapshot.status]}</Badge></div>
    {snapshot.status === 'PENDING' && <p>Hồ sơ đã được gửi tới bộ phận kiểm duyệt. Bạn có thể theo dõi kết quả tại đây.</p>}
    {request && <dl className="account-details"><dt>Họ và tên</dt><dd>{request.fullName}</dd><dt>CCCD</dt><dd>{request.idNumberMasked}</dd>
      <dt>Ngày gửi</dt><dd>{new Date(request.submittedAt).toLocaleString('vi-VN')}</dd><dt>Thẻ có hiệu lực đến</dt><dd>{new Date(`${request.expiryDate}T00:00:00`).toLocaleDateString('vi-VN')}</dd></dl>}
    {request?.rejectReason && <Alert tone="warning">Lý do cần bổ sung: {request.rejectReason}</Alert>}
    {error && <ApiErrorNotice error={error} />}
    {!!request?.files.length && <div className="ui-form-actions">{request.files.map(file => <Button key={file.id} variant="secondary" disabled={busy} onClick={() => view(file.id, files[file.type])}>Xem {files[file.type].toLowerCase()}</Button>)}</div>}
    <Modal isOpen={!!image} onClose={() => setImage(undefined)} title={image?.title} size="lg">{image && <img className="kyc-private-image" src={image.url} alt={image.title} />}</Modal>
  </div>;
}
