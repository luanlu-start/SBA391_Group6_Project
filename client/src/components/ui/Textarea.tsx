import { forwardRef, useId } from 'react';
import type { TextareaHTMLAttributes } from 'react';
import FormField from './FormField';
import type { FieldPresentation } from './FormField';

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement>, FieldPresentation {
  fieldClassName?: string;
}

const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea({ id, label, hint, error,
  fieldClassName, className = '', required, rows = 3, 'aria-describedby': describedBy,
  'aria-invalid': invalid, ...props }, ref) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const description = [describedBy, hint && `${fieldId}-hint`, error && `${fieldId}-error`].filter(Boolean).join(' ');
  return (
    <FormField id={fieldId} label={label} hint={hint} error={error} required={required} className={fieldClassName}>
      <textarea {...props} ref={ref} id={fieldId} rows={rows} required={required}
        className={`ui-control ui-textarea ${className}`} aria-invalid={error ? true : invalid}
        aria-describedby={description || undefined} />
    </FormField>
  );
});

export default Textarea;
