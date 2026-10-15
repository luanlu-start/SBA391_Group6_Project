export type OrderStatus =
  | 'PENDING'
  | 'FINDING_SHIPPER'
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
  SHIPPER_ASSIGNED: { label: 'Đã có tài xế', tone: 'info' },
  IN_DELIVERY: { label: 'Đang giao hàng', tone: 'warning' },
  COMPLETED: { label: 'Giao thành công', tone: 'success' },
  CANCELLED: { label: 'Đã hủy', tone: 'danger' },
};

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
