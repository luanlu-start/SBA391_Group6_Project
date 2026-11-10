import { Moon, Sun } from 'lucide-react';
import { useTheme } from '../../theme/useTheme';

export default function ThemeToggleButton({ compact = false }: { compact?: boolean }) {
  const { isDark, toggleTheme } = useTheme();
  const nextLabel = isDark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối';

  return (
    <button
      type="button"
      className={`ui-button ui-button-ghost ui-button-sm theme-toggle-btn${compact ? ' is-compact' : ''}`}
      onClick={toggleTheme}
      aria-label={nextLabel}
      aria-pressed={isDark}
      title={nextLabel}
    >
      {isDark ? <Sun size={16} aria-hidden="true" /> : <Moon size={16} aria-hidden="true" />}
      {!compact && <span className="theme-toggle-label">{isDark ? 'Sáng' : 'Tối'}</span>}
    </button>
  );
}
