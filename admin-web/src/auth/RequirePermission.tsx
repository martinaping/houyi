import type { ReactNode } from 'react';
import { useAuth } from './AuthContext';
import { ForbiddenPage } from '../pages/ForbiddenPage';

type RequirePermissionProps = {
  permission: string | string[];
  children: ReactNode;
};

/** 路由级权限校验：没有权限时展示 403，而不是空白页。 */
export function RequirePermission({ permission, children }: RequirePermissionProps) {
  const { hasPermission } = useAuth();
  return hasPermission(permission) ? <>{children}</> : <ForbiddenPage />;
}
