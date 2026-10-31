import axiosClient from '../api/axiosClient';
import { setAuthentication } from '../auth/authSession';
import { selectAccountMode } from '../auth/accountModeStore';
import type { ApiResponse } from '../types/api';
import type { CourierRegistrationInput, CourierRegistrationResult, CourierRegistrationStatus } from '../types/courier';

export const courierService = {
  status: () => axiosClient.get<unknown, ApiResponse<CourierRegistrationStatus>>('/me/courier-registration'),
  async register(input: CourierRegistrationInput) {
    const response = await axiosClient.post<unknown, ApiResponse<CourierRegistrationResult>>('/me/courier-registration', input);
    setAuthentication(response.data.authentication);
    selectAccountMode(response.data.authentication.user.id, 'COURIER');
    return response;
  },
};
