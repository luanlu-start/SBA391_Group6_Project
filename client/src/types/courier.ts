import type { KycStatus } from './kyc';
import type { AuthenticationResponse } from './identity';
export type VehicleType = 'BICYCLE' | 'MOTORBIKE' | 'CAR';
export type WeekDay = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
export interface AvailabilityWindow { day: WeekDay; start: string; end: string }
export interface CourierProfile {
  userId: string; vehicleType: VehicleType; areas: string[]; availableHours: AvailabilityWindow[];
  tier: string; trustScore: number; completedOrders: number; restrictedUntil?: string; registeredAt: string;
}
export interface CourierRegistrationStatus { profile?: CourierProfile; kycStatus: KycStatus; eligible: boolean; commitmentVersion: string }
export interface CourierRegistrationInput {
  vehicleType: VehicleType; areas: string[]; availableHours: AvailabilityWindow[]; commitmentAccepted: boolean; commitmentVersion: string;
}
export interface CourierRegistrationResult { profile: CourierProfile; authentication: AuthenticationResponse }
