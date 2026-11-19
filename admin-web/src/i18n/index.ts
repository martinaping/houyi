import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import zhCN from './locales/zh-CN.json';
import enUS from './locales/en-US.json';

export const LANGUAGE_STORAGE_KEY = 'houyi-admin-lang';

export type AppLanguage = 'zh-CN' | 'en-US';

function resolveInitialLanguage(): AppLanguage {
  const stored = localStorage.getItem(LANGUAGE_STORAGE_KEY);
  return stored === 'en-US' ? 'en-US' : 'zh-CN';
}

void i18n.use(initReactI18next).init({
  resources: {
    'zh-CN': { translation: zhCN },
    'en-US': { translation: enUS },
  },
  lng: resolveInitialLanguage(),
  fallbackLng: 'zh-CN',
  interpolation: { escapeValue: false },
});

export function changeLanguage(language: AppLanguage): void {
  localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
  void i18n.changeLanguage(language);
}

export default i18n;
