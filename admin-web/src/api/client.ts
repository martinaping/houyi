import type { ApiErrorBody, LoginResponse } from './types';

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
const UNAUTHORIZED_EVENT = 'houyi:unauthorized';
const SESSION_EXPIRED_KEY = 'houyi:session-expired';

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly details: string[];
  readonly traceId: string;

  constructor(status: number, body: Partial<ApiErrorBody>) {
    super(body.message ?? 'request failed');
    this.name = 'ApiError';
    this.code = body.code ?? 'UNKNOWN';
    this.status = status;
    this.details = body.details ?? [];
    this.traceId = body.traceId ?? '';
  }
}

let accessToken: string | null = null;
let refreshPromise: Promise<LoginResponse | null> | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as Partial<ApiErrorBody>;
    return new ApiError(response.status, body);
  } catch (error) {
    console.warn('解析错误响应失败', error);
    return new ApiError(response.status, { message: `HTTP ${response.status}` });
  }
}

async function peekErrorCode(response: Response): Promise<string> {
  try {
    const body = (await response.clone().json()) as Partial<ApiErrorBody>;
    return body.code ?? '';
  } catch {
    return '';
  }
}

async function rawFetch(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }
  return fetch(`${API_BASE}${path}`, { ...init, headers, credentials: 'include' });
}

/**
 * 全局请求入口：自动补 Bearer Token，遇到 401 时静默刷新并重试一次。
 * 仅当 401 的业务码为 UNAUTHORIZED（会话失效）时才刷新；
 * 其他 401（如查看密钥时密码校验失败）直接抛错，避免误登出。
 */
export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response = await rawFetch(path, init);
  if (response.status === 401 && !path.startsWith('/api/admin/auth/')) {
    const code = await peekErrorCode(response);
    if (code === 'UNAUTHORIZED') {
      const session = await refreshSession();
      if (session) {
        response = await rawFetch(path, init);
      } else {
        notifyUnauthorized();
      }
    }
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/**
 * 下载文件（如 CSV 导出）：自动携带 Bearer Token，401 时刷新后重试一次，
 * 并按响应头中的文件名保存。
 */
export async function download(path: string, init: RequestInit = {}, fallbackName = 'download.csv'): Promise<void> {
  let response = await rawFetch(path, init);
  if (response.status === 401 && !path.startsWith('/api/admin/auth/')) {
    const code = await peekErrorCode(response);
    if (code === 'UNAUTHORIZED') {
      const session = await refreshSession();
      if (session) {
        response = await rawFetch(path, init);
      } else {
        notifyUnauthorized();
      }
    }
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filenameFrom(response.headers.get('Content-Disposition')) ?? fallbackName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

function filenameFrom(disposition: string | null): string | null {
  if (!disposition) {
    return null;
  }
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  if (encoded) {
    try {
      return decodeURIComponent(encoded.trim());
    } catch (error) {
      console.warn('解析下载文件名失败', error);
    }
  }
  return /filename="?([^";]+)"?/i.exec(disposition)?.[1]?.trim() ?? null;
}

/**
 * 使用 HttpOnly Cookie 刷新 access token，并发调用会复用同一个请求。
 */
export function refreshSession(): Promise<LoginResponse | null> {
  if (refreshPromise) {
    return refreshPromise;
  }
  refreshPromise = (async () => {
    try {
      const response = await fetch(`${API_BASE}/api/admin/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
      });
      if (!response.ok) {
        accessToken = null;
        return null;
      }
      const session = (await response.json()) as LoginResponse;
      accessToken = session.accessToken;
      return session;
    } catch (error) {
      console.warn('刷新登录状态失败', error);
      accessToken = null;
      return null;
    } finally {
      refreshPromise = null;
    }
  })();
  return refreshPromise;
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  const session = await request<LoginResponse>('/api/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
  accessToken = session.accessToken;
  return session;
}

export async function logout(): Promise<void> {
  try {
    await request<void>('/api/admin/auth/logout', { method: 'POST' });
  } finally {
    accessToken = null;
  }
}

export function notifyUnauthorized(): void {
  try {
    sessionStorage.setItem(SESSION_EXPIRED_KEY, '1');
  } catch (error) {
    console.warn('记录登录失效状态失败', error);
  }
  window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
}

/**
 * 读取并清除“登录状态已失效”标记，供登录页展示一次性提示。
 */
export function consumeSessionExpired(): boolean {
  try {
    const flag = sessionStorage.getItem(SESSION_EXPIRED_KEY);
    if (flag) {
      sessionStorage.removeItem(SESSION_EXPIRED_KEY);
      return true;
    }
  } catch (error) {
    console.warn('读取登录失效状态失败', error);
  }
  return false;
}

export function onUnauthorized(listener: () => void): () => void {
  window.addEventListener(UNAUTHORIZED_EVENT, listener);
  return () => window.removeEventListener(UNAUTHORIZED_EVENT, listener);
}
