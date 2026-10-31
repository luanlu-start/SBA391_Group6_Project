import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { authService } from '../services/authService';
import { clearAuthentication, getSessionState } from './authSession';

export default function AuthProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    if (getSessionState().initializing) void authService.refresh().catch(() => clearAuthentication());
  }, []);
  return children;
}
