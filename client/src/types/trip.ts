export type VehicleType = 'MOTORBIKE' | 'CAR' | 'BICYCLE';

export type TripRecurrence = 'NONE' | 'DAILY' | 'WEEKDAYS' | 'WEEKENDS' | 'WEEKLY';

export type TripStatus = 'SCHEDULED' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'CANCELLED';

export interface TripAttachedOrder {
  id: number;
  orderCode: string;
  senderId?: number;
  pickupAddress: string;
  pickupLatitude?: number;
  pickupLongitude?: number;
  deliveryAddress: string;
  deliveryLatitude?: number;
  deliveryLongitude?: number;
  productName: string;
  quantity?: number;
  weightKg?: number;
  declaredValue?: number;
  priceSuggested?: number;
  priceAgreed?: number;
  distanceKm?: number;
  urgency?: string;
  deliveryNote?: string;
  status: string;
  receiverName?: string;
  receiverPhone?: string;
  createdAt?: string;
  detourKm?: number;
}

export interface Trip {
  id: number;
  courierId?: number;
  courierPublicId?: string;
  courierName?: string;
  courierAvatarUrl?: string;
  courierTier?: string;
  courierTrustScore?: number;
  origin: string;
  originLat: number;
  originLng: number;
  destination: string;
  destinationLat: number;
  destinationLng: number;
  routePolyline?: string;
  distanceKm?: number;
  estimatedDurationMinutes?: number;
  departAt: string;
  vehicleType: VehicleType;
  capacityKg: number;
  floorPrice?: number;
  recurrence: TripRecurrence;
  status: TripStatus;
  ordersCount?: number;
  attachedOrders?: TripAttachedOrder[];
  createdAt: string;
  updatedAt?: string;
}

export interface TripCreateRequest {
  origin: string;
  originLat: number;
  originLng: number;
  destination: string;
  destinationLat: number;
  destinationLng: number;
  departAt: string;
  vehicleType: VehicleType;
  capacityKg: number;
  floorPrice?: number;
  recurrence?: TripRecurrence;
  routePolyline?: string;
  distanceKm?: number;
  estimatedDurationMinutes?: number;
}

export interface TripUpdateRequest {
  origin: string;
  originLat: number;
  originLng: number;
  destination: string;
  destinationLat: number;
  destinationLng: number;
  departAt: string;
  vehicleType: VehicleType;
  capacityKg: number;
  floorPrice?: number;
  recurrence?: TripRecurrence;
  routePolyline?: string;
  distanceKm?: number;
  estimatedDurationMinutes?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
