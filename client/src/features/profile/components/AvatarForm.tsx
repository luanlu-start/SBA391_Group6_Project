import { useEffect, useState } from 'react';
import { UserRound } from 'lucide-react';
import type { UserResponse } from '../../../types/identity';
import { profileService } from '../../../services/profileService';
import { useProfileAction } from '../hooks/useProfileAction';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

export default function AvatarForm({ user }: { user: UserResponse }) {
  const [file, setFile] = useState<File>(); const [fileError, setFileError] = useState('');
  const [image, setImage] = useState(''); const [version, setVersion] = useState(0);
  const { busy, error, notice, run } = useProfileAction();
  useEffect(() => {
    if (!user.avatarAvailable) return;
    let active = true, url = '';
    void profileService.avatar().then(blob => { url = URL.createObjectURL(blob); if (active) setImage(url); else URL.revokeObjectURL(url); }).catch(() => { if (active) setImage(''); });
    return () => { active = false; if (url) URL.revokeObjectURL(url); };
  }, [user.avatarAvailable, version]);
  async function upload() {
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024) { setFileError('Chọn ảnh JPG, PNG hoặc WEBP tối đa 5 MB.'); return; }
    setFileError(''); if (await run(() => profileService.uploadAvatar(file), 'Đã cập nhật ảnh đại diện.')) { setVersion(value => value + 1); setFile(undefined); }
  }
  return <div className="ui-stack">{error && <ApiErrorNotice error={error} />}{notice && <Alert tone="success">{notice}</Alert>}
    <div className="profile-avatar-row"><div className="profile-avatar">{image ? <img src={image} alt={`Ảnh đại diện của ${user.fullName}`} /> : <UserRound size={36} aria-hidden="true" />}</div>
      <p className="ui-muted">Ảnh JPG, PNG hoặc WEBP, tối đa 5 MB.</p></div>
    <Input label="Chọn ảnh đại diện" type="file" accept="image/jpeg,image/png,image/webp" disabled={busy} onChange={event => { setFile(event.target.files?.[0]); setFileError(''); }} error={fileError} />
    <div className="ui-form-actions"><Button onClick={() => void upload()} loading={busy} disabled={!file} loadingText="Đang tải…">Cập nhật ảnh</Button></div>
  </div>;
}
