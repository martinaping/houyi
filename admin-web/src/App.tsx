import { App as AntdApp, Button, ConfigProvider, Result } from 'antd';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { Component, useEffect } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { BrowserRouter, Route, Routes, useNavigate } from 'react-router-dom';
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
            <BrowserRouter>
              <ErrorBoundary
                title={t('common.renderErrorTitle')}
                description={t('common.renderErrorHint')}
                reloadText={t('common.reload')}
              >
                <Routes>
                  <Route path="/login" element={<LoginPage />} />
                  <Route
                    element={
                      <RequireAuth>
                        <AdminLayout />
                      </RequireAuth>
                    }
                  >
                    <Route index element={<OverviewPage />} />
                    <Route path="health" element={<HealthPage />} />
                    <Route path="monitor" element={<MonitorPage />} />
                    <Route path="corps" element={<CorpsPage />} />
                    <Route path="messages" element={<MessagesPage />} />
                    <Route path="queue" element={<QueuePage />} />
                    <Route path="retention" element={<RetentionPage />} />
                    <Route path="audit" element={<AuditLogsPage />} />
                    <Route path="logs" element={<LogsPage />} />
                    <Route path="settings" element={<SettingsPage />} />
                    <Route path="system" element={<SystemPage />} />
                    <Route path="api-docs" element={<ApiDocsPage />} />
                    <Route path="*" element={<NotFoundPage />} />
                  </Route>
                </Routes>
              </ErrorBoundary>
            </BrowserRouter>
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
