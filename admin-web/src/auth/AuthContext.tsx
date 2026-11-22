import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import {
  login as loginRequest,
  logout as logoutRequest,
  onUnauthorized,
  refreshSession,
  request,
  setAccessToken,
} from '../api/client';
import type { UserView } from '../api/types';

type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

type AuthContextValue = {
  status: AuthStatus;
  user: UserView | null;
  login: (username: string, password: string) => Promise<UserView>;
  logout: () => Promise<void>;
  hasPermission: (permission: string | string[]) => boolean;
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

  useEffect(() => {
    // 角色/权限可能被其他管理员调整，定期同步当前账号，菜单与按钮随之更新。
    const timer = window.setInterval(() => {
      request<UserView>('/api/admin/auth/me')
        .then((view) => setUser(view))
        .catch(() => undefined);
    }, 300_000);
    return () => window.clearInterval(timer);
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
    return session.user;
  }, []);

  const hasPermission = useCallback(
    (permission: string | string[]) => {
      const granted = user?.permissions ?? [];
      if (granted.includes('*')) {
        return true;
      }
      const required = Array.isArray(permission) ? permission : [permission];
      return required.some((item) => granted.includes(item));
    },
    [user],
  );

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
    () => ({ status, user, login, logout, hasPermission }),
    [status, user, login, logout, hasPermission],
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
