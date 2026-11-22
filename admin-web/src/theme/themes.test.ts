import { describe, expect, it } from 'vitest';
import { findTheme, THEME_PRESETS, themeConfigOf } from './themes';

describe('theme presets', () => {
  it('has unique keys and at least three light and dark themes', () => {
    expect(new Set(THEME_PRESETS.map((item) => item.key)).size).toBe(THEME_PRESETS.length);
    expect(THEME_PRESETS.filter((item) => !item.dark).length).toBeGreaterThanOrEqual(3);
    expect(THEME_PRESETS.filter((item) => item.dark).length).toBeGreaterThanOrEqual(2);
  });

  it('falls back to the first theme for unknown keys', () => {
    expect(findTheme('missing').key).toBe(THEME_PRESETS[0]!.key);
  });

  it('builds antd config with the preset primary color and sidebar background', () => {
    for (const preset of THEME_PRESETS) {
      const config = themeConfigOf(preset);
      expect(config.token?.colorPrimary).toBe(preset.colorPrimary);
      expect(config.token?.colorBgLayout).toBe(preset.layoutBg);
      expect(config.components?.Menu?.darkItemBg).toBe(preset.siderBg);
    }
  });
});
