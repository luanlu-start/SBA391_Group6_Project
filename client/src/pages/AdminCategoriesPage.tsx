import { useEffect, useState, useMemo } from 'react';
import {
  Boxes,
  Plus,
  RefreshCw,
  Search,
  Pencil,
  Trash2,
  Eye,
  EyeOff,
  AlertTriangle,
  ShieldAlert,
  CheckCircle2,
} from 'lucide-react';
import { categoryApi } from '../api/categoryApi';
import type { ItemCategory, CategoryRequest } from '../types/category';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Input from '../components/ui/Input';
import Select from '../components/ui/Select';
import Button from '../components/ui/Button';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import EmptyState from '../components/ui/EmptyState';
import Alert from '../components/ui/Alert';
import Badge from '../components/ui/Badge';
import Modal from '../components/ui/Modal';

const initialFormData: CategoryRequest = {
  code: '',
  name: '',
  description: '',
  isProhibited: false,
  requiresReview: false,
  active: true,
};

export default function AdminCategoriesPage() {
  const [categories, setCategories] = useState<ItemCategory[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Filters
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'HIDDEN'>('ALL');

  // Modal State (Create / Edit)
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<ItemCategory | null>(null);
  const [formData, setFormData] = useState<CategoryRequest>(initialFormData);
  const [formError, setFormError] = useState<string | null>(null);

  // Delete Confirmation Modal
  const [categoryToDelete, setCategoryToDelete] = useState<ItemCategory | null>(null);

  // Load Categories
  const fetchCategories = async () => {
    try {
      setLoading(true);
      const res = await categoryApi.adminListAll();
      if (res.data) {
        setCategories(res.data);
      }
    } catch (err: unknown) {
      console.error('Lỗi khi tải danh mục loại hàng:', err);
      setFeedback({
        type: 'error',
        message: 'Không thể tải danh sách loại hàng hóa. Vui lòng thử lại sau.',
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCategories();
  }, []);

  // Filtered List
  const filteredCategories = useMemo(() => {
    return categories.filter((cat) => {
      const matchSearch =
        cat.code.toLowerCase().includes(search.toLowerCase()) ||
        cat.name.toLowerCase().includes(search.toLowerCase()) ||
        (cat.description && cat.description.toLowerCase().includes(search.toLowerCase()));

      const matchStatus =
        statusFilter === 'ALL' ||
        (statusFilter === 'ACTIVE' && cat.active) ||
        (statusFilter === 'HIDDEN' && !cat.active);

      return matchSearch && matchStatus;
    });
  }, [categories, search, statusFilter]);

  // Open Create Modal
  const handleOpenCreate = () => {
    setEditingCategory(null);
    setFormData(initialFormData);
    setFormError(null);
    setIsModalOpen(true);
  };

  // Open Edit Modal
  const handleOpenEdit = (category: ItemCategory) => {
    setEditingCategory(category);
    setFormData({
      code: category.code,
      name: category.name,
      description: category.description || '',
      isProhibited: category.isProhibited ?? false,
      requiresReview: category.requiresReview ?? false,
      active: category.active ?? true,
    });
    setFormError(null);
    setIsModalOpen(true);
  };

  // Save (Create or Update)
  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    if (!formData.code.trim()) {
      setFormError('Vui lòng nhập mã loại hàng hóa.');
      return;
    }
    if (!formData.name.trim()) {
      setFormError('Vui lòng nhập tên loại hàng hóa.');
      return;
    }

    try {
      setSaving(true);
      if (editingCategory) {
        await categoryApi.adminUpdate(editingCategory.id, formData);
        setFeedback({
          type: 'success',
          message: `Đã cập nhật loại hàng "${formData.name}" (${formData.code}) thành công.`,
        });
      } else {
        await categoryApi.adminCreate(formData);
        setFeedback({
          type: 'success',
          message: `Đã tạo mới loại hàng "${formData.name}" (${formData.code}) thành công.`,
        });
      }
      setIsModalOpen(false);
      await fetchCategories();
    } catch (err: any) {
      console.error('Lỗi lưu danh mục:', err);
      const msg = err.response?.data?.message || err.message || 'Không thể lưu loại hàng hóa.';
      setFormError(msg);
    } finally {
      setSaving(false);
    }
  };

  // Toggle Active
  const handleToggleActive = async (category: ItemCategory) => {
    try {
      await categoryApi.adminToggleActive(category.id);
      setFeedback({
        type: 'success',
        message: `Đã ${category.active ? 'ẩn' : 'kích hoạt'} loại hàng "${category.name}".`,
      });
      await fetchCategories();
    } catch (err: unknown) {
      console.error('Lỗi bật tắt trạng thái:', err);
      setFeedback({
        type: 'error',
        message: 'Không thể thay đổi trạng thái loại hàng.',
      });
    }
  };

  // Confirm Delete
  const handleConfirmDelete = async () => {
    if (!categoryToDelete) return;
    try {
      setDeleting(true);
      const res = await categoryApi.adminDelete(categoryToDelete.id);
      const msg = res.data?.message || 'Thao tác xóa/ẩn hoàn tất.';
      setFeedback({
        type: 'success',
        message: msg,
      });
      setCategoryToDelete(null);
      await fetchCategories();
    } catch (err: any) {
      console.error('Lỗi khi xóa loại hàng:', err);
      const msg = err.response?.data?.message || err.message || 'Không thể xóa loại hàng.';
      setFeedback({
        type: 'error',
        message: msg,
      });
    } finally {
      setDeleting(false);
    }
  };

  // Columns definition for DataTable
  const columns: TableColumn<ItemCategory>[] = [
    {
      key: 'code',
      header: 'Mã loại hàng',
      render: (cat) => (
        <span className="font-mono font-bold text-sm tracking-wide text-brand-primary" style={{ color: 'var(--brand-primary, #f97316)' }}>
          {cat.code}
        </span>
      ),
    },
    {
      key: 'name',
      header: 'Tên & Mô tả',
      render: (cat) => (
        <div className="ui-stack" style={{ gap: '0.25rem' }}>
          <strong className="ui-table-primary" style={{ fontSize: '0.95rem' }}>{cat.name}</strong>
          <span className="ui-table-secondary" style={{ fontSize: '0.825rem', color: 'var(--text-secondary, #94a3b8)' }}>
            {cat.description || 'Chưa có mô tả chi tiết'}
          </span>
        </div>
      ),
    },
    {
      key: 'properties',
      header: 'Đặc tính',
      render: (cat) => (
        <div style={{ display: 'flex', gap: '0.375rem', flexWrap: 'wrap' }}>
          {cat.isProhibited && (
            <Badge tone="danger">
              <ShieldAlert size={12} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 4 }} />
              Hàng cấm
            </Badge>
          )}
          {cat.requiresReview && (
            <Badge tone="warning">
              <AlertTriangle size={12} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 4 }} />
              Cần duyệt
            </Badge>
          )}
          {!cat.isProhibited && !cat.requiresReview && (
            <Badge tone="neutral">
              <CheckCircle2 size={12} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 4 }} />
              Thông thường
            </Badge>
          )}
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Trạng thái',
      render: (cat) => (
        <Badge dot tone={cat.active ? 'success' : 'neutral'}>
          {cat.active ? 'Đang hoạt động' : 'Đã ẩn'}
        </Badge>
      ),
    },
    {
      key: 'actions',
      header: 'Thao tác',
      align: 'right',
      render: (cat) => (
        <div className="ui-table-actions" style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
          <Button
            variant="ghost"
            size="sm"
            icon={<Pencil size={15} />}
            onClick={() => handleOpenEdit(cat)}
            aria-label={`Sửa ${cat.name}`}
          >
            Sửa
          </Button>
          <Button
            variant="secondary"
            size="sm"
            icon={cat.active ? <EyeOff size={15} /> : <Eye size={15} />}
            onClick={() => handleToggleActive(cat)}
            aria-label={`${cat.active ? 'Ẩn' : 'Hiện'} ${cat.name}`}
          >
            {cat.active ? 'Ẩn' : 'Hiện'}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            icon={<Trash2 size={15} />}
            onClick={() => setCategoryToDelete(cat)}
            aria-label={`Xóa ${cat.name}`}
            style={{ color: 'var(--danger, #ef4444)' }}
          >
            Xóa
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="ui-stack" style={{ gap: '1.5rem', paddingBottom: '3rem' }}>
      <PageHeader
        title="Quản lý loại hàng hóa"
        eyebrow="Quản trị hệ thống (UC-58)"
        description="Quản lý danh mục loại hàng, cấu hình đặc tính cảnh báo hàng cấm, yêu cầu kiểm duyệt và ẩn/hiện danh mục cho người gửi."
        actions={
          <div className="ui-row" style={{ display: 'flex', gap: '0.75rem' }}>
            <Button
              variant="secondary"
              disabled={loading}
              icon={<RefreshCw size={17} />}
              onClick={fetchCategories}
            >
              Tải lại
            </Button>
            <Button icon={<Plus size={17} />} onClick={handleOpenCreate}>
              Thêm loại hàng
            </Button>
          </div>
        }
      />

      {feedback && (
        <Alert
          tone={feedback.type === 'success' ? 'success' : 'danger'}
          action={
            <Button variant="ghost" size="sm" onClick={() => setFeedback(null)}>
              Đóng
            </Button>
          }
        >
          {feedback.message}
        </Alert>
      )}

      {/* Filter Card */}
      <SectionCard title="Bộ lọc & Tra cứu">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem', alignItems: 'end' }}>
          <Input
            label="Tìm kiếm theo mã, tên loại hàng"
            placeholder="Nhập tên hoặc mã (vd: DOCUMENTS, Điện tử...)"
            icon={<Search size={16} />}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />

          <Select
            label="Trạng thái hoạt động"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as 'ALL' | 'ACTIVE' | 'HIDDEN')}
          >
            <option value="ALL">Tất cả trạng thái</option>
            <option value="ACTIVE">Đang hoạt động</option>
            <option value="HIDDEN">Đã ẩn</option>
          </Select>

          <div>
            <Button
              variant="secondary"
              onClick={() => {
                setSearch('');
                setStatusFilter('ALL');
              }}
            >
              Đặt lại bộ lọc
            </Button>
          </div>
        </div>
      </SectionCard>

      {/* Table Card */}
      <SectionCard
        title="Danh mục loại hàng hóa"
        description={`${filteredCategories.length} / ${categories.length} loại hàng`}
        className="ui-card-table"
      >
        <DataTable
          caption="Bảng danh sách loại hàng hóa"
          columns={columns}
          rows={filteredCategories}
          rowKey={(cat) => cat.id}
          loading={loading}
          emptyState={
            <EmptyState
              title={search || statusFilter !== 'ALL' ? 'Không tìm thấy loại hàng phù hợp' : 'Chưa có loại hàng hóa'}
              description={
                search || statusFilter !== 'ALL'
                  ? 'Thử thay đổi từ khóa tìm kiếm hoặc bỏ lọc trạng thái.'
                  : 'Bấm nút "Thêm loại hàng" để tạo danh mục đầu tiên.'
              }
              icon={<Boxes size={32} />}
              action={
                search || statusFilter !== 'ALL' ? (
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setSearch('');
                      setStatusFilter('ALL');
                    }}
                  >
                    Bỏ bộ lọc
                  </Button>
                ) : (
                  <Button onClick={handleOpenCreate}>Thêm loại hàng</Button>
                )
              }
            />
          }
        />
      </SectionCard>

      {/* Create / Edit Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => !saving && setIsModalOpen(false)}
        title={editingCategory ? 'Chỉnh sửa loại hàng hóa' : 'Thêm loại hàng hóa mới'}
        description="Điền thông tin mã và tên hiển thị cho loại hàng hóa."
      >
        <form onSubmit={handleSave} className="ui-form" style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {formError && <Alert tone="danger">{formError}</Alert>}

          <Input
            label="Mã định danh (Code)"
            placeholder="VD: ELECTRONICS, DOCUMENTS, PERISHABLE..."
            required
            maxLength={50}
            value={formData.code}
            disabled={saving}
            onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
            hint="Mã duy nhất, viết hoa, không dấu, phân cách bằng gạch dưới."
          />

          <Input
            label="Tên hiển thị loại hàng"
            placeholder="VD: Thiết bị điện tử & Công nghệ"
            required
            maxLength={100}
            value={formData.name}
            disabled={saving}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          />

          <div className="ui-field">
            <label className="ui-label" htmlFor="cat-description">
              Mô tả chi tiết
            </label>
            <textarea
              id="cat-description"
              className="ui-control"
              rows={3}
              placeholder="Mô tả cụ thể về loại hàng, ví dụ các mặt hàng mẫu..."
              maxLength={500}
              value={formData.description}
              disabled={saving}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '0.5rem', padding: '0.75rem', borderRadius: '8px', background: 'var(--bg-secondary, rgba(255,255,255,0.03))' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.625rem', cursor: 'pointer', fontSize: '0.9rem' }}>
              <input
                type="checkbox"
                checked={formData.isProhibited}
                disabled={saving}
                onChange={(e) => setFormData({ ...formData, isProhibited: e.target.checked })}
              />
              <span style={{ fontWeight: 500, color: formData.isProhibited ? 'var(--danger, #ef4444)' : 'inherit' }}>
                Hàng cấm vận chuyển (Cảnh báo người gửi khi chọn)
              </span>
            </label>

            <label style={{ display: 'flex', alignItems: 'center', gap: '0.625rem', cursor: 'pointer', fontSize: '0.9rem' }}>
              <input
                type="checkbox"
                checked={formData.requiresReview}
                disabled={saving}
                onChange={(e) => setFormData({ ...formData, requiresReview: e.target.checked })}
              />
              <span style={{ fontWeight: 500, color: formData.requiresReview ? 'var(--warning, #f59e0b)' : 'inherit' }}>
                Yêu cầu kiểm duyệt đặc biệt từ bưu tá / kiểm duyệt viên
              </span>
            </label>

            <label style={{ display: 'flex', alignItems: 'center', gap: '0.625rem', cursor: 'pointer', fontSize: '0.9rem' }}>
              <input
                type="checkbox"
                checked={formData.active}
                disabled={saving}
                onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
              />
              <span>Cho phép hiển thị và hoạt động trên hệ thống</span>
            </label>
          </div>

          <div className="ui-form-actions" style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1rem' }}>
            <Button
              type="button"
              variant="secondary"
              disabled={saving}
              onClick={() => setIsModalOpen(false)}
            >
              Hủy
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Đang lưu…' : editingCategory ? 'Lưu thay đổi' : 'Tạo mới'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Delete Confirmation Modal (Rule A1) */}
      <Modal
        isOpen={Boolean(categoryToDelete)}
        onClose={() => !deleting && setCategoryToDelete(null)}
        title="Xác nhận xóa / ẩn loại hàng"
        description="Hệ thống tự động áp dụng quy tắc an toàn bảo toàn dữ liệu (A1)."
      >
        <div className="ui-stack" style={{ gap: '1rem' }}>
          <p>
            Bạn có chắc chắn muốn xóa loại hàng hóa{' '}
            <strong>{categoryToDelete?.name}</strong> (<code>{categoryToDelete?.code}</code>)?
          </p>

          <Alert tone="warning">
            <strong>Quy tắc xử lý ngoại lệ (A1):</strong> Nếu loại hàng này đã từng phát sinh đơn hàng trong hệ thống, hệ thống sẽ <strong>tự động chuyển sang trạng thái Ẩn</strong> thay vì xóa hẳn khỏi CSDL để không làm hỏng dữ liệu đơn hàng cũ.
          </Alert>

          <div className="ui-form-actions" style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
            <Button
              variant="secondary"
              disabled={deleting}
              onClick={() => setCategoryToDelete(null)}
            >
              Hủy bỏ
            </Button>
            <Button
              variant="primary"
              disabled={deleting}
              onClick={handleConfirmDelete}
              style={{ background: 'var(--danger, #ef4444)', borderColor: 'var(--danger, #ef4444)' }}
            >
              {deleting ? 'Đang xử lý…' : 'Xác nhận xóa / ẩn'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
