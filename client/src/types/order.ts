export type OrderStatus =
    | 'PENDING'
    | 'FINDING_SHIPPER'
    | 'AWAITING_PAYMENT'
    | 'SHIPPER_ASSIGNED'
    | 'IN_DELIVERY'
    | 'COMPLETED'
    | 'CANCELLED';

export interface OrderStatusConfig {
    label: string;
    tone: 'neutral' | 'success' | 'warning' | 'danger' | 'info';
}

export const ORDER_STATUS_MAP: Record<OrderStatus, OrderStatusConfig> = {
    PENDING: { label: 'Chờ xử lý', tone: 'warning' },
    FINDING_SHIPPER: { label: 'Đang tìm tài xế', tone: 'info' },
    AWAITING_PAYMENT: { label: 'Chờ ký quỹ', tone: 'warning' },
    SHIPPER_ASSIGNED: { label: 'Đã có tài xế', tone: 'info' },
    IN_DELIVERY: { label: 'Đang giao hàng', tone: 'warning' },
    COMPLETED: { label: 'Giao thành công', tone: 'success' },
    CANCELLED: { label: 'Đã hủy', tone: 'danger' },
};



export interface MarketPlaceOrder {
    id: number;
    orderCode: string;
    pickupAddress: string;
    deliveryAddress: string;
    productName: string;
    quantity: number;
    weightKg: number;
    declaredValue: number;
    status: OrderStatus;
    createdAt: string;
    estimatedDistanceKm?: number;
    priceSuggested?: number;
    priceAgreed?: number;
}

// Alias for flexibility
export type MarketplaceOrder = MarketPlaceOrder;

export interface SpringPageResponse<T> {
    content: T[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
    first: boolean;
    last: boolean;
    empty: boolean;
}

export interface MarketplaceOrderParams {
    page?: number;
    size?: number;
    sort?: string;
    keyword?: string;
}

export interface MarketplaceOrderDetail {
    id: number;
    orderCode: string;
    senderId: number;
    pickupAddress: string;
    deliveryAddress: string;
    maskedReceiverName: string;
    maskedReceiverPhone: string;
    productName: string;
    productDescription?: string | null;
    quantity: number;
    weightKg: number;
    declaredValue?: number | null;
    priceSuggested?: number | null;
    distanceKm?: number | null;
    deliveryNote?: string | null;
    status: OrderStatus;
    createdAt: string;
    imageUrls?: string[];
    requiresSilverTier: boolean;
    isAvailable: boolean;
}

export interface MarketOrderAcceptResponse {
    id: number;
    orderCode: string;
    senderId: number;
    courierId: number;
    pickupAddress: string;
    deliveryAddress: string;
    productName: string;
    quantity: number;
    weightKg: number;
    declaredValue?: number | null;
    priceAgreed: number;
    status: OrderStatus;
    acceptedAt: string;
    message: string;
}

export interface Order {
    id: number;
    orderCode: string;
    senderId: number;
    pickupAddress: string;
    deliveryAddress: string;
    receiverName: string;
    receiverPhone: string;
    productName: string;
    productDescription?: string | null;
    quantity: number;
    weightKg: number;
    declaredValue?: number | null;
    priceSuggested?: number | null;
    priceAgreed?: number | null;
    distanceKm?: number | null;
    deliveryNote?: string | null;
    status: OrderStatus;
    createdAt: string;
    updatedAt?: string | null;
    imageUrls?: string[] | null;
}

export interface OrderRequest {
    senderId: number;
    pickupAddress: string;
    deliveryAddress: string;
    receiverName: string;
    receiverPhone: string;
    productName: string;
    productDescription?: string;
    quantity: number;
    weightKg: number;
    declaredValue?: number;
    deliveryNote?: string;
    imageUrls?: string[];
}