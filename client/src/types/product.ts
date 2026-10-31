export type ProductStatus = 'ACTIVE' | 'INACTIVE';

export interface Product {
  id: string;
  name: string;
  description?: string;
  price: number;
  category: string;
  stock: number;
  status: ProductStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  name: string;
  description?: string;
  price: number;
  category: string;
  stock: number;
  status: ProductStatus;
}

export interface ProductSearchRequest {
  search?: string;
  category?: string;
  status?: ProductStatus;
  minPrice?: number;
  maxPrice?: number;
  page?: number;
  size?: number;
  sortBy?: 'name' | 'price' | 'stock' | 'createdAt' | 'updatedAt';
  direction?: 'ASC' | 'DESC';
}
