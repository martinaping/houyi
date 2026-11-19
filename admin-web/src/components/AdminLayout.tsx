import {
  AppstoreOutlined,
  AuditOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  HeartOutlined,
  InfoCircleOutlined,
  LogoutOutlined,
  MessageOutlined,
  SettingOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Button, Layout, Menu, Segmented, Space, Tag, Typography, theme } from 'antd';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { changeLanguage } from '../i18n';
import type { AppLanguage } from '../i18n';

const { Header, Sider, Content } = Layout;

export function AdminLayout() {
  const { t, i18n } = useTranslation();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { token } = theme.useToken();

  const menuItems = useMemo(
    () => [
      { key: '/', icon: <AppstoreOutlined />, label: t('nav.overview') },
      { key: '/health', icon: <HeartOutlined />, label: t('nav.health') },
      { key: '/corps', icon: <TeamOutlined />, label: t('nav.corps') },
      { key: '/messages', icon: <MessageOutlined />, label: t('nav.messages') },
      { key: '/queue', icon: <DatabaseOutlined />, label: t('nav.queue') },
      { key: '/retention', icon: <DeleteOutlined />, label: t('nav.retention') },
      { key: '/audit', icon: <AuditOutlined />, label: t('nav.audit') },
      { key: '/settings', icon: <SettingOutlined />, label: t('nav.settings') },
      { key: '/system', icon: <InfoCircleOutlined />, label: t('nav.system') },
    ],
    [t],
  );

  const selectedKey =
    menuItems.find((item) => item.key !== '/' && location.pathname.startsWith(item.key))?.key ?? '/';

  const language: AppLanguage = i18n.language === 'en-US' ? 'en-US' : 'zh-CN';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={220} theme="dark" breakpoint="lg" collapsedWidth={0}>
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
          <Typography.Text type="secondary">{user?.username}</Typography.Text>
          {user ? <Tag>{t('app.version', { version: user.version })}</Tag> : null}
          <Segmented<AppLanguage>
            size="small"
            value={language}
            onChange={changeLanguage}
            options={[
              { label: '中文', value: 'zh-CN' },
              { label: 'EN', value: 'en-US' },
            ]}
          />
          <Button icon={<LogoutOutlined />} onClick={() => void logout()}>
            {t('common.logout')}
          </Button>
        </Header>
        <Content style={{ padding: 24 }}>
          <Space orientation="vertical" size="large" style={{ width: '100%' }}>
            <Outlet />
          </Space>
        </Content>
      </Layout>
    </Layout>
  );
}
