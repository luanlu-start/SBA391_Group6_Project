import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { LockKeyhole, Send } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { UserResponse } from '../../../types/identity';
import type { KycSnapshot } from '../../../types/kyc';
import { kycService } from '../../../services/kycService';
import { useProfileAction } from '../../profile/hooks/useProfileAction';
import { kycSchema } from '../schemas/kycSchema';
import type { KycFormValues } from '../schemas/kycSchema';
import KycImageInput from './KycImageInput';
import Input from '../../../components/ui/Input';
import Checkbox from '../../../components/ui/Checkbox';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import Modal from '../../../components/ui/Modal';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

type Images = { front?: File; back?: File; portrait?: File };
const imageFields = [
  { name: 'front', kind: 'front', label: 'Mặt trước CCCD', hint: 'Đủ bốn góc, nhìn rõ số và thông tin trên thẻ.' },
  { name: 'back', kind: 'back', label: 'Mặt sau CCCD', hint: 'Không bị lóa, mất góc hoặc che thông tin.' },
  { name: 'portrait', kind: 'portrait', label: 'Chân dung cầm CCCD', hint: 'Khuôn mặt và CCCD trong cùng ảnh, nhìn rõ thông tin.' },
] as const;

function validateImage(file?: File) {
  if (!file) return 'Vui lòng chọn ảnh.';
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return 'Chỉ nhận ảnh JPG, PNG hoặc WEBP.';
  if (file.size > 5 * 1024 * 1024) return 'Mỗi ảnh tối đa 5 MB.';
  if (!file.size) return 'Ảnh không có dữ liệu. Vui lòng chọn ảnh khác.';
}

