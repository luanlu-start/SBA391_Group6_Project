export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED';

export interface InvitationStatusConfig {
    label: string;
    tone: 'info' | 'success' | 'danger' | 'warning' | 'neutral';
    description: string;
}

export const INVITATION_STATUS_MAP: Record<InvitationStatus, InvitationStatusConfig> = {
    PENDING: {
        label: 'Chờ phản hồi',
        tone: 'warning',
        description: 'Đang chờ tài xế tiếp nhận lời mời',
    },
    ACCEPTED: {
        label: 'Đã chấp nhận',
        tone: 'success',
        description: 'Tài xế đã đồng ý nhận đơn và đang chờ ký quỹ',
    },
    REJECTED: {
        label: 'Đã từ chối',
        tone: 'danger',
        description: 'Tài xế đã từ chối nhận đơn',
    },
    EXPIRED: {
        label: 'Hết hạn',
        tone: 'neutral',
        description: 'Lời mời đã quá thời hạn phản hồi',
    },
    CANCELLED: {
        label: 'Đã hủy',
        tone: 'neutral',
        description: 'Người gửi đã hủy lời mời này',
    },
};

export interface Invitation {
    id: number;
    orderId: number;
    orderCode: string;
    senderId?: number;
    senderName?: string;
    senderPhone?: string;

    courierId: number;
    courierName: string;
    courierPhone?: string;
    courierEmail?: string;
    courierTier?: string;
    courierTrustScore?: number;
    courierCompletedOrders?: number;
    courierVehicleType?: string;

    productName?: string;
    productDescription?: string;
    weightKg?: number;
    pickupAddress?: string;
    deliveryAddress?: string;
    distanceKm?: number;
    declaredValue?: number;
    priceSuggested?: number;
    priceAgreed?: number;
    urgency?: string;
    orderStatus?: string;

    message?: string;
    status: InvitationStatus;
    expiresAt: string;
    createdAt: string;
    updatedAt: string;
}

export interface InvitationCreateRequest {
    courierId: number;
    message?: string;
    expiryHours?: number;
}

export interface FavoriteCourier {
    id: number;
    courierId: number;
    fullName: string;
    phone?: string;
    email?: string;
    vehicleType?: string;
    tier?: string;
    trustScore?: number;
    completedOrders?: number;
    areas?: string[];
    note?: string;
    createdAt: string;
}

export interface CourierSummary {
    courierId: number;
    fullName: string;
    phone?: string;
    email?: string;
    vehicleType?: string;
    tier?: string;
    trustScore?: number;
    completedOrders?: number;
    areas?: string[];
    isFavorite: boolean;
}
