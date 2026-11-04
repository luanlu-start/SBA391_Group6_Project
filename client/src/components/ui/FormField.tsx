import type { ReactNode } from 'react';

export interface FieldPresentation {
  label: string;
  hint?: string;
  error?: string;
}

interface FormFieldProps extends FieldPresentation {
  id: string;
  required?: boolean;
  className?: string;
  children: ReactNode;
}

export default function FormField({ id, label, hint, error, required, className = '', children }: FormFieldProps) {
  return (
    <div className={`ui-field ${className}`}>
      <label className="ui-field-label" htmlFor={id}>{label}
        {required && <span className="ui-field-required" aria-hidden="true"> *</span>}
      </label>
      {children}
      {hint && <p id={`${id}-hint`} className="ui-field-hint">{hint}</p>}
      {error && <p id={`${id}-error`} className="ui-field-error" role="alert">{error}</p>}
    </div>
  );
}
