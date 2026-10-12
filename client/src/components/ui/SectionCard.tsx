import type { ReactNode } from 'react';

interface SectionCardProps {
  title?: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  id?: string;
}

export default function SectionCard({ title, description, actions, children, className = '', id }: SectionCardProps) {
  return <section id={id} className={`ui-card ${className}`}>
    {(title || actions) && <header className="ui-card-header">
      <div>{title && <h2>{title}</h2>}{description && <p>{description}</p>}</div>{actions}
    </header>}
    <div className="ui-card-body">{children}</div>
  </section>;
}
