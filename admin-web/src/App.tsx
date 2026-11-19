import { App as AntdApp, ConfigProvider } from 'antd';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { RequireAuth } from './auth/RequireAuth';
import { AdminLayout } from './components/AdminLayout';
import { AuditLogsPage } from './pages/AuditLogsPage';
import { CorpsPage } from './pages/CorpsPage';
import { HealthPage } from './pages/HealthPage';
import { LoginPage } from './pages/LoginPage';
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
  const { i18n } = useTranslation();
  const antdLocale = i18n.language === 'en-US' ? enUS : zhCN;

  return (
    <ConfigProvider
      locale={antdLocale}
      theme={{ token: { colorPrimary: '#1677ff', borderRadius: 6 } }}
    >
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <BrowserRouter>
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
                  <Route path="corps" element={<CorpsPage />} />
                  <Route path="messages" element={<MessagesPage />} />
                  <Route path="queue" element={<QueuePage />} />
                  <Route path="retention" element={<RetentionPage />} />
                  <Route path="audit" element={<AuditLogsPage />} />
                  <Route path="settings" element={<SettingsPage />} />
                  <Route path="system" element={<SystemPage />} />
                </Route>
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </BrowserRouter>
          </AuthProvider>
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  );
}
