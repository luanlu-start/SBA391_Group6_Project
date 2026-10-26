import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    ArrowLeft,
    CheckCircle2,
    XCircle,
    DollarSign,
    Clock,
    ShieldCheck,
    Award,
    RotateCcw,
    TrendingDown,
    TrendingUp,
    Package,
    MapPin,
    Send,
} from 'lucide-react';
import { offerApi } from '../api/offerApi';
import { orderApi } from '../api/orderApi';
import type { Offer, OfferAcceptResponse } from '../types/offer';
import { OFFER_STATUS_MAP } from '../types/offer';
import type { Order } from '../types/order';
import { ORDER_STATUS_MAP } from '../types/order';
import { getApiError, type ApiError } from '../api/ApiError';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Badge from '../components/ui/Badge';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ApiErrorNotice from '../components/ApiErrorNotice';
import Modal from '../components/ui/Modal';

const numberFormat = new Intl.NumberFormat('vi-VN');

export default function OrderOffersPage(): React.ReactElement {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const [order, setOrder] = useState<Order | null>(null);
    const [offers, setOffers] = useState<Offer[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [actionLoading, setActionLoading] = useState<boolean>(false);
    const [error, setError] = useState<ApiError | null>(null);
    const [actionError, setActionError] = useState<ApiError | null>(null);
    const [acceptResult, setAcceptResult] = useState<OfferAcceptResponse | null>(null);

    // Modal state for Accept
    const [selectedOfferForAccept, setSelectedOfferForAccept] = useState<Offer | null>(null);

    // Modal state for Reject
    const [selectedOfferForReject, setSelectedOfferForReject] = useState<Offer | null>(null);

    // Modal state for Counter (Phản giá)
    const [selectedOfferForCounter, setSelectedOfferForCounter] = useState<Offer | null>(null);
    const [counterPrice, setCounterPrice] = useState<string>('');
    const [counterMessage, setCounterMessage] = useState<string>('');

    const loadData = useCallback(async () => {
        if (!id) return;
        setLoading(true);
        setError(null);
        try {
            const [orderRes, offersRes] = await Promise.all([
                orderApi.getById(id),
                offerApi.getOffersForOrder(id),
            ]);
            setOrder(orderRes.data);
            setOffers(offersRes.data || []);
        } catch (err) {
            setError(getApiError(err));
        } finally {
            setLoading(false);
        }
    }, [id]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    // Handle Accept Offer
    const handleConfirmAccept = async () => {
        if (!selectedOfferForAccept) return;
        setActionLoading(true);
        setActionError(null);
        try {
            const res = await offerApi.acceptOffer(selectedOfferForAccept.id);
            setAcceptResult(res.data);
            setSelectedOfferForAccept(null);
            await loadData();
        } catch (err) {
            setActionError(getApiError(err));
        } finally {
            setActionLoading(false);
        }
    };

    // Handle Reject Offer
    const handleConfirmReject = async () => {
        if (!selectedOfferForReject) return;
        setActionLoading(true);
        setActionError(null);
        try {
            await offerApi.rejectOffer(selectedOfferForReject.id);
            setSelectedOfferForReject(null);
            await loadData();
        } catch (err) {
            setActionError(getApiError(err));
        } finally {
            setActionLoading(false);
        }
    };

    // Handle Counter Offer
    const handleConfirmCounter = async () => {
        if (!selectedOfferForCounter) return;
        const priceNum = Number(counterPrice.replace(/\D/g, ''));
        if (!priceNum || priceNum < 1000) {
            alert('Vui lòng nhập mức giá đề xuất hợp lệ (tối thiểu 1.000đ)');
            return;
        }

        setActionLoading(true);
        setActionError(null);
        try {
            await offerApi.counterOffer(selectedOfferForCounter.id, {
                price: priceNum,
                message: counterMessage.trim() || undefined,
            });
            setSelectedOfferForCounter(null);
            setCounterPrice('');
            setCounterMessage('');
            await loadData();
        } catch (err) {
            setActionError(getApiError(err));
        } finally {
            setActionLoading(false);
        }
    };

    if (loading) {
        return <LoadingSpinner text="Đang tải danh sách chào giá cho đơn hàng…" />;
    }

    if (error || !order) {
        return (
            <div className="ui-stack">
                <PageHeader
                    title="Không thể tải danh sách chào giá"
                    eyebrow="Quản lý chào giá"
                    actions={
                        <Button
                            variant="secondary"
                            icon={<ArrowLeft size={16} />}
                            onClick={() => navigate('/sender/orders')}
                        >
                            Quay lại danh sách đơn
                        </Button>
                    }
                />
                <SectionCard>
                    {error ? <ApiErrorNotice error={error} /> : <p>Đơn hàng không tồn tại.</p>}
                </SectionCard>
            </div>
        );
    }

    const orderStatusConfig = ORDER_STATUS_MAP[order.status] ?? {
        label: order.status,
        tone: 'neutral',
    };

    const suggestedPrice = order.priceSuggested || 30000;
    const minPriceAllowed = Math.round(suggestedPrice * 0.7);
    const maxPriceAllowed = Math.round(suggestedPrice * 1.5);

    const isOrderOpen = order.status === 'PENDING' || order.status === 'FINDING_SHIPPER';

    return (
        <div className="ui-stack" style={{ gap: '24px' }}>
            <PageHeader
                title={`Chào giá đơn hàng #${order.orderCode}`}
                eyebrow="SCR-21 / UC-19: Thương lượng & Chốt bưu tá"
                description={`So sánh các đề xuất giá và bưu tá phù hợp để giao kiện hàng: ${order.productName}`}
                actions={
                    <div className="ui-row" style={{ gap: '12px' }}>
                        <Badge dot tone={orderStatusConfig.tone}>
                            {orderStatusConfig.label}
                        </Badge>
                        <Button
                            variant="ghost"
                            icon={<ArrowLeft size={16} />}
                            onClick={() => navigate(`/sender/orders/${order.id}`)}
                        >
                            Xem chi tiết đơn
                        </Button>
                    </div>
                }
            />

            {actionError && <ApiErrorNotice error={actionError} />}

            {/* Banner Thành công khi đã Chấp nhận Chào giá */}
            {acceptResult && (
                <div
                    style={{
                        padding: '16px 20px',
                        borderRadius: '12px',
                        backgroundColor: 'var(--accent-emerald-subtle, #ecfdf5)',
                        border: '1px solid var(--accent-emerald, #10b981)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: '16px',
                        flexWrap: 'wrap',
                    }}
                >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <CheckCircle2 size={24} style={{ color: 'var(--accent-emerald, #10b981)' }} />
                        <div>
                            <div style={{ fontWeight: 600, color: '#065f46' }}>
                                {acceptResult.message}
                            </div>
                            <div style={{ fontSize: '0.875rem', color: '#047857' }}>
                                Bưu tá: <strong>{acceptResult.courierName}</strong> • Giá thống nhất:{' '}
                                <strong>{numberFormat.format(acceptResult.priceAgreed)} đ</strong>
                            </div>
                        </div>
                    </div>
                    <Button
                        variant="primary"
                        onClick={() => navigate(`/sender/orders/${order.id}`)}
                    >
                        Đến trang chi tiết & Ký quỹ
                    </Button>
                </div>
            )}

            {/* Order Overview Card */}
            <SectionCard title="Tóm tắt thông tin đơn hàng">
                <div
                    style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
                        gap: '16px',
                    }}
                >
                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                        <MapPin size={18} style={{ color: 'var(--primary)', flexShrink: 0, marginTop: '2px' }} />
                        <div>
                            <div style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>Điểm lấy hàng</div>
                            <div style={{ fontSize: '0.9375rem', fontWeight: 500 }}>{order.pickupAddress}</div>
                        </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                        <MapPin size={18} style={{ color: 'var(--accent-emerald, #10b981)', flexShrink: 0, marginTop: '2px' }} />
                        <div>
                            <div style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>Điểm giao hàng</div>
                            <div style={{ fontSize: '0.9375rem', fontWeight: 500 }}>{order.deliveryAddress}</div>
                        </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                        <Package size={18} style={{ color: 'var(--primary)', flexShrink: 0, marginTop: '2px' }} />
                        <div>
                            <div style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>Kiện hàng & Khối lượng</div>
                            <div style={{ fontSize: '0.9375rem', fontWeight: 500 }}>
                                {order.productName} ({order.weightKg} kg)
                            </div>
                        </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                        <DollarSign size={18} style={{ color: 'var(--accent-emerald, #10b981)', flexShrink: 0, marginTop: '2px' }} />
                        <div>
                            <div style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>Giá gợi ý hệ thống</div>
                            <div style={{ fontSize: '1.125rem', fontWeight: 700, color: 'var(--accent-emerald, #10b981)' }}>
                                {numberFormat.format(suggestedPrice)} đ
                            </div>
                            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                                Biên cho phép (70% - 150%): {numberFormat.format(minPriceAllowed)} đ - {numberFormat.format(maxPriceAllowed)} đ
                            </div>
                        </div>
                    </div>
                </div>
            </SectionCard>

            {/* Offers List */}
            <SectionCard
                title={`Danh sách các đề xuất chào giá (${offers.length})`}
                description="So sánh giá chào, điểm uy tín và đánh giá bưu tá để chọn người giao phù hợp nhất"
            >
                {offers.length === 0 ? (
                    <div
                        style={{
                            textAlign: 'center',
                            padding: '48px 16px',
                            color: 'var(--text-muted)',
                        }}
                    >
                        <Clock size={40} style={{ margin: '0 auto 12px', opacity: 0.5 }} />
                        <h3 style={{ fontSize: '1.125rem', fontWeight: 600, color: 'var(--text-main)', marginBottom: '6px' }}>
                            Chưa có chào giá nào cho đơn này
                        </h3>
                        <p style={{ maxWidth: '420px', margin: '0 auto', fontSize: '0.875rem' }}>
                            Các bưu tá trên sàn đang xem xét kiện hàng của bạn. Khi có shipper gửi mức giá hoặc thời gian lấy dự kiến, danh sách sẽ hiển thị tại đây.
                        </p>
                    </div>
                ) : (
                    <div className="ui-stack" style={{ gap: '16px' }}>
                        {offers.map(offer => {
                            const statusConfig = OFFER_STATUS_MAP[offer.status] ?? {
                                label: offer.status,
                                tone: 'neutral',
                            };

                            const diffPercent = offer.priceDifferencePercentage;
                            const isLower = diffPercent !== undefined && diffPercent < 0;
                            const isHigher = diffPercent !== undefined && diffPercent > 0;

                            const canAct =
                                isOrderOpen &&
                                (offer.status === 'PENDING' ||
                                    (offer.status === 'COUNTERED' && !offer.createdBySender));

                            return (
                                <div
                                    key={offer.id}
                                    style={{
                                        border: offer.status === 'ACCEPTED'
                                            ? '2px solid var(--accent-emerald, #10b981)'
                                            : '1px solid var(--border-color, #e5e7eb)',
                                        borderRadius: '12px',
                                        padding: '20px',
                                        backgroundColor: offer.status === 'ACCEPTED'
                                            ? 'var(--accent-emerald-subtle, #f0fdf4)'
                                            : 'var(--surface, #ffffff)',
                                        display: 'flex',
                                        flexDirection: 'column',
                                        gap: '16px',
                                    }}
                                >
                                    {/* Top Row: Courier Info & Status */}
                                    <div
                                        style={{
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            alignItems: 'flex-start',
                                            flexWrap: 'wrap',
                                            gap: '12px',
                                        }}
                                    >
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                            <div
                                                style={{
                                                    width: '44px',
                                                    height: '44px',
                                                    borderRadius: '50%',
                                                    backgroundColor: 'var(--primary-subtle, #eff6ff)',
                                                    color: 'var(--primary, #2563eb)',
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    justifyContent: 'center',
                                                    fontWeight: 600,
                                                    fontSize: '1.125rem',
                                                }}
                                            >
                                                {offer.courierName ? offer.courierName[0].toUpperCase() : 'B'}
                                            </div>
                                            <div>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                    <span style={{ fontWeight: 600, fontSize: '1rem' }}>
                                                        {offer.courierName}
                                                    </span>
                                                    {offer.fptBadge && (
                                                        <Badge tone="info">
                                                            <ShieldCheck size={12} style={{ marginRight: '4px' }} />
                                                            Cộng đồng FPT
                                                        </Badge>
                                                    )}
                                                </div>
                                                <div
                                                    style={{
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        gap: '12px',
                                                        fontSize: '0.8125rem',
                                                        color: 'var(--text-muted)',
                                                        marginTop: '4px',
                                                    }}
                                                >
                                                    <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                                                        <Award size={14} style={{ color: 'var(--primary)' }} />
                                                        Hạng: <strong>{offer.courierTierName}</strong>
                                                    </span>
                                                    <span>•</span>
                                                    <span>
                                                        Điểm uy tín: <strong>{offer.courierTrustScore}</strong>/100
                                                    </span>
                                                    <span>•</span>
                                                    <span>
                                                        Đã hoàn thành: <strong>{offer.courierCompletedOrders}</strong> đơn
                                                    </span>
                                                </div>
                                            </div>
                                        </div>

                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                            <Badge dot tone={statusConfig.tone}>
                                                {statusConfig.label}
                                            </Badge>
                                            {offer.roundNo > 1 && (
                                                <Badge tone="neutral">
                                                    Vòng thương lượng: {offer.roundNo}/3
                                                </Badge>
                                            )}
                                        </div>
                                    </div>

                                    {/* Middle: Price & Details */}
                                    <div
                                        style={{
                                            display: 'grid',
                                            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                                            gap: '12px',
                                            padding: '14px 16px',
                                            borderRadius: '8px',
                                            backgroundColor: 'var(--background-secondary, #f9fafb)',
                                        }}
                                    >
                                        <div>
                                            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                                                Mức giá chào
                                            </div>
                                            <div
                                                style={{
                                                    fontSize: '1.25rem',
                                                    fontWeight: 700,
                                                    color: 'var(--text-main)',
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    gap: '8px',
                                                }}
                                            >
                                                <span>{numberFormat.format(offer.price)} đ</span>
                                                {diffPercent !== undefined && (
                                                    <span
                                                        style={{
                                                            fontSize: '0.75rem',
                                                            fontWeight: 600,
                                                            padding: '2px 6px',
                                                            borderRadius: '4px',
                                                            backgroundColor: isLower
                                                                ? 'var(--accent-emerald-subtle, #ecfdf5)'
                                                                : isHigher
                                                                ? 'var(--warning-subtle, #fffbeb)'
                                                                : '#f3f4f6',
                                                            color: isLower
                                                                ? '#059669'
                                                                : isHigher
                                                                ? '#d97706'
                                                                : '#6b7280',
                                                            display: 'inline-flex',
                                                            alignItems: 'center',
                                                            gap: '2px',
                                                        }}
                                                    >
                                                        {isLower && <TrendingDown size={12} />}
                                                        {isHigher && <TrendingUp size={12} />}
                                                        {diffPercent > 0 ? `+${diffPercent.toFixed(1)}%` : `${diffPercent.toFixed(1)}%`}
                                                    </span>
                                                )}
                                            </div>
                                        </div>

                                        {offer.etaPickup && (
                                            <div>
                                                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                                                    Dự kiến lấy hàng
                                                </div>
                                                <div style={{ fontSize: '0.9375rem', fontWeight: 500, marginTop: '2px' }}>
                                                    <Clock size={14} style={{ display: 'inline', marginRight: '4px', verticalAlign: 'text-bottom' }} />
                                                    {new Date(offer.etaPickup).toLocaleTimeString('vi-VN', {
                                                        hour: '2-digit',
                                                        minute: '2-digit',
                                                        day: '2-digit',
                                                        month: '2-digit',
                                                    })}
                                                </div>
                                            </div>
                                        )}

                                        <div>
                                            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                                                Thời hạn hiệu lực
                                            </div>
                                            <div style={{ fontSize: '0.9375rem', fontWeight: 500, marginTop: '2px' }}>
                                                {new Date(offer.expiresAt).toLocaleTimeString('vi-VN', {
                                                    hour: '2-digit',
                                                    minute: '2-digit',
                                                    day: '2-digit',
                                                    month: '2-digit',
                                                })}
                                            </div>
                                        </div>
                                    </div>

                                    {/* Message */}
                                    {offer.message && (
                                        <div
                                            style={{
                                                fontSize: '0.875rem',
                                                color: 'var(--text-secondary, #4b5563)',
                                                fontStyle: 'italic',
                                                padding: '8px 12px',
                                                borderLeft: '3px solid var(--primary)',
                                                backgroundColor: 'var(--background-secondary, #f9fafb)',
                                                borderRadius: '0 6px 6px 0',
                                            }}
                                        >
                                            &ldquo;{offer.message}&rdquo;
                                        </div>
                                    )}

                                    {/* Action Buttons */}
                                    {canAct && (
                                        <div
                                            style={{
                                                display: 'flex',
                                                justifyContent: 'flex-end',
                                                gap: '10px',
                                                borderTop: '1px solid var(--border-color, #e5e7eb)',
                                                paddingTop: '14px',
                                                flexWrap: 'wrap',
                                            }}
                                        >
                                            <Button
                                                variant="secondary"
                                                size="sm"
                                                icon={<XCircle size={15} />}
                                                onClick={() => setSelectedOfferForReject(offer)}
                                                disabled={actionLoading}
                                            >
                                                Từ chối
                                            </Button>

                                            {offer.roundNo < 3 && (
                                                <Button
                                                    variant="secondary"
                                                    size="sm"
                                                    icon={<RotateCcw size={15} />}
                                                    onClick={() => {
                                                        setSelectedOfferForCounter(offer);
                                                        setCounterPrice(String(suggestedPrice));
                                                        setCounterMessage('');
                                                    }}
                                                    disabled={actionLoading}
                                                >
                                                    Phản giá (Vòng {offer.roundNo + 1}/3)
                                                </Button>
                                            )}

                                            <Button
                                                variant="primary"
                                                size="sm"
                                                icon={<CheckCircle2 size={15} />}
                                                onClick={() => setSelectedOfferForAccept(offer)}
                                                disabled={actionLoading}
                                            >
                                                Chấp nhận chào giá
                                            </Button>
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                )}
            </SectionCard>

            {/* Modal: Xác nhận Chấp nhận Chào giá */}
            <Modal
                isOpen={Boolean(selectedOfferForAccept)}
                onClose={() => setSelectedOfferForAccept(null)}
                title="Xác nhận chọn bưu tá giao hàng"
                description={`Chốt bưu tá ${selectedOfferForAccept?.courierName} với giá ${selectedOfferForAccept ? numberFormat.format(selectedOfferForAccept.price) : 0} đ`}
            >
                <div className="ui-stack" style={{ gap: '16px' }}>
                    <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                        Khi bạn chấp nhận chào giá này:
                    </p>
                    <ul style={{ fontSize: '0.875rem', paddingLeft: '20px', margin: 0, lineHeight: 1.6 }}>
                        <li>Đơn hàng sẽ chuyển sang trạng thái <strong>Chờ ký quỹ</strong>.</li>
                        <li>Tất cả các chào giá từ bưu tá khác sẽ tự động bị từ chối.</li>
                        <li>Bạn sẽ có <strong>15 phút</strong> để hoàn tất thanh toán tiền ký quỹ (giữ chỗ).</li>
                    </ul>

                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
                        <Button
                            variant="secondary"
                            onClick={() => setSelectedOfferForAccept(null)}
                            disabled={actionLoading}
                        >
                            Hủy bỏ
                        </Button>
                        <Button
                            variant="primary"
                            icon={<CheckCircle2 size={16} />}
                            onClick={handleConfirmAccept}
                            disabled={actionLoading}
                        >
                            {actionLoading ? 'Đang xử lý…' : 'Xác nhận chấp nhận'}
                        </Button>
                    </div>
                </div>
            </Modal>

            {/* Modal: Xác nhận Từ chối Chào giá */}
            <Modal
                isOpen={Boolean(selectedOfferForReject)}
                onClose={() => setSelectedOfferForReject(null)}
                title="Từ chối chào giá này?"
                description={`Bưu tá ${selectedOfferForReject?.courierName} sẽ nhận được thông báo từ chối mức giá ${selectedOfferForReject ? numberFormat.format(selectedOfferForReject.price) : 0} đ.`}
            >
                <div className="ui-stack" style={{ gap: '16px' }}>
                    <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                        Bạn có chắc chắn muốn từ chối đề xuất này không? Thao tác này không thể hoàn tác.
                    </p>

                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
                        <Button
                            variant="secondary"
                            onClick={() => setSelectedOfferForReject(null)}
                            disabled={actionLoading}
                        >
                            Đóng
                        </Button>
                        <Button
                            variant="danger"
                            icon={<XCircle size={16} />}
                            onClick={handleConfirmReject}
                            disabled={actionLoading}
                        >
                            {actionLoading ? 'Đang từ chối…' : 'Xác nhận từ chối'}
                        </Button>
                    </div>
                </div>
            </Modal>

            {/* Modal: Phản giá (Thương lượng) */}
            <Modal
                isOpen={Boolean(selectedOfferForCounter)}
                onClose={() => setSelectedOfferForCounter(null)}
                title={`Thương lượng giá với ${selectedOfferForCounter?.courierName}`}
                description={`Vòng phản giá ${selectedOfferForCounter ? selectedOfferForCounter.roundNo + 1 : 1}/3 (tối đa 3 vòng)`}
            >
                <div className="ui-stack" style={{ gap: '16px' }}>
                    <div>
                        <label style={{ display: 'block', fontWeight: 500, fontSize: '0.875rem', marginBottom: '6px' }}>
                            Mức giá bạn đề xuất (VNĐ) <span style={{ color: 'red' }}>*</span>
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
                            placeholder={`Khoảng cho phép: ${minPriceAllowed} - ${maxPriceAllowed}`}
                        />
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                            Quy tắc BR-R-029: Mức giá phải nằm trong khoảng từ 70% ({numberFormat.format(minPriceAllowed)} đ) đến 150% ({numberFormat.format(maxPriceAllowed)} đ) của giá gợi ý.
                        </div>
                    </div>

                    <div>
                        <label style={{ display: 'block', fontWeight: 500, fontSize: '0.875rem', marginBottom: '6px' }}>
                            Lời nhắn thương lượng (tùy chọn)
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
                            value={counterMessage}
                            onChange={e => setCounterMessage(e.target.value)}
                            placeholder="Ví dụ: Mình gửi bạn mức giá này nhé, vì gói hàng khá nhẹ..."
                            maxLength={500}
                        />
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
                        <Button
                            variant="secondary"
                            onClick={() => setSelectedOfferForCounter(null)}
                            disabled={actionLoading}
                        >
                            Hủy
                        </Button>
                        <Button
                            variant="primary"
                            icon={<Send size={16} />}
                            onClick={handleConfirmCounter}
                            disabled={actionLoading}
                        >
                            {actionLoading ? 'Đang gửi…' : 'Gửi đề xuất phản giá'}
                        </Button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
