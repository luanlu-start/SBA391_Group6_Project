import type { ReactNode } from 'react';

interface FormSectionProps {
  title: string;
  description?: string;
  children: ReactNode;
  disabled?: boolean;
}

export default function FormSection({ title, description, children, disabled }: FormSectionProps) {
  return <fieldset className="ui-form-section" disabled={disabled}>
    <legend>{title}</legend>{description && <p className="ui-form-description">{description}</p>}
    <div className="ui-form-grid">{children}</div>
  </fieldset>;
}
