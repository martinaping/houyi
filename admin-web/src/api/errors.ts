import type { TFunction } from 'i18next';
import { ApiError } from './client';

/**
 * 把后端错误码映射到本地化文案，找不到时回退到服务端 message。
 */
export function errorMessage(t: TFunction, error: unknown): string {
  if (error instanceof ApiError) {
    const localized = t(`errors.${error.code}`, { defaultValue: '' });
    const base = localized || error.message;
    const details = error.details.length > 0 ? error.details.slice(0, 2).join('; ') : '';
    return details ? `${base} (${details})` : base;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return t('errors.UNKNOWN');
}
