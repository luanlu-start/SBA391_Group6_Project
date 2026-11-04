import { forwardRef, useId } from 'react';
import type { SelectHTMLAttributes } from 'react';
import FormField from './FormField';
import type { FieldPresentation } from './FormField';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement>, FieldPresentation {
  fieldClassName?: string;
}

const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select({ id, label, hint, error,
  fieldClassName, className = '', required, children, 'aria-describedby': describedBy,
  'aria-invalid': invalid, ...props }, ref) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const description = [describedBy, hint && `${fieldId}-hint`, error && `${fieldId}-error`].filter(Boolean).join(' ');
  return (
    <FormField id={fieldId} label={label} hint={hint} error={error} required={required} className={fieldClassName}>
      <select {...props} ref={ref} id={fieldId} required={required} className={`ui-control ui-select ${className}`}
        aria-invalid={error ? true : invalid} aria-describedby={description || undefined}>{children}</select>
    </FormField>
  );
});

export default Select;
