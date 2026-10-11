import axios from 'axios';
import type { AxiosError } from 'axios';
import type { ApiProblem, FieldViolation } from '../types/api';
import { ApiError } from './ApiError';
import { apiErrorMessages } from '../i18n/apiErrors';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json, application/problem+json',
  },
});

// Request Interceptor: Attach Auth tokens or custom headers
axiosClient.interceptors.request.use(
  (config) => {
    config.headers['X-Correlation-Id'] = crypto.randomUUID();
    const token = localStorage.getItem('access_token');
    if (token) {
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
  (error: AxiosError<Partial<ApiProblem>>) => {
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
      
      if (error.response.status === 401) {
        // Handle unauthorized (redirect or clear session)
        console.warn('Session expired or unauthorized request');
      }
    }

    return Promise.reject(new ApiError(message, error.response?.status, code, errors,
      typeof correlationId === 'string' ? correlationId : undefined, error));
  }
);

export default axiosClient;
