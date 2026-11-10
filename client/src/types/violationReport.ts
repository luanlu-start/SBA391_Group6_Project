export type ViolationTargetType = 'ORDER' | 'USER' | 'MESSAGE';
export type ViolationReason = 'FRAUD' | 'HARASSMENT' | 'PROHIBITED_GOODS' | 'OFF_PLATFORM_DEAL' | 'SPAM' | 'OTHER';
export type ViolationCaseStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED';
export type ViolationAction = 'WARN' | 'ESCALATE' | 'DISMISS';

export interface ViolationParty {
  publicId: string;
  fullName: string | null;
  email: string;
}

export interface ViolationReportItem {
  reporter: ViolationParty | null;
  reason: ViolationReason;
  description: string | null;
  createdAt: string;
  evidenceIds: string[];
}

export interface ViolationCase {
  id: string;
  targetType: ViolationTargetType;
  targetId: string;
  targetLabel: string | null;
  relatedOrderId: number | null;
  respondent: ViolationParty | null;
  respondentPriorViolations: number;
  latestReason: ViolationReason;
  reportCount: number;
  status: ViolationCaseStatus;
  resolution: ViolationAction | null;
  resolutionNote: string | null;
  handledBy: ViolationParty | null;
  handledAt: string | null;
  createdAt: string;
  updatedAt: string;
  canResolve: boolean;
  reports: ViolationReportItem[] | null;
}

export interface MyViolationReport {
  caseId: string;
  targetType: ViolationTargetType;
  targetLabel: string | null;
  reason: ViolationReason;
  status: ViolationCaseStatus;
  merged: boolean;
  createdAt: string;
}
