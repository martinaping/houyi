const ROUTES: Array<{ permission: string; path: string }> = [
  { permission: 'overview.read', path: '/' },
  { permission: 'health.read', path: '/health' },
  { permission: 'monitor.read', path: '/monitor' },
  { permission: 'corps.read', path: '/corps' },
  { permission: 'messages.read', path: '/messages' },
  { permission: 'queue.read', path: '/queue' },
  { permission: 'retention.read', path: '/retention' },
  { permission: 'audit.read', path: '/audit' },
  { permission: 'logs.read', path: '/logs' },
  { permission: 'settings.read', path: '/settings' },
  { permission: 'system.read', path: '/system' },
  { permission: 'users.manage', path: '/access' },
  { permission: 'roles.manage', path: '/access' },
];

const PATH_ROUTES: Array<{ prefix: string; permissions: string[] }> = [
  { prefix: '/health', permissions: ['health.read'] },
  { prefix: '/monitor', permissions: ['monitor.read'] },
  { prefix: '/corps', permissions: ['corps.read'] },
  { prefix: '/messages', permissions: ['messages.read'] },
  { prefix: '/queue', permissions: ['queue.read'] },
  { prefix: '/rocketmq', permissions: ['queue.read'] },
  { prefix: '/redis', permissions: ['queue.read'] },
  { prefix: '/mns', permissions: ['queue.read'] },
  { prefix: '/retention', permissions: ['retention.read'] },
  { prefix: '/audit', permissions: ['audit.read'] },
  { prefix: '/logs', permissions: ['logs.read'] },
  { prefix: '/settings', permissions: ['settings.read'] },
  { prefix: '/system', permissions: ['system.read'] },
  { prefix: '/access', permissions: ['users.manage', 'roles.manage'] },
];

/** 判断账号能否访问指定站内路径。 */
export function canAccessPath(path: string, permissions: string[]): boolean {
  if (permissions.includes('*')) {
    return true;
  }
  if (path === '/' || path === '') {
    return permissions.includes('overview.read');
  }
  if (path.startsWith('/api-docs')) {
    return true;
  }
  const route = PATH_ROUTES.find((item) => path.startsWith(item.prefix));
  return route ? route.permissions.some((permission) => permissions.includes(permission)) : true;
}

/** 登录后落到当前账号有权限访问的第一个页面。 */
export function firstAllowedPath(permissions: string[]): string {
  if (permissions.includes('*')) {
    return '/';
  }
  const match = ROUTES.find((route) => permissions.includes(route.permission));
  return match?.path ?? '/api-docs';
}
