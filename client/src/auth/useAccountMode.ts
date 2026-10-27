import { useSyncExternalStore } from 'react';
import { useAuthentication } from './useAuthentication';
import { accountModeSnapshot, selectAccountMode, subscribeAccountMode } from './accountModeStore';
import type { AccountMode } from './accountModeStore';

export function useAccountMode() {
  const { user } = useAuthentication();
  const selected = useSyncExternalStore(subscribeAccountMode, () => accountModeSnapshot(user?.id));
  const canSwitch = !!user?.roles.includes('COURIER');
  return { mode: canSwitch ? selected : 'SENDER', canSwitch,
    selectMode: (mode: AccountMode) => { if (user && (mode === 'SENDER' || canSwitch)) selectAccountMode(user.id, mode); } };
}
