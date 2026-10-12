import { useEffect, useState } from 'react';
import { Plus, Search, Trash2, Pencil, RefreshCw } from 'lucide-react';
import type { Product } from '../types/product';
import type { ApiError } from '../api/ApiError';
import { getApiError } from '../api/ApiError';
import { productApi } from '../api/productApi';
import useProducts from '../features/product/hooks/useProducts';
import ProductForm from '../features/product/components/ProductForm';
import ApiErrorNotice from '../components/ApiErrorNotice';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Select from '../components/ui/Select';
import Modal from '../components/ui/Modal';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
import Badge from '../components/ui/Badge';
import Alert from '../components/ui/Alert';

export default function ProductsPage() {
  const list = useProducts();
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState('');
  const [editor, setEditor] = useState<{ product: Product | null } | null>(null);
  const [busy, setBusy] = useState(false);
  const [deleting, setDeleting] = useState<Product | null>(null);
  const [deleteError, setDeleteError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(''), 5000);
    return () => window.clearTimeout(timer);
  }, [notice]);

  async function deleteProduct() {
    if (!deleting) return;
    setBusy(true); setDeleteError(null);
    try {
      await productApi.delete(deleting.id);
      setNotice('Đã xóa sản phẩm.'); setDeleting(null); void list.reload();
    } catch (cause) { setDeleteError(getApiError(cause)); }
    finally { setBusy(false); }
  }

  const columns: TableColumn<Product>[] = [
    { key: 'name', header: 'Sản phẩm', render: product => <><span className="ui-table-primary">{product.name}</span>
      <span className="ui-table-secondary">{product.description || 'Chưa có mô tả'}</span></> },
    { key: 'category', header: 'Danh mục', render: product => product.category },
    { key: 'price', header: 'Giá ($)', align: 'right', render: product => product.price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) },
    { key: 'stock', header: 'Tồn kho', align: 'right', render: product => product.stock },
    { key: 'status', header: 'Trạng thái', render: product => <Badge dot tone={product.status === 'ACTIVE' ? 'success' : 'neutral'}>
      {product.status === 'ACTIVE' ? 'Hoạt động' : 'Ngừng hoạt động'}</Badge> },
    { key: 'actions', header: 'Thao tác', align: 'right', render: product => <div className="ui-table-actions">
      <Button variant="ghost" size="sm" aria-label={`Sửa ${product.name}`} icon={<Pencil size={16} />}
        onClick={() => setEditor({ product })} />
      <Button variant="ghost" size="sm" aria-label={`Xóa ${product.name}`} icon={<Trash2 size={16} />}
        onClick={() => { setDeleteError(null); setDeleting(product); }} />
    </div> },
  ];

  return <div className="ui-stack">
    <PageHeader title="Quản lý sản phẩm" eyebrow="Danh mục"
      description="Tìm kiếm, cập nhật và quản lý thông tin sản phẩm."
      actions={<Button icon={<Plus size={18} />} onClick={() => setEditor({ product: null })}>Thêm sản phẩm</Button>} />
    {notice && <Alert tone="success">{notice}</Alert>}
    <SectionCard>
      <form className="ui-toolbar" onSubmit={event => { event.preventDefault(); list.search(keyword, category); }}>
        <Input label="Tìm kiếm" icon={<Search size={17} />} placeholder="Nhập tên sản phẩm…"
          value={keyword} onChange={event => setKeyword(event.target.value)} />
        <Select label="Danh mục" value={category} onChange={event => setCategory(event.target.value)}>
          <option value="">Tất cả danh mục</option>{['Electronics', 'Accessories', 'Software', 'Gadgets'].map(value =>
            <option key={value} value={value}>{value}</option>)}
        </Select>
        <Button type="submit" variant="secondary">Tìm kiếm</Button>
        <Button variant="ghost" aria-label="Tải lại danh sách" onClick={() => void list.reload()} disabled={list.loading}
          icon={<RefreshCw size={18} />} />
      </form>
    </SectionCard>
    <SectionCard title="Danh sách sản phẩm" description={`${list.pagination.totalElements} sản phẩm phù hợp`} className="ui-card-table">
      {list.error ? <div className="ui-card-body ui-stack">
        <ApiErrorNotice message={list.error.message} correlationId={list.error.correlationId} />
        <div><Button variant="secondary" onClick={() => void list.reload()}>Thử lại</Button></div>
      </div> : <>
        <DataTable caption="Danh sách sản phẩm" columns={columns} rows={list.products} rowKey={product => product.id} loading={list.loading}
          emptyState={<EmptyState title="Chưa có sản phẩm phù hợp" description="Thử từ khóa khác hoặc thêm sản phẩm đầu tiên."
            action={<Button onClick={() => setEditor({ product: null })} icon={<Plus size={17} />}>Thêm sản phẩm</Button>} />} />
        {!list.loading && <Pagination page={list.page} size={list.size} {...list.pagination} onPageChange={list.setPage} />}
      </>}
    </SectionCard>
    <Modal isOpen={!!editor} onClose={() => setEditor(null)} closeDisabled={busy}
      title={editor?.product ? 'Chỉnh sửa sản phẩm' : 'Thêm sản phẩm'} description="Các trường có dấu * là bắt buộc.">
      {editor && <ProductForm product={editor.product} onCancel={() => setEditor(null)} onBusyChange={setBusy}
        onSaved={() => { setNotice(editor.product ? 'Đã cập nhật sản phẩm.' : 'Đã tạo sản phẩm.'); setEditor(null); void list.reload(); }} />}
    </Modal>
    <Modal isOpen={!!deleting} onClose={() => setDeleting(null)} title="Xóa sản phẩm?" closeDisabled={busy}>
      <div className="ui-stack"><p>Sản phẩm <strong>{deleting?.name}</strong> sẽ bị xóa. Thao tác này không thể hoàn tác.</p>
        {deleteError && <ApiErrorNotice message={deleteError.message} correlationId={deleteError.correlationId} />}
        <div className="ui-form-actions"><Button variant="secondary" onClick={() => setDeleting(null)} disabled={busy}>Hủy bỏ</Button>
          <Button variant="danger" onClick={() => void deleteProduct()} loading={busy} loadingText="Đang xóa…">Xóa sản phẩm</Button></div>
      </div>
    </Modal>
  </div>;
}
