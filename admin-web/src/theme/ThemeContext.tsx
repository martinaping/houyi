import { createContext, useContext, useLayoutEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import {
  applyThemeDocument,
  findTheme,
  initialThemeKey,
  THEME_STORAGE_KEY,
} from './themes';
import type { ThemePreset } from './themes';

type ThemeContextValue = {
  preset: ThemePreset;
  setTheme: (key: string) => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [themeKey, setThemeKey] = useState(initialThemeKey);
  const preset = findTheme(themeKey);

  useLayoutEffect(() => {
    applyThemeDocument(preset);
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, preset.key);
    } catch {
      // 无痕模式等场景下仅放弃持久化，不影响当前会话切换
    }
  }, [preset]);

  const value = useMemo<ThemeContextValue>(
    () => ({ preset, setTheme: setThemeKey }),
    [preset],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useThemePreset(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useThemePreset must be used inside ThemeProvider');
  }
  return context;
}
