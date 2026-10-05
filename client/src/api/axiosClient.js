import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

// Request Interceptor: Attach Auth tokens or custom headers
axiosClient.interceptors.request.use(
  (config) => {
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
  (error) => {
    let message = 'An unexpected network error occurred';
    
    if (error.response) {
      const data = error.response.data;
      message = data?.message || `Server returned error status ${error.response.status}`;
      
      if (error.response.status === 401) {
        // Handle unauthorized (redirect or clear session)
        console.warn('Session expired or unauthorized request');
      }
    } else if (error.request) {
      message = 'Cannot connect to backend server. Make sure Spring Boot is running on port 8080.';
    }

    return Promise.reject({
      message,
      originalError: error,
      status: error.response?.status,
      errors: error.response?.data?.errors,
    });
  }
);

export default axiosClient;
