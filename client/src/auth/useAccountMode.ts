import { useSyncExternalStore } from 'react';
import { useAuthentication } from './useAuthentication';
import { accountModeSnapshot, selectAccountMode, subscribeAccountMode } from './accountModeStore';
import type { AccountMode } from './accountModeStore';

export function useAccountMode() {
  const { user } = useAuthentication();
  const selected = useSyncExternalStore(subscribeAccountMode, () => accountModeSnapshot(user?.id, user?.roles));
  const isCourier = !!user?.roles.includes('COURIER');
  const isSender = !!user?.roles.includes('SENDER');
  const canSwitch = isCourier && isSender;
  const effectiveMode: AccountMode = canSwitch ? selected : isCourier ? 'COURIER' : 'SENDER';
  return {
    mode: effectiveMode,
    canSwitch,
    selectMode: (mode: AccountMode) => {
      if (user && (mode === 'SENDER' || canSwitch || isCourier)) selectAccountMode(user.id, mode);
    }
  };
}
