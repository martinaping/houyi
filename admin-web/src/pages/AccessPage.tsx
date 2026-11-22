import {
  DeleteOutlined,
  EditOutlined,
  KeyOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  Divider,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { rbacApi } from '../api/rbac';
import type { PermissionGroupView, RbacUserView, RoleView } from '../api/rbac';
import { errorMessage } from '../api/errors';
import { useAuth } from '../auth/AuthContext';
import { formatTime } from '../utils/format';

export function AccessPage() {
  const { t } = useTranslation();
  const { hasPermission } = useAuth();
  const canUsers = hasPermission('users.manage');
  const canRoles = hasPermission('roles.manage');
  const [tab, setTab] = useState(canUsers ? 'users' : 'roles');

  return (
    <Space orientation="vertical" size={4} style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {t('access.title')}
      </Typography.Title>
      <Typography.Text type="secondary">{t('access.hint')}</Typography.Text>
      <Tabs
        activeKey={tab}
        onChange={setTab}
        style={{ marginTop: 8 }}
        items={[
          ...(canUsers
            ? [{ key: 'users', label: t('access.tabs.users'), children: <UsersTab /> }]
            : []),
          ...(canRoles
            ? [{ key: 'roles', label: t('access.tabs.roles'), children: <RolesTab /> }]
            : []),
        ]}
      />
    </Space>
  );
}

function UsersTab() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const { user: currentUser } = useAuth();
  const queryClient = useQueryClient();
  const [keyword, setKeyword] = useState('');
  const [editor, setEditor] = useState<{ open: boolean; editing: RbacUserView | null }>({
    open: false,
    editing: null,
  });
  const [resetTarget, setResetTarget] = useState<RbacUserView | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const [resetForm] = Form.useForm();

  const usersQuery = useQuery({
    queryKey: ['rbac', 'users', keyword],
    queryFn: () => rbacApi.users(keyword),
  });
  const rolesQuery = useQuery({ queryKey: ['rbac', 'roles'], queryFn: rbacApi.roles });
  const roles = rolesQuery.data ?? [];
  const myPermissions = currentUser?.permissions ?? [];
  const isSuper = myPermissions.includes('*');
  const roleByCode = new Map(roles.map((role) => [role.code, role]));
  const canGrant = (roleCode: string) => {
    const role = roleByCode.get(roleCode);
    if (!role) {
      return false;
    }
    return (
      isSuper ||
      (!role.permissions.includes('*') &&
        role.permissions.every((permission) => myPermissions.includes(permission)))
    );
  };
  const canManageUser = (target: RbacUserView) =>
    isSuper || target.roles.every((role) => canGrant(role.code));
  const assignableRoles = roles.filter((role) => canGrant(role.code));

  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['rbac'] });

  const openEditor = (editing: RbacUserView | null) => setEditor({ open: true, editing });

  useEffect(() => {
    if (!editor.open) {
      return;
    }
    form.resetFields();
    form.setFieldsValue(
      editor.editing
        ? {
            displayName: editor.editing.displayName,
            roleIds: editor.editing.roles.map((role) => role.code),
            enabled: editor.editing.enabled,
          }
        : { enabled: true, roleIds: [] },
    );
  }, [editor, form]);

  useEffect(() => {
    if (resetTarget) {
      resetForm.resetFields();
    }
  }, [resetTarget, resetForm]);

  const submitEditor = async () => {
    const values = await form.validateFields();
    const roleIds = roles
      .filter((role) => (values.roleIds as string[]).includes(role.code))
      .map((role) => role.id);
    setSaving(true);
    try {
      if (editor.editing) {
        await rbacApi.updateUser(editor.editing.id, {
          displayName: values.displayName,
          roleIds,
          enabled: values.enabled,
        });
      } else {
        await rbacApi.createUser({
          username: values.username,
          displayName: values.displayName,
          password: values.password,
          roleIds,
          enabled: values.enabled,
        });
      }
      message.success(t('access.users.saved'));
      setEditor({ open: false, editing: null });
      refresh();
    } catch (error) {
      message.error(errorMessage(t, error));
    } finally {
      setSaving(false);
    }
  };

  const submitReset = async () => {
    if (!resetTarget) {
      return;
    }
    const values = await resetForm.validateFields();
    setSaving(true);
    try {
      await rbacApi.resetPassword(resetTarget.id, values.password);
      message.success(t('access.users.passwordReset'));
      setResetTarget(null);
    } catch (error) {
      message.error(errorMessage(t, error));
    } finally {
      setSaving(false);
    }
  };

  const columns: TableColumnsType<RbacUserView> = [
    {
      title: t('access.users.username'),
      dataIndex: 'username',
      width: 180,
      render: (value: string, record) => (
        <Space size={4}>
          <Typography.Text strong>{value}</Typography.Text>
          {record.username === currentUser?.username ? <Tag>{t('access.users.self')}</Tag> : null}
        </Space>
      ),
    },
    { title: t('access.users.displayName'), dataIndex: 'displayName', width: 160 },
    {
      title: t('access.users.roles'),
      dataIndex: 'roles',
      render: (rolesOfUser: RbacUserView['roles']) =>
        rolesOfUser.length === 0 ? (
          '-'
        ) : (
          <Space size={4} wrap>
            {rolesOfUser.map((role) => (
              <Tag key={role.code} color="blue">
                {role.name}
              </Tag>
            ))}
          </Space>
        ),
    },
    {
      title: t('access.users.status'),
      dataIndex: 'enabled',
      width: 110,
      render: (enabled: boolean) => (
        <Tag color={enabled ? 'green' : 'default'}>
          {enabled ? t('access.users.enabled') : t('access.users.disabled')}
        </Tag>
      ),
    },
    {
      title: t('access.users.lastLogin'),
      dataIndex: 'lastLoginAt',
      width: 180,
      render: (value: number | null) => (value ? formatTime(value) : '-'),
    },
    {
      title: t('access.users.lastLoginIp'),
      dataIndex: 'lastLoginIp',
      width: 150,
      render: (value: string | null) => value || '-',
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 220,
      render: (_, record) => {
        const isSelf = record.username === currentUser?.username;
        const allowed = canManageUser(record);
        return (
          <Space size={4}>
            <Button
              size="small"
              type="link"
              icon={<EditOutlined />}
              disabled={isSelf || !allowed}
              onClick={() => openEditor(record)}
            >
              {t('common.edit')}
            </Button>
            <Button
              size="small"
              type="link"
              icon={<KeyOutlined />}
              disabled={!allowed}
              onClick={() => setResetTarget(record)}
            >
              {t('access.users.resetPassword')}
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Input.Search
          allowClear
          placeholder={t('access.users.searchPlaceholder')}
          style={{ width: 280 }}
          onSearch={(value) => setKeyword(value.trim())}
          enterButton
        />
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void usersQuery.refetch()}>
            {t('common.refresh')}
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor(null)}>
            {t('access.users.create')}
          </Button>
        </Space>
      </Space>

      {usersQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, usersQuery.error)} />
      ) : null}

      <Table<RbacUserView>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={usersQuery.data ?? []}
        loading={usersQuery.isLoading}
        pagination={false}
        locale={{ emptyText: t('common.noData') }}
      />

      <Modal
        open={editor.open}
        title={editor.editing ? t('access.users.editTitle') : t('access.users.createTitle')}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        confirmLoading={saving}
        onOk={() => void submitEditor()}
        onCancel={() => setEditor({ open: false, editing: null })}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          {editor.editing ? null : (
            <Form.Item
              name="username"
              label={t('access.users.username')}
              rules={[
                { required: true, message: t('access.users.usernameRequired') },
                {
                  pattern: /^[A-Za-z0-9._-]{3,32}$/,
                  message: t('access.users.usernameInvalid'),
                },
              ]}
            >
              <Input autoComplete="off" />
            </Form.Item>
          )}
          <Form.Item name="displayName" label={t('access.users.displayName')}>
            <Input maxLength={64} />
          </Form.Item>
          {editor.editing ? null : (
            <Form.Item
              name="password"
              label={t('access.users.password')}
              rules={[
                { required: true, message: t('access.users.passwordRequired') },
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
          )}
          <Form.Item
            name="roleIds"
            label={t('access.users.roles')}
            rules={[{ required: true, message: t('access.users.rolesRequired') }]}
          >
            <Select
              mode="multiple"
              options={assignableRoles.map((role) => ({
                label: `${role.name} (${role.code})`,
                value: role.code,
              }))}
              placeholder={t('access.users.rolesPlaceholder')}
            />
          </Form.Item>
          <Form.Item
            name="enabled"
            label={t('access.users.status')}
            valuePropName="checked"
            extra={editor.editing ? t('access.users.disableHint') : undefined}
          >
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={resetTarget !== null}
        title={t('access.users.resetPasswordTitle', { username: resetTarget?.username ?? '' })}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        confirmLoading={saving}
        onOk={() => void submitReset()}
        onCancel={() => setResetTarget(null)}
        destroyOnHidden
      >
        <Form form={resetForm} layout="vertical">
          <Form.Item
            name="password"
            label={t('access.users.newPassword')}
            rules={[
              { required: true, message: t('access.users.passwordRequired') },
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
        </Form>
      </Modal>
    </Space>
  );
}

function RolesTab() {
  const { t, i18n } = useTranslation();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [editor, setEditor] = useState<{ open: boolean; editing: RoleView | null }>({
    open: false,
    editing: null,
  });
  const [selected, setSelected] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const { user: currentUser } = useAuth();
  const rolesQuery = useQuery({ queryKey: ['rbac', 'roles'], queryFn: rbacApi.roles });
  const permissionsQuery = useQuery({
    queryKey: ['rbac', 'permissions'],
    queryFn: rbacApi.permissions,
  });
  const groups = permissionsQuery.data ?? [];
  const myPermissions = currentUser?.permissions ?? [];
  const isSuper = myPermissions.includes('*');
  const canEditRole = (role: RoleView) =>
    isSuper ||
    (role.code !== 'SUPER_ADMIN' &&
      !role.permissions.includes('*') &&
      role.permissions.every((permission) => myPermissions.includes(permission)));
  const permissionGrantable = (code: string) => isSuper || myPermissions.includes(code);
  const lang = i18n.language === 'en-US' ? 'en' : 'zh';
  const labelOf = (item: { labelZh: string; labelEn: string }) =>
    lang === 'en' ? item.labelEn : item.labelZh;

  const isSuperEditing = editor.editing?.permissions.includes('*') ?? false;

  useEffect(() => {
    if (!editor.open) {
      return;
    }
    form.resetFields();
    form.setFieldsValue({
      code: editor.editing?.code,
      name: editor.editing?.name ?? '',
      description: editor.editing?.description ?? '',
    });
    setSelected(editor.editing ? editor.editing.permissions.filter((code) => code !== '*') : []);
  }, [editor, form]);

  const openEditor = (editing: RoleView | null) => setEditor({ open: true, editing });

  const submit = async () => {
    const values = await form.validateFields();
    const permissions = isSuperEditing ? ['*'] : selected;
    if (permissions.length === 0) {
      message.warning(t('access.roles.permissionRequired'));
      return;
    }
    setSaving(true);
    try {
      if (editor.editing) {
        await rbacApi.updateRole(editor.editing.id, {
          name: values.name,
          description: values.description,
          permissions,
        });
      } else {
        await rbacApi.createRole({
          code: values.code,
          name: values.name,
          description: values.description,
          permissions,
        });
      }
      message.success(t('access.roles.saved'));
      setEditor({ open: false, editing: null });
      void queryClient.invalidateQueries({ queryKey: ['rbac'] });
    } catch (error) {
      message.error(errorMessage(t, error));
    } finally {
      setSaving(false);
    }
  };

  const removeRole = async (role: RoleView) => {
    try {
      await rbacApi.deleteRole(role.id);
      message.success(t('access.roles.deleted'));
      void queryClient.invalidateQueries({ queryKey: ['rbac'] });
    } catch (error) {
      message.error(errorMessage(t, error));
    }
  };

  const toggleGroup = (group: PermissionGroupView, checked: boolean) => {
    const codes = group.permissions
      .map((item) => item.code)
      .filter((code) => permissionGrantable(code));
    setSelected((current) => {
      const rest = current.filter((code) => !codes.includes(code));
      return checked ? [...rest, ...codes] : rest;
    });
  };

  const columns: TableColumnsType<RoleView> = [
    {
      title: t('access.roles.code'),
      dataIndex: 'code',
      width: 170,
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: t('access.roles.name'),
      dataIndex: 'name',
      width: 160,
      render: (value: string, record) => (
        <Space size={4}>
          {value}
          {record.builtin ? <Tag color="gold">{t('access.roles.builtin')}</Tag> : null}
        </Space>
      ),
    },
    { title: t('access.roles.description'), dataIndex: 'description', ellipsis: true },
    {
      title: t('access.roles.permissions'),
      dataIndex: 'permissions',
      width: 120,
      render: (value: string[]) =>
        value.includes('*') ? <Tag color="red">{t('access.roles.all')}</Tag> : value.length,
    },
    { title: t('access.roles.users'), dataIndex: 'userCount', width: 90 },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 170,
      render: (_, record) => {
        const allowed = canEditRole(record);
        return (
          <Space size={4}>
            <Button
              size="small"
              type="link"
              icon={<EditOutlined />}
              disabled={!allowed}
              onClick={() => openEditor(record)}
            >
              {t('common.edit')}
            </Button>
            <Popconfirm
              title={t('access.roles.deleteConfirm', { name: record.name })}
              onConfirm={() => void removeRole(record)}
              disabled={record.builtin || !allowed}
            >
              <Button
                size="small"
                type="link"
                danger
                icon={<DeleteOutlined />}
                disabled={record.builtin || !allowed}
              >
                {t('common.delete')}
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
        <Button icon={<ReloadOutlined />} onClick={() => void rolesQuery.refetch()}>
          {t('common.refresh')}
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor(null)}>
          {t('access.roles.create')}
        </Button>
      </Space>

      {rolesQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, rolesQuery.error)} />
      ) : null}

      <Table<RoleView>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={rolesQuery.data ?? []}
        loading={rolesQuery.isLoading}
        pagination={false}
        locale={{ emptyText: t('common.noData') }}
      />

      <Modal
        open={editor.open}
        width={720}
        title={editor.editing ? t('access.roles.editTitle') : t('access.roles.createTitle')}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        confirmLoading={saving}
        onOk={() => void submit()}
        onCancel={() => setEditor({ open: false, editing: null })}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large" style={{ width: '100%' }} align="start">
            <Form.Item
              name="code"
              label={t('access.roles.code')}
              style={{ width: 220 }}
              rules={
                editor.editing
                  ? []
                  : [
                      { required: true, message: t('access.roles.codeRequired') },
                      {
                        pattern: /^[A-Z][A-Z0-9_]{1,31}$/,
                        message: t('access.roles.codeInvalid'),
                      },
                    ]
              }
            >
              <Input disabled={Boolean(editor.editing)} maxLength={32} />
            </Form.Item>
            <Form.Item
              name="name"
              label={t('access.roles.name')}
              style={{ width: 260 }}
              rules={[{ required: true, message: t('access.roles.nameRequired') }]}
            >
              <Input maxLength={64} />
            </Form.Item>
          </Space>
          <Form.Item name="description" label={t('access.roles.description')}>
            <Input maxLength={255} />
          </Form.Item>
        </Form>

        <Divider style={{ margin: '8px 0' }} />
        <Space size={6} style={{ marginBottom: 8 }}>
          <SafetyCertificateOutlined />
          <Typography.Text strong>{t('access.roles.permissions')}</Typography.Text>
        </Space>

        {isSuperEditing ? (
          <Alert type="warning" showIcon title={t('access.roles.superLocked')} />
        ) : (
          <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
            {groups.map((group) => {
              const codes = group.permissions
                .map((item) => item.code)
                .filter((code) => permissionGrantable(code));
              const checkedCount = codes.filter((code) => selected.includes(code)).length;
              return (
                <Card key={group.key} size="small" title={labelOf(group)}>
                  <Checkbox
                    checked={checkedCount === codes.length}
                    indeterminate={checkedCount > 0 && checkedCount < codes.length}
                    onChange={(event) => toggleGroup(group, event.target.checked)}
                  >
                    {t('access.roles.selectAll')}
                  </Checkbox>
                  <Divider style={{ margin: '8px 0' }} />
                  <Space size={[16, 8]} wrap>
                    {group.permissions.map((item) => (
                      <Checkbox
                        key={item.code}
                        checked={selected.includes(item.code)}
                        disabled={!permissionGrantable(item.code)}
                        onChange={(event) =>
                          setSelected((current) =>
                            event.target.checked
                              ? [...current, item.code]
                              : current.filter((code) => code !== item.code),
                          )
                        }
                      >
                        {labelOf(item)}
                      </Checkbox>
                    ))}
                  </Space>
                </Card>
              );
            })}
          </Space>
        )}
      </Modal>
    </Space>
  );
}
