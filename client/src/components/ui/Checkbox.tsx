import { forwardRef, useId } from 'react';
import type { InputHTMLAttributes, ReactNode } from 'react';

interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: ReactNode;
  error?: string;
}

const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox({ id, label, error,
  className = '', 'aria-describedby': describedBy, 'aria-invalid': invalid, ...props }, ref) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  return <div className={`ui-checkbox-field ${className}`}>
    <div className="ui-checkbox-row">
      <input {...props} ref={ref} id={fieldId} type="checkbox" aria-invalid={error ? true : invalid}
        aria-describedby={[describedBy, error && `${fieldId}-error`].filter(Boolean).join(' ') || undefined} />
      <label htmlFor={fieldId}>{label}</label>
    </div>
    {error && <p id={`${fieldId}-error`} className="ui-field-error" role="alert">{error}</p>}
  </div>;
});

export default Checkbox;
