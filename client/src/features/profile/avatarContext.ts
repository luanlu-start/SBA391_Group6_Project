import { createContext, useContext } from 'react';

export interface AvatarState {
  url: string;
  loading: boolean;
  failed: boolean;
  refresh: () => Promise<void>;
}

export const AvatarContext = createContext<AvatarState | null>(null);

export function useAvatar() {
  const context = useContext(AvatarContext);
  if (!context) throw new Error('AvatarProvider is required');
  return context;
}
