import axiosClient from './axiosClient';

export const productApi = {
  getAll: (params = {}) => {
    return axiosClient.get('/products', { params });
  },

  getById: (id) => {
    return axiosClient.get(`/products/${id}`);
  },

  create: (data) => {
    return axiosClient.post('/products', data);
  },

  update: (id, data) => {
    return axiosClient.put(`/products/${id}`, data);
  },

  delete: (id) => {
    return axiosClient.delete(`/products/${id}`);
  },
};
