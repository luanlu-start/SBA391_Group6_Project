import { useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { ArrowLeft, RefreshCw, ShieldCheck } from 'lucide-react';
import { useAuthentication } from '../auth/useAuthentication';
import { useSessions } from '../features/auth/hooks/useSessions';
import SessionList from '../features/auth/components/SessionList';
import ChangePasswordForm from '../features/auth/components/ChangePasswordForm';
import { describeDevice, formatSessionTime } from '../features/auth/utils/sessionDisplay';
import type { SessionResponse } from '../types/identity';
import ApiErrorNotice from '../components/ApiErrorNotice';
import Alert from '../components/ui/Alert';
import Button from '../components/ui/Button';
import EmptyState from '../components/ui/EmptyState';
import Modal from '../components/ui/Modal';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import '../styles/security.css';

export default function SecurityPage() {
  const { user, initializing } = useAuthentication();
  const { sessions, loading, loadError, revokeError, revokingId, notice, reload, revoke, clearRevokeError } =
    useSessions(!!user && !initializing);
  const [selected, setSelected] = useState<SessionResponse>();
  const busy = !!revokingId;

  function closeConfirmation() {
    setSelected(undefined);
    clearRevokeError();
  }
  async function confirmRevocation() {
    if (selected && await revoke(selected.id)) closeConfirmation();
  }

  if (initializing) return <p className="ui-muted" role="status">Đang kiểm tra phiên đăng nhập…</p>;
  if (!user) return <Navigate to="/login" replace />;

  return <div className="ui-stack security-page">
    <PageHeader eyebrow="Tài khoản" title="Bảo mật và thiết bị"
      description="Đổi mật khẩu và quản lý các phiên đăng nhập của bạn."
      actions={<Link to="/account" className="ui-button ui-button-secondary"><ArrowLeft size={17} aria-hidden="true" />Tài khoản</Link>} />
    {notice && <Alert tone="success">{notice}</Alert>}
    <SectionCard title="Đổi mật khẩu" description="Nhập mật khẩu hiện tại để xác nhận đây là tài khoản của bạn.">
      <ChangePasswordForm />
    </SectionCard>
    <Alert tone="info" title="Phiên đăng nhập là gì?">
      Mỗi trình duyệt có một phiên đăng nhập. Đăng nhập lại trên cùng trình duyệt sẽ thay phiên trước.
      Bạn chỉ cần đăng xuất phiên trên trình duyệt hoặc thiết bị không còn sử dụng hay không nhận ra.
    </Alert>
    <SectionCard title="Phiên đăng nhập còn hiệu lực"
      description="Danh sách chỉ gồm các phiên chưa hết hạn và chưa đăng xuất."
      actions={<Button variant="secondary" size="sm" onClick={() => void reload()} disabled={loading || busy}
        icon={<RefreshCw size={16} />}>Tải lại</Button>}>
      <div className="ui-stack" aria-busy={loading}>
        {loadError && <ApiErrorNotice error={loadError} />}
        {loading && <p className="ui-muted" role="status">Đang tải danh sách phiên đăng nhập…</p>}
        {!loading && !loadError && <p className="ui-muted session-count">{sessions.length} phiên đang hoạt động</p>}
        {sessions.length > 0 ? <SessionList sessions={sessions} disabled={loading || busy} onRevoke={setSelected} />
          : !loading && !loadError && <EmptyState title="Không có phiên đang hoạt động"
            description="Tải lại danh sách để kiểm tra trạng thái đăng nhập."
            icon={<ShieldCheck size={24} />} />}
      </div>
    </SectionCard>
    <Modal isOpen={!!selected} onClose={closeConfirmation} closeDisabled={busy} title="Đăng xuất phiên đăng nhập?"
      description={selected?.current ? 'Bạn sẽ kết thúc phiên đang sử dụng và được đưa về trang đăng nhập.'
        : 'Phiên được chọn sẽ cần đăng nhập lại. Các phiên khác vẫn hoạt động bình thường.'}>
      <div className="ui-stack">
        {selected && <p className="ui-muted">{describeDevice(selected.deviceInfo).name}<br />
          Đăng nhập lúc {formatSessionTime(selected.createdAt)}</p>}
        {revokeError && <ApiErrorNotice error={revokeError} />}
        <div className="ui-form-actions">
          <Button variant="secondary" disabled={busy} onClick={closeConfirmation}>Hủy</Button>
          <Button variant="danger" loading={busy} loadingText="Đang đăng xuất…" onClick={() => void confirmRevocation()}>
            Xác nhận đăng xuất
          </Button>
        </div>
      </div>
    </Modal>
  </div>;
}
