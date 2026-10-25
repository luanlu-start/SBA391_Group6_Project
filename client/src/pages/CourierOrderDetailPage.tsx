import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    ArrowLeft,
    MapPin,
    Package,
    ShieldAlert,
    ShieldCheck,
    Weight,
    DollarSign,
    CheckCircle2,
    Calendar,
    AlertTriangle,
    Truck,
} from 'lucide-react';
import { marketPlaceOrderApi } from '../api/marketPlaceOrderApi';
import type { MarketplaceOrderDetail, MarketOrderAcceptResponse } from '../types/order';
import { ORDER_STATUS_MAP } from '../types/order';
import { getApiError, type ApiError } from '../api/ApiError';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ApiErrorNotice from '../components/ApiErrorNotice';
import Modal from '../components/ui/Modal';
import { useAuthentication } from '../auth/useAuthentication';

const numberFormat = new Intl.NumberFormat('vi-VN');

export default function CourierOrderDetailPage(): React.ReactElement {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { user } = useAuthentication();
    const isCourier = Boolean(user?.roles?.includes('COURIER'));

    const [order, setOrder] = useState<MarketplaceOrderDetail | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<ApiError | null>(null);

    // Xử lý nhận đơn (UC-17)
    const [isConfirmOpen, setIsConfirmOpen] = useState<boolean>(false);
    const [accepting, setAccepting] = useState<boolean>(false);
    const [acceptResult, setAcceptResult] = useState<MarketOrderAcceptResponse | null>(null);
    const [acceptError, setAcceptError] = useState<ApiError | null>(null);

    useEffect(() => {
        if (!id) return;
        let mounted = true;
        setLoading(true);
        setError(null);

        marketPlaceOrderApi
            .getById(id)
            .then(res => {
                if (mounted) setOrder(res.data);
            })
            .catch(err => {
                if (mounted) setError(getApiError(err));
            })
            .finally(() => {
                if (mounted) setLoading(false);
            });

        return () => {
            mounted = false;
        };
    }, [id]);

    const handleAcceptOrder = async () => {
        if (!id) return;
        setAccepting(true);
        setAcceptError(null);
        try {
            const res = await marketPlaceOrderApi.acceptOrder(id);
            setAcceptResult(res.data);
            setIsConfirmOpen(false);
            // Cập nhật lại trạng thái local của đơn
            if (order) {
                setOrder({
                    ...order,
                    status: 'AWAITING_PAYMENT',
                    isAvailable: false,
                });
            }
        } catch (err) {
            setAcceptError(getApiError(err));
        } finally {
            setAccepting(false);
        }
    };

    if (loading) {
        return <LoadingSpinner text="Đang tải thông tin kiện hàng trên sàn…" />;
    }

    if (error || !order) {
        return (
            <div className="ui-stack">
                <PageHeader
                    title="Không tìm thấy đơn hàng"
                    eyebrow="Sàn đơn bưu chính"
                    actions={
                        <Button
                            variant="secondary"
                            icon={<ArrowLeft size={16} />}
                            onClick={() => navigate('/courier/market')}
                        >
                            Quay lại sàn đơn
                        </Button>
                    }
                />
                <SectionCard>
                    <div className="ui-stack">
                        {error ? (
                            <ApiErrorNotice message={error.message} correlationId={error.correlationId} />
                        ) : (
                            <p>Đơn hàng không tồn tại trên sàn hoặc đã bị hủy.</p>
                        )}
                        <div>
                            <Button variant="secondary" onClick={() => navigate('/courier/market')}>
                                Về trang sàn đơn
                            </Button>
                        </div>
                    </div>
                </SectionCard>
            </div>
        );
    }

    const statusConfig = ORDER_STATUS_MAP[order.status] ?? {
        label: order.status,
        tone: 'neutral',
    };

    return (
        <div className="ui-stack" style={{ gap: '24px' }}>
            <PageHeader
                title={`Kiện hàng: ${order.orderCode}`}
                eyebrow="Chi tiết đơn trên sàn (SCR-30)"
                description={`Đăng lúc ${new Date(order.createdAt).toLocaleString('vi-VN')}`}
                actions={
                    <div className="ui-row" style={{ gap: '12px' }}>
                        <Badge dot tone={statusConfig.tone}>
                            {statusConfig.label}
                        </Badge>
                        <Button
                            variant="ghost"
                            icon={<ArrowLeft size={16} />}
                            onClick={() => navigate('/courier/market')}
                        >
                            Về sàn đơn
                        </Button>
                    </div>
                }
            />

            {/* Banner thành công sau khi nhận đơn thành công */}
            {acceptResult && (
                <div
                    style={{
                        padding: '20px',
                        backgroundColor: 'var(--emerald-50, #ecfdf5)',
                        border: '1px solid var(--emerald-300, #6ee7b7)',
                        borderRadius: 'var(--radius-lg, 12px)',
                        color: 'var(--emerald-950, #022c22)',
                    }}
                >
                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
                        <CheckCircle2 size={24} color="var(--emerald-600, #059669)" style={{ flexShrink: 0, marginTop: '2px' }} />
                        <div className="ui-stack" style={{ gap: '8px' }}>
                            <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 600 }}>
                                Nhận đơn thành công! (Mã vận đơn: {acceptResult.orderCode})
                            </h3>
                            <p style={{ margin: 0, fontSize: '0.95rem' }}>
                                Đơn hàng đã được gắn với tài khoản của bạn và chuyển sang trạng thái <strong>Chờ ký quỹ</strong>.
                                Người gửi có <strong>15 phút</strong> (PRM-023) để hoàn tất thanh toán ký quỹ qua Ví hoặc VNPay.
                            </p>
                            <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--text-muted, #64748b)' }}>
                                Giá cước giao hàng đã chốt: <strong>{numberFormat.format(acceptResult.priceAgreed)} đ</strong>.
                            </p>
                            <div style={{ display: 'flex', gap: '12px', marginTop: '8px' }}>
                                <Button
                                    variant="primary"
                                    onClick={() => navigate('/courier/market')}
                                >
                                    Tiếp tục duyệt sàn đơn
                                </Button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Thông báo lỗi khi nhận đơn */}
            {acceptError && (
                <ApiErrorNotice message={acceptError.message} correlationId={acceptError.correlationId} />
            )}

            {/* Cảnh báo bảo mật dữ liệu nhạy cảm */}
            <div
                style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                    padding: '12px 16px',
                    backgroundColor: 'var(--sky-50, #f0f9ff)',
                    border: '1px solid var(--sky-200, #bae6fd)',
                    borderRadius: 'var(--radius-md, 8px)',
                    color: 'var(--sky-900, #0c4a6e)',
                    fontSize: '0.9rem',
                }}
            >
                <ShieldAlert size={18} color="var(--sky-600, #0284c7)" style={{ flexShrink: 0 }} />
                <span>
                    <strong>Quy định bảo mật (BR-R-023, BR-R-027):</strong> Số điện thoại và địa chỉ chi tiết của người nhận được bảo mật.
                    Sau khi bạn nhận đơn và người gửi ký quỹ thành công, thông tin liên lạc đầy đủ sẽ hiển thị.
                </span>
            </div>

            <div className="ui-showcase-grid">
                {/* Cột trái: Lộ trình và Thông tin hàng hóa */}
                <div className="ui-stack" style={{ gap: '20px' }}>
                    {/* Tuyến đường vận chuyển */}
                    <SectionCard
                        title="Tuyến đường & Điểm giao nhận"
                        description="Khu vực nhận hàng và giao hàng dự kiến"
                    >
                        <div className="ui-summary-list">
                            <div>
                                <dt className="ui-row" style={{ gap: '6px' }}>
                                    <MapPin size={16} style={{ color: 'var(--primary)' }} />
                                    <span>Điểm lấy hàng:</span>
                                </dt>
                                <dd><strong>{order.pickupAddress}</strong></dd>
                            </div>

                            <div>
                                <dt className="ui-row" style={{ gap: '6px' }}>
                                    <MapPin size={16} style={{ color: 'var(--accent-emerald)' }} />
                                    <span>Điểm giao hàng:</span>
                                </dt>
                                <dd><strong>{order.deliveryAddress}</strong></dd>
                            </div>

                            {order.distanceKm && (
                                <div>
                                    <dt>Quãng đường ước tính:</dt>
                                    <dd>~{order.distanceKm} km</dd>
                                </div>
                            )}

                            <div>
                                <dt>Người nhận (đã ẩn bảo mật):</dt>
                                <dd>{order.maskedReceiverName} ({order.maskedReceiverPhone})</dd>
                            </div>
                        </div>
                    </SectionCard>

                    {/* Chi tiết kiện hàng */}
                    <SectionCard
                        title="Thông tin kiện hàng"
                        description="Thông số kích thước, khối lượng và ghi chú vận chuyển"
                    >
                        <div className="ui-summary-list">
                            <div>
                                <dt className="ui-row" style={{ gap: '6px' }}>
                                    <Package size={16} />
                                    <span>Tên hàng hóa:</span>
                                </dt>
                                <dd><strong>{order.productName}</strong></dd>
                            </div>

                            <div>
                                <dt>Số lượng:</dt>
                                <dd>{order.quantity} kiện</dd>
                            </div>

                            <div>
                                <dt className="ui-row" style={{ gap: '6px' }}>
                                    <Weight size={16} />
                                    <span>Khối lượng:</span>
                                </dt>
                                <dd>{order.weightKg} kg</dd>
                            </div>

                            <div>
                                <dt className="ui-row" style={{ gap: '6px' }}>
                                    <DollarSign size={16} />
                                    <span>Giá trị khai báo:</span>
                                </dt>
                                <dd>
                                    <strong>{numberFormat.format(order.declaredValue || 0)} đ</strong>
                                    {order.requiresSilverTier && (
                                        <span
                                            style={{
                                                marginLeft: '8px',
                                                fontSize: '0.8rem',
                                                padding: '2px 8px',
                                                backgroundColor: 'var(--amber-100, #fef3c7)',
                                                color: 'var(--amber-800, #92400e)',
                                                borderRadius: '4px',
                                            }}
                                        >
                                            Yêu cầu hạng Bạc+
                                        </span>
                                    )}
                                </dd>
                            </div>

                            <div>
                                <dt>Mô tả chi tiết:</dt>
                                <dd>{order.productDescription || 'Không có mô tả chi tiết'}</dd>
                            </div>

                            <div>
                                <dt>Ghi chú cho tài xế:</dt>
                                <dd>{order.deliveryNote || 'Không có ghi chú'}</dd>
                            </div>
                        </div>
                    </SectionCard>

                    {/* Ảnh kiện hàng */}
                    {order.imageUrls && order.imageUrls.length > 0 && (
                        <SectionCard
                            title="Hình ảnh kiện hàng thực tế"
                            description={`${order.imageUrls.length} ảnh đính kèm từ người gửi`}
                        >
                            <div
                                style={{
                                    display: 'grid',
                                    gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
                                    gap: '12px',
                                }}
                            >
                                {order.imageUrls.map((url, idx) => (
                                    <a
                                        key={idx}
                                        href={url}
                                        target="_blank"
                                        rel="noreferrer"
                                        style={{
                                            display: 'block',
                                            borderRadius: 'var(--radius-md)',
                                            overflow: 'hidden',
                                            border: '1px solid var(--border-subtle)',
                                            aspectRatio: '1 / 1',
                                        }}
                                    >
                                        <img
                                            src={url}
                                            alt={`Kiện hàng ${idx + 1}`}
                                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                        />
                                    </a>
                                ))}
                            </div>
                        </SectionCard>
                    )}
                </div>

                {/* Cột phải: Giá niêm yết, quy tắc và Hành động nhận đơn */}
                <div className="ui-stack" style={{ gap: '20px' }}>
                    <SectionCard title="Cước phí & Thanh toán">
                        <div className="ui-stack" style={{ gap: '16px' }}>
                            <div style={{ textAlign: 'center', padding: '16px', backgroundColor: 'var(--bg-tertiary, #f8fafc)', borderRadius: '8px' }}>
                                <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Giá cước niêm yết (người gửi đề xuất)</span>
                                <div style={{ fontSize: '1.8rem', fontWeight: 'bold', color: 'var(--primary, #0284c7)', marginTop: '4px' }}>
                                    {numberFormat.format(order.priceSuggested || order.declaredValue || 35000)} <small style={{ fontSize: '1rem' }}>đ</small>
                                </div>
                            </div>

                            <div className="ui-summary-list">
                                <div>
                                    <dt className="ui-row" style={{ gap: '6px' }}>
                                        <Truck size={14} />
                                        <span>Mã vận đơn:</span>
                                    </dt>
                                    <dd className="font-mono">{order.orderCode}</dd>
                                </div>
                                <div>
                                    <dt className="ui-row" style={{ gap: '6px' }}>
                                        <Calendar size={14} />
                                        <span>Thời gian tạo:</span>
                                    </dt>
                                    <dd>{new Date(order.createdAt).toLocaleDateString('vi-VN')}</dd>
                                </div>
                            </div>

                            {/* Quy tắc bưu tá cần nắm */}
                            <div style={{ borderTop: '1px solid var(--border-subtle, #e2e8f0)', paddingTop: '12px' }}>
                                <h4 style={{ fontSize: '0.9rem', marginBottom: '8px', color: 'var(--text-secondary)' }}>
                                    Quy tắc nhận đơn (BR):
                                </h4>
                                <ul style={{ fontSize: '0.8rem', color: 'var(--text-muted)', paddingLeft: '16px', margin: 0, lineHeight: 1.6 }}>
                                    <li><strong>BR-R-008:</strong> Không tự nhận đơn do chính mình tạo.</li>
                                    <li><strong>BR-R-024:</strong> Đơn {'>'} 1.000.000đ yêu cầu bưu tá từ hạng Bạc trở lên.</li>
                                    <li><strong>BR-R-028:</strong> Hạn mức số đơn và tổng giá trị tối đa theo hạng bưu tá.</li>
                                    <li><strong>BR-F-029:</strong> Nhận đơn thành công sẽ chuyển trạng thái sang Chờ ký quỹ.</li>
                                </ul>
                            </div>

                            {/* Nút hành động chính (UC-17) */}
                            {order.isAvailable && !acceptResult && (
                                <>
                                    {!user ? (
                                        <div
                                            style={{
                                                backgroundColor: 'var(--blue-50, #eff6ff)',
                                                border: '1px solid var(--blue-200, #bfdbfe)',
                                                padding: '12px',
                                                borderRadius: '6px',
                                                fontSize: '0.85rem',
                                                color: '#1e3a8a',
                                                textAlign: 'center',
                                            }}
                                        >
                                            <p style={{ margin: '0 0 8px 0' }}>
                                                Bạn chưa đăng nhập. Vui lòng đăng nhập với tài khoản Bưu tá để nhận đơn này (<code>courier@fptpost.vn</code> / <code>12345678</code>).
                                            </p>
                                            <Button variant="secondary" onClick={() => navigate('/login')} style={{ width: '100%', padding: '8px' }}>
                                                Đăng nhập tài khoản Bưu tá
                                            </Button>
                                        </div>
                                    ) : !isCourier ? (
                                        <div
                                            style={{
                                                backgroundColor: 'var(--amber-50, #fffbeb)',
                                                border: '1px solid var(--amber-300, #fcd34d)',
                                                padding: '12px',
                                                borderRadius: '6px',
                                                fontSize: '0.85rem',
                                                color: '#92400e',
                                            }}
                                        >
                                            Tài khoản của bạn ({user.email}) có vai trò {user.roles?.join(', ')}. Theo quy tắc <strong>BR-R-009</strong>, chỉ Bưu tá mới được nhận đơn. Vui lòng chuyển sang tài khoản <code>courier@fptpost.vn</code>.
                                        </div>
                                    ) : (
                                        <Button
                                            variant="primary"
                                            icon={<CheckCircle2 size={18} />}
                                            onClick={() => setIsConfirmOpen(true)}
                                            style={{ width: '100%', padding: '14px', fontSize: '1rem' }}
                                        >
                                            Nhận đơn này (Giá niêm yết)
                                        </Button>
                                    )}
                                </>
                            )}

                            {!order.isAvailable && !acceptResult && (
                                <div style={{ textAlign: 'center', padding: '12px', color: 'var(--text-muted)', backgroundColor: 'var(--bg-tertiary)', borderRadius: '6px' }}>
                                    Đơn hàng hiện không còn khả dụng trên sàn.
                                </div>
                            )}
                        </div>
                    </SectionCard>
                </div>
            </div>

            {/* Modal xác nhận nhận đơn */}
            <Modal
                isOpen={isConfirmOpen}
                title="Xác nhận nhận đơn theo giá niêm yết (UC-17)"
                onClose={() => {
                    if (!accepting) setIsConfirmOpen(false);
                }}
            >
                <div className="ui-stack" style={{ gap: '16px' }}>
                    <p>
                        Bạn xác nhận sẽ nhận giao kiện hàng <strong>{order.orderCode}</strong> với mức giá niêm yết là{' '}
                        <strong style={{ color: 'var(--primary)' }}>
                            {numberFormat.format(order.priceSuggested || order.declaredValue || 35000)} đ
                        </strong>?
                    </p>

                    <div style={{ backgroundColor: 'var(--bg-tertiary, #f8fafc)', padding: '12px', borderRadius: '6px', fontSize: '0.9rem' }}>
                        <p style={{ margin: '4px 0' }}>• Hàng hóa: <strong>{order.productName}</strong> ({order.weightKg} kg)</p>
                        <p style={{ margin: '4px 0' }}>• Tuyến đường: {order.pickupAddress} ➔ {order.deliveryAddress}</p>
                        {order.requiresSilverTier && (
                            <p style={{ margin: '4px 0', color: 'var(--amber-700)' }}>
                                <ShieldCheck size={14} style={{ display: 'inline', marginRight: '4px' }} />
                                Đơn giá trị cao: Hệ thống sẽ tự động đối chiếu hạng thành viên Bạc trở lên.
                            </p>
                        )}
                    </div>

                    <div style={{ display: 'flex', gap: '8px', alignItems: 'center', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                        <AlertTriangle size={15} />
                        <span>Sau khi xác nhận, người gửi sẽ có 15 phút để thực hiện ký quỹ đơn hàng.</span>
                    </div>

                    {acceptError && (
                        <ApiErrorNotice message={acceptError.message} correlationId={acceptError.correlationId} />
                    )}

                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '12px' }}>
                        <Button
                            variant="secondary"
                            onClick={() => setIsConfirmOpen(false)}
                            disabled={accepting}
                        >
                            Xem lại
                        </Button>
                        <Button
                            variant="primary"
                            onClick={handleAcceptOrder}
                            disabled={accepting}
                        >
                            {accepting ? 'Đang gửi yêu cầu...' : 'Tôi đồng ý nhận đơn'}
                        </Button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
