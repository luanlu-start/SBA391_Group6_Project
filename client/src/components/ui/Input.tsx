import { forwardRef, useId } from 'react';
import type { InputHTMLAttributes, ReactNode } from 'react';
import FormField from './FormField';
import type { FieldPresentation } from './FormField';

interface InputProps extends InputHTMLAttributes<HTMLInputElement>, FieldPresentation {
  icon?: ReactNode;
  endAdornment?: ReactNode;
  fieldClassName?: string;
}

const Input = forwardRef<HTMLInputElement, InputProps>(function Input({ id, label, hint, error,
  icon, endAdornment, fieldClassName, className = '', required, 'aria-describedby': describedBy,
  'aria-invalid': invalid, ...props }, ref) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const description = [describedBy, hint && `${fieldId}-hint`, error && `${fieldId}-error`].filter(Boolean).join(' ');
  return (
    <FormField id={fieldId} label={label} hint={hint} error={error} required={required} className={fieldClassName}>
      <div className={`ui-input-wrap${icon ? ' ui-input-with-icon' : ''}${endAdornment ? ' ui-input-with-end' : ''}`}>
        {icon && <span className="ui-input-icon" aria-hidden="true">{icon}</span>}
        <input {...props} ref={ref} id={fieldId} required={required}
          className={`ui-control ${className}`} aria-invalid={error ? true : invalid}
          aria-describedby={description || undefined} />
        {endAdornment && <span className="ui-input-end">{endAdornment}</span>}
      </div>
    </FormField>
  );
});

export default Input;
