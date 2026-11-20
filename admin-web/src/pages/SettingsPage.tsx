import { ApiOutlined, ReloadOutlined, SendOutlined, UndoOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Input,
  InputNumber,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TFunction } from 'i18next';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useBlocker } from 'react-router-dom';
import { request } from '../api/client';
import { errorMessage } from '../api/errors';
import type {
  AlertLogView,
  AlertStatus,
  AlertTestResult,
  RocketMqTestResult,
  SettingView,
} from '../api/types';
import { formatTime } from '../utils/format';

const GROUP_ORDER = [
  'alert',
  'oss',
  'mns',
  'redismq',
  'rocketmq',
  'security',
  'runtime',
  'retention',
];

type RocketMqTestPayload = {
  endpoint?: string;
  username?: string;
  password?: string;
};

const APPLY_MODE_COLOR: Record<string, string> = {
  INSTANT: 'green',
  HOT: 'blue',
  RESTART: 'orange',
};

/** 设置项 key 形如 alert.webhook-url，展示时只取组内短名，避免拼出重复的 i18n 路径。 */
function settingLabel(t: TFunction, item: SettingView): string {
  const prefix = `${item.group}.`;
  const shortKey = item.key.startsWith(prefix) ? item.key.slice(prefix.length) : item.key;
  return t(`settings.keys.${item.group}.${shortKey}`, { defaultValue: item.description || item.key });
}

async function updateSetting(key: string, value: string): Promise<SettingView> {
  return request<SettingView>('/api/admin/settings', {
    method: 'PUT',
    body: JSON.stringify({ key, value }),
  });
}

async function resetSetting(key: string): Promise<SettingView> {
  return request<SettingView>('/api/admin/settings/reset', {
    method: 'POST',
    body: JSON.stringify({ key }),
  });
}

