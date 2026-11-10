import { useSyncExternalStore } from 'react';
import { getThemeSnapshot, setThemeMode, subscribeTheme, toggleThemeMode } from './themeStore';

export function useTheme() {
  const theme = useSyncExternalStore(subscribeTheme, getThemeSnapshot);
  return {
    theme,
    isDark: theme === 'dark',
    setTheme: setThemeMode,
    toggleTheme: toggleThemeMode,
  };
}
