export type DeliveryBatchStatus = 'PLANNING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export type BatchStopType = 'PICKUP' | 'DELIVERY';

export type BatchStopStatus = 'PENDING' | 'ARRIVED' | 'COMPLETED' | 'SKIPPED';

export interface BatchStop {
  id?: number;
  orderId: number;
  orderCode: string;
  stopOrder: number;
  stopType: BatchStopType;
  address: string;
  latitude: number;
  longitude: number;
  contactName?: string;
  contactPhone?: string;
  itemDescription?: string;
  weightKg?: number;
  distanceFromPrevKm?: number;
  estimatedArrivalMinutes?: number;
  status: BatchStopStatus;
  completedAt?: string;
  stopKey?: string;
}

export interface BatchOrderSummary {
  orderId: number;
  orderCode: string;
  productName: string;
  weightKg: number;
  declaredValue?: number;
  priceAgreed?: number;
  pickupAddress: string;
  deliveryAddress: string;
  receiverName: string;
  receiverPhone: string;
  status: string;
}

export interface DeliveryBatch {
  id: number;
  batchCode: string;
  batchName: string;
  status: DeliveryBatchStatus;
  totalOrders: number;
  totalWeightKg: number;
  totalDistanceKm: number;
  estimatedDurationMinutes: number;
  notes?: string;
  tripId?: number;
  tripOrigin?: string;
  tripDestination?: string;
  stops: BatchStop[];
  orders: BatchOrderSummary[];
  createdAt: string;
  updatedAt?: string;
}

export interface RouteOptimizationResult {
  totalOrders: number;
  totalStops: number;
  totalWeightKg: number;
  maxCapacityKg: number;
  isCapacityExceeded: boolean;
  totalDistanceKm: number;
  estimatedDurationMinutes: number;
  stops: BatchStop[];
  optimizationMethod?: string;
  message?: string;
}

export interface EligibleBatchOrder {
  id: number;
  orderCode: string;
  productName: string;
  productDescription?: string;
  weightKg: number;
  declaredValue?: number;
  priceAgreed?: number;
  pickupAddress: string;
  pickupLatitude: number;
  pickupLongitude: number;
  deliveryAddress: string;
  deliveryLatitude: number;
  deliveryLongitude: number;
  receiverName: string;
  receiverPhone: string;
  status: string;
  tripId?: number;
  createdAt: string;
}

export interface BatchCreateRequest {
  orderIds: number[];
  tripId?: number | null;
  batchName?: string;
  notes?: string;
  customStopOrderKeys?: string[];
}

export interface RouteOptimizeRequest {
  orderIds: number[];
  tripId?: number | null;
  currentLatitude?: number | null;
  currentLongitude?: number | null;
}
