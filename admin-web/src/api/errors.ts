import type { TFunction } from 'i18next';
import { ApiError } from './client';

/**
 * 把后端错误码映射到本地化文案，找不到时回退到服务端 message。
 */
export function errorMessage(t: TFunction, error: unknown): string {
  if (error instanceof ApiError) {
    return t(`errors.${error.code}`, { defaultValue: error.message });
  }
  if (error instanceof Error) {
    return error.message;
  }
  return t('errors.UNKNOWN');
}
