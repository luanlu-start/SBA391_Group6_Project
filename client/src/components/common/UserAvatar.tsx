import { useState } from 'react';
import { UserRound } from 'lucide-react';
import { useAuthentication } from '../../auth/useAuthentication';
import { useAvatar } from '../../features/profile/avatarContext';
import '../../styles/avatar.css';

const iconSizes = { sm: 18, md: 22, lg: 30, xl: 42 };

export default function UserAvatar({ size = 'md', src }: { size?: keyof typeof iconSizes; src?: string }) {
  const { user } = useAuthentication();
  const avatar = useAvatar();
  const [brokenUrl, setBrokenUrl] = useState('');
  const url = src || avatar.url;
  return <span className={`user-avatar user-avatar-${size}`}>
    {url && brokenUrl !== url
      ? <img src={url} alt={src ? 'Xem trước ảnh đại diện' : `Ảnh đại diện của ${user?.fullName || 'bạn'}`}
        onError={() => setBrokenUrl(url)} />
      : <UserRound size={iconSizes[size]} aria-hidden="true" />}
  </span>;
}
