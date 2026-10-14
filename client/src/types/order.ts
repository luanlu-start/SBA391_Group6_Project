export type OrderStatus =
  | 'DRAFT'
  | 'PENDING_CONFIRMATION'
  | 'CONFIRMED'
  | 'PENDING_ESCROW'
  | 'ESCROWED'
  | 'FINDING_SHIPPER'
  | 'SHIPPER_ACCEPTED'
  | 'PICKING_UP'
  | 'IN_TRANSIT'
  | 'DELIVERED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'RETURNING'
  | 'RETURNED'
  | 'FAILED'
  | 'EXPIRED';

export interface OrderResponse {
  id: number;
  orderCode: string;
  senderId: number;
  pickupAddress: string;
  deliveryAddress: string;
  receiverName: string;
  receiverPhone: string;
  productName: string;
  productDescription?: string;
  quantity: number;
  weightKg: number;
  declaredValue: number;
  deliveryNote?: string;
  status: OrderStatus;
  createdAt: string;
  updatedAt: string;
  imageUrls?: string[];
}
