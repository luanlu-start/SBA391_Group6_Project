import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { Plus, MapPin } from 'lucide-react';
import { useAuthentication } from '../auth/useAuthentication';
import { profileService } from '../services/profileService';
import type { AddressInput, SavedAddress } from '../types/profile';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import { useProfileAction } from '../features/profile/hooks/useProfileAction';
import AddressForm from '../features/profile/components/AddressForm';
import AccountNavigation from '../features/profile/components/AccountNavigation';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import EmptyState from '../components/ui/EmptyState';
import Modal from '../components/ui/Modal';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import Alert from '../components/ui/Alert';
import ApiErrorNotice from '../components/ApiErrorNotice';
import '../styles/profile.css';

export default function AddressesPage() {
  const { user, initializing } = useAuthentication(); const userId = user?.id;
  const [items, setItems] = useState<SavedAddress[]>([]); const [loading, setLoading] = useState(true); const [loadError, setLoadError] = useState<ApiError>();
  const [editing, setEditing] = useState<SavedAddress>(); const [open, setOpen] = useState(false); const [deleting, setDeleting] = useState<SavedAddress>();
  const { busy, error, notice, run, clearError } = useProfileAction();
  const reload = useCallback(async () => {
    if (!userId) return; setLoading(true); setLoadError(undefined);
    try { const { data } = await profileService.addresses(); setItems(data); } catch (cause) { setLoadError(getApiError(cause)); } finally { setLoading(false); }
  }, [userId]);
  useEffect(() => { if (!initializing && userId) void reload(); }, [initializing, userId, reload]);
  function edit(item?: SavedAddress) { clearError(); setEditing(item); setOpen(true); }
  async function save(input: AddressInput) { if (await run(() => profileService.saveAddress(input, editing?.id), 'Đã lưu địa chỉ.')) { setOpen(false); await reload(); } }
  async function remove() { if (deleting && await run(() => profileService.deleteAddress(deleting.id), 'Đã xóa địa chỉ.')) { setDeleting(undefined); await reload(); } }
  async function setDefault(item: SavedAddress) { if (await run(() => profileService.saveAddress({ ...item, isDefault: true }, item.id), 'Đã đổi địa chỉ mặc định.')) await reload(); }
  if (initializing) return <p role="status">Đang kiểm tra phiên đăng nhập…</p>;
  if (!user) return <Navigate to="/login" replace />;
  return <div className="ui-stack account-page"><PageHeader eyebrow="Tài khoản" title="Sổ địa chỉ" description="Lưu các địa chỉ thường dùng để chọn nhanh khi tạo đơn."
    actions={<Button icon={<Plus size={17} />} disabled={busy} onClick={() => edit()}>Thêm địa chỉ</Button>} /><AccountNavigation />
    {notice && <Alert tone="success">{notice}</Alert>}{error && !open && !deleting && <ApiErrorNotice error={error} />}
    {loading ? <p role="status">Đang tải địa chỉ…</p> : loadError ? <><ApiErrorNotice error={loadError} /><Button onClick={() => void reload()}>Thử lại</Button></> : items.length === 0
      ? <EmptyState title="Chưa có địa chỉ đã lưu" description="Thêm địa chỉ nhà, ký túc xá hoặc công ty để dùng cho những lần gửi hàng sau." icon={<MapPin size={28} />} />
      : <div className="address-list">{items.map(item => <SectionCard key={item.id} title={item.label} actions={item.isDefault ? <Badge tone="success">Mặc định</Badge> : undefined}>
        <div className="ui-stack"><p>{item.addressText}</p><p className="ui-muted">Vị trí: {item.lat}, {item.lng}</p><div className="ui-row">
          <Button variant="secondary" size="sm" disabled={busy} onClick={() => edit(item)}>Chỉnh sửa</Button>
          {!item.isDefault && <Button variant="secondary" size="sm" disabled={busy} onClick={() => void setDefault(item)}>Đặt mặc định</Button>}
          <Button variant="ghost" size="sm" disabled={busy} onClick={() => { clearError(); setDeleting(item); }}>Xóa</Button>
          <Link className="ui-button ui-button-primary ui-button-sm" to="/sender/orders/new" state={{ pickupAddress: item }}>Tạo đơn tại đây</Link>
        </div></div></SectionCard>)}</div>}
    <Modal isOpen={open} title={editing ? 'Chỉnh sửa địa chỉ' : 'Thêm địa chỉ'} size="lg" closeDisabled={busy} onClose={() => setOpen(false)}>
      <AddressForm key={editing?.id ?? 'new'} address={editing} busy={busy} error={error} onSave={save} onCancel={() => setOpen(false)} />
    </Modal>
    <Modal isOpen={!!deleting} title="Xóa địa chỉ đã lưu?" description={deleting?.addressText} closeDisabled={busy} onClose={() => setDeleting(undefined)}>
      <div className="ui-stack">{error && <ApiErrorNotice error={error} />}<div className="ui-form-actions"><Button variant="secondary" disabled={busy} onClick={() => setDeleting(undefined)}>Hủy</Button>
        <Button variant="danger" loading={busy} onClick={() => void remove()}>Xóa địa chỉ</Button></div></div>
    </Modal>
  </div>;
}
