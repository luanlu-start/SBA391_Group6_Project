import { useState, useRef, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import type { ChangeEvent } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Upload, Trash2, Loader2, UserCheck } from 'lucide-react';
import { orderApi } from '../../../api/orderApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { Order, OrderRequest } from '../../../types/order';
import { orderSchema } from '../schemas/orderSchema';
import type { OrderFormValues } from '../schemas/orderSchema';
import { useAuthentication } from '../../../auth/useAuthentication';
import Input from '../../../components/ui/Input';
import Textarea from '../../../components/ui/Textarea';
import Button from '../../../components/ui/Button';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import Select from '../../../components/ui/Select';
import { profileService } from '../../../services/profileService';
import type { SavedAddress } from '../../../types/profile';

interface OrderFormProps {
  initialData?: Order;
  onSuccess: (order: Order) => void;
  onCancel?: () => void;
}

export default function OrderForm({ initialData, onSuccess, onCancel }: OrderFormProps) {
  const isEditMode = Boolean(initialData);
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [uploadedImages, setUploadedImages] = useState<string[]>(initialData?.imageUrls || []);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { user } = useAuthentication();
  const location = useLocation();
  const initialPickup = (location.state as { pickupAddress?: SavedAddress } | null)?.pickupAddress;
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([]);
  const [addressLoadError, setAddressLoadError] = useState<ApiError>();
  const userId = user?.id;
  useEffect(() => {
    if (!userId) return;
    let active = true;
    void profileService.addresses().then(({ data }) => { if (active) setSavedAddresses(data); })
      .catch(cause => { if (active) setAddressLoadError(getApiError(cause)); });
    return () => { active = false; };
  }, [userId]);

  const {
    register,
    handleSubmit,
    setError,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<OrderFormValues>({
    resolver: zodResolver(orderSchema),
    defaultValues: {
      pickupAddress: initialData?.pickupAddress || initialPickup?.addressText || '',
      deliveryAddress: initialData?.deliveryAddress || '',
      receiverName: initialData?.receiverName || '',
      receiverPhone: initialData?.receiverPhone || '',
      productName: initialData?.productName || '',
      productDescription: initialData?.productDescription || '',
      quantity: initialData ? String(initialData.quantity) : '1',
      weightKg: initialData ? String(initialData.weightKg) : '1.0',
      declaredValue: initialData?.declaredValue != null ? String(initialData.declaredValue) : '0',
      deliveryNote: initialData?.deliveryNote || '',
      imageUrls: '',
    },
  });

  async function handleFileSelect(event: ChangeEvent<HTMLInputElement>) {
    const files = event.target.files;
    if (!files || files.length === 0) return;

    setUploadError(null);

    const validFiles: File[] = [];
    const allowedTypes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];

    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      if (!allowedTypes.includes(file.type)) {
        setUploadError(`File ${file.name} không phải là hình ảnh hợp lệ (JPG, PNG, WEBP, GIF).`);
        return;
      }
      if (file.size > 5 * 1024 * 1024) {
        setUploadError(`File ${file.name} vượt quá dung lượng tối đa cho phép (5MB).`);
        return;
      }
      validFiles.push(file);
    }

    if (uploadedImages.length + validFiles.length > 5) {
      setUploadError('Bạn chỉ có thể tải lên tối đa 5 hình ảnh cho mỗi đơn hàng.');
      return;
    }

    setUploading(true);
    try {
      const response = await orderApi.uploadImages(validFiles);
      if (response.data && response.data.length > 0) {
        setUploadedImages(prev => [...prev, ...response.data]);
      }
    } catch (cause) {
      const failure = getApiError(cause);
      setUploadError(failure.message || 'Tải ảnh lên thất bại. Vui lòng thử lại.');
    } finally {
      setUploading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  }

  function handleRemoveImage(indexToRemove: number) {
    setUploadedImages(prev => prev.filter((_, idx) => idx !== indexToRemove));
  }

  async function submit(values: OrderFormValues) {
    setApiError(null);
    try {
      const payload: OrderRequest = {
        pickupAddress: values.pickupAddress.trim(),
        deliveryAddress: values.deliveryAddress.trim(),
        receiverName: values.receiverName.trim(),
        receiverPhone: values.receiverPhone.trim(),
        productName: values.productName.trim(),
        productDescription: values.productDescription?.trim() || undefined,
        quantity: Number(values.quantity),
        weightKg: Number(values.weightKg),
        declaredValue: values.declaredValue ? Number(values.declaredValue) : 0,
        deliveryNote: values.deliveryNote?.trim() || undefined,
        imageUrls: uploadedImages.length > 0 ? uploadedImages : undefined,
      };

      const response = isEditMode && initialData
        ? await orderApi.update(initialData.id, payload)
        : await orderApi.create(payload);

      onSuccess(response.data);
    } catch (cause) {
      const failure = getApiError(cause);
      setApiError(failure);

      // Map backend validation errors to form fields
      for (const field of Object.keys(orderSchema.shape) as (keyof OrderFormValues)[]) {
        if (failure.fieldErrors[field]) {
          setError(field, { type: 'server', message: failure.fieldErrors[field] });
        }
      }
    }
  }

  return (
    <form className="ui-form" noValidate onSubmit={handleSubmit(submit)} aria-busy={isSubmitting || uploading}>
      {apiError && (
        <ApiErrorNotice error={apiError} />
      )}
      {addressLoadError && <ApiErrorNotice error={addressLoadError} />}

      {/* Thông tin người gửi & Điểm lấy hàng */}
      <fieldset className="ui-form-section" disabled={isSubmitting}>
        <legend>1. Người gửi & Điểm nhận</legend>
        <p className="ui-form-description">Thông tin người gửi được tự động xác nhận theo tài khoản đăng nhập của bạn.</p>
        <div className="ui-form-grid">
          {user && (
            <div className="ui-span-full" style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.75rem',
              padding: '0.85rem 1rem',
              borderRadius: 'var(--radius-md, 8px)',
              background: 'var(--color-bg-subtle, #f8fafc)',
              border: '1px solid var(--color-border-subtle, #e2e8f0)',
              fontSize: '0.925rem'
            }}>
              <UserCheck size={20} style={{ color: 'var(--color-primary-600, #2563eb)', flexShrink: 0 }} />
              <div>
                <strong>Người gửi:</strong> {user.fullName || 'Người dùng'}
                {user.phone ? ` • SĐT: ${user.phone}` : ''}
                {user.email ? ` • Email: ${user.email}` : ''}
              </div>
            </div>
          )}
          {savedAddresses.length > 0 && <Select label="Chọn điểm lấy từ sổ địa chỉ" fieldClassName="ui-span-full" defaultValue={initialPickup?.id ?? ''}
            onChange={event => { const selected = savedAddresses.find(item => item.id === event.target.value); if (selected) setValue('pickupAddress', selected.addressText, { shouldValidate: true }); }}>
            <option value="">Chọn địa chỉ đã lưu</option>{savedAddresses.map(item => <option key={item.id} value={item.id}>{item.label} — {item.addressText} ({item.lat}, {item.lng})</option>)}</Select>}
          <Input
            label="Địa chỉ lấy hàng"
            placeholder="Số nhà, tên đường, phường/xã, quận/huyện, tỉnh/thành phố"
            required
            maxLength={500}
            fieldClassName="ui-span-full"
            {...register('pickupAddress')}
            error={errors.pickupAddress?.message}
          />
        </div>
      </fieldset>

      {/* Thông tin người nhận & Điểm giao hàng */}
      <fieldset className="ui-form-section" disabled={isSubmitting}>
        <legend>2. Người nhận & Điểm giao</legend>
        <p className="ui-form-description">Thông tin người tiếp nhận và địa chỉ giao bưu kiện.</p>
        <div className="ui-form-grid">
          <Input
            label="Họ tên người nhận"
            placeholder="Nguyễn Văn A"
            required
            maxLength={100}
            {...register('receiverName')}
            error={errors.receiverName?.message}
          />
          <Input
            label="Số điện thoại người nhận"
            type="tel"
            placeholder="0912345678"
            required
            maxLength={20}
            {...register('receiverPhone')}
            error={errors.receiverPhone?.message}
          />
          <Input
            label="Địa chỉ giao hàng"
            placeholder="Số nhà, tên đường, phường/xã, quận/huyện, tỉnh/thành phố"
            required
            maxLength={500}
            fieldClassName="ui-span-full"
            {...register('deliveryAddress')}
            error={errors.deliveryAddress?.message}
          />
          {savedAddresses.length > 0 && <Select label="Chọn điểm giao từ sổ địa chỉ" fieldClassName="ui-span-full" defaultValue=""
            onChange={event => { const selected = savedAddresses.find(item => item.id === event.target.value); if (selected) setValue('deliveryAddress', selected.addressText, { shouldValidate: true }); }}>
            <option value="">Chọn địa chỉ đã lưu</option>{savedAddresses.map(item => <option key={item.id} value={item.id}>{item.label} — {item.addressText} ({item.lat}, {item.lng})</option>)}</Select>}
        </div>
      </fieldset>

      {/* Thông tin hàng hóa & bưu kiện */}
      <fieldset className="ui-form-section" disabled={isSubmitting}>
        <legend>3. Hàng hóa & Bưu kiện</legend>
        <p className="ui-form-description">Chi tiết vật phẩm, trọng lượng và giá trị bưu kiện gửi.</p>
        <div className="ui-form-grid">
          <Input
            label="Tên hàng hóa"
            placeholder="Tài liệu, Điện thoại, Quần áo..."
            required
            maxLength={255}
            fieldClassName="ui-span-full"
            {...register('productName')}
            error={errors.productName?.message}
          />
          <Input
            label="Số lượng kiện"
            type="number"
            min="1"
            step="1"
            required
            placeholder="1"
            {...register('quantity')}
            error={errors.quantity?.message}
          />
          <Input
            label="Khối lượng (kg)"
            type="number"
            min="0.001"
            step="0.01"
            required
            placeholder="1.0"
            hint="Đơn vị tính: kilogram (kg)"
            {...register('weightKg')}
            error={errors.weightKg?.message}
          />
          <Input
            label="Giá trị khai báo (VNĐ)"
            type="number"
            min="0"
            step="1000"
            placeholder="0"
            hint="Khai báo để được bảo hiểm hàng hóa khi vận chuyển."
            fieldClassName="ui-span-full"
            {...register('declaredValue')}
            error={errors.declaredValue?.message}
          />
          <Textarea
            label="Mô tả hàng hóa"
            placeholder="Chi tiết kích thước, chất liệu, tính chất dễ vỡ..."
            maxLength={1000}
            hint="Tối đa 1000 ký tự."
            fieldClassName="ui-span-full"
            {...register('productDescription')}
            error={errors.productDescription?.message}
          />
          <Textarea
            label="Ghi chú giao hàng"
            placeholder="Chỉ dẫn tài xế khi đến lấy hoặc giao hàng (ví dụ: Gọi trước khi tới, bấm chuông...)"
            maxLength={500}
            hint="Tối đa 500 ký tự."
            fieldClassName="ui-span-full"
            {...register('deliveryNote')}
            error={errors.deliveryNote?.message}
          />
        </div>
      </fieldset>

      {/* Hình ảnh bưu kiện */}
      <fieldset className="ui-form-section" disabled={isSubmitting}>
        <legend>4. Hình ảnh bưu kiện (Tùy chọn)</legend>
        <p className="ui-form-description">
          Chụp ảnh gói hàng trước khi gửi để làm bằng chứng vận chuyển và hỗ trợ khiếu nại (tối đa 5 ảnh, định dạng JPG/PNG/WEBP/GIF, tối đa 5MB/ảnh).
        </p>

        <div className="ui-stack" style={{ gap: '12px' }}>
          <div className="ui-row" style={{ alignItems: 'center', gap: '12px' }}>
            <input
              type="file"
              ref={fileInputRef}
              accept="image/jpeg,image/png,image/webp,image/gif"
              multiple
              style={{ display: 'none' }}
              onChange={e => void handleFileSelect(e)}
              disabled={uploading || uploadedImages.length >= 5}
            />
            <Button
              type="button"
              variant="secondary"
              icon={uploading ? <Loader2 size={16} className="ui-spin" /> : <Upload size={16} />}
              disabled={uploading || uploadedImages.length >= 5}
              onClick={() => fileInputRef.current?.click()}
            >
              {uploading ? 'Đang tải ảnh lên…' : 'Chọn ảnh bưu kiện'}
            </Button>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              {uploadedImages.length}/5 ảnh đã tải lên
            </span>
          </div>

          {uploadError && (
            <p style={{ color: 'var(--accent-rose)', fontSize: '0.85rem', margin: 0 }}>
              {uploadError}
            </p>
          )}
        </div>

        {uploadedImages.length > 0 && (
          <div style={{ marginTop: '16px' }}>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(110px, 1fr))',
                gap: '12px',
              }}
            >
              {uploadedImages.map((url, idx) => (
                <div
                  key={idx}
                  style={{
                    position: 'relative',
                    aspectRatio: '1 / 1',
                    borderRadius: 'var(--radius-md)',
                    overflow: 'hidden',
                    border: '1px solid var(--border-subtle)',
                    backgroundColor: 'var(--bg-tertiary)',
                  }}
                >
                  <img
                    src={url}
                    alt={`Ảnh kiện hàng ${idx + 1}`}
                    style={{
                      width: '100%',
                      height: '100%',
                      objectFit: 'cover',
                    }}
                  />
                  <button
                    type="button"
                    aria-label="Xóa ảnh"
                    onClick={() => handleRemoveImage(idx)}
                    style={{
                      position: 'absolute',
                      top: '4px',
                      right: '4px',
                      background: 'rgba(0, 0, 0, 0.7)',
                      color: 'var(--accent-rose)',
                      border: 0,
                      borderRadius: '50%',
                      width: '24px',
                      height: '24px',
                      display: 'grid',
                      placeItems: 'center',
                      cursor: 'pointer',
                      padding: 0,
                    }}
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}
      </fieldset>

      <div className="ui-form-actions">
        {onCancel && (
          <Button variant="secondary" onClick={onCancel} disabled={isSubmitting || uploading}>
            Hủy bỏ
          </Button>
        )}
        <Button
          type="submit"
          loading={isSubmitting}
          disabled={uploading}
          loadingText={isEditMode ? 'Đang lưu thay đổi…' : 'Đang xử lý tạo đơn…'}
        >
          {isEditMode ? 'Lưu thay đổi' : 'Tạo đơn hàng'}
        </Button>
      </div>
    </form>
  );
}
