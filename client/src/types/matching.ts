import type { OrderStatus, DeliveryUrgency } from './order';
import type { VehicleType } from './trip';

export type CourierTier = 'NEWBIE' | 'BRONZE' | 'SILVER' | 'GOLD' | 'DIAMOND';

export interface TripOrderMatch {
  id: number;
  orderCode: string;
  senderId: number;
  pickupAddress: string;
  pickupLatitude?: number;
  pickupLongitude?: number;
  deliveryAddress: string;
  deliveryLatitude?: number;
  deliveryLongitude?: number;
  productName: string;
  quantity: number;
  weightKg: number;
  declaredValue?: number;
  priceSuggested?: number;
  priceAgreed?: number;
  distanceKm?: number;
  urgency?: DeliveryUrgency;
  deliveryNote?: string;
  status: OrderStatus;
  receiverName?: string;
  receiverPhone?: string;
  createdAt?: string;

  // Matching fields (UC-21)
  detourKm?: number;
  matchScore?: number;
  matchReasons?: string[];
}

export interface OrderCourierMatch {
  tripId: number;
  courierId: number;
  courierPublicId?: string;
  courierName: string;
  courierAvatar?: string;
  courierPhone?: string;
  courierTier?: CourierTier;
  trustScore?: number;
  completedOrders?: number;
  vehicleType?: VehicleType;
  vehicleLabel?: string;

  // Trip details
  origin: string;
  originLat: number;
  originLng: number;
  destination: string;
  destinationLat: number;
  destinationLng: number;
  departAt: string;
  distanceKm?: number;
  capacityKg?: number;
  floorPrice?: number;
  routePolyline?: string;

  // Matching fields (UC-21)
  detourKm?: number;
  matchScore?: number;
  matchReasons?: string[];
}

export interface QuickBidRequest {
  price: number;
  message?: string;
  etaPickup?: string;
}
