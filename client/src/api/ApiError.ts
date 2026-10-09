export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status?: number,
    public readonly code?: number,
    public readonly errors?: Record<string, string>,
    cause?: unknown,
  ) {
    super(message, { cause });
    this.name = 'ApiError';
  }
}

export function getApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  return new ApiError(error instanceof Error ? error.message : 'An unexpected error occurred');
}
