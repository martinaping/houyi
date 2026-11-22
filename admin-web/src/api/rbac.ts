import { request } from './client';
import type { RoleBrief } from './types';

export type RoleView = {
  id: number;
  code: string;
  name: string;
  description: string | null;
  builtin: boolean;
  permissions: string[];
  userCount: number;
};

export type RbacUserView = {
  id: number;
  username: string;
  displayName: string;
  enabled: boolean;
  roles: RoleBrief[];
  lastLoginAt: number | null;
  lastLoginIp: string | null;
  createdAt: number;
};

export type PermissionView = {
  code: string;
  labelZh: string;
  labelEn: string;
};

export type PermissionGroupView = {
  key: string;
  labelZh: string;
  labelEn: string;
  permissions: PermissionView[];
};

export type RolePayload = {
  code?: string;
  name: string;
  description?: string;
  permissions: string[];
};

export type UserPayload = {
  username?: string;
  displayName?: string;
  password?: string;
  roleIds: number[];
  enabled: boolean;
};

export const rbacApi = {
  permissions: () => request<PermissionGroupView[]>('/api/admin/rbac/permissions'),
  roles: () => request<RoleView[]>('/api/admin/rbac/roles'),
  createRole: (body: RolePayload) =>
    request<RoleView>('/api/admin/rbac/roles', { method: 'POST', body: JSON.stringify(body) }),
  updateRole: (id: number, body: RolePayload) =>
    request<RoleView>(`/api/admin/rbac/roles/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteRole: (id: number) => request<void>(`/api/admin/rbac/roles/${id}`, { method: 'DELETE' }),
  users: (keyword?: string) =>
    request<RbacUserView[]>(`/api/admin/rbac/users${keyword ? `?keyword=${encodeURIComponent(keyword)}` : ''}`),
  createUser: (body: UserPayload) =>
    request<RbacUserView>('/api/admin/rbac/users', { method: 'POST', body: JSON.stringify(body) }),
  updateUser: (id: number, body: UserPayload) =>
    request<RbacUserView>(`/api/admin/rbac/users/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  resetPassword: (id: number, password: string) =>
    request<void>(`/api/admin/rbac/users/${id}/password`, {
      method: 'POST',
      body: JSON.stringify({ password }),
    }),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('/api/admin/auth/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    }),
};
