import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { UserPlus } from 'lucide-react';
import { adminUserApi } from '../api/adminUserApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { AdminUserSummary, StaffCreationInput } from '../types/adminUsers';
import { accountStatusLabels, accountStatusTones, formatAdminTime, roleLabels } from '../features/admin-users/adminUserPresentation';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Input from '../components/ui/Input';
import Select from '../components/ui/Select';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import Alert from '../components/ui/Alert';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import '../styles/admin-users.css';

const emptyForm: StaffCreationInput = { fullName: '', email: '', phone: '', role: 'MODERATOR' };

export default function AdminStaffPage() {
  const [staff, setStaff] = useState<AdminUserSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState<StaffCreationInput>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    void adminUserApi.staff().then(response => setStaff(response.data.content))
      .catch(cause => setError(getApiError(cause))).finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setNotice('');
    try {
      const response = await adminUserApi.createStaff({ ...form, fullName: form.fullName.trim(), email: form.email.trim(), phone: form.phone.trim() });
      setNotice(`Đã tạo tài khoản ${response.data.email}. Nhân sự dùng chức năng Quên mật khẩu để đặt mật khẩu.`);
      setForm(emptyForm);
      load();
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setSaving(false);
    }
  }

  const columns: TableColumn<AdminUserSummary>[] = [
    { key: 'name', header: 'Nhân sự', render: user => <div className="admin-users-cell">
      <Link to={`/admin/users/${user.publicId}`}><strong>{user.fullName ?? user.email}</strong></Link>
      <span className="admin-users-muted">{user.email}</span></div> },
    { key: 'roles', header: 'Vai trò', render: user => <div className="admin-users-badges">
      {user.roles.map(role => <Badge key={role} tone="info">{roleLabels[role]}</Badge>)}</div> },
    { key: 'status', header: 'Trạng thái', render: user => <Badge dot tone={accountStatusTones[user.status]}>{accountStatusLabels[user.status]}</Badge> },
    { key: 'created', header: 'Ngày tạo', render: user => formatAdminTime(user.createdAt) },
  ];

  const field = (key: keyof StaffCreationInput) => error?.fieldErrors?.[key];

  return <div className="ui-stack admin-users-page">
    <PageHeader title="Nhân sự và phân quyền" eyebrow="Quản trị" description="Tạo tài khoản Kiểm duyệt hoặc Quản trị cho nhân sự nội bộ." />
    <SectionCard title="Tạo tài khoản nhân sự" description="Không đặt mật khẩu hộ: nhân sự nhận thông báo và tự đặt mật khẩu qua Quên mật khẩu.">
      <form className="admin-users-staff-form" onSubmit={submit} noValidate>
        <Input label="Họ tên" required maxLength={100} value={form.fullName} error={field('fullName')}
          onChange={event => setForm(previous => ({ ...previous, fullName: event.target.value }))} />
        <Input label="Email" type="email" required maxLength={254} value={form.email} error={field('email')}
          onChange={event => setForm(previous => ({ ...previous, email: event.target.value }))} />
        <Input label="Số điện thoại" required maxLength={12} value={form.phone} error={field('phone')} placeholder="09xxxxxxxx"
          onChange={event => setForm(previous => ({ ...previous, phone: event.target.value }))} />
        <Select label="Vai trò" value={form.role} onChange={event => setForm(previous => ({ ...previous, role: event.target.value as StaffCreationInput['role'] }))}>
          <option value="MODERATOR">Kiểm duyệt</option><option value="ADMIN">Quản trị</option>
        </Select>
        <div className="admin-users-staff-submit"><Button type="submit" icon={<UserPlus size={16} />} loading={saving}
          disabled={!form.fullName.trim() || !form.email.trim() || !form.phone.trim()}>Tạo tài khoản</Button></div>
      </form>
      {notice && <Alert tone="success">{notice}</Alert>}
      {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
    </SectionCard>
    <SectionCard title="Danh sách nhân sự" className="ui-card-table">
      <DataTable caption="Danh sách nhân sự" columns={columns} rows={staff} rowKey={user => user.publicId} loading={loading} />
    </SectionCard>
  </div>;
}
