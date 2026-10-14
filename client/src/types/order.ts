export interface MarketPlaceOrder {
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