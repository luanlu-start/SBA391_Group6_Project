import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    ArrowLeft,
    MapPin,
    Package,
    Weight,
    DollarSign,
    CheckCircle2,
    Calendar,
    AlertTriangle,
    Truck,
    Send,
    RotateCcw,
    XCircle,
} from 'lucide-react';
import { marketPlaceOrderApi } from '../api/marketPlaceOrderApi';
import { offerApi } from '../api/offerApi';
import type { MarketplaceOrderDetail, MarketOrderAcceptResponse } from '../types/order';
import { ORDER_STATUS_MAP } from '../types/order';
import type { Offer } from '../types/offer';
import { OFFER_STATUS_MAP } from '../types/offer';
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

    // Xử lý nhận đơn theo giá niêm yết (UC-17)
    const [isConfirmOpen, setIsConfirmOpen] = useState<boolean>(false);
    const [accepting, setAccepting] = useState<boolean>(false);
    const [acceptResult, setAcceptResult] = useState<MarketOrderAcceptResponse | null>(null);
    const [acceptError, setAcceptError] = useState<ApiError | null>(null);

    // Xử lý chào giá (UC-18)
    const [myOffers, setMyOffers] = useState<Offer[]>([]);
    const [isOfferModalOpen, setIsOfferModalOpen] = useState<boolean>(false);
    const [offerPrice, setOfferPrice] = useState<string>('');
    const [offerMessage, setOfferMessage] = useState<string>('');
    const [offerEtaMinutes, setOfferEtaMinutes] = useState<string>('30');
    const [submittingOffer, setSubmittingOffer] = useState<boolean>(false);
    const [offerActionError, setOfferActionError] = useState<ApiError | null>(null);

    // Phản giá lại khi người gửi đã counter (UC-19)
    const [isCounterModalOpen, setIsCounterModalOpen] = useState<boolean>(false);
    const [counterPrice, setCounterPrice] = useState<string>('');
    const [counterMessage, setCounterMessage] = useState<string>('');

    const fetchOrderAndOffers = useCallback(async () => {
        if (!id) return;
        setLoading(true);
        setError(null);
        try {
            const orderRes = await marketPlaceOrderApi.getById(id);
            setOrder(orderRes.data);

            if (isCourier) {
                try {
                    const offersRes = await offerApi.getOffersForOrder(id);
                    setMyOffers(offersRes.data || []);
                } catch {
                    // Non-critical if courier doesn't have offers yet
                }
            }
        } catch (err) {
            setError(getApiError(err));
        } finally {
            setLoading(false);
        }
    }, [id, isCourier]);

    useEffect(() => {
        fetchOrderAndOffers();
    }, [fetchOrderAndOffers]);

    // Handle accept fixed price order (UC-17)
    const handleAcceptOrder = async () => {
        if (!id) return;
        setAccepting(true);
        setAcceptError(null);
        try {
            const res = await marketPlaceOrderApi.acceptOrder(id);
            setAcceptResult(res.data);
            setIsConfirmOpen(false);
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

    // Handle submit custom offer (UC-18)
    const handleSubmitOffer = async () => {
        if (!id || !order) return;
        const priceNum = Number(offerPrice.replace(/\D/g, ''));
        if (!priceNum || priceNum < 1000) {
            alert('Vui lòng nhập mức giá chào hợp lệ (tối thiểu 1.000đ)');
            return;
        }

        setSubmittingOffer(true);
        setOfferActionError(null);
        try {
            let etaPickup: string | undefined = undefined;
            const minutes = Number(offerEtaMinutes);
            if (!isNaN(minutes) && minutes > 0) {
                etaPickup = new Date(Date.now() + minutes * 60 * 1000).toISOString();
            }

            await offerApi.createOffer(id, {
                price: priceNum,
                message: offerMessage.trim() || undefined,
                etaPickup,
            });

            setIsOfferModalOpen(false);
            setOfferPrice('');
            setOfferMessage('');
            await fetchOrderAndOffers();
        } catch (err) {
            setOfferActionError(getApiError(err));
        } finally {
            setSubmittingOffer(false);
        }
    };

    // Handle withdraw offer (API-045 / UC-18)
    const handleWithdrawOffer = async (offerId: number) => {
        if (!window.confirm('Bạn có chắc chắn muốn rút lại chào giá này không?')) return;
        setSubmittingOffer(true);
        setOfferActionError(null);
        try {
            await offerApi.withdrawOffer(offerId);
            await fetchOrderAndOffers();
        } catch (err) {
            setOfferActionError(getApiError(err));
        } finally {
            setSubmittingOffer(false);
        }
    };

    // Handle counter-offer by courier when sender countered (UC-19)
    const handleCounterOffer = async (parentOfferId: number) => {
        const priceNum = Number(counterPrice.replace(/\D/g, ''));
        if (!priceNum || priceNum < 1000) {
            alert('Vui lòng nhập mức giá phản hồi hợp lệ');
            return;
        }

        setSubmittingOffer(true);
        setOfferActionError(null);
        try {
            await offerApi.counterOffer(parentOfferId, {
                price: priceNum,
                message: counterMessage.trim() || undefined,
            });
            setIsCounterModalOpen(false);
            setCounterPrice('');
            setCounterMessage('');
            await fetchOrderAndOffers();
        } catch (err) {
            setOfferActionError(getApiError(err));
        } finally {
            setSubmittingOffer(false);
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
                            <ApiErrorNotice error={error} />
                        ) : (
                            <p>Đơn hàng không tồn tại trên sàn hoặc đã bị hủy.</p>
                        )}
                        <div>
                            <Button variant="secondary" onClick={() => navigate('/courier/market')}>
                                Về trang danh sách sàn đơn
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

    const suggestedPrice = order.priceSuggested || order.declaredValue || 35000;
    const minPriceAllowed = Math.round(suggestedPrice * 0.7);
    const maxPriceAllowed = Math.round(suggestedPrice * 1.5);

    // Tìm chào giá mới nhất còn hiệu lực của bưu tá cho đơn này
    const activeOffer = myOffers.find(o => o.status === 'PENDING' || o.status === 'COUNTERED');

    return (
        <div className="ui-stack" style={{ gap: '20px' }}>
            <PageHeader
                title={`Kiện hàng: ${order.productName}`}
                eyebrow={`Mã vận đơn: ${order.orderCode} (SCR-30)`}
                description={`Đơn được đăng từ ${order.pickupAddress} tới ${order.deliveryAddress}`}
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

            {offerActionError && <ApiErrorNotice error={offerActionError} />}

            {/* Thông báo thành công nhận đơn (UC-17) */}
            {acceptResult && (
                <div
                    style={{
                        backgroundColor: 'var(--emerald-50, #ecfdf5)',
                        border: '1px solid var(--emerald-500, #10b981)',
                        padding: '16px',
                        borderRadius: '8px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: '12px',
                    }}
                >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <CheckCircle2 size={28} style={{ color: 'var(--emerald-600, #059669)', flexShrink: 0 }} />
                        <div>
                            <h3 style={{ margin: 0, fontSize: '1rem', color: '#065f46' }}>
                                Nhận đơn thành công!
                            </h3>
                            <p style={{ margin: '4px 0 0', fontSize: '0.875rem', color: '#047857' }}>
                                Đơn hàng <strong>{acceptResult.orderCode}</strong> đã được chuyển sang trạng thái <strong>Chờ ký quỹ</strong>. Người gửi sẽ có 15 phút để hoàn tất ký quỹ cước phí.
                            </p>
                        </div>
                    </div>
                    <Button variant="secondary" onClick={() => navigate('/courier/market')}>
                        Tiếp tục tìm đơn
                    </Button>
                </div>
            )}

            <div className="ui-showcase-grid">
                {/* Cột trái: Thông tin hành trình & Kiện hàng */}
                <div className="ui-stack" style={{ gap: '20px' }}>
                    {/* Tuyến đường giao nhận */}
                    <SectionCard
                        title="Tuyến đường vận chuyển"
                        description="Thông tin địa điểm lấy và giao hàng của người gửi"
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
                                                color: '#b45309',
                                                borderRadius: '4px',
                                                fontWeight: 600,
                                            }}
                                        >
                                            Hàng giá trị cao (Yêu cầu hạng Bạc trở lên)
                                        </span>
                                    )}
                                </dd>
                            </div>

                            <div>
                                <dt>Mô tả kiện hàng:</dt>
                                <dd>{order.productDescription || 'Không có mô tả chi tiết'}</dd>
                            </div>

                            <div>
                                <dt>Ghi chú của người gửi:</dt>
                                <dd>{order.deliveryNote || 'Không có ghi chú'}</dd>
                            </div>
                        </div>
                    </SectionCard>

                    {/* Ảnh hàng */}
                    {order.imageUrls && order.imageUrls.length > 0 && (
                        <SectionCard
                            title="Hình ảnh kiện hàng"
                            description={`${order.imageUrls.length} ảnh thực tế do người gửi tải lên`}
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
                                            borderRadius: '6px',
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

                {/* Cột phải: Giá niêm yết, Chào giá của bưu tá và Hành động */}
                <div className="ui-stack" style={{ gap: '20px' }}>
                    <SectionCard title="Cước phí & Nhận đơn">
                        <div className="ui-stack" style={{ gap: '16px' }}>
                            <div style={{ textAlign: 'center', padding: '16px', backgroundColor: 'var(--bg-tertiary, #f8fafc)', borderRadius: '8px' }}>
                                <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Giá cước niêm yết (hệ thống & người gửi)</span>
                                <div style={{ fontSize: '1.8rem', fontWeight: 'bold', color: 'var(--primary, #0284c7)', marginTop: '4px' }}>
                                    {numberFormat.format(suggestedPrice)} <small style={{ fontSize: '1rem' }}>đ</small>
                                </div>
                                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                                    Biên chào giá hợp lệ (70% - 150%): <strong>{numberFormat.format(minPriceAllowed)} đ</strong> - <strong>{numberFormat.format(maxPriceAllowed)} đ</strong>
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

                            {/* Card Hiển thị Chào giá hiện tại của bưu tá (UC-18 / UC-19) */}
                            {activeOffer && (
                                <div
                                    style={{
                                        border: '1px solid var(--primary, #2563eb)',
                                        borderRadius: '8px',
                                        padding: '14px',
                                        backgroundColor: 'var(--primary-subtle, #eff6ff)',
                                    }}
                                >
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                                        <span style={{ fontWeight: 600, fontSize: '0.9rem', color: '#1e3a8a' }}>
                                            Chào giá của bạn cho đơn này
                                        </span>
                                        <Badge dot tone={OFFER_STATUS_MAP[activeOffer.status]?.tone || 'neutral'}>
                                            {OFFER_STATUS_MAP[activeOffer.status]?.label || activeOffer.status}
                                        </Badge>
                                    </div>

                                    <div style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--primary)' }}>
                                        {numberFormat.format(activeOffer.price)} đ
                                    </div>

                                    {activeOffer.message && (
                                        <div style={{ fontSize: '0.8rem', fontStyle: 'italic', color: '#3b82f6', marginTop: '4px' }}>
                                            &ldquo;{activeOffer.message}&rdquo;
                                        </div>
                                    )}

                                    {/* Nếu người gửi đã phản giá lại cho shipper */}
                                    {activeOffer.status === 'COUNTERED' && activeOffer.createdBySender && (
                                        <div
                                            style={{
                                                marginTop: '10px',
                                                padding: '10px',
                                                backgroundColor: '#fff',
                                                borderRadius: '6px',
                                                border: '1px solid #fed7aa',
                                            }}
                                        >
                                            <div style={{ fontSize: '0.8rem', fontWeight: 600, color: '#c2410c' }}>
                                                Người gửi phản đề xuất mức giá: {numberFormat.format(activeOffer.price)} đ
                                            </div>
                                            {activeOffer.message && (
                                                <div style={{ fontSize: '0.8rem', color: '#7c2d12', marginTop: '2px' }}>
                                                    Lời nhắn: &ldquo;{activeOffer.message}&rdquo;
                                                </div>
                                            )}
                                            <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                                                {activeOffer.roundNo < 3 && (
                                                    <Button
                                                        variant="secondary"
                                                        size="sm"
                                                        icon={<RotateCcw size={14} />}
                                                        onClick={() => {
                                                            setCounterPrice(String(suggestedPrice));
                                                            setIsCounterModalOpen(true);
                                                        }}
                                                    >
                                                        Phản giá lại
                                                    </Button>
                                                )}
                                            </div>
                                        </div>
                                    )}

                                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '10px' }}>
                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            icon={<XCircle size={14} />}
                                            onClick={() => handleWithdrawOffer(activeOffer.id)}
                                            disabled={submittingOffer}
                                        >
                                            Rút chào giá (API-045)
                                        </Button>
                                    </div>
                                </div>
                            )}

                            {/* Quy tắc bưu tá cần nắm */}
                            <div style={{ borderTop: '1px solid var(--border-subtle, #e2e8f0)', paddingTop: '12px' }}>
                                <h4 style={{ fontSize: '0.9rem', marginBottom: '8px', color: 'var(--text-secondary)' }}>
                                    Quy tắc bưu tá (BR):
                                </h4>
                                <ul style={{ fontSize: '0.8rem', color: 'var(--text-muted)', paddingLeft: '16px', margin: 0, lineHeight: 1.6 }}>
                                    <li><strong>BR-R-008:</strong> Không tự nhận/chào giá đơn của chính mình.</li>
                                    <li><strong>BR-R-024:</strong> Đơn {'>'} 1.000.000đ yêu cầu từ hạng Bạc trở lên.</li>
                                    <li><strong>BR-R-029:</strong> Biên giá chào từ 70% đến 150% giá gợi ý.</li>
                                    <li><strong>BR-R-030:</strong> Mỗi bưu tá chỉ có 1 chào giá hiệu lực cho 1 đơn.</li>
                                </ul>
                            </div>

                            {/* Nút hành động chính (UC-17 & UC-18) */}
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
                                                Bạn chưa đăng nhập. Vui lòng đăng nhập với tài khoản Bưu tá để nhận đơn hoặc chào giá (<code>courier@fptpost.vn</code> / <code>12345678</code>).
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
                                            Tài khoản của bạn ({user.email}) có vai trò {user.roles?.join(', ')}. Theo quy tắc <strong>BR-R-009</strong>, chỉ Bưu tá mới được nhận đơn/chào giá.
                                        </div>
                                    ) : (
                                        <div className="ui-stack" style={{ gap: '10px' }}>
                                            <Button
                                                variant="primary"
                                                icon={<CheckCircle2 size={18} />}
                                                onClick={() => setIsConfirmOpen(true)}
                                                style={{ width: '100%', padding: '14px', fontSize: '1rem' }}
                                            >
                                                Nhận đơn (Giá niêm yết: {numberFormat.format(suggestedPrice)} đ)
                                            </Button>

                                            {!activeOffer && (
                                                <Button
                                                    variant="secondary"
                                                    icon={<Send size={16} />}
                                                    onClick={() => {
                                                        setOfferPrice(String(suggestedPrice));
                                                        setOfferMessage('');
                                                        setIsOfferModalOpen(true);
                                                    }}
                                                    style={{ width: '100%', padding: '12px' }}
                                                >
                                                    Gửi mức giá chào khác (UC-18)
                                                </Button>
                                            )}
                                        </div>
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

            {/* Modal: Xác nhận nhận đơn giá niêm yết (UC-17) */}
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
                            {numberFormat.format(suggestedPrice)} đ
                        </strong>?
                    </p>

                    <div style={{ backgroundColor: 'var(--bg-tertiary, #f8fafc)', padding: '12px', borderRadius: '6px', fontSize: '0.9rem' }}>
                        <p style={{ margin: '4px 0' }}>• Hàng hóa: <strong>{order.productName}</strong> ({order.weightKg} kg)</p>
                        <p style={{ margin: '4px 0' }}>• Tuyến đường: {order.pickupAddress} ➔ {order.deliveryAddress}</p>
                    </div>

                    <div style={{ display: 'flex', gap: '8px', alignItems: 'center', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                        <AlertTriangle size={15} />
                        <span>Sau khi xác nhận, người gửi sẽ có 15 phút để thực hiện ký quỹ đơn hàng.</span>
                    </div>

                    {acceptError && (
                        <ApiErrorNotice error={acceptError} />
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

            {/* Modal: Gửi chào giá (UC-18 / API-044) */}
            <Modal
                isOpen={isOfferModalOpen}
                title="Gửi chào giá cho người gửi (UC-18)"
                description={`Kiện hàng: ${order.productName} • Giá gợi ý: ${numberFormat.format(suggestedPrice)} đ`}
                onClose={() => {
                    if (!submittingOffer) setIsOfferModalOpen(false);
                }}
            >
                <div className="ui-stack" style={{ gap: '16px' }}>
                    <div>
                        <label style={{ display: 'block', fontWeight: 500, fontSize: '0.875rem', marginBottom: '6px' }}>
                            Mức giá bạn chào (VNĐ) <span style={{ color: 'red' }}>*</span>
                        </label>
                        <input
                            type="number"
                            className="ui-input"
                            style={{
                                width: '100%',
                                padding: '10px 12px',
                                borderRadius: '8px',
                                border: '1px solid var(--border-color, #d1d5db)',
                                fontSize: '1.1rem',
                                fontWeight: 600,
                            }}
                            min={minPriceAllowed}
                            max={maxPriceAllowed}
                            step={1000}
                            value={offerPrice}
                            onChange={e => setOfferPrice(e.target.value)}
                            placeholder={`Ví dụ: ${suggestedPrice}`}
                        />
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                            Quy tắc BR-R-029: Mức giá hợp lệ từ 70% ({numberFormat.format(minPriceAllowed)} đ) đến 150% ({numberFormat.format(maxPriceAllowed)} đ).
                        </div>
                    </div>

                    <div>
                        <label style={{ display: 'block', fontWeight: 500, fontSize: '0.875rem', marginBottom: '6px' }}>
                            Thời gian bạn có thể đến lấy hàng dự kiến
                        </label>
                        <select
                            className="ui-input"
                            style={{
                                width: '100%',
                                padding: '10px 12px',
                                borderRadius: '8px',
                                border: '1px solid var(--border-color, #d1d5db)',
                                fontSize: '0.9rem',
                            }}
                            value={offerEtaMinutes}
                            onChange={e => setOfferEtaMinutes(e.target.value)}
                        >
                            <option value="15">Trong vòng 15 phút</option>
                            <option value="30">Trong vòng 30 phút</option>
                            <option value="45">Trong vòng 45 phút</option>
                            <option value="60">Trong vòng 1 giờ</option>
                            <option value="120">Trong vòng 2 giờ</option>
                        </select>
                    </div>

                    <div>
                        <label style={{ display: 'block', fontWeight: 500, fontSize: '0.875rem', marginBottom: '6px' }}>
                            Lời nhắn gửi người gửi (tùy chọn)
                        </label>
                        <textarea
                            className="ui-input"
                            style={{
                                width: '100%',
                                padding: '10px 12px',
                                borderRadius: '8px',
                                border: '1px solid var(--border-color, #d1d5db)',
                                fontSize: '0.875rem',
                                minHeight: '80px',
                                resize: 'vertical',
                            }}
                            value={offerMessage}
                            onChange={e => setOfferMessage(e.target.value)}
                            placeholder="Ví dụ: Em đang ở gần khu vực này, có thể qua lấy ngay được ạ..."
                            maxLength={500}
                        />
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '12px' }}>
                        <Button
                            variant="secondary"
                            onClick={() => setIsOfferModalOpen(false)}
                            disabled={submittingOffer}
                        >
                            Hủy bỏ
                        </Button>
                        <Button
                            variant="primary"
                            icon={<Send size={16} />}
                            onClick={handleSubmitOffer}
                            disabled={submittingOffer}
                        >
                            {submittingOffer ? 'Đang gửi...' : 'Gửi lời chào giá'}
                        </Button>
                    </div>
                </div>
            </Modal>

            {/* Modal: Phản giá lại cho người gửi (UC-19) */}
            <Modal
                isOpen={isCounterModalOpen}
                title="Phản đề xuất mức giá mới"
                onClose={() => setIsCounterModalOpen(false)}
            >
                <div className="ui-stack" style={{ gap: '16px' }}>
                    <div>
                        <label style={{ display: 'block', fontWeight: 500, fontSize: '0.875rem', marginBottom: '6px' }}>
                            Mức giá phản đề xuất (VNĐ) <span style={{ color: 'red' }}>*</span>
                        </label>
                        <input
                            type="number"
                            className="ui-input"
                            style={{
                                width: '100%',
                                padding: '10px 12px',
                                borderRadius: '8px',
                                border: '1px solid var(--border-color, #d1d5db)',
                                fontSize: '1rem',
                            }}
                            min={minPriceAllowed}
                            max={maxPriceAllowed}
                            step={1000}
                            value={counterPrice}
                            onChange={e => setCounterPrice(e.target.value)}
                        />
                    </div>

                    <div>
                        <label style={{ display: 'block', fontWeight: 500, fontSize: '0.875rem', marginBottom: '6px' }}>
                            Lời nhắn
                        </label>
                        <textarea
                            className="ui-input"
                            style={{
                                width: '100%',
                                padding: '10px 12px',
                                borderRadius: '8px',
                                border: '1px solid var(--border-color, #d1d5db)',
                                fontSize: '0.875rem',
                                minHeight: '80px',
                            }}
                            value={counterMessage}
                            onChange={e => setCounterMessage(e.target.value)}
                            maxLength={500}
                        />
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '12px' }}>
                        <Button variant="secondary" onClick={() => setIsCounterModalOpen(false)}>
                            Đóng
                        </Button>
                        <Button
                            variant="primary"
                            onClick={() => {
                                if (activeOffer) handleCounterOffer(activeOffer.id);
                            }}
                            disabled={submittingOffer}
                        >
                            {submittingOffer ? 'Đang gửi...' : 'Gửi phản giá'}
                        </Button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
