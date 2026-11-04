import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { LoaderCircle } from 'lucide-react';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  size?: 'sm' | 'md';
  loading?: boolean;
  loadingText?: string;
  icon?: ReactNode;
  fullWidth?: boolean;
}

export default function Button({ variant = 'primary', size = 'md', loading = false,
  loadingText, icon, fullWidth = false, disabled, type = 'button', className = '', children,
  ...props }: ButtonProps) {
  return (
    <button {...props} type={type} disabled={disabled || loading} aria-busy={loading || undefined}
      className={`ui-button ui-button-${variant} ui-button-${size}${fullWidth ? ' ui-button-full' : ''} ${className}`}>
      {loading ? <LoaderCircle size={17} className="ui-spin" aria-hidden="true" />
        : icon && <span className="ui-button-icon" aria-hidden="true">{icon}</span>}
      {loading && loadingText ? loadingText : children}
    </button>
  );
}
