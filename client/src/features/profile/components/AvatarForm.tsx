import { useEffect, useRef, useState } from 'react';
import { ImagePlus, Upload, X } from 'lucide-react';
import type { UserResponse } from '../../../types/identity';
import { profileService } from '../../../services/profileService';
import { useProfileAction } from '../hooks/useProfileAction';
import { useAvatar } from '../avatarContext';
import UserAvatar from '../../../components/common/UserAvatar';
import Button from '../../../components/ui/Button';
import Badge from '../../../components/ui/Badge';
import Alert from '../../../components/ui/Alert';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

export default function AvatarForm({ user }: { user: UserResponse }) {
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File>();
  const [fileError, setFileError] = useState('');
  const [preview, setPreview] = useState('');
  const avatar = useAvatar();
  const { busy, error, notice, retryAfterSeconds, run, clearError } = useProfileAction();

  useEffect(() => {
    if (!file) { setPreview(''); return; }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  function chooseFile(selected?: File) {
    clearError();
    setFileError('');
    if (!selected) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(selected.type) || selected.size > 5 * 1024 * 1024) {
      setFile(undefined);
      setFileError('Chọn ảnh JPG, PNG hoặc WEBP, dung lượng tối đa 5 MB.');
      return;
    }
    setFile(selected);
  }

  async function upload() {
    if (!file) return;
    const result = await run(async () => {
      await profileService.uploadAvatar(file);
      await avatar.refresh();
    }, 'Đã cập nhật ảnh đại diện.');
    if (result) setFile(undefined);
  }

  return <div className="ui-stack profile-avatar-form">
    <div className="profile-avatar-editor">
      <UserAvatar size="xl" src={preview} />
      <div className="profile-identity-copy">
        <div className="profile-identity-heading"><h2>{user.fullName}</h2>
          {user.fptBadge && <Badge tone="success">Cộng đồng FPT</Badge>}
        </div>
        <p className="profile-avatar-hint">Ảnh JPG, PNG hoặc WEBP, tối đa 5 MB.</p>
        <div className="profile-avatar-actions">
          <input ref={input} type="file" hidden aria-label="Chọn ảnh đại diện" accept="image/jpeg,image/png,image/webp" disabled={busy}
            onChange={event => { chooseFile(event.target.files?.[0]); event.target.value = ''; }} />
          <Button variant="secondary" size="sm" icon={<ImagePlus size={16} />} disabled={busy} onClick={() => input.current?.click()}>Chọn ảnh</Button>
          <Button size="sm" icon={<Upload size={16} />} onClick={() => void upload()} loading={busy}
            disabled={!file || retryAfterSeconds > 0} loadingText="Đang lưu…">Lưu ảnh</Button>
          {file && <Button variant="ghost" size="sm" icon={<X size={15} />} disabled={busy}
            onClick={() => { setFile(undefined); setFileError(''); }}>Hủy</Button>}
        </div>
        {file && <p className="profile-avatar-selection" role="status">{file.name} · Ảnh xem trước, chưa được lưu.</p>}
        {fileError && <p className="ui-field-error" role="alert">{fileError}</p>}
      </div>
    </div>
    {error && <ApiErrorNotice error={error} />}
    {notice && !file && <Alert tone="success">{notice}</Alert>}
    {avatar.failed && <div className="ui-row"><p className="ui-muted">Chưa tải được ảnh đại diện.</p>
      <Button size="sm" variant="secondary" onClick={() => void avatar.refresh()}>Tải lại ảnh</Button></div>}
    {retryAfterSeconds > 0 && <p className="ui-muted" role="status">Có thể thử lại sau {retryAfterSeconds} giây.</p>}
  </div>;
}
