import React from 'react';
import { Clock, Package, Weight, MapPin, ArrowRight, CheckCircle2, ShieldCheck } from 'lucide-react';
import type { MarketPlaceOrder } from '../../types/order';

interface MarketplaceOrderCardProps {
    order: MarketPlaceOrder;
    onViewDetail?: (orderId: number, orderCode: string) => void;
    onAcceptOrder?: (orderId: number, orderCode: string) => void;
    isAccepting?: boolean;
}

const numberFormat = new Intl.NumberFormat('vi-VN');

export default function MarketplaceOrderCard({
    order,
    onViewDetail,
    onAcceptOrder,
    isAccepting = false,
}: MarketplaceOrderCardProps): React.ReactElement {
    const isHighValue = order.declaredValue > 1000000;

    const handleDetailClick = () => {
        if (onViewDetail) {
            onViewDetail(order.id, order.orderCode);
        }
    };

    const handleAcceptClick = () => {
        if (onAcceptOrder) {
            onAcceptOrder(order.id, order.orderCode);
        }
    };

    return (
        <article className="order-card">
            <div className="order-card-top">
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                    <span className="order-badge">{order.orderCode}</span>
                    {isHighValue && (
                        <span
                            className="order-badge"
                            style={{
                                backgroundColor: 'var(--amber-50, #fffbeb)',
                                color: 'var(--amber-700, #b45309)',
                                borderColor: 'var(--amber-300, #fcd34d)',
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '4px',
                            }}
                            title="Đơn hàng giá trị cao (> 1.000.000đ) - Yêu cầu tài xế hạng Bạc trở lên"
                        >
                            <ShieldCheck size={12} />
                            Hàng giá trị cao
                        </span>
                    )}
                </div>
                <span className="order-time">
                    <Clock size={13} aria-hidden="true" />
                    {order.createdAt ? new Date(order.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) : 'Vừa đăng'}
                </span>
            </div>

            <div className="order-route">
                <div className="route-point">
                    <span className="route-bullet pickup" />
                    <div>
                        <dt>Điểm lấy hàng</dt>
                        <dd>{order.pickupAddress}</dd>
                    </div>
                </div>

                <div className="route-divider" />

                <div className="route-point">
                    <span className="route-bullet dropoff" />
                    <div>
                        <dt>Điểm giao hàng</dt>
                        <dd>{order.deliveryAddress}</dd>
                    </div>
                </div>
            </div>

            <div className="order-details-pills">
                <span className="detail-pill">
                    <Package size={14} aria-hidden="true" />
                    {order.productName} ({order.quantity})
                </span>
                <span className="detail-pill">
                    <Weight size={14} aria-hidden="true" />
                    {order.weightKg} kg
                </span>
                {order.estimatedDistanceKm !== undefined && (
                    <span className="detail-pill">
                        <MapPin size={14} aria-hidden="true" />
                        ~{order.estimatedDistanceKm} km
                    </span>
                )}
            </div>

            <div className="order-card-footer">
                <div className="order-value-box">
                    <span className="order-value-label">Giá trị khai báo</span>
                    <span className="order-value-amount">
                        {numberFormat.format(order.declaredValue)} <small>đ</small>
                    </span>
                </div>
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                    <button
                        type="button"
                        className="ui-button ui-button-secondary"
                        onClick={handleDetailClick}
                        style={{ padding: '8px 12px', fontSize: '0.85rem' }}
                    >
                        Chi tiết
                        <ArrowRight size={14} aria-hidden="true" />
                    </button>
                    {onAcceptOrder && (
                        <button
                            type="button"
                            className="ui-button ui-button-primary receive-btn"
                            onClick={handleAcceptClick}
                            disabled={isAccepting}
                            style={{ padding: '8px 14px', fontSize: '0.85rem' }}
                        >
                            <CheckCircle2 size={15} aria-hidden="true" />
                            {isAccepting ? 'Đang nhận...' : 'Nhận đơn'}
                        </button>
                    )}
                </div>
            </div>
        </article>
    );
}