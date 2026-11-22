import { theme } from 'antd';
import type { ThemeConfig } from 'antd';

export type ThemePreset = {
  key: string;
  labelZh: string;
  labelEn: string;
  dark: boolean;
  colorPrimary: string;
  borderRadius: number;
  layoutBg: string;
  containerBg?: string;
  elevatedBg?: string;
  loginGradient: string;
  swatch: string;
  siderBg: string;
};

export const THEME_STORAGE_KEY = 'houyi.admin.theme';

export const THEME_PRESETS: ThemePreset[] = [
  {
    key: 'ocean',
    labelZh: '海雾蓝',
    labelEn: 'Ocean blue',
    dark: false,
    colorPrimary: '#1677ff',
    borderRadius: 6,
    layoutBg: '#f4f7fb',
    loginGradient: 'linear-gradient(135deg, #0f2027 0%, #203a43 50%, #2c5364 100%)',
    swatch: '#1677ff',
    siderBg: '#001529',
  },
  {
    key: 'jade',
    labelZh: '翡翠绿',
    labelEn: 'Jade green',
    dark: false,
    colorPrimary: '#0f9d58',
    borderRadius: 6,
    layoutBg: '#f4faf6',
    loginGradient: 'linear-gradient(135deg, #0b3d2e 0%, #16694f 55%, #2fa47a 100%)',
    swatch: '#0f9d58',
    siderBg: '#0c2d22',
  },
  {
    key: 'violet',
    labelZh: '紫罗兰',
    labelEn: 'Violet',
    dark: false,
    colorPrimary: '#7c4dff',
    borderRadius: 10,
    layoutBg: '#f7f5ff',
    loginGradient: 'linear-gradient(135deg, #1f1147 0%, #4b2e83 55%, #7c4dff 100%)',
    swatch: '#7c4dff',
    siderBg: '#1c1238',
  },
  {
    key: 'sunset',
    labelZh: '日落橙',
    labelEn: 'Sunset orange',
    dark: false,
    colorPrimary: '#fa8c16',
    borderRadius: 8,
    layoutBg: '#fff8f1',
    loginGradient: 'linear-gradient(135deg, #3a1c2b 0%, #8e3b46 55%, #f2994a 100%)',
    swatch: '#fa8c16',
    siderBg: '#2b1620',
  },
  {
    key: 'rose',
    labelZh: '玫瑰粉',
    labelEn: 'Rose pink',
    dark: false,
    colorPrimary: '#e8467c',
    borderRadius: 12,
    layoutBg: '#fff5f8',
    loginGradient: 'linear-gradient(135deg, #3d1225 0%, #8e2a52 55%, #e8467c 100%)',
    swatch: '#e8467c',
    siderBg: '#2b1020',
  },
  {
    key: 'midnight',
    labelZh: '暗夜蓝',
    labelEn: 'Midnight blue',
    dark: true,
    colorPrimary: '#4c8dff',
    borderRadius: 8,
    layoutBg: '#0f141c',
    containerBg: '#151c26',
    elevatedBg: '#1b2431',
    loginGradient: 'linear-gradient(135deg, #070b14 0%, #101a2b 55%, #1e3a5f 100%)',
    swatch: '#4c8dff',
    siderBg: '#0a1017',
  },
  {
    key: 'obsidian',
    labelZh: '曜石青',
    labelEn: 'Obsidian teal',
    dark: true,
    colorPrimary: '#2dd4bf',
    borderRadius: 8,
    layoutBg: '#101314',
    containerBg: '#161b1c',
    elevatedBg: '#1d2425',
    loginGradient: 'linear-gradient(135deg, #0a0f10 0%, #12201f 55%, #1f3d3a 100%)',
    swatch: '#2dd4bf',
    siderBg: '#0b0f10',
  },
  {
    key: 'aurora',
    labelZh: '极光紫',
    labelEn: 'Aurora violet',
    dark: true,
    colorPrimary: '#a78bfa',
    borderRadius: 10,
    layoutBg: '#121019',
    containerBg: '#191624',
    elevatedBg: '#211d30',
    loginGradient: 'linear-gradient(135deg, #0d0a18 0%, #241a3d 55%, #4c3b8f 100%)',
    swatch: '#a78bfa',
    siderBg: '#100d18',
  },
];

export const DEFAULT_THEME_KEY = 'ocean';

export function findTheme(key: string | null | undefined): ThemePreset {
  return THEME_PRESETS.find((preset) => preset.key === key) ?? THEME_PRESETS[0]!;
}

export function initialThemeKey(): string {
  try {
    return findTheme(window.localStorage.getItem(THEME_STORAGE_KEY)).key;
  } catch {
    return DEFAULT_THEME_KEY;
  }
}

export function themeConfigOf(preset: ThemePreset): ThemeConfig {
  return {
    algorithm: preset.dark ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      colorPrimary: preset.colorPrimary,
      colorInfo: preset.colorPrimary,
      borderRadius: preset.borderRadius,
      colorBgLayout: preset.layoutBg,
      ...(preset.containerBg ? { colorBgContainer: preset.containerBg } : {}),
      ...(preset.elevatedBg ? { colorBgElevated: preset.elevatedBg } : {}),
    },
    components: {
      Menu: {
        darkItemBg: preset.siderBg,
        darkSubMenuItemBg: preset.siderBg,
      },
    },
  };
}

export function applyThemeDocument(preset: ThemePreset) {
  const root = document.documentElement;
  root.dataset.houyiTheme = preset.key;
  root.style.colorScheme = preset.dark ? 'dark' : 'light';
  root.style.setProperty('--houyi-layout-bg', preset.layoutBg);
  const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  if (meta) {
    meta.content = preset.dark ? preset.layoutBg : preset.colorPrimary;
  }
}
