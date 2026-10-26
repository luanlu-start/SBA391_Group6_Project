export interface ProfileInput { fullName: string; dob: string | null; bio: string; emergencyContact: string }
export interface SavedAddress { id: string; label: string; addressText: string; lat: number; lng: number; isDefault: boolean }
export type AddressInput = Omit<SavedAddress, 'id'>;
export interface AccountDataRequest {
  id: string; type: 'DATA_EXPORT' | 'ACCOUNT_DELETION'; status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'REJECTED';
  requestedAt: string; dueAt?: string;
}
