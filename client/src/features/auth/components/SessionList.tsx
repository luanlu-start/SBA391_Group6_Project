import { LogOut, Monitor, Smartphone } from 'lucide-react';
import Badge from '../../../components/ui/Badge';
import Button from '../../../components/ui/Button';
import type { SessionResponse } from '../../../types/identity';
import { describeDevice, formatSessionTime } from '../utils/sessionDisplay';

interface SessionListProps {
  sessions: SessionResponse[];
  disabled: boolean;
  onRevoke: (session: SessionResponse) => void;
}

export default function SessionList({ sessions, disabled, onRevoke }: SessionListProps) {
  return <ul className="session-list" aria-label="Các phiên đang hoạt động">
    {sessions.map(session => {
      const device = describeDevice(session.deviceInfo);
      const Icon = device.mobile ? Smartphone : Monitor;
      return <li className="session-card" key={session.id}>
        <div className="session-device">
          <span className="session-icon"><Icon size={23} aria-hidden="true" /></span>
          <div className="session-device-heading"><h3>{device.name}</h3>
            {session.current && <Badge tone="success" dot>Thiết bị này</Badge>}
          </div>
        </div>
        <dl className="session-details">
          <div><dt>Địa chỉ IP</dt><dd>{session.ip === 'unknown' ? 'Không rõ' : session.ip}</dd></div>
          <div><dt>Đăng nhập lúc</dt><dd><time dateTime={session.createdAt}>{formatSessionTime(session.createdAt)}</time></dd></div>
          <div><dt>Hết hạn lúc</dt><dd><time dateTime={session.expiresAt}>{formatSessionTime(session.expiresAt)}</time></dd></div>
        </dl>
        <Button className="session-revoke" variant={session.current ? 'secondary' : 'danger'} size="sm"
          disabled={disabled} onClick={() => onRevoke(session)} icon={<LogOut size={16} />}
          aria-label={session.current ? 'Đăng xuất thiết bị này' : `Đăng xuất ${device.name}`}>
          Đăng xuất
        </Button>
      </li>;
    })}
  </ul>;
}
