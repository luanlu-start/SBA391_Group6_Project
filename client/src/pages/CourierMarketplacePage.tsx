import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import MarketplaceOrderCard from '../components/ui/MarketplaceOrderCard';
import { marketPlaceOrderApi } from '../api/marketPlaceOrderApi';
import { invitationApi } from '../api/invitationApi';
import type { MarketPlaceOrder } from '../types/order';
import type { Invitation } from '../types/invitation';
import { getApiError, ApiError } from '../api/ApiError';
import {
    Search,
    Package,
    ChevronLeft,
    ChevronRight,
    ShieldAlert,
    CheckCircle2,
    AlertTriangle,
    Mail,
    Clock,
    MapPin,
    XCircle,
    User as UserIcon,
} from 'lucide-react';
import Select from '../components/ui/Select';
import Modal from '../components/ui/Modal';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import ApiErrorNotice from '../components/ApiErrorNotice';
import { useAuthentication } from '../auth/useAuthentication';
import '../styles/courier-market.css';

const numberFormat = new Intl.NumberFormat('vi-VN');

export default function CourierMarketplacePage(): React.ReactElement {
    const navigate = useNavigate();
    const { user } = useAuthentication();
    const isCourier = Boolean(user?.roles?.includes('COURIER'));

    // Tab view: 'public' or 'invitations'
    const [marketplaceTab, setMarketplaceTab] = useState<'public' | 'invitations'>('public');

    const [orders, setOrders] = useState<MarketPlaceOrder[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [keyword, setKeyword] = useState<string>('');
    const [sort, setSort] = useState<string>('createdAt,desc');

    // Lời mời trực tiếp
    const [invitations, setInvitations] = useState<Invitation[]>([]);
    const [invitationsLoading, setInvitationsLoading] = useState<boolean>(false);
    const [selectedInvitationForAction, setSelectedInvitationForAction] = useState<Invitation | null>(null);
    const [actionType, setActionType] = useState<'accept' | 'reject' | null>(null);
    const [rejectReason, setRejectReason] = useState<string>('');

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
            const raw = res.data as { page?: number | { totalPages?: number; totalElements?: number } };
            const metadata = typeof raw.page === 'object' ? raw.page : undefined;
            const totalP = res.data.totalPages ?? metadata?.totalPages ?? 1;
            const totalE = res.data.totalElements ?? metadata?.totalElements ?? 0;
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

    const fetchInvitations = useCallback(async (): Promise<void> => {
        if (!isCourier) return;
        setInvitationsLoading(true);
        try {
            const res = await invitationApi.getMyInvitations({ status: 'PENDING' });
            setInvitations(res.data?.content || []);
        } catch (error) {
            console.error('Lỗi tải danh sách lời mời:', error);
        } finally {
            setInvitationsLoading(false);
        }
    }, [isCourier]);

    useEffect(() => {
        fetchMarketplaceOrders();
        fetchInvitations();
    }, [fetchMarketplaceOrders, fetchInvitations]);

    const handleConfirmAcceptInvitation = async () => {
        if (!selectedInvitationForAction) return;
        setAccepting(true);
        setActionError(null);
        try {
            await invitationApi.acceptInvitation(selectedInvitationForAction.id);
            setAcceptSuccessMessage(
                `Chấp nhận lời mời đơn ${selectedInvitationForAction.orderCode} thành công! Đơn hàng đã được chuyển sang Chờ ký quỹ.`
            );
            setSelectedInvitationForAction(null);
            setActionType(null);
            await fetchInvitations();
            await fetchMarketplaceOrders();
        } catch (error) {
            setActionError(getApiError(error));
        } finally {
            setAccepting(false);
        }
    };

    const handleConfirmRejectInvitation = async () => {
        if (!selectedInvitationForAction) return;
        setAccepting(true);
        setActionError(null);
        try {
            await invitationApi.rejectInvitation(selectedInvitationForAction.id, rejectReason.trim() || undefined);
            setAcceptSuccessMessage(`Đã từ chối lời mời đơn hàng ${selectedInvitationForAction.orderCode}.`);
            setSelectedInvitationForAction(null);
            setActionType(null);
            setRejectReason('');
            await fetchInvitations();
        } catch (error) {
            setActionError(getApiError(error));
        } finally {
            setAccepting(false);
        }
    };

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
                new ApiError('Tài khoản hiện tại của bạn không có vai trò Bưu tá. Chỉ tài khoản COURIER mới được nhận đơn.')
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
                    Khám phá các kiện hàng đang mở trên toàn hệ thống. Nhận đơn ngay theo giá niêm yết để tối ưu lộ trình và thu nhập bưu tá của bạn.
                </p>
            </header>

            {/* Banner trạng thái đăng nhập / vai trò Bưu tá */}
            {!user && (
                <div className="market-alert-banner info">
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <AlertTriangle size={20} color="#0284c7" />
                        <span style={{ fontSize: '0.92rem', lineHeight: '1.5' }}>
                Bạn chưa đăng nhập. Để nhận đơn, vui lòng <strong style={{ color: '#0f172a' }}>Đăng nhập tài khoản Bưu tá</strong>
                            {' '}(Tài khoản mẫu: <code className="market-alert-code">courier@fptpost.vn</code> / Mật khẩu: <code className="market-alert-code">12345678</code>).
            </span>
                    </div>
                    <Button
                        variant="secondary"
                        onClick={() => navigate('/login')}
                        
                    >
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
                        Chỉ tài khoản có vai trò <strong>Bưu tá (COURIER)</strong> mới được nhận đơn. Vui lòng đăng nhập với <code>courier@fptpost.vn</code> (12345678).
                    </span>
                </div>
            )}

            {user && isCourier && (
                <div className="market-alert-banner-courier">
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
                    <ApiErrorNotice error={actionError} />
                </div>
            )}

            {/* Tab navigation: Sàn đơn công khai / Lời mời trực tiếp */}
            {isCourier && (
                <div
                    style={{
                        display: 'flex',
                        gap: '12px',
                        marginBottom: '20px',
                        borderBottom: '1px solid var(--border-subtle)',
                        paddingBottom: '12px',
                    }}
                >
                    <button
                        type="button"
                        onClick={() => setMarketplaceTab('public')}
                        style={{
                            padding: '8px 18px',
                            borderRadius: 'var(--radius-full)',
                            border: '1px solid',
                            borderColor: marketplaceTab === 'public' ? 'var(--primary)' : 'var(--border-subtle)',
                            background: marketplaceTab === 'public' ? 'var(--brand-soft)' : 'var(--bg-secondary)',
                            color: marketplaceTab === 'public' ? 'var(--primary)' : 'var(--text-secondary)',
                            fontWeight: 600,
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            fontSize: '0.9rem',
                            transition: 'all var(--transition-fast)',
                        }}
                    >
                        <Package size={16} />
                        Sàn đơn công khai
                        <span style={{ fontSize: '0.75rem', opacity: 0.8 }}>({totalElements})</span>
                    </button>

                    <button
                        type="button"
                        onClick={() => {
                            setMarketplaceTab('invitations');
                            fetchInvitations();
                        }}
                        style={{
                            padding: '8px 18px',
                            borderRadius: 'var(--radius-full)',
                            border: '1px solid',
                            borderColor: marketplaceTab === 'invitations' ? 'var(--primary)' : 'var(--border-subtle)',
                            background: marketplaceTab === 'invitations' ? 'var(--brand-soft)' : 'var(--bg-secondary)',
                            color: marketplaceTab === 'invitations' ? 'var(--primary)' : 'var(--text-secondary)',
                            fontWeight: 600,
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            fontSize: '0.9rem',
                            transition: 'all var(--transition-fast)',
                        }}
                    >
                        <Mail size={16} />
                        Lời mời trực tiếp
                        {invitations.length > 0 && (
                            <span
                                style={{
                                    backgroundColor: 'var(--primary)',
                                    color: '#fff',
                                    borderRadius: '10px',
                                    padding: '1px 7px',
                                    fontSize: '0.75rem',
                                    fontWeight: 700,
                                }}
                            >
                                {invitations.length}
                            </span>
                        )}
                    </button>
                </div>
            )}

            {/* TAB 1: SÀN ĐƠN CÔNG KHAI */}
            {marketplaceTab === 'public' && (
                <>
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
                                <Select label="Sắp xếp đơn hàng" value={sort} onChange={handleSortChange}>
                                    <option value="createdAt,desc">Mới nhất trước</option>
                                    <option value="weightKg,asc">Khối lượng: Nhẹ đến nặng</option>
                                    <option value="weightKg,desc">Khối lượng: Nặng đến nhẹ</option>
                                </Select>
                            </div>
                        </div>
                    </section>

                    {/* Privacy Notice */}
                    <div className="privacy-pill-notice">
                        <ShieldAlert size={16} aria-hidden="true" />
                        <span>Thông tin chi tiết (số nhà, số điện thoại người nhận) được ẩn để bảo mật cho đến khi tài xế nhận đơn thành công.</span>
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
                </>
            )}

            {/* TAB 2: LỜI MỜI TRỰC TIẾP TỪ NGƯỜI GỬI */}
            {marketplaceTab === 'invitations' && (
                <section style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    {invitationsLoading ? (
                        <div className="market-loading">Đang tải danh sách lời mời trực tiếp...</div>
                    ) : invitations.length === 0 ? (
                        <div className="market-empty" style={{ padding: '60px 20px' }}>
                            <Mail size={52} strokeWidth={1.5} style={{ opacity: 0.5, marginBottom: '8px' }} />
                            <p style={{ fontSize: '1.1rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                                Bạn chưa có lời mời trực tiếp nào đang chờ.
                            </p>
                            <p style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>
                                Khi người gửi chọn bạn từ danh sách bưu tá quen hoặc tìm kiếm bạn để giao đơn, lời mời sẽ hiển thị tại đây.
                            </p>
                        </div>
                    ) : (
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))', gap: '20px' }}>
                            {invitations.map((inv) => {
                                const fare = inv.priceAgreed || inv.priceSuggested || 0;
                                const isExpired = new Date(inv.expiresAt).getTime() < Date.now();
                                return (
                                    <div
                                        key={inv.id}
                                        style={{
                                            backgroundColor: 'var(--bg-secondary)',
                                            borderRadius: 'var(--radius-lg)',
                                            border: '1px solid var(--border-subtle)',
                                            padding: '20px',
                                            display: 'flex',
                                            flexDirection: 'column',
                                            justifyContent: 'space-between',
                                            gap: '16px',
                                            transition: 'border-color var(--transition-fast)',
                                        }}
                                    >
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                            {/* Card Header */}
                                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                                <div>
                                                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Mã vận đơn:</span>
                                                    <div style={{ fontWeight: 700, color: 'var(--text-primary)', fontSize: '1rem', letterSpacing: '0.5px' }}>
                                                        {inv.orderCode}
                                                    </div>
                                                </div>
                                                <Badge dot tone={isExpired ? 'danger' : 'warning'}>
                                                    {isExpired ? 'Hết hạn' : 'Chờ phản hồi'}
                                                </Badge>
                                            </div>

                                            {/* Sender info */}
                                            <div
                                                style={{
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    gap: '10px',
                                                    padding: '8px 12px',
                                                    borderRadius: 'var(--radius-sm)',
                                                    backgroundColor: 'var(--bg-tertiary)',
                                                }}
                                            >
                                                <div
                                                    style={{
                                                        width: '32px',
                                                        height: '32px',
                                                        borderRadius: '50%',
                                                        backgroundColor: 'var(--brand-soft)',
                                                        color: 'var(--primary)',
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'center',
                                                        fontWeight: 600,
                                                        fontSize: '0.85rem',
                                                    }}
                                                >
                                                    {inv.senderName ? inv.senderName.charAt(0).toUpperCase() : <UserIcon size={16} />}
                                                </div>
                                                <div>
                                                    <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                                                        Người gửi: {inv.senderName || 'Khách hàng FPTPost'}
                                                    </div>
                                                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                                                        {inv.senderPhone || 'Đã xác minh'}
                                                    </div>
                                                </div>
                                            </div>

                                            {/* Message */}
                                            {inv.message && (
                                                <div
                                                    style={{
                                                        padding: '10px 12px',
                                                        backgroundColor: 'var(--bg-primary)',
                                                        borderRadius: 'var(--radius-sm)',
                                                        borderLeft: '3px solid var(--primary)',
                                                        fontSize: '0.85rem',
                                                        color: 'var(--text-secondary)',
                                                        fontStyle: 'italic',
                                                    }}
                                                >
                                                    &quot;{inv.message}&quot;
                                                </div>
                                            )}

                                            {/* Route & Distance */}
                                            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '0.85rem' }}>
                                                <div style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                                                    <MapPin size={16} style={{ color: 'var(--primary)', flexShrink: 0, marginTop: '2px' }} />
                                                    <div>
                                                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'block' }}>Điểm lấy hàng</span>
                                                        <span style={{ color: 'var(--text-primary)' }}>{inv.pickupAddress}</span>
                                                    </div>
                                                </div>
                                                <div style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                                                    <MapPin size={16} style={{ color: 'var(--accent-emerald)', flexShrink: 0, marginTop: '2px' }} />
                                                    <div>
                                                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'block' }}>Điểm giao hàng</span>
                                                        <span style={{ color: 'var(--text-primary)' }}>{inv.deliveryAddress}</span>
                                                    </div>
                                                </div>
                                            </div>

                                            {/* Package & Fare Details */}
                                            <div
                                                style={{
                                                    display: 'flex',
                                                    justifyContent: 'space-between',
                                                    alignItems: 'center',
                                                    padding: '12px 14px',
                                                    borderRadius: 'var(--radius-md)',
                                                    backgroundColor: 'var(--bg-tertiary)',
                                                    marginTop: '4px',
                                                }}
                                            >
                                                <div>
                                                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'block' }}>Kiện hàng</span>
                                                    <span style={{ fontWeight: 600, color: 'var(--text-primary)', fontSize: '0.875rem' }}>
                                                        {inv.productName || 'Bưu phẩm'} • {inv.weightKg ?? 1} kg
                                                    </span>
                                                </div>
                                                <div style={{ textAlign: 'right' }}>
                                                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'block' }}>Cước phí nhận</span>
                                                    <span style={{ fontWeight: 700, color: 'var(--primary)', fontSize: '1.1rem' }}>
                                                        {numberFormat.format(fare)} đ
                                                    </span>
                                                </div>
                                            </div>
                                        </div>

                                        {/* Card Footer: Expiry & Action Buttons */}
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', borderTop: '1px solid var(--border-subtle)', paddingTop: '12px' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                                                <Clock size={13} />
                                                <span>
                                                    Hạn phản hồi: {new Date(inv.expiresAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}{' '}
                                                    {new Date(inv.expiresAt).toLocaleDateString('vi-VN')}
                                                </span>
                                            </div>

                                            <div style={{ display: 'flex', gap: '10px' }}>
                                                <Button
                                                    variant="secondary"
                                                    icon={<XCircle size={15} />}
                                                    onClick={() => {
                                                        setSelectedInvitationForAction(inv);
                                                        setActionType('reject');
                                                    }}
                                                    disabled={isExpired || accepting}
                                                    style={{ flex: 1 }}
                                                >
                                                    Từ chối
                                                </Button>
                                                <Button
                                                    variant="primary"
                                                    icon={<CheckCircle2 size={15} />}
                                                    onClick={() => {
                                                        setSelectedInvitationForAction(inv);
                                                        setActionType('accept');
                                                    }}
                                                    disabled={isExpired || accepting}
                                                    style={{ flex: 1.4 }}
                                                >
                                                    Chấp nhận
                                                </Button>
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </section>
            )}

            {/* Modal xác nhận nhận đơn */}
            {selectedOrderToAccept && (
                <Modal
                    isOpen={!!selectedOrderToAccept}
                    title="Xác nhận nhận đơn hàng"
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
                                    ⚠️ <em>Đơn hàng giá trị cao: Yêu cầu bưu tá từ hạng Bạc trở lên.</em>
                                </p>
                            )}
                        </div>

                        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                            <AlertTriangle size={15} />
                            <span>Sau khi nhận, đơn sẽ chuyển sang <strong>Chờ ký quỹ</strong>. Người gửi có 15 phút để hoàn tất ký quỹ.</span>
                        </div>

                        {actionError && (
                            <ApiErrorNotice error={actionError} />
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

            {/* Modal: Xác nhận chấp nhận lời mời trực tiếp */}
            {selectedInvitationForAction && actionType === 'accept' && (
                <Modal
                    isOpen={Boolean(selectedInvitationForAction)}
                    title="Xác nhận nhận đơn theo lời mời trực tiếp"
                    onClose={() => {
                        if (!accepting) {
                            setSelectedInvitationForAction(null);
                            setActionType(null);
                        }
                    }}
                >
                    <div className="ui-stack" style={{ gap: '16px' }}>
                        <p style={{ margin: 0, color: 'var(--text-primary)' }}>
                            Bạn có chắc chắn muốn đồng ý nhận đơn hàng <strong>{selectedInvitationForAction.orderCode}</strong> từ người gửi <strong>{selectedInvitationForAction.senderName}</strong>?
                        </p>

                        <div style={{ backgroundColor: 'var(--bg-tertiary)', padding: '14px', borderRadius: 'var(--radius-md)' }}>
                            <p style={{ margin: '4px 0', fontSize: '0.9rem', color: 'var(--text-primary)' }}>
                                📦 <strong>Hàng hóa:</strong> {selectedInvitationForAction.productName} ({selectedInvitationForAction.weightKg ?? 1} kg)
                            </p>
                            <p style={{ margin: '4px 0', fontSize: '0.9rem', color: 'var(--text-primary)' }}>
                                📍 <strong>Điểm lấy:</strong> {selectedInvitationForAction.pickupAddress}
                            </p>
                            <p style={{ margin: '4px 0', fontSize: '0.9rem', color: 'var(--text-primary)' }}>
                                📍 <strong>Điểm giao:</strong> {selectedInvitationForAction.deliveryAddress}
                            </p>
                            <p style={{ margin: '4px 0', fontSize: '0.9rem', color: 'var(--primary)', fontWeight: 600 }}>
                                💰 <strong>Cước phí:</strong> {numberFormat.format(selectedInvitationForAction.priceAgreed || selectedInvitationForAction.priceSuggested || 0)} đ
                            </p>
                        </div>

                        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                            <AlertTriangle size={15} />
                            <span>Sau khi đồng ý, đơn sẽ chuyển sang <strong>Chờ ký quỹ</strong> và được khóa độc quyền cho bạn.</span>
                        </div>

                        {actionError && <ApiErrorNotice error={actionError} />}

                        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '8px' }}>
                            <Button
                                variant="secondary"
                                onClick={() => {
                                    setSelectedInvitationForAction(null);
                                    setActionType(null);
                                }}
                                disabled={accepting}
                            >
                                Hủy bỏ
                            </Button>
                            <Button
                                variant="primary"
                                onClick={handleConfirmAcceptInvitation}
                                disabled={accepting}
                            >
                                {accepting ? 'Đang xử lý...' : 'Đồng ý nhận đơn'}
                            </Button>
                        </div>
                    </div>
                </Modal>
            )}

            {/* Modal: Xác nhận từ chối lời mời trực tiếp */}
            {selectedInvitationForAction && actionType === 'reject' && (
                <Modal
                    isOpen={Boolean(selectedInvitationForAction)}
                    title="Từ chối lời mời giao hàng"
                    onClose={() => {
                        if (!accepting) {
                            setSelectedInvitationForAction(null);
                            setActionType(null);
                        }
                    }}
                >
                    <div className="ui-stack" style={{ gap: '16px' }}>
                        <p style={{ margin: 0, color: 'var(--text-primary)' }}>
                            Bạn có chắc muốn từ chối lời mời giao đơn hàng <strong>{selectedInvitationForAction.orderCode}</strong>?
                        </p>

                        <div>
                            <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '6px' }}>
                                Lý do từ chối (Tùy chọn)
                            </label>
                            <input
                                type="text"
                                style={{
                                    width: '100%',
                                    padding: '10px 12px',
                                    borderRadius: 'var(--radius-sm)',
                                    backgroundColor: 'var(--bg-secondary)',
                                    color: 'var(--text-primary)',
                                    border: '1px solid var(--border-subtle)',
                                    fontSize: '0.875rem',
                                    outline: 'none',
                                    boxSizing: 'border-box',
                                }}
                                placeholder="Ví dụ: Không tiện đường, lịch trình bị trùng..."
                                value={rejectReason}
                                onChange={(e) => setRejectReason(e.target.value)}
                            />
                        </div>

                        {actionError && <ApiErrorNotice error={actionError} />}

                        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '8px' }}>
                            <Button
                                variant="secondary"
                                onClick={() => {
                                    setSelectedInvitationForAction(null);
                                    setActionType(null);
                                }}
                                disabled={accepting}
                            >
                                Quay lại
                            </Button>
                            <Button
                                variant="danger"
                                onClick={handleConfirmRejectInvitation}
                                disabled={accepting}
                            >
                                {accepting ? 'Đang từ chối...' : 'Xác nhận từ chối'}
                            </Button>
                        </div>
                    </div>
                </Modal>
            )}
        </div>
    );
}
