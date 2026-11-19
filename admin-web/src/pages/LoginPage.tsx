import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, App, Button, Card, Form, Input, Typography } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { consumeSessionExpired } from '../api/client';
import { errorMessage } from '../api/errors';
import { useAuth } from '../auth/AuthContext';

type LoginForm = {
  username: string;
  password: string;
};

type LocationState = {
  from?: string;
};

export function LoginPage() {
  const { t } = useTranslation();
  const { status, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = App.useApp();
  const [submitting, setSubmitting] = useState(false);
  const [sessionExpired] = useState(consumeSessionExpired);

  const from = (location.state as LocationState | null)?.from ?? '/';

  if (status === 'authenticated') {
    return <Navigate to={from} replace />;
  }

  const onFinish = async (values: LoginForm) => {
    setSubmitting(true);
    try {
      await login(values.username, values.password);
      message.success(t('auth.loginSuccess'));
      navigate(from, { replace: true });
    } catch (error) {
      message.error(errorMessage(t, error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #0f2027 0%, #203a43 50%, #2c5364 100%)',
        padding: 24,
      }}
    >
      <Card style={{ width: 380 }}>
        <Typography.Title level={3} style={{ marginBottom: 4 }}>
          {t('app.title')}
        </Typography.Title>
        <Typography.Paragraph type="secondary">{t('auth.subtitle')}</Typography.Paragraph>
        {sessionExpired ? (
          <Alert type="warning" showIcon title={t('auth.sessionExpired')} style={{ marginBottom: 16 }} />
        ) : null}
        <Form<LoginForm> layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item
            name="username"
            label={t('auth.username')}
            rules={[{ required: true, message: t('auth.usernameRequired') }]}
          >
            <Input prefix={<UserOutlined />} autoComplete="username" autoFocus />
          </Form.Item>
          <Form.Item
            name="password"
            label={t('auth.password')}
            rules={[{ required: true, message: t('auth.passwordRequired') }]}
          >
            <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>
            {t('auth.submit')}
          </Button>
        </Form>
      </Card>
    </div>
  );
}
