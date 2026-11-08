export type ComplaintStatus = 'OPEN' | 'INVESTIGATING' | 'AWAITING_INFO' | 'RESOLVED' | 'APPEALED' | 'CLOSED';
export type ComplaintQueueFilter = 'ACTIVE' | ComplaintStatus | 'ALL';
export type ComplaintReason = 'DAMAGED' | 'LOST' | 'LATE' | 'WRONG_ITEM' | 'NOT_DELIVERED' | 'PAYMENT' | 'CONDUCT' | 'OTHER';
export type ComplaintOutcome = 'SENDER_FAVOR' | 'COURIER_FAVOR' | 'SPLIT';
export type ComplaintRole = 'SENDER' | 'COURIER' | 'MODERATOR' | 'ADMIN' | 'SYSTEM';
export type AppealDecision = 'UPHOLD' | 'AMEND';
export type EscrowState = 'HELD' | 'RELEASED' | 'REFUNDED' | 'PARTIALLY_REFUNDED';

export interface ComplaintPerson {
  publicId: string;
  fullName: string | null;
  email: string | null;
}

export interface ComplaintSummary {
  id: string;
  code: string;
  status: ComplaintStatus;
  reason: ComplaintReason;
  orderCode: string | null;
  productName: string | null;
  openerRole: ComplaintRole;
  openedBy: ComplaintPerson | null;
  assignee: ComplaintPerson | null;
  outcome: ComplaintOutcome | null;
  createdAt: string;
  firstResponseDueAt: string;
  resolveDueAt: string;
  responseOverdue: boolean;
  resolutionOverdue: boolean;
  appealDeadline: string | null;
}

export interface ComplaintRuling {
  outcome: ComplaintOutcome;
  senderPercent: number | null;
  refundAmount: number;
  courierAmount: number;
  compensationAmount: number;
  reason: string | null;
  ruledBy: ComplaintPerson | null;
  ruledAt: string | null;
  appealDeadline: string | null;
}

export interface ComplaintAppeal {
  appealedBy: ComplaintPerson | null;
  reason: string;
  appealedAt: string;
  decision: AppealDecision | null;
  note: string | null;
  reviewedBy: ComplaintPerson | null;
  reviewedAt: string | null;
}

export interface ComplaintMessage {
  id: string;
  authorRole: ComplaintRole;
  author: ComplaintPerson | null;
  body: string | null;
  createdAt: string;
  evidenceIds: string[];
}

export interface ComplaintDetail {
  id: string;
  code: string;
  status: ComplaintStatus;
  reason: ComplaintReason;
  description: string | null;
  openerRole: ComplaintRole;
  openedBy: ComplaintPerson | null;
  sender: ComplaintPerson | null;
  courier: ComplaintPerson | null;
  assignee: ComplaintPerson | null;
  order: {
    id: number;
    orderCode: string;
    productName: string;
    status: string | null;
    declaredValue: number | null;
    priceAgreed: number | null;
    insured: boolean;
  };
  escrow: { amount: number; status: EscrowState; frozen: boolean; paymentMethod: string } | null;
  createdAt: string;
  updatedAt: string;
  firstResponseAt: string | null;
  firstResponseDueAt: string;
  resolveDueAt: string;
  responseOverdue: boolean;
  resolutionOverdue: boolean;
  ruling: ComplaintRuling | null;
  appeal: ComplaintAppeal | null;
  settlement: { clawbackAmount: number; unrecoveredAmount: number; closedAt: string } | null;
  messages: ComplaintMessage[];
  viewerRole: ComplaintRole;
  permissions: {
    claim: boolean;
    message: boolean;
    requestInfo: boolean;
    rule: boolean;
    appeal: boolean;
    reviewAppeal: boolean;
  };
}

export interface ComplaintChatHistory {
  available: boolean;
  messages: { authorRole: ComplaintRole; content: string; createdAt: string }[];
}

export interface ComplaintRulingInput {
  outcome: ComplaintOutcome;
  senderPercent?: number;
  compensationAmount: number;
  reason: string;
}

export interface AppealReviewInput {
  decision: AppealDecision;
  outcome?: ComplaintOutcome;
  senderPercent?: number;
  compensationAmount?: number;
  note: string;
}
