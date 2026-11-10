// Only local destinations are accepted from router state.
export function getLoginDestination(state: unknown, roles: readonly string[] = []): string {
  const home = roles.includes('ADMIN') ? '/admin/dashboard'
    : roles.includes('MODERATOR') ? '/mod/kyc' : '/account';
  if (state && typeof state === 'object' && 'returnTo' in state && typeof state.returnTo === 'string') {
    const target = state.returnTo;
    if (target.startsWith('/') && !target.startsWith('//') && !target.includes('\\')
      && !/^\/(login|register|verify)(?:[/?#]|$)/.test(target)) {
      // Staff sign in to their workspace instead of a saved customer destination.
      if (roles.includes('ADMIN')) return /^\/(admin|mod)(?:[/?#]|$)/.test(target) ? target : home;
      if (roles.includes('MODERATOR')) return /^\/mod(?:[/?#]|$)/.test(target) ? target : home;
      if (/^\/(admin|mod)(?:[/?#]|$)/.test(target)) return home;
      return target;
    }
  }
  return home;
}
