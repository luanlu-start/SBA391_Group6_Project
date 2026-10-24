import type { ReactNode } from 'react';

interface BadgeProps {
  tone?: 'neutral' | 'success' | 'warning' | 'danger' | 'info';
  children: ReactNode;
  dot?: boolean;
}

export default function Badge({ tone = 'neutral', children, dot = false }: BadgeProps) {
  return <span className={`ui-badge ui-badge-${tone}`}>
    {dot && <span className="ui-badge-dot" aria-hidden="true" />}{children}
  </span>;
}
