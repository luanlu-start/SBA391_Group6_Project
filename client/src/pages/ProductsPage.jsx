import React, { useState, useEffect } from 'react';
import { 
  Plus, 
  Search, 
  Trash2, 
  Edit3, 
  Package, 
  DollarSign, 
  Layers, 
  RefreshCw, 
  AlertCircle, 
  CheckCircle2, 
  Filter
} from 'lucide-react';
import { productApi } from '../api/productApi';
import Modal from '../components/ui/Modal';
import LoadingSpinner from '../components/common/LoadingSpinner';

const CATEGORIES = ['All', 'Electronics', 'Accessories', 'Software', 'Gadgets'];

const INITIAL_FORM = {
  name: '',
  description: '',
  price: '',
  category: 'Electronics',
  stock: 10,
  status: 'ACTIVE',
};

export default function ProductsPage() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [error, setError] = useState(null);
  const [toast, setToast] = useState(null);

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [currentId, setCurrentId] = useState(null);
  const [formData, setFormData] = useState(INITIAL_FORM);
  const [formErrors, setFormErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  // Fetch Products
  const loadProducts = async () => {
    setLoading(true);
    setError(null);
    try {
      const params = {};
      if (search.trim()) params.search = search.trim();
      if (selectedCategory !== 'All') params.category = selectedCategory;

      const res = await productApi.getAll(params);
      if (res && res.success) {
        setProducts(res.data || []);
      }
    } catch (err) {
      setError(err.message || 'Không thể tải danh sách sản phẩm');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProducts();
  }, [selectedCategory]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    loadProducts();
  };

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  // Open Modal for Create
  const handleOpenCreate = () => {
    setIsEditing(false);
    setCurrentId(null);
    setFormData(INITIAL_FORM);
    setFormErrors({});
    setIsModalOpen(true);
  };

  // Open Modal for Edit
  const handleOpenEdit = (prod) => {
    setIsEditing(true);
    setCurrentId(prod.id);
    setFormData({
      name: prod.name || '',
      description: prod.description || '',
      price: prod.price || '',
      category: prod.category || 'Electronics',
      stock: prod.stock || 0,
      status: prod.status || 'ACTIVE',
    });
    setFormErrors({});
    setIsModalOpen(true);
  };

  // Delete
  const handleDelete = async (id, name) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa sản phẩm "${name}" không?`)) {
      return;
    }
    try {
      await productApi.delete(id);
      showToast(`Đã xóa sản phẩm "${name}" thành công!`);
      loadProducts();
    } catch (err) {
      alert(`Xóa thất bại: ${err.message}`);
    }
  };

  // Submit Create or Edit
  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setFormErrors({});

    try {
      const payload = {
        name: formData.name,
        description: formData.description,
        price: parseFloat(formData.price),
        category: formData.category,
        stock: parseInt(formData.stock, 10),
        status: formData.status,
      };

      if (isEditing) {
        await productApi.update(currentId, payload);
        showToast('Cập nhật sản phẩm thành công!');
      } else {
        await productApi.create(payload);
        showToast('Thêm mới sản phẩm thành công!');
      }

      setIsModalOpen(false);
      loadProducts();
    } catch (err) {
      if (err.errors) {
        setFormErrors(err.errors);
      } else {
        alert(err.message || 'Thao tác không thành công');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="page-container">
      {/* Page Header */}
      <div className="page-header-row">
        <div>
          <div className="breadcrumb-tag">
            <Layers size={14} /> Full-Stack REST API Demo
          </div>
          <h1 className="page-title">Quản lý Sản phẩm (Products)</h1>
          <p className="page-subtitle">
            Dữ liệu kết nối trực tiếp với Spring Boot 3 API (<code>/api/v1/products</code>) và lưu trữ qua Spring Data MongoDB.
          </p>
        </div>

        <button onClick={handleOpenCreate} className="btn btn-primary">
          <Plus size={18} />
          <span>Thêm sản phẩm</span>
        </button>
      </div>

      {/* Toast Notification */}
      {toast && (
        <div className={`toast-notification toast-${toast.type}`}>
          <CheckCircle2 size={18} />
          <span>{toast.message}</span>
        </div>
      )}

      {/* Filter & Search Bar */}
      <div className="filter-card">
        <form onSubmit={handleSearchSubmit} className="search-box">
          <Search size={18} className="search-icon" />
          <input
            type="text"
            className="search-input"
            placeholder="Tìm theo tên sản phẩm... (Enter để tìm)"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <button type="submit" className="btn btn-sm btn-secondary">
            Tìm kiếm
          </button>
        </form>

        <div className="category-chips">
          <span className="filter-label">
            <Filter size={14} /> Danh mục:
          </span>
          {CATEGORIES.map((cat) => (
            <button
              key={cat}
              className={`chip-btn ${selectedCategory === cat ? 'active' : ''}`}
              onClick={() => setSelectedCategory(cat)}
            >
              {cat}
            </button>
          ))}
          <button
            onClick={loadProducts}
            className="btn btn-icon btn-ghost"
            title="Tải lại danh sách"
          >
            <RefreshCw size={16} className={loading ? 'spin' : ''} />
          </button>
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div className="alert alert-danger mt-4">
          <AlertCircle size={20} />
          <div>
            <strong>Lỗi tải dữ liệu:</strong> {error}
            <button onClick={loadProducts} className="btn btn-sm btn-outline mt-2">
              Thử lại
            </button>
          </div>
        </div>
      )}

      {/* Content list */}
      {loading ? (
        <LoadingSpinner text="Đang đồng bộ dữ liệu từ Spring Boot backend..." />
      ) : products.length === 0 ? (
        <div className="empty-state-card">
          <Package size={48} className="text-muted" />
          <h3>Chưa có sản phẩm nào</h3>
          <p>Không tìm thấy sản phẩm phù hợp với bộ lọc hiện tại hoặc cơ sở dữ liệu đang trống.</p>
          <button onClick={handleOpenCreate} className="btn btn-primary mt-3">
            <Plus size={16} /> Thêm sản phẩm đầu tiên
          </button>
        </div>
      ) : (
        <div className="products-grid">
          {products.map((p) => (
            <div key={p.id} className="product-card">
              <div className="product-card-header">
                <span className="product-category-tag">{p.category}</span>
                <span className={`status-pill ${p.status === 'ACTIVE' ? 'active' : 'inactive'}`}>
                  {p.status}
                </span>
              </div>

              <h3 className="product-name">{p.name}</h3>
              <p className="product-description">{p.description || 'Không có mô tả chi tiết'}</p>

              <div className="product-meta-row">
                <div className="product-price">
                  <DollarSign size={16} className="text-primary" />
                  <span>${p.price?.toFixed(2)}</span>
                </div>
                <div className="product-stock">
                  Kho: <strong>{p.stock}</strong>
                </div>
              </div>

              <div className="product-actions-footer">
                <button
                  onClick={() => handleOpenEdit(p)}
                  className="btn btn-sm btn-outline"
                  title="Chỉnh sửa"
                >
                  <Edit3 size={14} /> Sửa
                </button>
                <button
                  onClick={() => handleDelete(p.id, p.name)}
                  className="btn btn-sm btn-danger-outline"
                  title="Xóa"
                >
                  <Trash2 size={14} /> Xóa
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Modal for Add / Edit */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={isEditing ? 'Chỉnh sửa sản phẩm' : 'Thêm sản phẩm mới'}
      >
        <form onSubmit={handleSubmit} className="product-form">
          <div className="form-group">
            <label className="form-label">Tên sản phẩm *</label>
            <input
              type="text"
              required
              className={`form-input ${formErrors.name ? 'input-error' : ''}`}
              placeholder="VD: MacBook Pro 16 inch..."
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            />
            {formErrors.name && <span className="error-text">{formErrors.name}</span>}
          </div>

          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Giá ($) *</label>
              <input
                type="number"
                step="0.01"
                min="0"
                required
                className={`form-input ${formErrors.price ? 'input-error' : ''}`}
                placeholder="VD: 999.99"
                value={formData.price}
                onChange={(e) => setFormData({ ...formData, price: e.target.value })}
              />
              {formErrors.price && <span className="error-text">{formErrors.price}</span>}
            </div>

            <div className="form-group">
              <label className="form-label">Số lượng trong kho *</label>
              <input
                type="number"
                min="0"
                required
                className={`form-input ${formErrors.stock ? 'input-error' : ''}`}
                placeholder="VD: 25"
                value={formData.stock}
                onChange={(e) => setFormData({ ...formData, stock: e.target.value })}
              />
              {formErrors.stock && <span className="error-text">{formErrors.stock}</span>}
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Danh mục *</label>
              <select
                className="form-input"
                value={formData.category}
                onChange={(e) => setFormData({ ...formData, category: e.target.value })}
              >
                <option value="Electronics">Electronics</option>
                <option value="Accessories">Accessories</option>
                <option value="Software">Software</option>
                <option value="Gadgets">Gadgets</option>
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Trạng thái</label>
              <select
                className="form-input"
                value={formData.status}
                onChange={(e) => setFormData({ ...formData, status: e.target.value })}
              >
                <option value="ACTIVE">ACTIVE</option>
                <option value="INACTIVE">INACTIVE</option>
              </select>
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Mô tả sản phẩm</label>
            <textarea
              rows="3"
              className="form-input form-textarea"
              placeholder="Nhập mô tả ngắn gọn về sản phẩm..."
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            ></textarea>
          </div>

          <div className="modal-actions">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setIsModalOpen(false)}
            >
              Hủy bỏ
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={submitting}
            >
              {submitting ? 'Đang lưu...' : isEditing ? 'Lưu thay đổi' : 'Tạo mới'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
