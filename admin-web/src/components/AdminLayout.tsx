import {
  ApiOutlined,
  AppstoreOutlined,
  AuditOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  DownOutlined,
  FileTextOutlined,
  HeartOutlined,
  InfoCircleOutlined,
  LineChartOutlined,
  LockOutlined,
  LogoutOutlined,
  MessageOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Button, Dropdown, Layout, Menu, Segmented, Space, Tag, Typography, theme } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useThemePreset } from '../theme/ThemeContext';
import { ChangePasswordModal } from '../components/ChangePasswordModal';
import { ThemeSwitcher } from '../components/ThemeSwitcher';
import { changeLanguage } from '../i18n';
import type { AppLanguage } from '../i18n';

const { Header, Sider, Content } = Layout;

type MenuEntry = {
  key: string;
  icon: ReactNode;
  label: string;
  permission?: string | string[];
};

export function AdminLayout() {
  const { t, i18n } = useTranslation();
  const { user, logout, hasPermission } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { token } = theme.useToken();
  const { preset } = useThemePreset();
  const [passwordOpen, setPasswordOpen] = useState(false);

  const menuEntries = useMemo<MenuEntry[]>(
    () => [
      { key: '/', icon: <AppstoreOutlined />, label: t('nav.overview'), permission: 'overview.read' },
      { key: '/health', icon: <HeartOutlined />, label: t('nav.health'), permission: 'health.read' },
      { key: '/monitor', icon: <LineChartOutlined />, label: t('nav.monitor'), permission: 'monitor.read' },
      { key: '/corps', icon: <TeamOutlined />, label: t('nav.corps'), permission: 'corps.read' },
      { key: '/messages', icon: <MessageOutlined />, label: t('nav.messages'), permission: 'messages.read' },
      { key: '/queue', icon: <DatabaseOutlined />, label: t('nav.queue'), permission: 'queue.read' },
      { key: '/retention', icon: <DeleteOutlined />, label: t('nav.retention'), permission: 'retention.read' },
      { key: '/audit', icon: <AuditOutlined />, label: t('nav.audit'), permission: 'audit.read' },
      { key: '/logs', icon: <FileTextOutlined />, label: t('nav.logs'), permission: 'logs.read' },
      { key: '/settings', icon: <SettingOutlined />, label: t('nav.settings'), permission: 'settings.read' },
      { key: '/system', icon: <InfoCircleOutlined />, label: t('nav.system'), permission: 'system.read' },
      {
        key: '/access',
        icon: <SafetyCertificateOutlined />,
        label: t('nav.access'),
        permission: ['users.manage', 'roles.manage'],
      },
      { key: '/api-docs', icon: <ApiOutlined />, label: t('nav.apiDocs') },
    ],
    [t],
  );

  const menuItems = useMemo(
    () =>
      menuEntries
        .filter((item) => !item.permission || hasPermission(item.permission))
        .map((item) => ({ key: item.key, icon: item.icon, label: item.label })),
    [menuEntries, hasPermission],
  );

  const selectedKey =
    menuEntries.find((item) => item.key !== '/' && location.pathname.startsWith(item.key))?.key ?? '/';

  const language: AppLanguage = i18n.language === 'en-US' ? 'en-US' : 'zh-CN';
  const contentRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // 路由切换后内容区回到顶部，左侧菜单保持独立。
    contentRef.current?.scrollTo({ top: 0 });
  }, [location.pathname]);

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <Sider
        width={220}
        theme="dark"
        breakpoint="lg"
        collapsedWidth={0}
        style={{ height: '100vh', overflowY: 'auto', background: preset.siderBg }}
      >
        <div style={{ padding: '20px 16px', color: '#fff', fontSize: 16, fontWeight: 600 }}>
          {t('app.title')}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: token.colorBgContainer,
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: 16,
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <Space size={8}>
            <Typography.Text strong>{user?.displayName || user?.username}</Typography.Text>
            {user?.builtin ? <Tag color="gold">{t('auth.builtinTag')}</Tag> : null}
            {user?.roles.slice(0, 2).map((role) => (
              <Tag key={role.code} color="blue">
                {role.name}
              </Tag>
            ))}
          </Space>
          {user ? <Tag>{t('app.version', { version: user.version })}</Tag> : null}
          <ThemeSwitcher />
          <Segmented<AppLanguage>
            size="small"
            value={language}
            onChange={changeLanguage}
            options={[
              { label: '中文', value: 'zh-CN' },
              { label: 'EN', value: 'en-US' },
            ]}
          />
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                { key: 'password', icon: <LockOutlined />, label: t('auth.changePassword') },
                { type: 'divider' },
                { key: 'logout', icon: <LogoutOutlined />, label: t('common.logout') },
              ],
              onClick: ({ key }) => {
                if (key === 'logout') {
                  void logout();
                } else if (key === 'password') {
                  setPasswordOpen(true);
                }
              },
            }}
          >
            <Button icon={<UserOutlined />}>
              <DownOutlined />
            </Button>
          </Dropdown>
        </Header>
        <Content style={{ flex: 1, minHeight: 0, padding: 24, overflow: 'hidden' }}>
          <div ref={contentRef} style={{ height: '100%', overflowY: 'auto' }}>
            <Space orientation="vertical" size="large" style={{ width: '100%' }}>
              <Outlet />
            </Space>
          </div>
        </Content>
      </Layout>
      <ChangePasswordModal open={passwordOpen} onClose={() => setPasswordOpen(false)} />
    </Layout>
  );
}
