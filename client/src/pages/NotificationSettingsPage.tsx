import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { Save } from 'lucide-react';
import { notificationApi } from '../api/notificationApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { NotificationCategory, NotificationChannel, NotificationSetting, NotificationSettings } from '../types/notification';
import { notificationCategoryLabels } from '../types/notification';
import { canChangeNotificationChannel } from '../features/notifications/notificationSettingsPolicy';
import { useAuthentication } from '../auth/useAuthentication';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Checkbox from '../components/ui/Checkbox';
import Badge from '../components/ui/Badge';
import Alert from '../components/ui/Alert';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ApiErrorNotice from '../components/ApiErrorNotice';
import '../styles/notifications.css';

export default function NotificationSettingsPage() {
  const { user, initializing } = useAuthentication();
  const location = useLocation();

  if (initializing) return <LoadingSpinner text="Đang kiểm tra phiên đăng nhập…" />;
  if (!user) return <Navigate to="/login" replace state={{ returnTo: location.pathname }} />;
  return <NotificationSettingsEditor key={user.id} />;
}

function NotificationSettingsEditor() {
  const [result, setResult] = useState<NotificationSettings | null>(null);
  const [draft, setDraft] = useState<NotificationSetting[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState('');
  const [reload, setReload] = useState(0);
  const mounted = useRef(false);
  const actionInFlight = useRef(false);
  const actionRequest = useRef<AbortController | null>(null);
  const dirty = result !== null && JSON.stringify(result.settings) !== JSON.stringify(draft);

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; actionRequest.current?.abort(); }; }, []);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError(null);
    notificationApi.settings(controller.signal).then(response => {
      if (!controller.signal.aborted) { setResult(response.data); setDraft(response.data.settings); }
    }).catch(cause => { if (!controller.signal.aborted) setError(getApiError(cause)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [reload]);

  function update(category: NotificationCategory, channel: NotificationChannel, checked: boolean) {
    if (actionInFlight.current || !result) return;
    setNotice(''); setDraft(previous => previous.map(row => row.category === category
      && canChangeNotificationChannel(row, channel, result.smsAvailable) ? { ...row, channels: { ...row.channels, [channel]: checked } } : row));
  }

  async function save() {
    if (actionInFlight.current || !dirty || loading) return;
    actionInFlight.current = true;
    const controller = new AbortController(); actionRequest.current = controller;
    setSaving(true); setError(null); setNotice('');
    try {
      const response = await notificationApi.saveSettings(draft, controller.signal);
      if (mounted.current) { setResult(response.data); setDraft(response.data.settings); setNotice('Đã lưu cài đặt thông báo.'); }
    } catch (cause) { if (mounted.current) setError(getApiError(cause)); }
    finally { actionInFlight.current = false; if (mounted.current) setSaving(false); }
  }

  return <div className="ui-stack notifications-page">
    <PageHeader eyebrow="Tài khoản" title="Cài đặt thông báo" description="Chọn kênh nhận thông báo theo từng nhóm nội dung."
      actions={<Link to="/notifications" className="ui-button ui-button-secondary">Về thông báo</Link>} />
    {notice && <Alert tone="success">{notice}</Alert>}
    {error && <><ApiErrorNotice error={error} />{!result && <Button variant="secondary" onClick={() => setReload(value => value + 1)}>Thử lại</Button>}</>}
    <SectionCard title="Kênh nhận thông báo" description="Thông báo xác minh, bảo mật, thanh toán, khiếu nại và trạng thái đơn hàng luôn được gửi trong ứng dụng và qua email.">
      {loading ? <LoadingSpinner text="Đang tải cài đặt…" /> : result && <form onSubmit={event => { event.preventDefault(); void save(); }}>
        <p className="ui-muted" id="notification-mandatory-status">Những nhóm bắt buộc luôn bật thông báo trong ứng dụng và email.</p>
        {!result.smsAvailable && <p className="ui-muted" id="notification-sms-status">SMS hiện chưa khả dụng cho tài khoản của bạn.</p>}
        <fieldset disabled={saving} className="notification-settings-fields">
          <legend className="sr-only">Nhóm nội dung và kênh nhận</legend>
          {draft.map(row => <div className="notification-setting-row" key={row.category}>
            <div><h3>{notificationCategoryLabels[row.category]}</h3>{row.mandatory && <Badge tone="info">Bắt buộc</Badge>}</div>
            <div className="notification-channels">{(['IN_APP', 'EMAIL', 'SMS'] as const).map(channel => <Checkbox key={channel}
              label={{ IN_APP: 'Trong ứng dụng', EMAIL: 'Email', SMS: 'SMS' }[channel]}
              aria-label={`${notificationCategoryLabels[row.category]}: ${{ IN_APP: 'trong ứng dụng', EMAIL: 'email', SMS: 'SMS' }[channel]}`}
              checked={row.channels[channel]} disabled={!canChangeNotificationChannel(row, channel, result.smsAvailable)}
              aria-describedby={channel === 'SMS' && !result.smsAvailable ? 'notification-sms-status'
                : row.mandatory && channel !== 'SMS' ? 'notification-mandatory-status' : undefined}
              onChange={event => update(row.category, channel, event.target.checked)} />)}</div>
          </div>)}
        </fieldset>
        <div className="notification-settings-actions"><Button variant="secondary" disabled={!dirty || saving} onClick={() => { setDraft(result.settings); setError(null); setNotice(''); }}>Hủy thay đổi</Button>
          <Button type="submit" icon={<Save size={17} />} loading={saving} loadingText="Đang lưu…" disabled={!dirty}>Lưu cài đặt</Button></div>
      </form>}
    </SectionCard>
  </div>;
}
