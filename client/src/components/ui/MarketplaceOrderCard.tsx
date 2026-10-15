import React from 'react';
import { Clock, Package, Weight, MapPin, ArrowRight } from 'lucide-react';
import type {MarketplaceOrder} from '../../pages/CourierMarketplacePage';

interface MarketplaceOrderCardProps {
    order: MarketplaceOrder;
    onViewDetail?: (orderCode: string) => void;
}

const numberFormat = new Intl.NumberFormat('vi-VN');

export default function MarketplaceOrderCard({
                                                 order,
                                                 onViewDetail,
                                             }: MarketplaceOrderCardProps): React.ReactElement {
    const handleDetailClick = () => {
        if (onViewDetail) {
            onViewDetail(order.orderCode);
        } else {
            alert(`Xem chi tiết đơn ${order.orderCode}`);
        }
    };

    return (
        <article className="order-card">
            <div className="order-card-top">
                <span className="order-badge">{order.orderCode}</span>
                <span className="order-time">
          <Clock size={13} aria-hidden="true" />
          Vừa đăng
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
                <button
                    type="button"
                    className="ui-button ui-button-primary receive-btn"
                    onClick={handleDetailClick}
                >
                    Xem chi tiết
                    <ArrowRight size={16} aria-hidden="true" />
                </button>
            </div>
        </article>
    );
}