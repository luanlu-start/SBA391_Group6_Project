import { ChevronLeft, ChevronRight } from 'lucide-react';
import Button from './Button';

interface PaginationProps {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  disabled?: boolean;
}

export default function Pagination({ page, size, totalElements, totalPages, onPageChange, disabled }: PaginationProps) {
  if (totalPages === 0) return null;
  const first = page * size + 1;
  const last = Math.min((page + 1) * size, totalElements);
  return <nav className="ui-pagination" aria-label="Phân trang">
    <p><strong>{first}–{last}</strong> trên {totalElements} kết quả</p>
    <div className="ui-pagination-controls">
      <Button variant="secondary" size="sm" disabled={disabled || page <= 0} aria-label="Trang trước"
        onClick={() => onPageChange(page - 1)} icon={<ChevronLeft size={16} />}>Trước</Button>
      <span aria-live="polite">{page + 1} / {totalPages}</span>
      <Button variant="secondary" size="sm" disabled={disabled || page + 1 >= totalPages} aria-label="Trang sau"
        onClick={() => onPageChange(page + 1)} icon={<ChevronRight size={16} />}>Sau</Button>
    </div>
  </nav>;
}
