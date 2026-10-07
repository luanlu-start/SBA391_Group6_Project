export interface ApiResponse<T> {
  success: boolean;
  status: number;
  code: number;
  message: string;
  data: T;
  errors?: Record<string, string>;
  timestamp: string;
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
