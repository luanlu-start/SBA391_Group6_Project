import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { productApi } from '../../../api/productApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { Product } from '../../../types/product';
import { productSchema } from '../schemas/productSchema';
import type { ProductFormValues } from '../schemas/productSchema';
import Input from '../../../components/ui/Input';
import Select from '../../../components/ui/Select';
import Textarea from '../../../components/ui/Textarea';
import Button from '../../../components/ui/Button';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

interface ProductFormProps {
  product: Product | null;
  onCancel: () => void;
  onSaved: () => void;
  onBusyChange: (busy: boolean) => void;
}

export default function ProductForm({ product, onCancel, onSaved, onBusyChange }: ProductFormProps) {
  const [error, setFailure] = useState<ApiError | null>(null);
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<ProductFormValues>({
    resolver: zodResolver(productSchema),
    defaultValues: { name: product?.name ?? '', description: product?.description ?? '',
      price: product ? String(product.price) : '', stock: product ? String(product.stock) : '0',
      category: product?.category ?? 'Electronics', status: product?.status ?? 'ACTIVE' },
  });

  async function submit(values: ProductFormValues) {
    setFailure(null);
    onBusyChange(true);
    try {
      const payload = { ...values, price: Number(values.price), stock: Number(values.stock) };
      if (product) await productApi.update(product.id, payload);
      else await productApi.create(payload);
      onSaved();
    } catch (cause) {
      const failure = getApiError(cause);
      setFailure(failure);
      for (const field of Object.keys(productSchema.shape) as (keyof ProductFormValues)[]) {
        if (failure.fieldErrors[field]) setError(field, { type: 'server', message: failure.fieldErrors[field] });
      }
    } finally { onBusyChange(false); }
  }

  return <form className="ui-form" noValidate onSubmit={handleSubmit(submit)} aria-busy={isSubmitting}>
    {error && <ApiErrorNotice error={error} />}
    <fieldset className="ui-form-section" disabled={isSubmitting}>
      <div className="ui-form-grid">
        <Input label="Tên sản phẩm" placeholder="Nhập tên sản phẩm" required maxLength={100}
          fieldClassName="ui-span-full" {...register('name')} error={errors.name?.message} />
        <Input label="Giá ($)" type="number" min="0" step="0.01" placeholder="0.00" required
          {...register('price')} error={errors.price?.message} />
        <Input label="Số lượng trong kho" type="number" min="0" step="1" required
          {...register('stock')} error={errors.stock?.message} />
        <Select label="Danh mục" required {...register('category')} error={errors.category?.message}>
          {['Electronics', 'Accessories', 'Software', 'Gadgets'].map(category =>
            <option key={category} value={category}>{category}</option>)}
          {product?.category && !['Electronics', 'Accessories', 'Software', 'Gadgets'].includes(product.category)
            && <option value={product.category}>{product.category}</option>}
        </Select>
        <Select label="Trạng thái" {...register('status')} error={errors.status?.message}>
          <option value="ACTIVE">Đang hoạt động</option><option value="INACTIVE">Ngừng hoạt động</option>
        </Select>
        <Textarea label="Mô tả sản phẩm" maxLength={500} hint="Tối đa 500 ký tự."
          fieldClassName="ui-span-full" {...register('description')} error={errors.description?.message} />
      </div>
    </fieldset>
    <div className="ui-form-actions">
      <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>Hủy bỏ</Button>
      <Button type="submit" loading={isSubmitting} loadingText="Đang lưu…">{product ? 'Lưu thay đổi' : 'Tạo sản phẩm'}</Button>
    </div>
  </form>;
}
