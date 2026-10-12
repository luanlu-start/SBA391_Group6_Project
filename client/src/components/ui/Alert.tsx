import { CircleCheck, CircleAlert, Info, TriangleAlert } from 'lucide-react';
import type { ReactNode } from 'react';

interface AlertProps {
  tone?: 'info' | 'success' | 'warning' | 'danger';
  title?: string;
  children: ReactNode;
  action?: ReactNode;
}

export default function Alert({ tone = 'info', title, children, action }: AlertProps) {
  const Icon = { info: Info, success: CircleCheck, warning: TriangleAlert, danger: CircleAlert }[tone];
  return <div className={`ui-alert ui-alert-${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>
    <Icon size={19} aria-hidden="true" />
    <div className="ui-alert-content">{title && <p className="ui-alert-title">{title}</p>}
      <div>{children}</div>{action && <div className="ui-alert-action">{action}</div>}
    </div>
  </div>;
}
