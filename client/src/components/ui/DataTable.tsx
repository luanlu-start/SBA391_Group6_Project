import type { ReactNode, Key } from 'react';
import EmptyState from './EmptyState';
import LoadingSpinner from '../common/LoadingSpinner';

export interface TableColumn<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  align?: 'left' | 'right';
}

interface DataTableProps<T> {
  caption: string;
  columns: TableColumn<T>[];
  rows: T[];
  rowKey: (row: T) => Key;
  loading?: boolean;
  emptyState?: ReactNode;
}

export default function DataTable<T>({ caption, columns, rows, rowKey, loading = false, emptyState }: DataTableProps<T>) {
  if (loading) return <LoadingSpinner text="Đang tải dữ liệu…" />;
  if (rows.length === 0) return <>{emptyState ?? <EmptyState />}</>;
  return <div className="ui-table-scroll" role="region" aria-label={caption} tabIndex={0}>
    <table className="ui-table">
      <caption className="sr-only">{caption}</caption>
      <thead><tr>{columns.map(column => <th scope="col" key={column.key}
        className={column.align === 'right' ? 'ui-align-right' : undefined}>{column.header}</th>)}</tr></thead>
      <tbody>{rows.map(row => <tr key={rowKey(row)}>{columns.map(column => <td key={column.key}
        className={column.align === 'right' ? 'ui-align-right' : undefined}>{column.render(row)}</td>)}</tr>)}</tbody>
    </table>
  </div>;
}
