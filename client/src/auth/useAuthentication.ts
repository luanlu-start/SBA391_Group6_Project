import { useSyncExternalStore } from 'react';
import { getSessionState, subscribeSession } from './authSession';

export function useAuthentication() { return useSyncExternalStore(subscribeSession, getSessionState); }
