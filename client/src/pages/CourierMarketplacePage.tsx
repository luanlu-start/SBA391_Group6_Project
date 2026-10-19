import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import MarketplaceOrderCard from '../components/ui/MarketplaceOrderCard';
import { marketPlaceOrderApi } from '../api/marketPlaceOrderApi';
import type { MarketPlaceOrder } from '../types/order';
import { getApiError, ApiError } from '../api/ApiError';
import {
    Search,
    Package,
    ChevronLeft,
    ChevronRight,
    ShieldAlert,
    CheckCircle2,
    AlertTriangle,
} from 'lucide-react';
import Select from '../components/ui/Select';
import Modal from '../components/ui/Modal';
import Button from '../components/ui/Button';
import ApiErrorNotice from '../components/ApiErrorNotice';
import { useAuthentication } from '../auth/useAuthentication';
import '../styles/courier-market.css';

export default function CourierMarketplacePage(): React.ReactElement {
    const navigate = useNavigate();
    const { user } = useAuthentication();
    const isCourier = Boolean(user?.roles?.includes('COURIER'));

    const [orders, setOrders] = useState<MarketPlaceOrder[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [keyword, setKeyword] = useState<string>('');
    const [sort, setSort] = useState<string>('createdAt,desc');

    // Quản lý phân trang (Spring Boot 0-indexed)
    const [page, setPage] = useState<number>(0);
    const [totalPages, setTotalPages] = useState<number>(1);
    const [totalElements, setTotalElements] = useState<number>(0);

    // Xử lý nhận đơn (UC-17)
    const [selectedOrderToAccept, setSelectedOrderToAccept] = useState<MarketPlaceOrder | null>(null);
    const [accepting, setAccepting] = useState<boolean>(false);
    const [acceptSuccessMessage, setAcceptSuccessMessage] = useState<string | null>(null);
    const [actionError, setActionError] = useState<ApiError | null>(null);

    const fetchMarketplaceOrders = useCallback(async (): Promise<void> => {
        setLoading(true);
        setActionError(null);
        try {
            const res = await marketPlaceOrderApi.getAll({
                page,
                size: 6,
                sort,
                keyword: keyword.trim() || undefined,
            });
            const content = res.data.content ?? [];
            const raw = res.data as any;
            const totalP = res.data.totalPages ?? raw?.page?.totalPages ?? 1;
            const totalE = res.data.totalElements ?? raw?.page?.totalElements ?? 0;
            setOrders(content);
            setTotalPages(totalP);
            setTotalElements(totalE);
        } catch (error) {
            console.error('Lỗi tải danh sách sàn đơn:', error);
            setActionError(getApiError(error));
        } finally {
            setLoading(false);
        }
    }, [page, sort, keyword]);

    useEffect(() => {
        fetchMarketplaceOrders();
    }, [fetchMarketplaceOrders]);

    const handleSearchSubmit = (e: React.FormEvent<HTMLFormElement>): void => {
        e.preventDefault();
        setPage(0);
        fetchMarketplaceOrders();
    };

    const handleSortChange = (e: React.ChangeEvent<HTMLSelectElement>): void => {
        setSort(e.target.value);
        setPage(0);
    };

    const handleViewDetail = (orderId: number, orderCode?: string) => {
        navigate(`/courier/market/${orderCode || orderId}`);
    };

    const handleOpenAcceptModal = (orderId: number) => {
        if (!user) {
            setActionError(
                new ApiError('Vui lòng đăng nhập với tài khoản Bưu tá (courier@fptpost.vn / 12345678) để thực hiện nhận đơn.')
            );
            return;
        }
        if (!isCourier) {
            setActionError(
                new ApiError('Tài khoản hiện tại của bạn không có vai trò Bưu tá. Theo quy tắc BR-R-009, chỉ tài khoản COURIER mới được nhận đơn.')
            );
            return;
        }
        const target = orders.find(o => o.id === orderId);
        if (target) {
            setSelectedOrderToAccept(target);
            setActionError(null);
        }
    };

    const handleConfirmAccept = async () => {
        if (!selectedOrderToAccept) return;
        setAccepting(true);
        setActionError(null);
        setAcceptSuccessMessage(null);
        try {
            const res = await marketPlaceOrderApi.acceptOrder(selectedOrderToAccept.id);
            const msg = res.message || `Nhận đơn hàng ${selectedOrderToAccept.orderCode} thành công! Đơn chuyển sang Chờ ký quỹ.`;
            setAcceptSuccessMessage(msg);
            setSelectedOrderToAccept(null);
            // Refresh danh sách sàn đơn
            await fetchMarketplaceOrders();
        } catch (error) {
            setActionError(getApiError(error));
        } finally {
            setAccepting(false);
        }
    };

    return (
        <div className="courier-market-container">
            {/* Header section */}
            <header className="market-header">
                <p className="home-eyebrow"><span />Sàn Giao Hàng Bưu Chính</p>
                <h1 className="market-title">Duyệt & Tìm Kiếm <span>Sàn Đơn Khả Dụng</span></h1>
                <p className="market-description">
                    Khám phá các kiện hàng đang mở trên toàn hệ thống. Nhận đơn ngay theo giá niêm yết (UC-17) để tối ưu lộ trình và thu nhập bưu tá của bạn.
                </p>
            </header>

            {/* Banner trạng thái đăng nhập / vai trò Bưu tá */}
            {!user && (
                <div
                    style={{
                        padding: '12px 16px',
                        marginBottom: '16px',
                        backgroundColor: 'var(--blue-50, #eff6ff)',
                        border: '1px solid var(--blue-200, #bfdbfe)',
                        borderRadius: 'var(--radius-md, 8px)',
                        color: 'var(--blue-900, #1e3a8a)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: '12px',
                        flexWrap: 'wrap',
                    }}
                >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <AlertTriangle size={18} color="var(--primary, #0284c7)" />
                        <span>
                            Bạn chưa đăng nhập. Để nhận đơn (UC-17), vui lòng <strong>Đăng nhập tài khoản Bưu tá</strong> (Tài khoản mẫu: <code>courier@fptpost.vn</code> / Mật khẩu: <code>12345678</code>).
                        </span>
                    </div>
                    <Button variant="secondary" onClick={() => navigate('/login')} style={{ padding: '6px 14px', fontSize: '0.85rem' }}>
                        Đăng nhập ngay
                    </Button>
                </div>
            )}

            {user && !isCourier && (
                <div
                    style={{
                        padding: '12px 16px',
                        marginBottom: '16px',
                        backgroundColor: 'var(--amber-50, #fffbeb)',
                        border: '1px solid var(--amber-300, #fcd34d)',
                        borderRadius: 'var(--radius-md, 8px)',
                        color: 'var(--amber-900, #78350f)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                    }}
                >
                    <AlertTriangle size={18} color="#d97706" />
                    <span>
                        Bạn đang đăng nhập với tài khoản <strong>{user.email}</strong> ({user.roles?.join(', ')}).
                        Theo quy tắc <strong>BR-R-009</strong>, chỉ tài khoản có vai trò <strong>Bưu tá (COURIER)</strong> mới được nhận đơn. Vui lòng đăng nhập với <code>courier@fptpost.vn</code> (12345678).
                    </span>
                </div>
            )}

            {user && isCourier && (
                <div
                    style={{
                        padding: '10px 16px',
                        marginBottom: '16px',
                        backgroundColor: 'var(--emerald-50, #ecfdf5)',
                        border: '1px solid var(--emerald-200, #a7f3d0)',
                        borderRadius: 'var(--radius-md, 8px)',
                        color: 'var(--emerald-900, #064e3b)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        fontSize: '0.9rem',
                    }}
                >
                    <CheckCircle2 size={16} color="#059669" />
                    <span>
                        Tài khoản: <strong>{user.fullName || user.email}</strong> (Bưu tá - Hạng Bạc). Đủ điều kiện nhận đơn trên sàn!
                    </span>
                </div>
            )}

            {/* Thông báo thành công */}
            {acceptSuccessMessage && (
                <div
                    style={{
                        padding: '16px',
                        marginBottom: '16px',
                        backgroundColor: 'var(--emerald-50, #ecfdf5)',
                        border: '1px solid var(--emerald-200, #a7f3d0)',
                        borderRadius: 'var(--radius-md, 8px)',
                        color: 'var(--emerald-900, #064e3b)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: '12px',
                    }}
                >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <CheckCircle2 size={20} color="var(--emerald-600, #059669)" />
                        <span><strong>{acceptSuccessMessage}</strong></span>
                    </div>
                    <button
                        type="button"
                        onClick={() => setAcceptSuccessMessage(null)}
                        style={{ background: 'transparent', border: 'none', cursor: 'pointer', fontWeight: 'bold' }}
                    >
                        ✕
                    </button>
                </div>
            )}

            {/* Thông báo lỗi nếu có */}
            {actionError && (
                <div style={{ marginBottom: '16px' }}>
                    <ApiErrorNotice message={actionError.message} correlationId={actionError.correlationId} />
                </div>
            )}

            {/* Filter & Search Bar */}
            <section className="market-filter-bar">
                <form onSubmit={handleSearchSubmit} className="search-box">
                    <Search size={18} className="search-icon" aria-hidden="true" />
                    <input
                        type="text"
                        placeholder="Tìm theo khu vực quận/huyện, tên kiện hàng..."
                        value={keyword}
                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setKeyword(e.target.value)}
                    />
                    <button type="submit" className="ui-button ui-button-primary search-btn">Tìm kiếm</button>
                </form>

                <div className="filter-actions">
                    <div className="filter-select-wrapper">
                        <Select label="Sắp xếp theo" value={sort} onChange={handleSortChange}>
                            <option value="createdAt,desc">Mới nhất trước</option>
                            <option value="weightKg,asc">Khối lượng: Nhẹ đến nặng</option>
                            <option value="weightKg,desc">Khối lượng: Nặng đến nhẹ</option>
                        </Select>
                    </div>
                </div>
            </section>

            {/* Privacy Notice (BR-R-027) */}
            <div className="privacy-pill-notice">
                <ShieldAlert size={16} aria-hidden="true" />
                <span>Thông tin chi tiết (số nhà, số điện thoại người nhận) được ẩn cho đến khi tài xế nhận đơn (Quy tắc BR-R-023, BR-R-027).</span>
            </div>

            {/* Order Cards Grid */}
            <section className="market-order-grid" aria-busy={loading}>
                {loading ? (
                    <div className="market-loading">Đang tải dữ liệu sàn đơn...</div>
                ) : orders.length === 0 ? (
                    <div className="market-empty">
                        <Package size={48} strokeWidth={1.5} />
                        <p>Không có đơn hàng nào phù hợp với bộ lọc hiện tại.</p>
                    </div>
                ) : (
                    orders.map((order: MarketPlaceOrder) => (
                        <MarketplaceOrderCard
                            key={order.id}
                            order={order}
                            onViewDetail={(id, code) => handleViewDetail(id, code)}
                            onAcceptOrder={() => handleOpenAcceptModal(order.id)}
                        />
                    ))
                )}
            </section>

            {/* Pagination Footer */}
            <footer className="market-pagination">
                <span className="pagination-info">
                    Hiển thị <strong>{orders.length}</strong> trên tổng số <strong>{totalElements}</strong> đơn
                </span>
                <div className="pagination-controls">
                    <button
                        type="button"
                        className="pagination-btn"
                        disabled={page === 0 || loading}
                        onClick={() => setPage((prev: number) => Math.max(0, prev - 1))}
                    >
                        <ChevronLeft size={18} />
                    </button>
                    <span className="pagination-current">Trang {page + 1} / {totalPages || 1}</span>
                    <button
                        type="button"
                        className="pagination-btn"
                        disabled={page >= totalPages - 1 || loading}
                        onClick={() => setPage((prev: number) => prev + 1)}
                    >
                        <ChevronRight size={18} />
                    </button>
                </div>
            </footer>

            {/* Modal xác nhận nhận đơn (UC-17) */}
            {selectedOrderToAccept && (
                <Modal
                    isOpen={!!selectedOrderToAccept}
                    title="Xác nhận nhận đơn hàng (UC-17)"
                    onClose={() => {
                        if (!accepting) setSelectedOrderToAccept(null);
                    }}
                >
                    <div className="ui-stack" style={{ gap: '16px' }}>
                        <p>
                            Bạn có chắc chắn muốn nhận đơn hàng <strong>{selectedOrderToAccept.orderCode}</strong> với thông tin sau?
                        </p>
                        <div style={{ backgroundColor: 'var(--bg-tertiary, #f3f4f6)', padding: '12px', borderRadius: '6px' }}>
                            <p style={{ margin: '4px 0' }}>📦 <strong>Hàng hóa:</strong> {selectedOrderToAccept.productName} ({selectedOrderToAccept.quantity} kiện, {selectedOrderToAccept.weightKg} kg)</p>
                            <p style={{ margin: '4px 0' }}>📍 <strong>Tuyến đường:</strong> {selectedOrderToAccept.pickupAddress} ➔ {selectedOrderToAccept.deliveryAddress}</p>
                            <p style={{ margin: '4px 0' }}>💰 <strong>Giá trị khai báo:</strong> {Number(selectedOrderToAccept.declaredValue).toLocaleString('vi-VN')} đ</p>
                            {selectedOrderToAccept.declaredValue > 1000000 && (
                                <p style={{ margin: '4px 0', color: 'var(--amber-700, #b45309)', fontSize: '0.9rem' }}>
                                    ⚠️ <em>Đơn hàng giá trị cao: Yêu cầu bưu tá từ hạng Bạc trở lên (BR-R-024).</em>
                                </p>
                            )}
                        </div>

                        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                            <AlertTriangle size={15} />
                            <span>Sau khi nhận, đơn sẽ chuyển sang <strong>Chờ ký quỹ</strong>. Người gửi có 15 phút để hoàn tất ký quỹ.</span>
                        </div>

                        {actionError && (
                            <ApiErrorNotice message={actionError.message} correlationId={actionError.correlationId} />
                        )}

                        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '8px' }}>
                            <Button
                                variant="secondary"
                                onClick={() => setSelectedOrderToAccept(null)}
                                disabled={accepting}
                            >
                                Hủy bỏ
                            </Button>
                            <Button
                                variant="primary"
                                onClick={handleConfirmAccept}
                                disabled={accepting}
                            >
                                {accepting ? 'Đang xử lý...' : 'Xác nhận nhận đơn'}
                            </Button>
                        </div>
                    </div>
                </Modal>
            )}
        </div>
    );
}