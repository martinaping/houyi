import type { ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';

/** 按钮级权限控制：无权限时不渲染。 */
export function Can({ permission, children }: { permission: string | string[]; children: ReactNode }) {
  const { hasPermission } = useAuth();
  return hasPermission(permission) ? <>{children}</> : null;
}
