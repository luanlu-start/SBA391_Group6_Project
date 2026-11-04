import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  description?: string;
  eyebrow?: string;
  actions?: ReactNode;
}

export default function PageHeader({ title, description, eyebrow, actions }: PageHeaderProps) {
  return <header className="ui-page-header">
    <div>{eyebrow && <p className="ui-eyebrow">{eyebrow}</p>}
      <h1>{title}</h1>{description && <p className="ui-page-description">{description}</p>}
    </div>
    {actions && <div className="ui-page-actions">{actions}</div>}
  </header>;
}
