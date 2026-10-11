import type { OtpChallenge } from './identity';

export interface ApiResponse<T> {
  success: boolean;
  status: number;
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

export interface FieldViolation {
  field: string;
  code: string;
  message: string;
}

export interface ApiProblem {
  type: string;
  title: string;
  status: number;
  code: string;
  detail: string;
  instance?: string;
  correlationId: string;
  errors?: FieldViolation[];
  verification?: OtpChallenge;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface HealthInfo {
  status: string;
  service: string;
  profile: string;
  timestamp: string;
  uptimeMs: number;
  jvmVersion: string;
}
