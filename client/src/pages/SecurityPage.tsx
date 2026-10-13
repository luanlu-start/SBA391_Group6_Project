import { useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { ArrowLeft, RefreshCw, ShieldCheck } from 'lucide-react';
import { useAuthentication } from '../auth/useAuthentication';
import { useSessions } from '../features/auth/hooks/useSessions';
import SessionList from '../features/auth/components/SessionList';
import { describeDevice } from '../features/auth/utils/sessionDisplay';
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
      description="Quản lý các thiết bị đang đăng nhập vào tài khoản của bạn."
      actions={<Link to="/account" className="ui-button ui-button-secondary"><ArrowLeft size={17} aria-hidden="true" />Tài khoản</Link>} />
    {notice && <Alert tone="success">{notice}</Alert>}
    <SectionCard title="Thiết bị đang đăng nhập"
      description="Đăng xuất thiết bị bạn không còn sử dụng hoặc không nhận ra."
      actions={<Button variant="secondary" size="sm" onClick={() => void reload()} disabled={loading || busy}
        icon={<RefreshCw size={16} />}>Tải lại</Button>}>
      <div className="ui-stack" aria-busy={loading}>
        {loadError && <ApiErrorNotice message={loadError.message} correlationId={loadError.correlationId} />}
        {loading && <p className="ui-muted" role="status">Đang tải danh sách thiết bị…</p>}
        {!loading && !loadError && <p className="ui-muted session-count">{sessions.length} phiên đang hoạt động</p>}
        {sessions.length > 0 ? <SessionList sessions={sessions} disabled={loading || busy} onRevoke={setSelected} />
          : !loading && !loadError && <EmptyState title="Không có phiên đang hoạt động"
            description="Tải lại danh sách để kiểm tra trạng thái đăng nhập."
            icon={<ShieldCheck size={24} />} />}
      </div>
    </SectionCard>
    <Modal isOpen={!!selected} onClose={closeConfirmation} closeDisabled={busy} title="Đăng xuất thiết bị?"
      description={selected?.current ? 'Bạn sẽ được đưa về trang đăng nhập.' : 'Thiết bị này sẽ cần đăng nhập lại để tiếp tục sử dụng.'}>
      <div className="ui-stack">
        {selected && <p className="ui-muted">{describeDevice(selected.deviceInfo).name} · {selected.ip}</p>}
        {revokeError && <ApiErrorNotice message={revokeError.message} correlationId={revokeError.correlationId} />}
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
