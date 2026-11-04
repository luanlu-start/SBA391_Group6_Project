export type KycStatus = 'NOT_SUBMITTED' | 'PENDING' | 'APPROVED' | 'REJECTED';
export type KycFileType = 'ID_FRONT' | 'ID_BACK' | 'SELFIE_WITH_ID';
export interface KycFile { id: string; type: KycFileType; mediaType: string; sizeBytes: number }
export interface KycRequest {
  id: string; status: KycStatus; fullName: string; dob: string; issuedDate: string; expiryDate: string;
  idNumberMasked: string; submittedAt: string; reviewedAt?: string; rejectReason?: string; files: KycFile[];
}
export interface KycSnapshot {
  status: KycStatus; request?: KycRequest; submissionsRemaining: number; nextSubmissionAt?: string; policyVersion: string;
}
export interface KycInput {
  idNumber: string; fullName: string; dob: string; issuedDate: string; expiryDate: string; consentAccepted: boolean; policyVersion: string;
}
