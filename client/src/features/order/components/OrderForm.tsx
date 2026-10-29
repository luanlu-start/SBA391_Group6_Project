import { useState, useRef, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import type { ChangeEvent } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Upload, Trash2, Loader2, UserCheck, MapPin } from 'lucide-react';
import { orderApi } from '../../../api/orderApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { Order, OrderRequest, DeliveryUrgency, PricingEstimateResponse } from '../../../types/order';
import { DELIVERY_URGENCY_MAP } from '../../../types/order';
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
import MapPickerModal from './MapPickerModal';
import PricingBreakdown from './PricingBreakdown';

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

  // Tọa độ điểm gửi và điểm nhận
  const [pickupCoords, setPickupCoords] = useState<{ lat: number | null; lng: number | null }>({
    lat: initialData?.pickupLatitude ?? initialPickup?.lat ?? null,
    lng: initialData?.pickupLongitude ?? initialPickup?.lng ?? null,
  });
  const [deliveryCoords, setDeliveryCoords] = useState<{ lat: number | null; lng: number | null }>({
    lat: initialData?.deliveryLatitude ?? null,
    lng: initialData?.deliveryLongitude ?? null,
  });

  // Modal chọn bản đồ
  const [mapTarget, setMapTarget] = useState<'pickup' | 'delivery' | null>(null);

  // Trạng thái tính cước (UC-11)
  const [pricing, setPricing] = useState<PricingEstimateResponse | null>(null);
  const [pricingLoading, setPricingLoading] = useState(false);
  const [pricingError, setPricingError] = useState<string | null>(null);

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
    watch,
    formState: { errors, isSubmitting },
  } = useForm<OrderFormValues>({
    resolver: zodResolver(orderSchema),
    defaultValues: {
      pickupAddress: initialData?.pickupAddress || initialPickup?.addressText || '',
      pickupLatitude: initialData?.pickupLatitude ?? initialPickup?.lat ?? null,
      pickupLongitude: initialData?.pickupLongitude ?? initialPickup?.lng ?? null,
      deliveryAddress: initialData?.deliveryAddress || '',
      deliveryLatitude: initialData?.deliveryLatitude ?? null,
      deliveryLongitude: initialData?.deliveryLongitude ?? null,
      receiverName: initialData?.receiverName || '',
      receiverPhone: initialData?.receiverPhone || '',
      productName: initialData?.productName || '',
      productDescription: initialData?.productDescription || '',
      quantity: initialData ? String(initialData.quantity) : '1',
      weightKg: initialData ? String(initialData.weightKg) : '1.0',
      declaredValue: initialData?.declaredValue != null ? String(initialData.declaredValue) : '0',
      urgency: (initialData?.urgency as DeliveryUrgency) || 'STANDARD',
      deliveryNote: initialData?.deliveryNote || '',
      imageUrls: '',
    },
  });

  const weightKgWatch = watch('weightKg');
  const declaredValueWatch = watch('declaredValue');
  const urgencyWatch = watch('urgency');

  // Tự động tính toán khoảng cách và giá cước theo thời gian thực (UC-11)
  useEffect(() => {
    let active = true;
    const timeoutId = setTimeout(() => {
      const weight = Number(weightKgWatch);
      const declared = declaredValueWatch ? Number(declaredValueWatch) : 0;
      const urgency = (urgencyWatch as DeliveryUrgency) || 'STANDARD';

      if (isNaN(weight) || weight <= 0) return;

      setPricingLoading(true);
      setPricingError(null);

      orderApi
        .estimatePricing({
          pickupLat: pickupCoords.lat ?? undefined,
          pickupLng: pickupCoords.lng ?? undefined,
          deliveryLat: deliveryCoords.lat ?? undefined,
          deliveryLng: deliveryCoords.lng ?? undefined,
          weightKg: weight,
          declaredValue: declared >= 0 ? declared : 0,
          urgency: urgency,
        })
        .then(res => {
          if (active) {
            setPricing(res.data);
          }
        })
        .catch(err => {
          if (active) {
            const failure = getApiError(err);
            setPricingError(failure.message || 'Không thể ước tính cước phí.');
          }
        })
        .finally(() => {
          if (active) setPricingLoading(false);
        });
    }, 400);

    return () => {
      active = false;
      clearTimeout(timeoutId);
    };
  }, [pickupCoords, deliveryCoords, weightKgWatch, declaredValueWatch, urgencyWatch]);

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

  function handleMapConfirm(lat: number, lng: number, suggestedAddress?: string) {
    if (mapTarget === 'pickup') {
      setPickupCoords({ lat, lng });
      setValue('pickupLatitude', lat);
      setValue('pickupLongitude', lng);
      if (suggestedAddress) {
        setValue('pickupAddress', suggestedAddress, { shouldValidate: true });
      }
    } else if (mapTarget === 'delivery') {
      setDeliveryCoords({ lat, lng });
      setValue('deliveryLatitude', lat);
      setValue('deliveryLongitude', lng);
      if (suggestedAddress) {
        setValue('deliveryAddress', suggestedAddress, { shouldValidate: true });
      }
    }
    setMapTarget(null);
  }

  async function submit(values: OrderFormValues) {
    setApiError(null);
    try {
      const payload: OrderRequest = {
        pickupAddress: values.pickupAddress.trim(),
        pickupLatitude: pickupCoords.lat ?? undefined,
        pickupLongitude: pickupCoords.lng ?? undefined,
        deliveryAddress: values.deliveryAddress.trim(),
        deliveryLatitude: deliveryCoords.lat ?? undefined,
        deliveryLongitude: deliveryCoords.lng ?? undefined,
        receiverName: values.receiverName.trim(),
        receiverPhone: values.receiverPhone.trim(),
        productName: values.productName.trim(),
        productDescription: values.productDescription?.trim() || undefined,
        quantity: Number(values.quantity),
        weightKg: Number(values.weightKg),
        declaredValue: values.declaredValue ? Number(values.declaredValue) : 0,
        urgency: values.urgency,
        distanceKm: pricing?.distanceKm,
        priceSuggested: pricing?.totalEstimatedPrice,
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

      for (const field of Object.keys(orderSchema.shape) as (keyof OrderFormValues)[]) {
        if (failure.fieldErrors[field]) {
          setError(field, { type: 'server', message: failure.fieldErrors[field] });
        }
      }
    }
  }

  return (
    <>
      <form className="ui-form" noValidate onSubmit={handleSubmit(submit)} aria-busy={isSubmitting || uploading}>
        {apiError && <ApiErrorNotice error={apiError} />}
        {addressLoadError && <ApiErrorNotice error={addressLoadError} />}

        {/* 1. Thông tin người gửi & Điểm lấy hàng */}
        <fieldset className="ui-form-section" disabled={isSubmitting}>
          <legend>1. Người gửi & Điểm lấy hàng</legend>
          <p className="ui-form-description">Thông tin người gửi và vị trí nhận bưu kiện.</p>
          <div className="ui-form-grid">
            {user && (
              <div
                className="ui-span-full"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.75rem',
                  padding: '0.85rem 1rem',
                  borderRadius: 'var(--radius-md, 10px)',
                  background: 'var(--brand-soft, rgba(243, 112, 33, 0.1))',
                  border: '1px solid var(--brand-border, rgba(243, 112, 33, 0.3))',
                  fontSize: '0.925rem',
                  color: 'var(--text-primary, #f1f4fb)',
                }}
              >
                <UserCheck size={20} style={{ color: 'var(--primary, #f37021)', flexShrink: 0 }} />
                <div>
                  <strong style={{ color: 'var(--text-accent, #ffb17d)' }}>Người gửi:</strong> {user.fullName || 'Người dùng'}
                  <span style={{ color: 'var(--text-secondary, #b0bdd2)' }}>
                    {user.phone ? ` • SĐT: ${user.phone}` : ''}
                    {user.email ? ` • Email: ${user.email}` : ''}
                  </span>
                </div>
              </div>
            )}

            {savedAddresses.length > 0 && (
              <Select
                label="Chọn điểm lấy từ sổ địa chỉ"
                fieldClassName="ui-span-full"
                defaultValue={initialPickup?.id ?? ''}
                onChange={event => {
                  const selected = savedAddresses.find(item => item.id === event.target.value);
                  if (selected) {
                    setValue('pickupAddress', selected.addressText, { shouldValidate: true });
                    setPickupCoords({ lat: selected.lat, lng: selected.lng });
                    setValue('pickupLatitude', selected.lat);
                    setValue('pickupLongitude', selected.lng);
                  }
                }}
              >
                <option value="">Chọn địa chỉ đã lưu</option>
                {savedAddresses.map(item => (
                  <option key={item.id} value={item.id}>
                    {item.label} — {item.addressText} ({item.lat}, {item.lng})
                  </option>
                ))}
              </Select>
            )}

            <div className="ui-span-full" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <div style={{ display: 'flex', gap: '8px', alignItems: 'flex-start' }}>
                <div style={{ flex: 1 }}>
                  <Input
                    label="Địa chỉ lấy hàng"
                    placeholder="Số nhà, tên đường, phường/xã, quận/huyện, tỉnh/thành phố"
                    required
                    maxLength={500}
                    {...register('pickupAddress')}
                    error={errors.pickupAddress?.message}
                  />
                </div>
                <div style={{ paddingTop: '28px' }}>
                  <Button
                    type="button"
                    variant="secondary"
                    icon={<MapPin size={16} style={{ color: 'var(--primary, #f37021)' }} />}
                    onClick={() => setMapTarget('pickup')}
                    title="Mở bản đồ chọn điểm lấy"
                  >
                    Bản đồ
                  </Button>
                </div>
              </div>

              {pickupCoords.lat && pickupCoords.lng && (
                <div style={{ fontSize: '0.8rem', color: 'var(--accent-emerald, #6cdeb4)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <MapPin size={13} style={{ color: 'var(--accent-emerald, #6cdeb4)' }} />
                  <span>Đã ghim tọa độ lấy hàng: [{pickupCoords.lat}, {pickupCoords.lng}]</span>
                </div>
              )}
            </div>
          </div>
        </fieldset>

        {/* 2. Thông tin người nhận & Điểm giao hàng */}
        <fieldset className="ui-form-section" disabled={isSubmitting}>
          <legend>2. Người nhận & Điểm giao hàng</legend>
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

            {savedAddresses.length > 0 && (
              <Select
                label="Chọn điểm giao từ sổ địa chỉ"
                fieldClassName="ui-span-full"
                defaultValue=""
                onChange={event => {
                  const selected = savedAddresses.find(item => item.id === event.target.value);
                  if (selected) {
                    setValue('deliveryAddress', selected.addressText, { shouldValidate: true });
                    setDeliveryCoords({ lat: selected.lat, lng: selected.lng });
                    setValue('deliveryLatitude', selected.lat);
                    setValue('deliveryLongitude', selected.lng);
                  }
                }}
              >
                <option value="">Chọn địa chỉ đã lưu</option>
                {savedAddresses.map(item => (
                  <option key={item.id} value={item.id}>
                    {item.label} — {item.addressText} ({item.lat}, {item.lng})
                  </option>
                ))}
              </Select>
            )}

            <div className="ui-span-full" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <div style={{ display: 'flex', gap: '8px', alignItems: 'flex-start' }}>
                <div style={{ flex: 1 }}>
                  <Input
                    label="Địa chỉ giao hàng"
                    placeholder="Số nhà, tên đường, phường/xã, quận/huyện, tỉnh/thành phố"
                    required
                    maxLength={500}
                    {...register('deliveryAddress')}
                    error={errors.deliveryAddress?.message}
                  />
                </div>
                <div style={{ paddingTop: '28px' }}>
                  <Button
                    type="button"
                    variant="secondary"
                    icon={<MapPin size={16} style={{ color: 'var(--primary, #f37021)' }} />}
                    onClick={() => setMapTarget('delivery')}
                    title="Mở bản đồ chọn điểm giao"
                  >
                    Bản đồ
                  </Button>
                </div>
              </div>

              {deliveryCoords.lat && deliveryCoords.lng && (
                <div style={{ fontSize: '0.8rem', color: 'var(--accent-emerald, #6cdeb4)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <MapPin size={13} style={{ color: 'var(--accent-emerald, #6cdeb4)' }} />
                  <span>Đã ghim tọa độ giao hàng: [{deliveryCoords.lat}, {deliveryCoords.lng}]</span>
                </div>
              )}
            </div>
          </div>
        </fieldset>

        {/* 3. Thông tin hàng hóa & bưu kiện */}
        <fieldset className="ui-form-section" disabled={isSubmitting}>
          <legend>3. Hàng hóa & Gói dịch vụ</legend>
          <p className="ui-form-description">Chi tiết vật phẩm, khối lượng và gói dịch vụ vận chuyển.</p>
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
              hint="Phí bảo hiểm: 0.5% giá trị khai báo (tối đa 50.000đ)"
              fieldClassName="ui-span-full"
              {...register('declaredValue')}
              error={errors.declaredValue?.message}
            />

            {/* Chọn gói vận chuyển (Urgency) */}
            <div className="ui-span-full">
              <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 500, marginBottom: '6px', color: 'var(--text-primary, #f1f4fb)' }}>
                Gói tốc độ giao hàng
              </label>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                  gap: '10px',
                }}
              >
                {(Object.keys(DELIVERY_URGENCY_MAP) as DeliveryUrgency[]).map(urgencyKey => {
                  const info = DELIVERY_URGENCY_MAP[urgencyKey];
                  const isSelected = urgencyWatch === urgencyKey;
                  return (
                    <label
                      key={urgencyKey}
                      style={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '4px',
                        padding: '0.75rem 1rem',
                        borderRadius: 'var(--radius-md, 10px)',
                        border: isSelected
                          ? '2px solid var(--primary, #f37021)'
                          : '1px solid var(--border-subtle, #333d4c)',
                        backgroundColor: isSelected ? 'var(--brand-soft, rgba(243, 112, 33, 0.1))' : 'var(--bg-secondary, #181e27)',
                        color: 'var(--text-primary, #f1f4fb)',
                        cursor: 'pointer',
                        transition: 'all 0.15s ease',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <input
                            type="radio"
                            value={urgencyKey}
                            {...register('urgency')}
                            style={{ margin: 0, accentColor: 'var(--primary, #f37021)' }}
                          />
                          <span style={{ fontWeight: 600, fontSize: '0.9rem', color: isSelected ? 'var(--text-accent, #ffb17d)' : 'var(--text-primary, #f1f4fb)' }}>
                            {info.label}
                          </span>
                        </div>
                        {info.multiplier > 1 && (
                          <span
                            style={{
                              fontSize: '0.75rem',
                              fontWeight: 600,
                              background: 'var(--brand-border, rgba(243, 112, 33, 0.3))',
                              color: 'var(--text-accent, #ffb17d)',
                              padding: '2px 8px',
                              borderRadius: 'var(--radius-sm, 6px)',
                            }}
                          >
                            x{info.multiplier}
                          </span>
                        )}
                      </div>
                      <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary, #b0bdd2)', paddingLeft: '24px' }}>
                        {info.desc}
                      </span>
                    </label>
                  );
                })}
              </div>
            </div>

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

        {/* 4. Bảng tính cước phí ước tính (UC-11) */}
        <fieldset className="ui-form-section" disabled={isSubmitting}>
          <legend>4. Bảng ước tính cước phí (UC-11)</legend>
          <p className="ui-form-description">
            Cước phí được tự động tính toán dựa trên khoảng cách định tuyến, khối lượng và gói dịch vụ.
          </p>
          <PricingBreakdown pricing={pricing} loading={pricingLoading} error={pricingError} />
        </fieldset>

        {/* 5. Hình ảnh bưu kiện */}
        <fieldset className="ui-form-section" disabled={isSubmitting}>
          <legend>5. Hình ảnh bưu kiện (Tùy chọn)</legend>
          <p className="ui-form-description">
            Chụp ảnh gói hàng trước khi gửi để làm bằng chứng vận chuyển và hỗ trợ khiếu nại (tối đa 5 ảnh, JPG/PNG/WEBP/GIF, ≤ 5MB/ảnh).
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
              <span style={{ fontSize: '0.85rem', color: 'var(--text-muted, #8e9eb8)' }}>
                {uploadedImages.length}/5 ảnh đã tải lên
              </span>
            </div>

            {uploadError && (
              <p style={{ color: 'var(--accent-rose, #ffa6b3)', fontSize: '0.85rem', margin: 0 }}>
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
                      borderRadius: 'var(--radius-md, 10px)',
                      overflow: 'hidden',
                      border: '1px solid var(--border-subtle, #333d4c)',
                      backgroundColor: 'var(--bg-tertiary, #242d39)',
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
                        color: 'var(--accent-rose, #ffa6b3)',
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

      {/* Modal chọn vị trí trên bản đồ */}
      {mapTarget && (
        <MapPickerModal
          isOpen={Boolean(mapTarget)}
          onClose={() => setMapTarget(null)}
          title={mapTarget === 'pickup' ? 'Chọn tọa độ điểm lấy hàng' : 'Chọn tọa độ điểm giao hàng'}
          initialLat={mapTarget === 'pickup' ? pickupCoords.lat : deliveryCoords.lat}
          initialLng={mapTarget === 'pickup' ? pickupCoords.lng : deliveryCoords.lng}
          onConfirm={handleMapConfirm}
        />
      )}
    </>
  );
}
