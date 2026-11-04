export type KycReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type KycReviewStatusFilter = KycReviewStatus | 'ALL';
export type KycReviewFileType = 'ID_FRONT' | 'ID_BACK' | 'SELFIE_WITH_ID';

export interface KycReviewItem {
  id: string;
  applicantId: string;
  fullName: string;
  email: string;
  idNumberMasked: string;
  dob: string;
  issuedDate: string;
  expiryDate: string;
  status: KycReviewStatus;
  submittedAt: string;
  reviewedAt: string | null;
  reviewerName: string | null;
  rejectReason: string | null;
  flagged: boolean;
  flagReason: string | null;
  version: number;
  dueAt: string;
  overdue: boolean;
}

export interface KycReviewFile {
  id: string;
  type: KycReviewFileType;
  mediaType: string;
  sizeBytes: number;
}

export interface KycReviewDetail extends KycReviewItem {
  files: KycReviewFile[];
}

export interface KycReviewQuery {
  q?: string;
  status: KycReviewStatusFilter;
  flagged?: boolean;
  overdue?: boolean;
  page: number;
  size: number;
}

export interface KycRejectionReason {
  code: string;
  label: string;
  noteRequired: boolean;
}

export interface KycReviewFileLink {
  url: string;
  expiresAt: string;
}

export type KycReviewAction = 'approve' | 'reject' | 'flag';
