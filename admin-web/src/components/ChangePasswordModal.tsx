import { App, Form, Input, Modal } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { rbacApi } from '../api/rbac';
import { errorMessage } from '../api/errors';
import { useAuth } from '../auth/AuthContext';

type ChangePasswordValues = {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
};

export function ChangePasswordModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const { user, logout } = useAuth();
  const [form] = Form.useForm<ChangePasswordValues>();
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await rbacApi.changePassword(values.currentPassword, values.newPassword);
      message.success(t('auth.passwordChanged'));
      onClose();
      form.resetFields();
      // 修改密码后服务端已提升令牌版本，当前会话立即失效。
      await logout();
    } catch (error) {
      message.error(errorMessage(t, error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title={t('auth.changePassword')}
      okText={t('common.save')}
      cancelText={t('common.cancel')}
      confirmLoading={submitting}
      onOk={() => void submit()}
      onCancel={() => {
        form.resetFields();
        onClose();
      }}
      destroyOnHidden
    >
      {user?.builtin ? (
        <p style={{ color: 'rgba(0,0,0,0.55)' }}>{t('auth.builtinPasswordHint')}</p>
      ) : null}
      <Form form={form} layout="vertical" disabled={user?.builtin}>
        <Form.Item
          name="currentPassword"
          label={t('auth.currentPassword')}
          rules={[{ required: true, message: t('auth.currentPasswordRequired') }]}
        >
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label={t('auth.newPassword')}
          rules={[
            { required: true, message: t('auth.newPasswordRequired') },
            { min: 10, max: 128, message: t('auth.passwordPolicy') },
            {
              validator: (_, value: string) =>
                !value || (/[A-Za-z]/.test(value) && /\d/.test(value))
                  ? Promise.resolve()
                  : Promise.reject(new Error(t('auth.passwordPolicy'))),
            },
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label={t('auth.confirmPassword')}
          dependencies={['newPassword']}
          rules={[
            { required: true, message: t('auth.confirmPasswordRequired') },
            ({ getFieldValue }) => ({
              validator: (_, value: string) =>
                !value || getFieldValue('newPassword') === value
                  ? Promise.resolve()
                  : Promise.reject(new Error(t('auth.passwordMismatch'))),
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
