import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Search, Users } from 'lucide-react';
import { adminUserApi } from '../api/adminUserApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { PageResponse } from '../types/api';
import type { AccountRole, AccountStatus, AdminUserQuery, AdminUserSummary } from '../types/adminUsers';
import AccountRequestsPanel from '../features/admin-users/components/AccountRequestsPanel';
import { accountStatusLabels, accountStatusTones, formatAdminTime, roleLabels } from '../features/admin-users/adminUserPresentation';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Input from '../components/ui/Input';
import Select from '../components/ui/Select';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
import Badge from '../components/ui/Badge';
import '../styles/admin-users.css';

const initialQuery: AdminUserQuery = { page: 0, size: 20 };

export default function AdminUsersPage() {
  const [query, setQuery] = useState<AdminUserQuery>(initialQuery);
  const [keyword, setKeyword] = useState('');
  const [result, setResult] = useState<PageResponse<AdminUserSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const searchPending = keyword.trim() !== (query.q ?? '');

  useEffect(() => {
    const q = keyword.trim();
    if (q === (query.q ?? '')) return;
    const timer = window.setTimeout(() => setQuery(previous => ({ ...previous, q: q || undefined, page: 0 })), 350);
    return () => window.clearTimeout(timer);
  }, [keyword, query.q]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    void adminUserApi.search(query, controller.signal)
      .then(response => { if (!controller.signal.aborted) setResult(response.data); })
      .catch(cause => { if (!controller.signal.aborted) { setError(getApiError(cause)); setResult(null); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [query]);

  const columns: TableColumn<AdminUserSummary>[] = [
    { key: 'user', header: 'Người dùng', render: user => <div className="admin-users-cell">
      <Link to={`/admin/users/${user.publicId}`}><strong>{user.fullName ?? user.email}</strong></Link>
      <span className="admin-users-muted">{user.email}</span></div> },
    { key: 'phone', header: 'Số điện thoại', render: user => <span className="admin-users-code">{user.phone}</span> },
    { key: 'roles', header: 'Vai trò', render: user => <div className="admin-users-badges">
      {user.roles.map(role => <Badge key={role} tone={role === 'ADMIN' || role === 'MODERATOR' ? 'info' : 'neutral'}>{roleLabels[role]}</Badge>)}</div> },
    { key: 'status', header: 'Trạng thái', render: user => <Badge dot tone={accountStatusTones[user.status]}>{accountStatusLabels[user.status]}</Badge> },
    { key: 'created', header: 'Ngày tạo', render: user => <span className="admin-users-time">{formatAdminTime(user.createdAt)}</span> },
  ];

  return <div className="ui-stack admin-users-page">
    <PageHeader title="Quản lý người dùng" eyebrow="Quản trị" description="Tìm kiếm theo email, số điện thoại, họ tên hoặc mã người dùng." />
    <SectionCard title="Danh sách người dùng" className="ui-card-table"
      description={result && !loading && !error ? `${result.totalElements} tài khoản khớp bộ lọc` : 'Đang tải'}>
      <div className="admin-users-filters">
        <Input label="Tìm kiếm" placeholder="Email, SĐT, họ tên hoặc mã…" maxLength={100} value={keyword}
          icon={<Search size={17} />} onChange={event => setKeyword(event.target.value)} />
        <Select label="Vai trò" value={query.role ?? ''} onChange={event => setQuery(previous => ({
          ...previous, role: (event.target.value || undefined) as AccountRole | undefined, page: 0 }))}>
          <option value="">Tất cả vai trò</option>
          {(Object.keys(roleLabels) as AccountRole[]).map(role => <option key={role} value={role}>{roleLabels[role]}</option>)}
        </Select>
        <Select label="Trạng thái" value={query.status ?? ''} onChange={event => setQuery(previous => ({
          ...previous, status: (event.target.value || undefined) as AccountStatus | undefined, page: 0 }))}>
          <option value="">Tất cả trạng thái</option>
          {(Object.keys(accountStatusLabels) as AccountStatus[]).map(status => <option key={status} value={status}>{accountStatusLabels[status]}</option>)}
        </Select>
      </div>
      {error ? <ApiErrorNotice message={error.message} correlationId={error.correlationId} /> : <>
        <DataTable caption="Danh sách người dùng" columns={columns} rows={result?.content ?? []} rowKey={user => user.publicId}
          loading={loading || searchPending}
          emptyState={<EmptyState title="Không có người dùng khớp bộ lọc" description="Thử từ khóa hoặc bộ lọc khác." icon={<Users size={24} />} />} />
        {!loading && !searchPending && result && <Pagination page={result.page} size={result.size} totalElements={result.totalElements}
          totalPages={result.totalPages} onPageChange={page => setQuery(previous => ({ ...previous, page }))} />}
      </>}
    </SectionCard>
    <AccountRequestsPanel />
  </div>;
}
