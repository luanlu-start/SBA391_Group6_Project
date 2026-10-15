import axios from 'axios';
import type { AxiosError } from 'axios';
import type { ApiProblem, FieldViolation } from '../types/api';
import { ApiError } from './ApiError';
import { apiErrorMessages } from '../i18n/apiErrors';
import { clearAuthentication, getAccessToken } from '../auth/authSession';
import { parseRegistrationChallenge } from '../utils/registrationChallenge';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json, application/problem+json',
  },
});

// Request Interceptor: Attach Auth tokens or custom headers
axiosClient.interceptors.request.use(
  (config) => {
    config.headers['X-Correlation-Id'] = crypto.randomUUID();
    const token = getAccessToken();
    if (token && !isPublicAuthentication(config.url)) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response Interceptor: Standardize API responses & centralized error handling
axiosClient.interceptors.response.use(
  (response) => {
    // Return backend ApiResponse payload directly
    return response.data;
  },
  async (error: AxiosError<Partial<ApiProblem>>) => {
    const config = error.config as (typeof error.config & { authRetried?: boolean });
    if (error.response?.status === 401 && config && !isPublicAuthentication(config.url)
      && getAccessToken() && !config.authRetried) {
      config.authRetried = true;
      try {
        const { authService } = await import('../services/authService');
        if (config.headers.Authorization === `Bearer ${getAccessToken()}`) await authService.refresh();
      } catch { clearAuthentication(); }
      if (getAccessToken()) return axiosClient.request(config);
    }
    let message = 'Không thể kết nối máy chủ. Vui lòng thử lại.';
    const data = error.response?.data;
    const code = typeof data?.code === 'string' ? data.code : undefined;
    const correlationId = typeof data?.correlationId === 'string' ? data.correlationId
      : error.response?.headers['x-correlation-id'];
    const errors: FieldViolation[] = Array.isArray(data?.errors) ? data.errors.filter(
      (value): value is FieldViolation => value !== null && typeof value === 'object'
        && typeof value.field === 'string' && typeof value.code === 'string' && typeof value.message === 'string',
    ) : [];
    
    if (error.response) {
      message = (code && apiErrorMessages[code]) || data?.detail || data?.title
        || `Máy chủ trả về lỗi ${error.response.status}.`;
      
      if (error.response.status === 401 && !isPublicAuthentication(config?.url)) clearAuthentication();
    }

    const retryAfter = Number(error.response?.headers['retry-after']);
    return Promise.reject(new ApiError(message, error.response?.status, code, errors,
      typeof correlationId === 'string' ? correlationId : undefined, error,
      Number.isFinite(retryAfter) && retryAfter > 0 ? Math.ceil(retryAfter) : undefined,
      code === 'AUTH_ACCOUNT_UNVERIFIED' ? parseRegistrationChallenge(data?.verification) ?? undefined : undefined));
  }
);

export default axiosClient;

function isPublicAuthentication(url?: string) {
  return !!url && (url.startsWith('/auth/otp/') || ['/auth/register', '/auth/registration-policies',
    '/auth/csrf', '/auth/login', '/auth/refresh'].includes(url));
}
