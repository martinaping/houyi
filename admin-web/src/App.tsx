import { App as AntdApp, Button, ConfigProvider, Result } from 'antd';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { Component, useEffect } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { createBrowserRouter, Navigate, RouterProvider, useNavigate } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { RequireAuth } from './auth/RequireAuth';
import { AdminLayout } from './components/AdminLayout';
import { ApiDocsPage } from './pages/ApiDocsPage';
import { AuditLogsPage } from './pages/AuditLogsPage';
import { CorpsPage } from './pages/CorpsPage';
import { HealthPage } from './pages/HealthPage';
import { LoginPage } from './pages/LoginPage';
import { LogsPage } from './pages/LogsPage';
import { MonitorPage } from './pages/MonitorPage';
import { MessagesPage } from './pages/MessagesPage';
import { OverviewPage } from './pages/OverviewPage';
import { QueuePage } from './pages/QueuePage';
import { RetentionPage } from './pages/RetentionPage';
import { SettingsPage } from './pages/SettingsPage';
import { SystemPage } from './pages/SystemPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 15_000,
    },
  },
});

// 使用数据路由，页面才能通过 useBlocker 拦截站内跳转（如设置页的未保存修改）。
const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AdminLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <OverviewPage /> },
      { path: 'health', element: <HealthPage /> },
      { path: 'monitor', element: <MonitorPage /> },
      { path: 'corps', element: <CorpsPage /> },
      { path: 'messages', element: <MessagesPage /> },
      { path: 'queue', element: <QueuePage /> },
      { path: 'rocketmq', element: <Navigate to="/queue?tab=rocketmq" replace /> },
      { path: 'redis', element: <Navigate to="/queue?tab=redis" replace /> },
      { path: 'mns', element: <Navigate to="/queue?tab=mns" replace /> },
      { path: 'retention', element: <RetentionPage /> },
      { path: 'audit', element: <AuditLogsPage /> },
      { path: 'logs', element: <LogsPage /> },
      { path: 'settings', element: <SettingsPage /> },
      { path: 'system', element: <SystemPage /> },
      { path: 'api-docs', element: <ApiDocsPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);

export function App() {
  const { t, i18n } = useTranslation();
  const antdLocale = i18n.language === 'en-US' ? enUS : zhCN;

  // antd 的日期组件依赖 dayjs 的全局 locale，需与界面语言保持一致。
  useEffect(() => {
    dayjs.locale(i18n.language === 'en-US' ? 'en' : 'zh-cn');
  }, [i18n.language]);

  return (
    <ConfigProvider
      locale={antdLocale}
      theme={{ token: { colorPrimary: '#1677ff', borderRadius: 6 } }}
    >
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <ErrorBoundary
              title={t('common.renderErrorTitle')}
              description={t('common.renderErrorHint')}
              reloadText={t('common.reload')}
            >
              <RouterProvider router={router} />
            </ErrorBoundary>
          </AuthProvider>
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  );
}

function NotFoundPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <Result
      status="404"
      title="404"
      subTitle={t('common.notFoundHint')}
      extra={
        <Button type="primary" onClick={() => navigate('/')}>
          {t('common.backHome')}
        </Button>
      }
    />
  );
}

interface ErrorBoundaryProps {
  title: string;
  description: string;
  reloadText: string;
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

/** 兜底渲染异常，避免整页白屏。 */
class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('页面渲染异常', error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <Result
          status="500"
          title={this.props.title}
          subTitle={this.props.description}
          extra={
            <Button type="primary" onClick={() => window.location.reload()}>
              {this.props.reloadText}
            </Button>
          }
        />
      );
    }
    return this.props.children;
  }
}
