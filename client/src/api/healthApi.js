import axiosClient from './axiosClient';

export const healthApi = {
  getHealth: () => {
    return axiosClient.get('/health');
  },
};
