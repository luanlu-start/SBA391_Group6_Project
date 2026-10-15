export type OrderStatus =
  | 'PENDING'
  | 'DRAFT'
  | 'PENDING_CONFIRMATION'
  | 'CONFIRMED'
  | 'PENDING_ESCROW'
  | 'ESCROWED'
  | 'FINDING_SHIPPER'
  | 'SHIPPER_ASSIGNED'
  | 'SHIPPER_ACCEPTED'
  | 'PICKING_UP'
  | 'IN_TRANSIT'
  | 'IN_DELIVERY'
  | 'DELIVERED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'RETURNING'
  | 'RETURNED'
  | 'FAILED'
  | 'EXPIRED';

export interface OrderStatusConfig {
  label: string;
  tone: 'neutral' | 'success' | 'warning' | 'danger' | 'info';
}

export const ORDER_STATUS_MAP: Record<OrderStatus, OrderStatusConfig> = {
  PENDING: { label: 'Chờ xử lý', tone: 'warning' },
  DRAFT: { label: 'Bản nháp', tone: 'neutral' },
  PENDING_CONFIRMATION: { label: 'Chờ xác nhận', tone: 'warning' },
  CONFIRMED: { label: 'Đã xác nhận', tone: 'info' },
  PENDING_ESCROW: { label: 'Chờ ký quỹ', tone: 'warning' },
  ESCROWED: { label: 'Đã ký quỹ', tone: 'info' },
  FINDING_SHIPPER: { label: 'Đang tìm tài xế', tone: 'info' },
  SHIPPER_ASSIGNED: { label: 'Đã có tài xế', tone: 'info' },
  SHIPPER_ACCEPTED: { label: 'Tài xế đã nhận', tone: 'info' },
  PICKING_UP: { label: 'Đang lấy hàng', tone: 'warning' },
  IN_TRANSIT: { label: 'Đang vận chuyển', tone: 'warning' },
  IN_DELIVERY: { label: 'Đang giao hàng', tone: 'warning' },
  DELIVERED: { label: 'Đã giao hàng', tone: 'success' },
  COMPLETED: { label: 'Hoàn thành', tone: 'success' },
  CANCELLED: { label: 'Đã hủy', tone: 'danger' },
  RETURNING: { label: 'Đang trả hàng', tone: 'warning' },
  RETURNED: { label: 'Đã trả hàng', tone: 'danger' },
  FAILED: { label: 'Thất bại', tone: 'danger' },
  EXPIRED: { label: 'Hết hạn', tone: 'danger' },
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

export type OrderResponse = Order;

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
