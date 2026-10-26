export type OfferStatus =
    | 'PENDING'
    | 'ACCEPTED'
    | 'REJECTED'
    | 'COUNTERED'
    | 'EXPIRED'
    | 'WITHDRAWN';

export interface OfferStatusConfig {
    label: string;
    tone: 'neutral' | 'success' | 'warning' | 'danger' | 'info';
}

export const OFFER_STATUS_MAP: Record<OfferStatus, OfferStatusConfig> = {
    PENDING: { label: 'Chờ phản hồi', tone: 'info' },
    ACCEPTED: { label: 'Đã chấp nhận', tone: 'success' },
    REJECTED: { label: 'Bị từ chối', tone: 'danger' },
    COUNTERED: { label: 'Đã phản giá', tone: 'warning' },
    EXPIRED: { label: 'Hết hạn', tone: 'neutral' },
    WITHDRAWN: { label: 'Đã rút', tone: 'neutral' },
};

export interface Offer {
    id: number;
    orderId: number;
    orderCode: string;
    courierId: number;
    courierName: string;
    courierAvatarUrl?: string;
    courierPhone?: string;
    courierTier: 'NEW' | 'BRONZE' | 'SILVER' | 'GOLD' | 'DIAMOND';
    courierTierName: string;
    courierTrustScore: number;
    courierCompletedOrders: number;
    fptBadge: boolean;
    price: number;
    priceSuggested: number;
    priceDifferencePercentage?: number;
    message?: string;
    etaPickup?: string;
    status: OfferStatus;
    parentOfferId?: number;
    roundNo: number;
    createdBySender: boolean;
    expiresAt: string;
    expired: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface OfferCreateRequest {
    price: number;
    message?: string;
    etaPickup?: string;
}

export interface OfferCounterRequest {
    price: number;
    message?: string;
}

export interface OfferAcceptResponse {
    offerId: number;
    orderId: number;
    orderCode: string;
    courierId: number;
    courierName: string;
    priceAgreed: number;
    orderStatus: string;
    holdExpiresAt: string;
    message: string;
}
