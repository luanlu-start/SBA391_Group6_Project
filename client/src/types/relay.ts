import type { OrderStatus } from './order';

export interface RelayMeetingPoint {
  id: string;
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  campus?: string;
  description: string;
  securityFeatures: string;
}

export interface DetailedOrderLeg {
  id: number;
  legNo: number;
  pickupAddress: string;
  pickupLatitude?: number | null;
  pickupLongitude?: number | null;
  deliveryAddress: string;
  deliveryLatitude?: number | null;
  deliveryLongitude?: number | null;
  distanceKm: number;
  legFee: number;
  courierId?: number | null;
  courierPublicId?: string | null;
  courierName?: string | null;
  courierPhone?: string | null;
  courierEmail?: string | null;
  status: OrderStatus;
  handoverPhotoUrl?: string | null;
  handoverNote?: string | null;
  handoverAt?: string | null;
  handoverMeetingPoint?: string | null;
  handoverLatitude?: number | null;
  handoverLongitude?: number | null;
  createdAt: string;
}

export interface HandoverQrData {
  qrToken: string;
  otpCode: string;
  orderId: number;
  orderCode: string;
  legNo: number;
  nextLegNo: number;
  fromCourierId: number;
  toCourierId?: number | null;
  meetingPoint?: string | null;
  issuedAt: string;
  expiresAt: string;
  remainingSeconds: number;
  handoverUrl?: string;
}

export interface RelayHandoverResult {
  orderId: number;
  orderCode: string;
  completedLegNo: number;
  nextLegNo: number;
  previousCourierId: number;
  nextCourierId: number;
  handoverAt: string;
  handoverPhotoUrl: string;
  handoverMeetingPoint?: string | null;
  handoverNote?: string | null;
  orderStatus: OrderStatus;
  message: string;
  legs: DetailedOrderLeg[];
}

export function isLegAssignedToUser(
  leg: {
    courierId?: number | null;
    courierPublicId?: string | null;
    courierPhone?: string | null;
    courierEmail?: string | null;
    courierName?: string | null;
  } | null | undefined,
  user: {
    id?: string;
    phone?: string;
    email?: string;
    fullName?: string;
    roles?: string[];
  } | null | undefined
): boolean {
  if (!leg || !user) return false;
  if (!leg.courierId && !leg.courierPublicId && !leg.courierName && !leg.courierPhone && !leg.courierEmail) return false;

  // 1. So khớp UUID người dùng
  if (user.id && leg.courierPublicId && user.id === leg.courierPublicId) {
    return true;
  }
  // 2. So khớp số điện thoại tài xế
  if (user.phone && leg.courierPhone && user.phone.trim() === leg.courierPhone.trim()) {
    return true;
  }
  // 3. So khớp email tài xế
  if (user.email && leg.courierEmail && user.email.trim().toLowerCase() === leg.courierEmail.trim().toLowerCase()) {
    return true;
  }
  // 4. So khớp họ và tên tài xế
  if (user.fullName && leg.courierName && user.fullName.trim() === leg.courierName.trim()) {
    return true;
  }
  // 5. So khớp chuỗi ID số nguyên nếu có
  if (user.id && leg.courierId && String(user.id) === String(leg.courierId)) {
    return true;
  }
  return false;
}
