import React, {
  createContext,
  ReactNode,
  useCallback,
  useContext,
  useMemo,
  useState,
} from 'react';

import { settings } from '../settings';
import { login as apiLogin, LoginResponse, Role } from '../api/auth';
import { loadItem, removeItem, saveItem } from './storage';

const STORAGE_KEY = 'authenticself.auth.v1';

export type AuthState = {
  token:  string;
  userId: string;
  role:   Role;
  name:   string;
};

export type AuthContextValue = {
  state:       AuthState | null;
  isLoggedIn:  boolean;
  /** Throws on failure (network or 4xx) so the caller can render an error toast. */
  login:       (username: string, password: string) => Promise<void>;
  logout:      () => void;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function readPersisted(): AuthState | null {
  const raw = loadItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<AuthState> | null;
    if (
      parsed
      && typeof parsed.token === 'string'
      && typeof parsed.userId === 'string'
      && (parsed.role === 'USER' || parsed.role === 'ADMIN')
      && typeof parsed.name === 'string'
    ) {
      return parsed as AuthState;
    }
  } catch {
    // Fall through and treat as no session.
  }
  return null;
}

/**
 * Mirror the session into the mutable `settings` globals read by the api
 * layer: `userId` (X-User-Id header) and `token` (Authorization: Bearer).
 * Pass empty strings on logout to clear both.
 */
function syncAuthToSettings(userId: string, token: string): void {
  settings.userId = userId;
  settings.token = token;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  // Restore synchronously so the first render already knows whether to mount
  // AuthStack vs UserStack/AdminStack — avoids a login-screen flash on reload.
  const [state, setState] = useState<AuthState | null>(() => {
    const restored = readPersisted();
    if (restored) syncAuthToSettings(restored.userId, restored.token);
    return restored;
  });

  const login = useCallback(async (username: string, password: string) => {
    const resp: LoginResponse = await apiLogin({
      baseUrl: settings.apiBaseUrl,
      username,
      password,
    });
    const next: AuthState = {
      token:  resp.token,
      userId: resp.userId,
      role:   resp.role,
      name:   resp.name,
    };
    saveItem(STORAGE_KEY, JSON.stringify(next));
    syncAuthToSettings(next.userId, next.token);
    setState(next);
  }, []);

  const logout = useCallback(() => {
    removeItem(STORAGE_KEY);
    syncAuthToSettings('', '');
    setState(null);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    state,
    isLoggedIn: state != null,
    login,
    logout,
  }), [state, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>');
  return ctx;
}
