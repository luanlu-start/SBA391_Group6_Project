import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { ApiError } from '../../../api/ApiError';
import type { AddressInput, SavedAddress } from '../../../types/profile';
import { addressSchema } from '../schemas/profileSchema';
import type { AddressFormValues } from '../schemas/profileSchema';
import AddressMap from './AddressMap';
import Input from '../../../components/ui/Input';
import Textarea from '../../../components/ui/Textarea';
import Checkbox from '../../../components/ui/Checkbox';
import Button from '../../../components/ui/Button';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

export default function AddressForm({ address, busy, error, onSave, onCancel }: { address?: SavedAddress; busy: boolean; error?: ApiError; onSave: (value: AddressInput) => Promise<void>; onCancel: () => void }) {
  const [locationError, setLocationError] = useState('');
  const { register, handleSubmit, setValue, setError, watch, formState: { errors } } = useForm<AddressFormValues>({ resolver: zodResolver(addressSchema), defaultValues: {
    label: address?.label ?? '', addressText: address?.addressText ?? '', lat: address ? String(address.lat) : '', lng: address ? String(address.lng) : '', isDefault: address?.isDefault ?? false,
  } });
  const lat = watch('lat'), lng = watch('lng');
  useEffect(() => { for (const field of ['label', 'addressText', 'lat', 'lng'] as const) if (error?.fieldErrors[field]) setError(field, { message: error.fieldErrors[field] }); }, [error, setError]);
  function pick(latitude: number, longitude: number) { setValue('lat', latitude.toFixed(7), { shouldValidate: true }); setValue('lng', longitude.toFixed(7), { shouldValidate: true }); }
  function locate() {
    if (!navigator.geolocation) { setLocationError('Trình duyệt chưa hỗ trợ định vị. Bạn có thể chọn trên bản đồ hoặc nhập tọa độ.'); return; }
    navigator.geolocation.getCurrentPosition(position => { pick(position.coords.latitude, position.coords.longitude); setLocationError(''); }, () => setLocationError('Chưa lấy được vị trí. Bạn có thể chọn trên bản đồ hoặc nhập tọa độ.'), { timeout: 10000 });
  }
  return <form className="ui-form" noValidate aria-busy={busy} onSubmit={handleSubmit(values => onSave({ ...values, lat: Number(Number(values.lat).toFixed(7)), lng: Number(Number(values.lng).toFixed(7)) }))}>
    {error && <ApiErrorNotice error={error} />}
    <Input label="Tên địa chỉ" placeholder="Nhà, ký túc xá, công ty…" required maxLength={60} disabled={busy} {...register('label')} error={errors.label?.message} />
    <Textarea label="Địa chỉ đầy đủ" required maxLength={500} autoComplete="street-address" disabled={busy} {...register('addressText')} error={errors.addressText?.message} />
    <div><p className="ui-muted">Chọn vị trí trên bản đồ hoặc nhập tọa độ bên dưới.</p><AddressMap lat={Number.parseFloat(lat)} lng={Number.parseFloat(lng)} onPick={pick} /></div>
    <div className="ui-row"><Button variant="secondary" size="sm" disabled={busy} onClick={locate}>Dùng vị trí hiện tại</Button>{locationError && <p role="status" className="ui-muted">{locationError}</p>}</div>
    <div className="ui-form-grid"><Input label="Vĩ độ" type="number" step="0.0000001" required disabled={busy} {...register('lat')} error={errors.lat?.message} />
      <Input label="Kinh độ" type="number" step="0.0000001" required disabled={busy} {...register('lng')} error={errors.lng?.message} /></div>
    <Checkbox label="Đặt làm địa chỉ mặc định" disabled={busy} {...register('isDefault')} />
    <div className="ui-form-actions"><Button variant="secondary" disabled={busy} onClick={onCancel}>Hủy</Button><Button type="submit" loading={busy} loadingText="Đang lưu…">Lưu địa chỉ</Button></div>
  </form>;
}