export function SettingsPage() {
  const { t } = useTranslation();
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const [values, setValues] = useState<Record<string, string>>({});
  const [originals, setOriginals] = useState<Record<string, string>>({});
  const [keyword, setKeyword] = useState('');
  const [activeGroup, setActiveGroup] = useState<string>('alert');

  // 供数据刷新时判断哪些项被用户改过（useEffect 中读取最新值，避免闭包过期）。
  const valuesRef = useRef(values);
  const originalsRef = useRef(originals);
  useEffect(() => {
    valuesRef.current = values;
  }, [values]);
  useEffect(() => {
    originalsRef.current = originals;
  }, [originals]);

  const settingsQuery = useQuery({
    queryKey: ['settings'],
    queryFn: () => request<SettingView[]>('/api/admin/settings'),
  });

  const alertStatusQuery = useQuery({
    queryKey: ['alertStatus'],
    queryFn: () => request<AlertStatus>('/api/admin/alerts/status'),
  });

  const alertLogsQuery = useQuery({
    queryKey: ['alertLogs'],
    queryFn: () => request<AlertLogView[]>('/api/admin/alerts/logs?limit=20'),
  });

  useEffect(() => {
    if (!settingsQuery.data) {
      return;
    }
    // 重新拉取设置时保留用户尚未保存的修改，只覆盖未编辑项，避免静默丢失输入。
    const currentValues = valuesRef.current;
    const currentOriginals = originalsRef.current;
    const nextValues: Record<string, string> = {};
    const nextOriginals: Record<string, string> = {};
    settingsQuery.data.forEach((item) => {
      nextOriginals[item.key] = item.value;
      const edited = (currentValues[item.key] ?? '') !== (currentOriginals[item.key] ?? '');
      nextValues[item.key] = edited ? currentValues[item.key] ?? '' : item.value;
    });
    setValues(nextValues);
    setOriginals(nextOriginals);
  }, [settingsQuery.data]);

  const dirtyKeys = useMemo(
    () =>
      (settingsQuery.data ?? [])
        .filter((item) => (values[item.key] ?? '') !== (originals[item.key] ?? ''))
        .map((item) => item.key),
    [settingsQuery.data, values, originals],
  );

  useEffect(() => {
    if (dirtyKeys.length === 0) {
      return;
    }
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirtyKeys.length]);

  // 站内跳转（左侧菜单、返回首页等）同样需要确认，避免未保存的修改被静默丢弃。
  const blocker = useBlocker(dirtyKeys.length > 0);
  useEffect(() => {
    if (blocker.state !== 'blocked') {
      return;
    }
    modal.confirm({
      title: t('settings.unsavedConfirmTitle'),
      content: t('settings.unsavedConfirmContent'),
      okText: t('settings.unsavedConfirmOk'),
      cancelText: t('settings.unsavedConfirmCancel'),
      onOk: () => blocker.proceed(),
      onCancel: () => blocker.reset(),
    });
  }, [blocker, modal, t]);

  const saveMutation = useMutation({
    mutationFn: async (group: string) => {
      const items = (settingsQuery.data ?? []).filter((item) => item.group === group);
      const dirty = items.filter((item) => (values[item.key] ?? '') !== (originals[item.key] ?? ''));
      const invalid = dirty.find((item) => !isValid(item, values[item.key] ?? ''));
      if (invalid) {
        throw new Error(t('settings.invalidRange', { min: invalid.min, max: invalid.max }));
      }
      for (const item of dirty) {
        await updateSetting(item.key, values[item.key] ?? '');
      }
      return dirty.length;
    },
    onSuccess: (count) => {
      message.success(t('settings.saveSuccess', { count }));
      void queryClient.invalidateQueries({ queryKey: ['settings'] });
      void queryClient.invalidateQueries({ queryKey: ['alertStatus'] });
      void queryClient.invalidateQueries({ queryKey: ['retentionPreview'] });
      void queryClient.invalidateQueries({ queryKey: ['queue'] });
      void queryClient.invalidateQueries({ queryKey: ['health'] });
      void queryClient.invalidateQueries({ queryKey: ['systemInfo'] });
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const resetMutation = useMutation({
    mutationFn: resetSetting,
    onSuccess: (view) => {
      message.success(t('settings.resetSuccess'));
      setValues((previous) => ({ ...previous, [view.key]: view.value }));
      setOriginals((previous) => ({ ...previous, [view.key]: view.value }));
      void queryClient.invalidateQueries({ queryKey: ['settings'] });
      void queryClient.invalidateQueries({ queryKey: ['systemInfo'] });
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const testMutation = useMutation({
    mutationFn: () => request<AlertTestResult>('/api/admin/alerts/test', { method: 'POST' }),
    onSuccess: (result) => {
      if (result.delivered) {
        message.success(t('settings.alert.testSuccess'));
      } else {
        message.error(t('settings.alert.testFailed', { detail: result.message ?? '-' }));
      }
      void queryClient.invalidateQueries({ queryKey: ['alertLogs'] });
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const rocketMqTestMutation = useMutation({
    mutationFn: (payload: RocketMqTestPayload) =>
      request<RocketMqTestResult>('/api/admin/settings/rocketmq/test', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const grouped = useMemo(() => {
    const map = new Map<string, SettingView[]>();
    (settingsQuery.data ?? []).forEach((item) => {
      const list = map.get(item.group) ?? [];
      list.push(item);
      map.set(item.group, list);
    });
    const order = [...GROUP_ORDER, ...Array.from(map.keys()).filter((g) => !GROUP_ORDER.includes(g))];
    return order
      .filter((group) => map.has(group))
      .map((group) => ({ group, items: map.get(group) ?? [] }));
  }, [settingsQuery.data]);

  const visibleGroups = useMemo(() => {
    const search = keyword.trim().toLowerCase();
    if (!search) {
      return grouped;
    }
    return grouped
      .map(({ group, items }) => ({
        group,
        items: items.filter((item) => {
          const label = settingLabel(t, item);
          return (
            item.key.toLowerCase().includes(search) ||
            String(label).toLowerCase().includes(search) ||
            item.description.toLowerCase().includes(search)
          );
        }),
      }))
      .filter(({ items }) => items.length > 0);
  }, [grouped, keyword, t]);

  useEffect(() => {
    const first = visibleGroups[0];
    if (!first) {
      return;
    }
    if (!visibleGroups.some(({ group }) => group === activeGroup)) {
      setActiveGroup(first.group);
    }
  }, [visibleGroups, activeGroup]);

  const setValue = (key: string, value: string) => {
    setValues((previous) => ({ ...previous, [key]: value }));
  };

  const handleRocketMqTest = () => {
    const password = values['rocketmq.password'] ?? '';
    const originalPassword = originals['rocketmq.password'] ?? '';
    const unchanged = password === originalPassword || password.startsWith('****');
    rocketMqTestMutation.mutate({
      endpoint: values['rocketmq.endpoint'] ?? '',
      username: values['rocketmq.username'] ?? '',
      password: unchanged ? '' : password,
    });
  };

  const control = (item: SettingView) => {
    const value = values[item.key] ?? '';
    const readonlyMode = item.editable === false;
    if (item.type === 'BOOLEAN') {
      return (
        <Switch
          disabled={readonlyMode}
          checked={value === 'true'}
          onChange={(checked) => setValue(item.key, checked ? 'true' : 'false')}
        />
      );
    }
    if (item.type === 'NUMBER') {
      return (
        <Space size="middle" wrap>
          <InputNumber
            style={{ width: 220 }}
            disabled={readonlyMode}
            min={item.min}
            max={item.max}
            value={value === '' ? null : Number(value)}
            onChange={(next) => setValue(item.key, next == null ? '' : String(next))}
            suffix={item.unit || undefined}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {t('settings.rangeHint', { min: item.min, max: item.max })}
          </Typography.Text>
        </Space>
      );
    }
    if (item.sensitive) {
      return (
        <Input.Password
          style={{ maxWidth: 420 }}
          disabled={readonlyMode}
          value={value}
          placeholder={t('settings.sensitivePlaceholder')}
          onChange={(event) => setValue(item.key, event.target.value)}
          autoComplete="new-password"
        />
      );
    }
    return (
      <Input
        style={{ maxWidth: 420 }}
        disabled={readonlyMode}
        value={value}
        placeholder={item.defaultValue || undefined}
        onChange={(event) => setValue(item.key, event.target.value)}
      />
    );
  };

  const confirmReset = (item: SettingView) => {
    modal.confirm({
      title: t('settings.resetConfirmTitle'),
      content: t('settings.resetConfirmContent', {
        name: settingLabel(t, item),
        value: item.defaultValue || '-',
      }),
      okText: t('settings.reset'),
      cancelText: t('common.cancel'),
      onOk: () => resetMutation.mutateAsync(item.key),
    });
  };

  const alertLogColumns: TableColumnsType<AlertLogView> = [
    {
      title: t('settings.alert.time'),
      dataIndex: 'createdAt',
      width: 170,
      render: (value: number) => formatTime(value),
    },
    {
      title: t('settings.alert.severity'),
      dataIndex: 'severity',
      width: 90,
      render: (value: string) => <Tag color={value === 'WARN' ? 'orange' : 'red'}>{value}</Tag>,
    },
    { title: t('settings.alert.titleColumn'), dataIndex: 'title', width: 180, ellipsis: true },
    { title: t('settings.alert.content'), dataIndex: 'message', ellipsis: true },
    {
      title: t('settings.alert.delivered'),
      dataIndex: 'delivered',
      width: 100,
      render: (value: boolean) =>
        value ? (
          <Tag color="green">{t('common.yes')}</Tag>
        ) : (
          <Tag color="red">{t('common.no')}</Tag>
        ),
    },
  ];

  const status = alertStatusQuery.data;
  const rocketMqTestResult = rocketMqTestMutation.data;

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('settings.title')}
        </Typography.Title>
        <Space>
          {dirtyKeys.length > 0 ? (
            <Tag color="orange">{t('settings.unsaved', { count: dirtyKeys.length })}</Tag>
          ) : null}
          <Input.Search
            allowClear
            placeholder={t('settings.searchPlaceholder')}
            style={{ width: 240 }}
            onSearch={(value) => setKeyword(value)}
          />
          <Button
            icon={<ReloadOutlined />}
            loading={settingsQuery.isFetching}
            onClick={() => void settingsQuery.refetch()}
          >
            {t('common.refresh')}
          </Button>
        </Space>
      </Space>

      {settingsQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, settingsQuery.error)} />
      ) : null}

      {visibleGroups.length === 0 ? (
        <Card>
          <Typography.Text type="secondary">{t('settings.noMatch')}</Typography.Text>
        </Card>
      ) : (
        <Tabs
          activeKey={activeGroup}
          onChange={setActiveGroup}
          items={visibleGroups.map(({ group, items }) => {
            const dirty = items.some(
              (item) => (values[item.key] ?? '') !== (originals[item.key] ?? ''),
            );
            return {
              key: group,
              label: (
                <Space size={6}>
                  <span>{t(`settings.group.${group}`, { defaultValue: group })}</span>
                  <Tag style={{ marginInlineEnd: 0 }}>{items.length}</Tag>
                  {dirty ? (
                    <Tag color="orange" style={{ marginInlineEnd: 0 }}>
                      {t('settings.changed')}
                    </Tag>
                  ) : null}
                </Space>
              ),
              children: (
                <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
                  {group === 'alert' ? (
                    <Card
                      title={t('settings.alert.title')}
                      extra={
                        <Button
                          icon={<SendOutlined />}
                          loading={testMutation.isPending}
                          onClick={() => testMutation.mutate()}
                        >
                          {t('settings.alert.test')}
                        </Button>
                      }
                    >
                      {alertStatusQuery.isError ? (
                        <Alert
                          type="warning"
                          showIcon
                          title={t('settings.alert.statusLoadFailed')}
                          description={errorMessage(t, alertStatusQuery.error)}
                        />
                      ) : (
                        <Space size="large" wrap>
                          <Tag color={status?.enabled ? 'green' : 'default'}>
                            {t('settings.alert.enabled')}:{' '}
                            {status?.enabled ? t('common.yes') : t('common.no')}
                          </Tag>
                          <Tag color={status?.webhookConfigured ? 'blue' : 'default'}>
                            {t('settings.alert.webhook')}:{' '}
                            {status?.webhookConfigured
                              ? t('settings.alert.configured')
                              : t('settings.alert.notConfigured')}
                          </Tag>
                        </Space>
                      )}
                    </Card>
                  ) : null}

                  {group === 'rocketmq' ? (
                    <Card
                      title={t('settings.rocketMq.title')}
                      extra={
                        <Button
                          icon={<ApiOutlined />}
                          loading={rocketMqTestMutation.isPending}
                          onClick={handleRocketMqTest}
                        >
                          {t('settings.rocketMq.test')}
                        </Button>
                      }
                    >
                      {rocketMqTestResult ? (
                        rocketMqTestResult.state === 'UP' ? (
                          <Alert
                            type="success"
                            showIcon
                            title={t('settings.rocketMq.testSuccess')}
                            description={
                              <Space size="large" wrap>
                                <Typography.Text>
                                  {t('settings.rocketMq.endpoint')}:{' '}
                                  {rocketMqTestResult.endpoint || '-'}
                                </Typography.Text>
                                <Typography.Text>
                                  {t('settings.rocketMq.latency', {
                                    ms: rocketMqTestResult.latencyMs ?? '-',
                                  })}
                                </Typography.Text>
                                <Typography.Text>
                                  {t('settings.rocketMq.checkedAt')}:{' '}
                                  {formatTime(rocketMqTestResult.checkedAt)}
                                </Typography.Text>
                              </Space>
                            }
                          />
                        ) : rocketMqTestResult.state === 'DOWN' ? (
                          <Alert
                            type="error"
                            showIcon
                            title={t('settings.rocketMq.testFailed')}
                            description={rocketMqTestResult.message ?? '-'}
                          />
                        ) : (
                          <Alert
                            type="warning"
                            showIcon
                            title={t('settings.rocketMq.notConfigured')}
                          />
                        )
                      ) : null}
                    </Card>
                  ) : null}

                  <Card
                    extra={
                      <Button
                        type="primary"
                        disabled={!dirty}
                        loading={saveMutation.isPending && saveMutation.variables === group}
                        onClick={() => saveMutation.mutate(group)}
                      >
                        {t('settings.save')}
                      </Button>
                    }
                  >
                    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
                      {items.map((item) => {
                        const changed = (values[item.key] ?? '') !== (originals[item.key] ?? '');
                        return (
                          <div
                            key={item.key}
                            style={{
                              display: 'flex',
                              gap: 24,
                              alignItems: 'flex-start',
                              padding: '8px 0',
                              borderBottom: '1px solid rgba(5, 5, 5, 0.06)',
                            }}
                          >
                            <div style={{ width: 320, flexShrink: 0 }}>
                              <Space size="small" wrap>
                                <Typography.Text strong>{settingLabel(t, item)}</Typography.Text>
                                <Tag color={APPLY_MODE_COLOR[item.applyMode] ?? 'default'}>
                                  {t(`settings.applyMode.${item.applyMode}`, {
                                    defaultValue: item.applyMode,
                                  })}
                                </Tag>
                                {item.source === 'ENV' ? (
                                  <Tag color="blue">{t('settings.source.env')}</Tag>
                                ) : item.editable === false ? (
                                  <Tag>{t('settings.readOnly')}</Tag>
                                ) : null}
                                {changed ? (
                                  <Tag color="orange">{t('settings.changed')}</Tag>
                                ) : null}
                              </Space>
                              <div>
                                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                  {item.description}
                                </Typography.Text>
                              </div>
                              {item.source === 'ENV' ? (
                                <div>
                                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                    {t('settings.source.envHint')}
                                  </Typography.Text>
                                </div>
                              ) : null}
                              {item.updatedAt || item.updatedBy ? (
                                <div>
                                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                    {[
                                      item.updatedAt ? formatTime(item.updatedAt) : null,
                                      item.updatedBy,
                                    ]
                                      .filter(Boolean)
                                      .join(' · ')}
                                  </Typography.Text>
                                </div>
                              ) : null}
                            </div>
                            <div style={{ flex: 1 }}>{control(item)}</div>
                            {item.editable === false ? null : (
                              <Button
                                size="small"
                                icon={<UndoOutlined />}
                                loading={
                                  resetMutation.isPending && resetMutation.variables === item.key
                                }
                                onClick={() => confirmReset(item)}
                              >
                                {t('settings.reset')}
                              </Button>
                            )}
                          </div>
                        );
                      })}
                    </Space>
                  </Card>

                  {group === 'alert' ? (
                    <Card title={t('settings.alert.recentLogs')}>
                      {alertLogsQuery.isError ? (
                        <Alert
                          type="error"
                          showIcon
                          title={errorMessage(t, alertLogsQuery.error)}
                        />
                      ) : null}
                      <Table<AlertLogView>
                        rowKey="id"
                        size="small"
                        columns={alertLogColumns}
                        dataSource={alertLogsQuery.data ?? []}
                        loading={alertLogsQuery.isLoading}
                        pagination={false}
                        locale={{ emptyText: t('common.noData') }}
                      />
                    </Card>
                  ) : null}
                </Space>
              ),
            };
          })}
        />
      )}
    </Space>
  );
}

function isValid(item: SettingView, value: string): boolean {
  if (item.type !== 'NUMBER') {
    return true;
  }
  // 数字项必须填写：空串会让后端返回 SETTING_INVALID。
  const trimmed = value.trim();
  if (trimmed === '') {
    return false;
  }
  const numeric = Number(trimmed);
  return Number.isFinite(numeric) && numeric >= item.min && numeric <= item.max;
}
