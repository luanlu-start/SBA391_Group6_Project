import React, { useState, useEffect } from 'react';
import MarketplaceOrderCard from '../components/ui/MarketplaceOrderCard';
import {marketPlaceOrderApi} from '../api/marketPlaceOrderApi';
import {
    Search,
    MapPin,
    Package,
    Weight,
    ArrowRight,
    Clock,
    ChevronLeft,
    ChevronRight,
    ShieldAlert
} from 'lucide-react';
import Select from '../components/ui/Select';
import '../styles/courier-market.css';

// 1. Định nghĩa Interface chuẩn theo MarketplaceOrderResponse DTO
export interface MarketplaceOrder {
    id: number;
    orderCode: string;
    pickupArea: string;
    deliveryArea: string;
    productName: string;
    quantity: number;
    weightKg: number;
    declaredValue: number;
    status: 'CANCELLED' | 'COMPLETED' | 'IN_DELIVERY' | 'SHIPPER_ASSIGNED' | 'FINDING_SHIPPER' | 'PENDING';
    createAt: string;
}

// Interface phân trang tương thích Spring Data Page<T>
interface SpringPageResponse<T> {
    content: T[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
}

const numberFormat = new Intl.NumberFormat('vi-VN');

export default function CourierMarketplacePage(): React.ReactElement {
    const [orders, setOrders] = useState<MarketplaceOrder[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [keyword, setKeyword] = useState<string>('');
    const [sort, setSort] = useState<string>('createdAt,desc');

    // Quản lý phân trang (Spring Boot 0-indexed)
    const [page, setPage] = useState<number>(0);
    const [totalPages, setTotalPages] = useState<number>(1);
    const [totalElements, setTotalElements] = useState<number>(0);
    const pageSize: number = 6;

    useEffect(() => {
        fetchMarketplaceOrders();
    }, [page, sort]);

    const fetchMarketplaceOrders = async (): Promise<void> => {
        setLoading(true);
        try {
            // Khi gọi API thực tế qua Axios:
            const res = await marketPlaceOrderApi.getAll();
            setOrders(res.data.content);
            setTotalPages(res.data.totalPages);
            setTotalElements(res.data.totalElements);
            setLoading(false);
        } catch (error) {
            console.error('Lỗi tải danh sách sàn đơn:', error);
            setLoading(false);
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

    return (
        <div className="courier-market-container">
            {/* Header section */}
            <header className="market-header">
                <p className="home-eyebrow"><span />Sàn Giao Hàng Bưu Chính</p>
                <h1 className="market-title">Duyệt & Tìm Kiếm <span>Sàn Đơn Khả Dụng</span></h1>
                <p className="market-description">
                    Khám phá các kiện hàng đang mở trên toàn hệ thống. Nhận đơn ngay để tối ưu lộ trình và thu nhập bưu tá của bạn.
                </p>
            </header>

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
                        <Select value={sort} onChange={handleSortChange}>
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
                <span>Thông tin chi tiết (số nhà, số điện thoại người nhận) được ẩn cho đến khi tài xế nhận đơn.</span>
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
                    orders.map((order: MarketplaceOrder) => (
                        <MarketplaceOrderCard
                            key={order.id}
                            order={order}
                            onViewDetail={(code) => {
                                // Xử lý điều hướng hoặc mở modal xem chi tiết
                                console.log('Xem chi tiết đơn:', code);
                            }}
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
                    <span className="pagination-current">Trang {page + 1} / {totalPages}</span>
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
        </div>
    );
}