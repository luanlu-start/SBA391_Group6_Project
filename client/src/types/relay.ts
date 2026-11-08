export type LegStatus =
  | 'PENDING'
  | 'ASSIGNED'
  | 'IN_TRANSIT'
  | 'READY_FOR_HANDOVER'
  | 'HANDED_OVER'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'INCIDENT';

export interface MeetingPoint {
  id: number;
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  campusArea?: string;
  description?: string;
  hasSecurityGuard?: boolean;
  hasSurveillanceCamera?: boolean;
  isActive?: boolean;
}

export interface OrderLeg {
  id: number;
  orderId: number;
  orderCode: string;
  productName: string;
  legNo: number;
  courierId?: number;
  courierName?: string;
  courierPhone?: string;
  startAddress: string;
  startLatitude?: number;
  startLongitude?: number;
  endAddress: string;
  endLatitude?: number;
  endLongitude?: number;
  meetingPoint?: MeetingPoint;
  distanceKm?: number;
  fee?: number;
  status: LegStatus;
  startedAt?: string;
  handedOverAt?: string;
  completedAt?: string;
  handoverNote?: string;
  isCurrentLeg?: boolean;
  canHandover?: boolean;
  canReceiveHandover?: boolean;
}

export interface HandoverToken {
  orderId: number;
  orderCode: string;
  legNo: number;
  tokenCode: string;
  qrData: string;
  generatedByCourierId: number;
  generatedByCourierName: string;
  expiresAt: string;
  remainingSeconds: number;
  meetingPointName?: string;
  meetingPointAddress?: string;
}

export interface HandoverConfirmRequest {
  tokenCode: string;
  photoUrl?: string;
  notes?: string;
  latitude?: number;
  longitude?: number;
}

export interface HandoverConfirmResponse {
  success: boolean;
  message: string;
  orderId: number;
  orderCode: string;
  completedLegNo: number;
  completedLegStatus: LegStatus;
  nextLegNo: number;
  nextLegStatus: LegStatus;
  nextCourierId: number;
  nextCourierName: string;
  nextDestinationAddress: string;
  handoverTime: string;
  proofPhotoUrl?: string;
}

export interface HandoverIncidentRequest {
  incidentType: string;
  description: string;
  photoUrl?: string;
}

export interface HandoverIncidentResponse {
  id: number;
  orderId: number;
  legNo: number;
  incidentType: string;
  description: string;
  photoUrl?: string;
  reportedAt: string;
  message: string;
}

export interface HandoverRecord {
  id: number;
  legNo: number;
  fromCourierId: number;
  fromCourierName: string;
  toCourierId: number;
  toCourierName: string;
  handoverPhotoUrl?: string;
  meetingPointName?: string;
  notes?: string;
  latitude?: number;
  longitude?: number;
  handoverTime: string;
}

export interface RelayOrderDetail {
  orderId: number;
  orderCode: string;
  productName: string;
  weightKg: number;
  declaredValue?: number;
  totalPrice?: number;
  orderStatus: string;
  isRelay?: boolean;
  currentLegNo?: number;
  totalLegs: number;
  pickupAddress: string;
  deliveryAddress: string;
  receiverName: string;
  receiverPhone: string;
  legs: OrderLeg[];
  handoverHistory: HandoverRecord[];
}
