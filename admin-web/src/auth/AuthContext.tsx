import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import {
  login as loginRequest,
  logout as logoutRequest,
  onUnauthorized,
  refreshSession,
  setAccessToken,
} from '../api/client';
import type { UserView } from '../api/types';

type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

type AuthContextValue = {
  status: AuthStatus;
  user: UserView | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<UserView | null>(null);

  useEffect(() => {
    let cancelled = false;
    void refreshSession().then((session) => {
      if (cancelled) {
        return;
      }
      if (session) {
        setUser(session.user);
        setStatus('authenticated');
      } else {
        setStatus('anonymous');
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(
    () =>
      onUnauthorized(() => {
        queryClient.clear();
        setUser(null);
        setStatus('anonymous');
      }),
    [queryClient],
  );

  const login = useCallback(async (username: string, password: string) => {
    const session = await loginRequest(username, password);
    setUser(session.user);
    setStatus('authenticated');
  }, []);

  const logout = useCallback(async () => {
    try {
      await logoutRequest();
    } finally {
      // 清空查询缓存，避免退出后仍能在内存中看到上一个会话的敏感数据。
      queryClient.clear();
      setAccessToken(null);
      setUser(null);
      setStatus('anonymous');
    }
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, logout }),
    [status, user, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
