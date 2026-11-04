import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { Bell, CheckCheck, RefreshCw, Settings2 } from 'lucide-react';
import { notificationApi } from '../api/notificationApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { PageResponse } from '../types/api';
import type { Notification } from '../types/notification';
import { notificationCategoryLabels, safeNotificationLink } from '../types/notification';
import { useAuthentication } from '../auth/useAuthentication';
import { useNotifications } from '../features/notifications/notificationContext';
import { formatAuditDate } from '../features/audit/auditPresentation';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Checkbox from '../components/ui/Checkbox';
import Badge from '../components/ui/Badge';
import Alert from '../components/ui/Alert';
import EmptyState from '../components/ui/EmptyState';
import Pagination from '../components/ui/Pagination';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ApiErrorNotice from '../components/ApiErrorNotice';
import '../styles/notifications.css';

export default function NotificationsPage() {
  const { user, initializing } = useAuthentication();
  const location = useLocation();

  if (initializing) return <LoadingSpinner text="Đang kiểm tra phiên đăng nhập…" />;
  if (!user) return <Navigate to="/login" replace state={{ returnTo: location.pathname }} />;
  return <NotificationInbox key={user.id} />;
}

function NotificationInbox() {
  const { unreadCount, connection, revision, refresh } = useNotifications();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<PageResponse<Notification> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState('');
  const mounted = useRef(false);
  const actionInFlight = useRef(false);
  const actionRequest = useRef<AbortController | null>(null);

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; actionRequest.current?.abort(); }; }, []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(null);
    notificationApi.list({ unreadOnly, page, size: 20 }, controller.signal).then(response => {
      if (controller.signal.aborted) return;
      const lastPage = Math.max(0, response.data.totalPages - 1);
      if (page > lastPage) setPage(lastPage);
      else setResult(response.data);
    }).catch(cause => { if (!controller.signal.aborted) setError(getApiError(cause)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [unreadOnly, page, reload, revision]);

  async function markRead(id?: string) {
    if (actionInFlight.current) return;
    actionInFlight.current = true;
    const controller = new AbortController(); actionRequest.current = controller;
    setBusy(id ?? 'all'); setActionError(null); setNotice('');
    try {
      if (id) await notificationApi.read(id, controller.signal); else await notificationApi.readAll(controller.signal);
      if (mounted.current) { setReload(value => value + 1); setNotice(id ? 'Đã đánh dấu thông báo là đã đọc.' : 'Đã đánh dấu tất cả thông báo là đã đọc.'); void refresh(); }
    } catch (cause) { if (mounted.current) setActionError(getApiError(cause)); }
    finally { actionInFlight.current = false; if (mounted.current) setBusy(null); }
  }

  return <div className="ui-stack notifications-page">
    <PageHeader eyebrow="Tài khoản" title="Thông báo" description="Theo dõi cập nhật về tài khoản, hồ sơ và đơn hàng."
      actions={<><Link to="/me/notification-settings" className="ui-button ui-button-secondary"><Settings2 size={17} aria-hidden="true" />Cài đặt</Link>
        <Button variant="secondary" icon={<RefreshCw size={17} />} disabled={loading || busy !== null} onClick={() => { setReload(value => value + 1); void refresh(); }}>Tải lại</Button></>} />
    {notice && <Alert tone="success">{notice}</Alert>}
    {actionError && <ApiErrorNotice error={actionError} />}
    <SectionCard title="Hộp thông báo" description={unreadCount === null ? 'Xem tất cả thông báo của bạn.' : `${unreadCount} thông báo chưa đọc.`}>
      <div className="notification-toolbar">
        <Checkbox label="Chỉ xem chưa đọc" checked={unreadOnly} disabled={busy !== null} onChange={event => { setUnreadOnly(event.target.checked); setPage(0); }} />
        <Button variant="secondary" size="sm" icon={<CheckCheck size={17} />} loading={busy === 'all'} disabled={busy !== null || unreadCount === 0 || loading} onClick={() => void markRead()}>Đánh dấu tất cả đã đọc</Button>
      </div>
      {connection !== 'connected' && <p className="ui-muted" role="status">{connection === 'connecting' ? 'Đang kết nối cập nhật thông báo…' : 'Kết nối cập nhật trực tiếp đang gián đoạn. Bạn vẫn có thể tải lại danh sách.'}</p>}
      <div aria-busy={loading}>
        {loading ? <LoadingSpinner text="Đang tải thông báo…" /> : error ? <><ApiErrorNotice error={error} /><Button variant="secondary" onClick={() => setReload(value => value + 1)}>Thử lại</Button></>
          : !result?.content.length ? <EmptyState icon={<Bell size={25} />} title={unreadOnly ? 'Bạn đã đọc hết thông báo' : 'Chưa có thông báo'} description="Các cập nhật mới sẽ xuất hiện tại đây." />
            : <ul className="notification-list">{result.content.map(notification => {
              const link = safeNotificationLink(notification.relativeLink);
              return <li key={notification.id} className={`notification-item${notification.readAt ? '' : ' is-unread'}`}>
                <div className="notification-item-meta"><Badge tone={notification.readAt ? 'neutral' : 'info'}>{notification.readAt ? 'Đã đọc' : 'Chưa đọc'}</Badge>
                  <span>{notificationCategoryLabels[notification.category] ?? notification.category}</span><time dateTime={notification.at}>{formatAuditDate(notification.at)}</time></div>
                <h3>{notification.title}</h3><p className="notification-body">{notification.body}</p>
                <div className="ui-row">{link && <Link to={link} className="ui-button ui-button-secondary ui-button-sm" aria-label={`Xem chi tiết: ${notification.title}`}>Xem chi tiết</Link>}
                  {!notification.readAt && <Button variant="ghost" size="sm" disabled={busy !== null} loading={busy === notification.id}
                    aria-label={`Đánh dấu đã đọc: ${notification.title}`} onClick={() => void markRead(notification.id)}>Đánh dấu đã đọc</Button>}</div>
              </li>;
            })}</ul>}
      </div>
      {!loading && !error && result && <Pagination {...result} onPageChange={setPage} disabled={busy !== null} />}
    </SectionCard>
  </div>;
}
