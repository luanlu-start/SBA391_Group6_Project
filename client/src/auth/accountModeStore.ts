export type AccountMode = 'SENDER' | 'COURIER';
const key = 'fpost_account_mode';
const listeners = new Set<() => void>();

export function readAccountMode(userId?: string, roles?: string[]): AccountMode {
  try {
    const value = JSON.parse(sessionStorage.getItem(key) ?? 'null');
    if (userId && value?.userId === userId && (value?.mode === 'COURIER' || value?.mode === 'SENDER')) {
      return value.mode;
    }
  } catch { /* fallback */ }

  if (roles?.includes('COURIER') && !roles?.includes('SENDER')) {
    return 'COURIER';
  }
  return 'SENDER';
}

export function selectAccountMode(userId: string, mode: AccountMode) {
  try { sessionStorage.setItem(key, JSON.stringify({ userId, mode })); } catch { /* Browser storage may be disabled. */ }
  current = { userId, mode };
  listeners.forEach(listener => listener());
}

let current: { userId: string; mode: AccountMode } | undefined;
export const accountModeSnapshot = (userId?: string, roles?: string[]) =>
  current && current.userId === userId ? current.mode : readAccountMode(userId, roles);

export function clearAccountMode() {
  current = undefined;
  try { sessionStorage.removeItem(key); } catch { /* Optional UI preference. */ }
  listeners.forEach(listener => listener());
}

export function subscribeAccountMode(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}
