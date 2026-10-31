import { Inbox } from 'lucide-react';
import type { ReactNode } from 'react';

interface EmptyStateProps {
  title?: string;
  description?: string;
  icon?: ReactNode;
  action?: ReactNode;
}

export default function EmptyState({ title = 'Chưa có dữ liệu', description = 'Dữ liệu sẽ hiển thị tại đây.',
  icon = <Inbox size={24} />, action }: EmptyStateProps) {
  return <div className="ui-empty-state">
    <span className="ui-empty-icon" aria-hidden="true">{icon}</span>
    <h3>{title}</h3><p>{description}</p>{action}
  </div>;
}
