export type AccountRole = 'SENDER' | 'COURIER' | 'MODERATOR' | 'ADMIN';
export type AccountStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'LOCKED' | 'DELETED';
export type AccountRequestType = 'DATA_EXPORT' | 'ACCOUNT_DELETION';
export type AccountRequestStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'REJECTED';

export interface AdminUserSummary {
  publicId: string;
  fullName: string | null;
  email: string;
  phone: string;
  roles: AccountRole[];
  status: AccountStatus;
  emailVerified: boolean;
  phoneVerified: boolean;
  fptBadge: boolean;
  createdAt: string;
}

export interface AdminUserOrder {
  id: number;
  orderCode: string;
  productName: string;
  status: string;
  participation: 'SENDER' | 'COURIER';
  createdAt: string;
}

export interface AdminUserTransaction {
  transactionCode: string;
  type: string;
  amount: number;
  description: string | null;
  createdAt: string;
}

export interface AccountRequestItem {
  id: string;
  type: AccountRequestType;
  status: AccountRequestStatus;
  reason: string | null;
  requestedAt: string;
  dueAt: string | null;
  overdue: boolean;
  userPublicId: string;
  userFullName: string | null;
  userEmail: string;
  processedAt: string | null;
  resolutionNote: string | null;
}

export interface AdminUserDetail {
  account: AdminUserSummary;
  dob: string | null;
  kycStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | null;
  courierTier: string | null;
  courierVehicle: string | null;
  courierTrustScore: number | null;
  courierCompletedOrders: number | null;
  walletAvailable: number;
  walletEscrowHeld: number;
  walletPendingWithdraw: number;
  sentOrderCount: number;
  courierOrderCount: number;
  recentOrders: AdminUserOrder[];
  recentTransactions: AdminUserTransaction[];
  activeSessions: number;
  dataRequests: AccountRequestItem[];
  self: boolean;
  lock: AccountLockInfo | null;
  ordersNeedingAction: AdminUserOrder[];
}

export interface AccountLockInfo {
  reason: string;
  lockedAt: string;
  lockedUntil: string | null;
  permanent: boolean;
  lockedByName: string | null;
}

export type BlacklistKind = 'PHONE' | 'ID_NUMBER' | 'DEVICE';

export interface AdminUserQuery {
  q?: string;
  role?: AccountRole;
  status?: AccountStatus;
  page: number;
  size: number;
}

export interface StaffCreationInput {
  fullName: string;
  email: string;
  phone: string;
  role: 'MODERATOR' | 'ADMIN';
}
