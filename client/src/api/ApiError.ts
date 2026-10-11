import type { FieldViolation } from '../types/api';
import type { OtpChallenge } from '../types/identity';

export class ApiError extends Error {
  public readonly fieldErrors: Record<string, string>;

  constructor(
    message: string,
    public readonly status?: number,
    public readonly code?: string,
    public readonly errors: FieldViolation[] = [],
    public readonly correlationId?: string,
    cause?: unknown,
    public readonly retryAfterSeconds?: number,
    public readonly verification?: OtpChallenge,
  ) {
    super(message, { cause });
    this.name = 'ApiError';
    this.fieldErrors = Object.fromEntries(errors.map(error => [error.field, error.message]));
  }
}

export function getApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  return new ApiError(error instanceof Error ? error.message : 'An unexpected error occurred');
}
