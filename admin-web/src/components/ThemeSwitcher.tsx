import { BgColorsOutlined, CheckOutlined } from '@ant-design/icons';
import { Button, Dropdown, Space, Tooltip, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { useTranslation } from 'react-i18next';
import { useThemePreset } from '../theme/ThemeContext';
import { THEME_PRESETS } from '../theme/themes';
import type { ThemePreset } from '../theme/themes';

export function ThemeSwitcher({ floating = false }: { floating?: boolean }) {
  const { t, i18n } = useTranslation();
  const { preset, setTheme } = useThemePreset();
  const lang = i18n.language === 'en-US' ? 'en' : 'zh';
  const nameOf = (item: ThemePreset) => (lang === 'en' ? item.labelEn : item.labelZh);

  const item = (presetItem: ThemePreset): NonNullable<MenuProps['items']>[number] => ({
    key: presetItem.key,
    label: (
      <Space size={8} style={{ minWidth: 130, justifyContent: 'space-between' }}>
        <Space size={8}>
          <span
            style={{
              display: 'inline-block',
              width: 14,
              height: 14,
              borderRadius: '50%',
              background: presetItem.swatch,
              boxShadow: `0 0 0 2px ${presetItem.dark ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.12)'}`,
            }}
          />
          <span>{nameOf(presetItem)}</span>
          {presetItem.dark ? <Typography.Text type="secondary">Dark</Typography.Text> : null}
        </Space>
        {presetItem.key === preset.key ? <CheckOutlined /> : null}
      </Space>
    ),
  });

  const items: MenuProps['items'] = [
    {
      type: 'group',
      label: t('theme.lightGroup'),
      children: THEME_PRESETS.filter((item) => !item.dark).map(item),
    },
    {
      type: 'group',
      label: t('theme.darkGroup'),
      children: THEME_PRESETS.filter((item) => item.dark).map(item),
    },
  ];

  const button = (
    <Button
      icon={<BgColorsOutlined />}
      ghost={floating}
      type={floating ? 'primary' : 'default'}
      style={floating ? { backdropFilter: 'blur(6px)' } : undefined}
    >
      {floating ? nameOf(preset) : null}
    </Button>
  );

  return (
    <Dropdown
      trigger={['click']}
      menu={{
        items,
        selectable: true,
        selectedKeys: [preset.key],
        onClick: ({ key }) => setTheme(key),
      }}
    >
      <Tooltip title={t('theme.title')} placement="bottomRight">
        {button}
      </Tooltip>
    </Dropdown>
  );
}
