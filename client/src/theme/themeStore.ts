export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'fpost_theme';
const listeners = new Set<() => void>();

function readStoredTheme(): ThemeMode {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'dark' || saved === 'light') {
      return saved;
    }
  } catch {
    /* ignore storage errors */
  }
  return 'light';
}

let currentTheme: ThemeMode = readStoredTheme();

function applyThemeToDom(theme: ThemeMode) {
  if (typeof document !== 'undefined') {
    document.documentElement.dataset.theme = theme;
  }
}

export function initTheme(): ThemeMode {
  currentTheme = readStoredTheme();
  applyThemeToDom(currentTheme);
  return currentTheme;
}

export function getThemeSnapshot(): ThemeMode {
  return currentTheme;
}

export function setThemeMode(next: ThemeMode) {
  currentTheme = next;
  try {
    localStorage.setItem(STORAGE_KEY, next);
  } catch {
    /* ignore storage errors */
  }
  applyThemeToDom(next);
  listeners.forEach(listener => listener());
}

export function toggleThemeMode() {
  setThemeMode(currentTheme === 'dark' ? 'light' : 'dark');
}

export function subscribeTheme(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
