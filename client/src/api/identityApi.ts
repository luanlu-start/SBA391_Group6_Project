import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type { RegistrationPolicies, RegisterRequest, RegisterResponse } from '../types/identity';

export const identityApi = {
  getRegistrationPolicies: () => axiosClient.get<unknown, ApiResponse<RegistrationPolicies>>('/auth/registration-policies'),
  register: (data: RegisterRequest) => axiosClient.post<unknown, ApiResponse<RegisterResponse>>('/auth/register', data),
};
