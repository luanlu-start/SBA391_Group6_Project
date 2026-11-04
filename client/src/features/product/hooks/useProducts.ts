import { useCallback, useEffect, useRef, useState } from 'react';
import { productApi } from '../../../api/productApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { Product } from '../../../types/product';

export default function useProducts() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0);
  const [pagination, setPagination] = useState({ totalPages: 0, totalElements: 0 });
  const latestRequest = useRef(0);
  const size = 12;

  const reload = useCallback(async () => {
    const requestId = ++latestRequest.current;
    setLoading(true);
    setError(null);
    try {
      const response = await productApi.getAll({ page, size, search: keyword || undefined, category: category || undefined });
      if (requestId !== latestRequest.current) return;
      const result = response.data;
      if (page > 0 && page >= result.totalPages) {
        setPage(Math.max(0, result.totalPages - 1));
        return;
      }
      setProducts(result.content);
      setPagination({ totalPages: result.totalPages, totalElements: result.totalElements });
    } catch (cause) {
      if (requestId === latestRequest.current) setError(getApiError(cause));
    } finally {
      if (requestId === latestRequest.current) setLoading(false);
    }
  }, [page, keyword, category]);

  useEffect(() => {
    void reload();
    return () => { latestRequest.current += 1; };
  }, [reload]);

  function search(nextKeyword: string, nextCategory: string) {
    const trimmed = nextKeyword.trim();
    if (page === 0 && keyword === trimmed && category === nextCategory) void reload();
    setKeyword(trimmed);
    setCategory(nextCategory);
    setPage(0);
  }

  return { products, loading, error, page, size, pagination, setPage, search, reload };
}
