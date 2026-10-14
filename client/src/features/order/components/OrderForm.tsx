import { useState, useRef } from 'react';
import type { ChangeEvent } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Upload, Trash2, Loader2 } from 'lucide-react';
import { orderApi } from '../../../api/orderApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { Order, OrderRequest } from '../../../types/order';
import { orderSchema } from '../schemas/orderSchema';
import type { OrderFormValues } from '../schemas/orderSchema';
import { getSessionState } from '../../../auth/authSession';
import Input from '../../../components/ui/Input';
import Textarea from '../../../components/ui/Textarea';
import Button from '../../../components/ui/Button';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

interface OrderFormProps {
  onSuccess: (order: Order) => void;
  onCancel?: () => void;
}

export default function OrderForm({ onSuccess, onCancel }: OrderFormProps) {
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [uploadedImages, setUploadedImages] = useState<string[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const currentUser = getSessionState().user;
  const defaultSenderId = currentUser?.id ? String(currentUser.id) : '1';

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<OrderFormValues>({
    resolver: zodResolver(orderSchema),
    defaultValues: {
      senderId: defaultSenderId,
      pickupAddress: '',
      deliveryAddress: '',
      receiverName: '',
      receiverPhone: '',
      productName: '',
      productDescription: '',
      quantity: '1',
      weightKg: '1.0',
      declaredValue: '0',
      deliveryNote: '',
      imageUrls: '',
    },
  });

  async function handleFileSelect(event: ChangeEvent<HTMLInputElement>) {
    const files = event.target.files;
    if (!files || files.length === 0) return;

    setUploadError(null);

    const validFiles: File[] = [];
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      if (!file.type.startsWith('image/')) {
        setUploadError(`Tệp "${file.name}" không phải định dạng ảnh hợp lệ.`);
        return;
      }
      if (file.size > 10 * 1024 * 1024) {
        setUploadError(`Tệp "${file.name}" vượt quá kích thước tối đa 10MB.`);
        return;
      }
      validFiles.push(file);
    }

    setUploading(true);
    try {
      const res = await orderApi.uploadImages(validFiles);
      if (res.data && res.data.length > 0) {
        setUploadedImages(prev => [...prev, ...res.data]);
      }
    } catch (cause) {
      const failure = getApiError(cause);
      setUploadError(failure.message || 'Tải ảnh lên thất bại. Vui lòng thử lại.');
    } finally {
      setUploading(false);
      // Reset file input value to allow selecting the same file again if needed
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
        senderId: Number(values.senderId),
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

      const response = await orderApi.create(payload);
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
        <ApiErrorNotice message={apiError.message} correlationId={apiError.correlationId} />
      )}

      {/* Thông tin người gửi & Điểm lấy hàng */}
      <fieldset className="ui-form-section" disabled={isSubmitting}>
        <legend>1. Người gửi & Điểm nhận</legend>
        <p className="ui-form-description">Cung cấp ID người gửi và địa chỉ lấy hàng ban đầu.</p>
        <div className="ui-form-grid">
          <Input
            label="ID người gửi"
            type="number"
            min="1"
            placeholder="1"
            required
            hint="Mặc định sử dụng ID tài khoản của bạn hoặc ID khách hàng gửi đơn."
            {...register('senderId')}
            error={errors.senderId?.message}
          />
          <div /> {/* Giữ grid cân đối */}
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
            placeholder="Giao giờ hành chính, gọi trước khi giao, hàng dễ vỡ xin nhẹ tay..."
            maxLength={500}
            hint="Tối đa 500 ký tự."
            fieldClassName="ui-span-full"
            {...register('deliveryNote')}
            error={errors.deliveryNote?.message}
          />
        </div>
      </fieldset>

      {/* Upload Hình ảnh đính kèm */}
      <fieldset className="ui-form-section" disabled={isSubmitting}>
        <legend>4. Hình ảnh bưu gửi</legend>
        <p className="ui-form-description">
          Tải lên ảnh chụp thực tế kiện hàng, hóa đơn hoặc tem niêm phong (hỗ trợ JPG, PNG, WEBP, GIF, tối đa 10MB/ảnh).
        </p>

        {/* Khung kéo thả / chọn file */}
        <div
          onClick={() => fileInputRef.current?.click()}
          style={{
            border: '2px dashed var(--border-subtle)',
            borderRadius: 'var(--radius-md)',
            padding: '24px 16px',
            textAlign: 'center',
            backgroundColor: 'var(--bg-primary)',
            cursor: uploading ? 'wait' : 'pointer',
            transition: 'border-color var(--transition-fast)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: '8px',
          }}
          onMouseEnter={e => (e.currentTarget.style.borderColor = 'var(--primary)')}
          onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border-subtle)')}
        >
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            multiple
            style={{ display: 'none' }}
            onChange={handleFileSelect}
            disabled={uploading || isSubmitting}
          />

          {uploading ? (
            <div className="ui-row" style={{ alignItems: 'center', gap: '8px', color: 'var(--primary)' }}>
              <Loader2 size={24} className="ui-spin" />
              <span style={{ fontSize: '0.85rem', fontWeight: 600 }}>Đang tải ảnh lên máy chủ…</span>
            </div>
          ) : (
            <>
              <div
                style={{
                  width: '44px',
                  height: '44px',
                  borderRadius: '50%',
                  backgroundColor: 'var(--bg-tertiary)',
                  display: 'grid',
                  placeItems: 'center',
                  color: 'var(--primary)',
                }}
              >
                <Upload size={20} />
              </div>
              <div>
                <p style={{ fontWeight: 600, fontSize: '0.86rem', color: 'var(--text-primary)' }}>
                  Nhấn để tải ảnh lên hoặc kéo thả tệp vào đây
                </p>
                <p style={{ fontSize: '0.74rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                  Hỗ trợ JPG, PNG, WEBP, GIF (cho phép chọn nhiều ảnh cùng lúc)
                </p>
              </div>
            </>
          )}
        </div>

        {uploadError && (
          <p className="ui-field-error" style={{ marginTop: '8px' }}>
            {uploadError}
          </p>
        )}

        {/* Danh sách ảnh đã upload */}
        {uploadedImages.length > 0 && (
          <div style={{ marginTop: '16px' }}>
            <p style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', marginBottom: '8px' }}>
              Đã tải lên ({uploadedImages.length} ảnh):
            </p>
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
                    borderRadius: 'var(--radius-md)',
                    overflow: 'hidden',
                    border: '1px solid var(--border-subtle)',
                    aspectRatio: '1 / 1',
                    backgroundColor: 'var(--bg-secondary)',
                  }}
                >
                  <img
                    src={url}
                    alt={`Ảnh kiện hàng ${idx + 1}`}
                    style={{
                      width: '100%',
                      height: '100%',
                      objectFit: 'cover',
                      display: 'block',
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
          loadingText="Đang xử lý tạo đơn…"
        >
          Tạo đơn hàng
        </Button>
      </div>
    </form>
  );
}
