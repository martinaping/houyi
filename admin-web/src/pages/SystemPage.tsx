import { InfoCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Col, Descriptions, Row, Space, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { request } from '../api/client';
import { errorMessage } from '../api/errors';
import type { RuntimeSettingView, SystemInfoView } from '../api/types';
import { formatNumber } from '../utils/format';

const APPLY_MODE_COLOR: Record<string, string> = {
  INSTANT: 'green',
  HOT: 'blue',
  RESTART: 'orange',
};

function formatUptime(seconds: number) {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return { days, hours, minutes };
}

export function SystemPage() {
  const { t } = useTranslation();

  const systemQuery = useQuery({
    queryKey: ['systemInfo'],
    queryFn: () => request<SystemInfoView>('/api/admin/system'),
    refetchInterval: 60_000,
  });

  const data = systemQuery.data;

  const runtimeColumns: TableColumnsType<RuntimeSettingView> = [
    {
      title: t('system.runtimeKey'),
      dataIndex: 'key',
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    { title: t('system.runtimeValue'), dataIndex: 'value', width: 160 },
    {
      title: t('system.runtimeUnit'),
      dataIndex: 'unit',
      width: 120,
      render: (value: string) => value || '-',
    },
    {
      title: t('system.applyMode'),
      dataIndex: 'applyMode',
      width: 140,
      render: (value: string) => (
        <Tag color={APPLY_MODE_COLOR[value] ?? 'default'}>
          {t(`settings.applyMode.${value}`, { defaultValue: value })}
        </Tag>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('system.title')}
        </Typography.Title>
        <Button
          icon={<ReloadOutlined />}
          loading={systemQuery.isFetching}
          onClick={() => void systemQuery.refetch()}
        >
          {t('common.refresh')}
        </Button>
      </Space>

      {systemQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, systemQuery.error)} />
      ) : null}

      <Alert type="info" showIcon title={t('system.readonlyHint')} icon={<InfoCircleOutlined />} />

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title={t('system.service')} loading={systemQuery.isLoading}>
            <Descriptions
              column={1}
              size="small"
              items={[
                { key: 'version', label: t('system.version'), children: data?.version ?? '-' },
                {
                  key: 'uptime',
                  label: t('system.uptime'),
                  children: data
                    ? t('health.uptimeValue', formatUptime(data.uptimeSeconds))
                    : '-',
                },
                { key: 'java', label: t('system.javaVersion'), children: data?.javaVersion ?? '-' },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title={t('system.connections')} loading={systemQuery.isLoading}>
            <Descriptions
              column={1}
              size="small"
              items={[
                {
                  key: 'database',
                  label: t('system.database'),
                  children: data ? (
                    <Space>
                      <Tag color={data.databaseReachable ? 'green' : 'red'}>
                        {data.databaseReachable ? t('health.ok') : t('health.fail')}
                      </Tag>
                      <Typography.Text type="secondary" copyable={!!data.jdbcUrl}>
                        {data.jdbcUrl || '-'}
                      </Typography.Text>
                    </Space>
                  ) : (
                    '-'
                  ),
                },
                {
                  key: 'redis',
                  label: t('system.redis'),
                  children: data ? (
                    <Space>
                      <Tag color={data.redisReachable ? 'green' : 'red'}>
                        {data.redisReachable ? t('health.ok') : t('health.fail')}
                      </Tag>
                      <Typography.Text type="secondary">
                        {data.redisHost}:{data.redisPort} / db{data.redisDatabase}
                      </Typography.Text>
                    </Space>
                  ) : (
                    '-'
                  ),
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title={t('system.integrations')} loading={systemQuery.isLoading}>
            <Descriptions
              column={1}
              size="small"
              items={[
                {
                  key: 'redisMq',
                  label: t('queue.redisEnabled'),
                  children: data ? (
                    <Tag color={data.redisMqEnabled ? 'green' : 'default'}>
                      {data.redisMqEnabled ? t('queue.on') : t('queue.off')}
                    </Tag>
                  ) : (
                    '-'
                  ),
                },
                {
                  key: 'rocketMq',
                  label: t('queue.rocketMq'),
                  children: data ? (
                    <Tag color={data.rocketMqConfigured ? 'blue' : 'default'}>
                      {data.rocketMqConfigured ? t('queue.rocketConfigured') : t('queue.notConfigured')}
                    </Tag>
                  ) : (
                    '-'
                  ),
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title={t('system.stats')} loading={systemQuery.isLoading}>
            <Descriptions
              column={2}
              size="small"
              items={[
                {
                  key: 'corpTotal',
                  label: t('overview.corpTotal'),
                  children: data ? formatNumber(data.corpTotal) : '-',
                },
                {
                  key: 'corpEnabled',
                  label: t('overview.corpEnabled'),
                  children: data ? formatNumber(data.corpEnabled) : '-',
                },
                {
                  key: 'settingCount',
                  label: t('system.settingCount'),
                  children: data ? formatNumber(data.settingCount) : '-',
                },
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Card title={t('system.runtimeSettings')} extra={t('system.runtimeHint')}>
        <Table<RuntimeSettingView>
          rowKey="key"
          size="small"
          columns={runtimeColumns}
          dataSource={data?.runtimeSettings ?? []}
          loading={systemQuery.isLoading}
          pagination={false}
          locale={{ emptyText: t('common.noData') }}
        />
      </Card>
    </Space>
  );
}
