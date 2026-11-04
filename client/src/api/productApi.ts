import axiosClient from './axiosClient';
import type { ApiResponse, PageResponse } from '../types/api';
import type { Product, ProductRequest, ProductSearchRequest } from '../types/product';

export const productApi = {
  getAll: (params: ProductSearchRequest = {}) => {
    return axiosClient.get<unknown, ApiResponse<PageResponse<Product>>>('/products', { params });
  },

  getById: (id: string) => {
    return axiosClient.get<unknown, ApiResponse<Product>>(`/products/${id}`);
  },

  create: (data: ProductRequest) => {
    return axiosClient.post<unknown, ApiResponse<Product>>('/products', data);
  },

  update: (id: string, data: ProductRequest) => {
    return axiosClient.put<unknown, ApiResponse<Product>>(`/products/${id}`, data);
  },

  delete: (id: string) => {
    return axiosClient.delete<unknown, ApiResponse<void>>(`/products/${id}`);
  },
};