export default function KycForm({ user, snapshot, onSubmitted }: { user: UserResponse; snapshot: KycSnapshot; onSubmitted: (data: KycSnapshot) => void }) {
  const { run, busy, error, retryAfterSeconds } = useProfileAction();
  const [images, setImages] = useState<Images>({});
  const [fileErrors, setFileErrors] = useState<Partial<Record<keyof Images, string>>>({});
  const [policyOpen, setPolicyOpen] = useState(false);
  const { register, handleSubmit, setError, formState: { errors } } = useForm<KycFormValues>({
    resolver: zodResolver(kycSchema), defaultValues: { idNumber: '', issuedDate: '', expiryDate: '', consentAccepted: false },
  });
  useEffect(() => {
    for (const name of ['idNumber', 'issuedDate', 'expiryDate', 'consentAccepted'] as const)
      if (error?.fieldErrors[name]) setError(name, { type: 'server', message: error.fieldErrors[name] });
  }, [error, setError]);
  if (!user.dob) return <Alert tone="warning">Bạn cần <Link to="/me/profile">cập nhật ngày sinh trong hồ sơ</Link> trước khi xác minh.</Alert>;

  function chooseImage(name: keyof Images, file?: File) {
    const failure = file ? validateImage(file) : undefined;
    setImages(current => ({ ...current, [name]: failure ? undefined : file }));
    setFileErrors(current => ({ ...current, [name]: failure }));
  }

  async function submit(values: KycFormValues) {
    const failures: typeof fileErrors = {};
    for (const { name } of imageFields) {
      const failure = validateImage(images[name]);
      if (failure) failures[name] = failure;
    }
    setFileErrors(failures);
    if (Object.keys(failures).length) return;
    const result = await run(() => kycService.submit({ ...values, fullName: user.fullName, dob: user.dob!, policyVersion: snapshot.policyVersion }, images as Required<Images>));
    if (result) { setImages({}); onSubmitted(result.data.data); }
  }

  return <><form className="ui-form kyc-form" noValidate onSubmit={handleSubmit(submit)} aria-busy={busy}>
    {error && <ApiErrorNotice error={error} />}
    <fieldset className="ui-form-section">
      <legend><span className="kyc-section-number">1</span>Thông tin cá nhân</legend>
      <p className="ui-form-description">Thông tin cần trùng với CCCD. <Link className="ui-text-link" to="/me/profile">Cập nhật hồ sơ</Link> nếu chưa chính xác.</p>
      <dl className="kyc-personal-summary">
        <div><dt>Họ và tên</dt><dd>{user.fullName}</dd></div>
        <div><dt>Ngày sinh</dt><dd>{new Date(`${user.dob}T00:00:00Z`).toLocaleDateString('vi-VN', { timeZone: 'UTC' })}</dd></div>
      </dl>
    </fieldset>
    <fieldset className="ui-form-section">
      <legend><span className="kyc-section-number">2</span>Thông tin CCCD</legend>
      <p className="ui-form-description">Nhập thông tin trên CCCD còn hiệu lực của bạn.</p>
      <div className="kyc-document-fields">
        <Input label="Số CCCD" required inputMode="numeric" autoComplete="off" maxLength={12} placeholder="Nhập 12 chữ số"
          disabled={busy} {...register('idNumber')} error={errors.idNumber?.message} />
        <Input label="Ngày cấp" required type="date" disabled={busy} {...register('issuedDate')} error={errors.issuedDate?.message} />
        <Input label="Ngày hết hạn" required type="date" disabled={busy} {...register('expiryDate')} error={errors.expiryDate?.message} />
      </div>
    </fieldset>
    <fieldset className="ui-form-section">
      <legend><span className="kyc-section-number">3</span>Ảnh xác minh</legend>
      <p className="ui-form-description">Chọn đủ 3 ảnh rõ nét, không chỉnh sửa thông tin. JPG, PNG hoặc WEBP, tối đa 5 MB mỗi ảnh.</p>
      <div className="kyc-upload-grid">{imageFields.map(field => <KycImageInput key={field.name} {...field} value={images[field.name]} disabled={busy}
        error={fileErrors[field.name]} onChange={file => chooseImage(field.name, file)} />)}</div>
    </fieldset>
    <div className="kyc-consent"><Checkbox required disabled={busy} {...register('consentAccepted')} error={errors.consentAccepted?.message}
      label={<>Tôi đồng ý xử lý dữ liệu CCCD và ảnh để <button type="button" className="ui-text-link" onClick={() => setPolicyOpen(true)}>xác minh danh tính</button>.</>} />
    </div>
    <div className="ui-form-actions kyc-submit-actions"><p><LockKeyhole size={16} aria-hidden="true" />Ảnh chỉ được xem bởi bạn và người có quyền kiểm duyệt.</p>
      <Button type="submit" icon={<Send size={16} />} loading={busy} disabled={retryAfterSeconds > 0} loadingText="Đang gửi hồ sơ…">Gửi hồ sơ xác minh</Button>
    </div>
    {retryAfterSeconds > 0 && <p role="status">Vui lòng chờ {retryAfterSeconds} giây trước khi gửi lại.</p>}
  </form><Modal isOpen={policyOpen} onClose={() => setPolicyOpen(false)} title="Đồng ý xử lý dữ liệu xác minh">
    <div className="ui-stack"><p>FPost xử lý số CCCD, họ tên, ngày sinh, ngày cấp, ngày hết hạn và ba ảnh bạn cung cấp để xác minh danh tính, kiểm tra hồ sơ trùng và xét điều kiện trở thành người giao.</p>
      <p>Dữ liệu CCCD và ảnh được mã hóa khi lưu. Chỉ bạn và người được phân quyền kiểm duyệt mới được xem; mỗi lần xem ảnh được ghi nhật ký.</p>
      <p>Bạn có thể yêu cầu xuất hoặc xóa dữ liệu tại Hồ sơ cá nhân. Yêu cầu sẽ được chuyển tới bộ phận quản trị để xử lý theo chính sách hiện hành.</p>
      <Button onClick={() => setPolicyOpen(false)}>Đã đọc</Button>
    </div>
  </Modal></>;
}
