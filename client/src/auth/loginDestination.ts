// Only local destinations are accepted from router state.
export function getLoginDestination(state: unknown): string {
  if (state && typeof state === 'object' && 'returnTo' in state && typeof state.returnTo === 'string') {
    const target = state.returnTo;
    if (target.startsWith('/') && !target.startsWith('//') && !target.includes('\\')
      && !/^\/(login|register|verify)(?:[/?#]|$)/.test(target)) return target;
  }
  return '/account';
}
