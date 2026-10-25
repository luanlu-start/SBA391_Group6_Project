import type {
  AuthenticationResponse,
  UserResponse,
} from '../types/identity';

interface SessionState {
  user: UserResponse | null;
  initializing: boolean;
}

let accessToken: string | null = null;
let accessTokenExpiresAt: string | null = null;

let state: SessionState = {
  user: null,
  initializing: true,
};

const listeners = new Set<() => void>();

export const getAccessToken = () => accessToken;

export const getAccessTokenExpiresAt = () => accessTokenExpiresAt;

export const getSessionState = () => state;

export function subscribeSession(listener: () => void) {
  listeners.add(listener);

  return () => {
    listeners.delete(listener);
  };
}

export function setAuthentication(response: AuthenticationResponse) {
  accessToken = response.accessToken;
  accessTokenExpiresAt = response.expiresAt;

  state = {
    user: response.user,
    initializing: false,
  };

  listeners.forEach(listener => listener());
}

export function clearAuthentication() {
  accessToken = null;
  accessTokenExpiresAt = null;

  state = {
    user: null,
    initializing: false,
  };

  listeners.forEach(listener => listener());
}

export function updateAuthenticatedUser(user: UserResponse) {
  if (state.user?.id !== user.id) return;
  state = { ...state, user };
  listeners.forEach(listener => listener());
}
