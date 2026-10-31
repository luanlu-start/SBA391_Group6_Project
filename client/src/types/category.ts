export interface ItemCategory {
  id: number;
  code: string;
  name: string;
  description?: string;
  isProhibited: boolean;
  requiresReview: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CategoryRequest {
  code: string;
  name: string;
  description?: string;
  isProhibited: boolean;
  requiresReview: boolean;
  active: boolean;
}
