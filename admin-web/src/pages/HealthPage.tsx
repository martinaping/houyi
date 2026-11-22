import { ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { DescriptionsProps, TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { theme } from 'antd';
import { request } from '../api/client';
import { errorMessage } from '../api/errors';
import type { CorpPullMetric, HealthCheck, HealthReport } from '../api/types';
import { formatNumber, formatTime } from '../utils/format';

const REFRESH_INTERVAL_MS = 30_000;

export function HealthPage() {
  const { t } = useTranslation();
  const { token } = theme.useToken();
  const healthQuery = useQuery({
    queryKey: ['health'],
    queryFn: () => request<HealthReport>('/api/admin/health'),
    refetchInterval: REFRESH_INTERVAL_MS,
  });

  const data = healthQuery.data;
  const statusOk = data?.status === 'UP';

  const checkItem = (
    label: string,
    check: HealthCheck | undefined,
  ): NonNullable<DescriptionsProps['items']>[number] => ({
    key: label,
    label,
    children: check ? (
      <Space size="small">
        <Tag color={check.ok ? 'green' : 'red'}>
          {check.ok ? t('health.ok') : t('health.fail')}
        </Tag>
        <Typography.Text type="secondary">{t('health.latency', { ms: check.latencyMs })}</Typography.Text>
        {check.message ? <Typography.Text type="secondary">{check.message}</Typography.Text> : null}
      </Space>
    ) : (
      '-'
    ),
  });

  const rocketMqStatus = (queue: HealthReport['queue']) => {
    if (queue.rocketMqState === 'UP') {
      return (
        <Space size="small" wrap>
          <Tag color="green">{t('health.rocketMqUp')}</Tag>
          <Typography.Text type="secondary">
            {t('health.latency', { ms: queue.rocketMqLatencyMs ?? '-' })}
          </Typography.Text>
          <Typography.Text type="secondary">
            {t('health.checkedAt')}: {formatTime(queue.rocketMqCheckedAt)}
          </Typography.Text>
        </Space>
      );
    }
    if (queue.rocketMqState === 'DOWN') {
      return (
        <Space size="small" wrap>
          <Tag color="red">{t('health.rocketMqDown')}</Tag>
          <Typography.Text type="secondary">
            {t('health.latency', { ms: queue.rocketMqLatencyMs ?? '-' })}
          </Typography.Text>
          <Typography.Text type="secondary">
            {t('health.checkedAt')}: {formatTime(queue.rocketMqCheckedAt)}
          </Typography.Text>
          {queue.rocketMqError ? (
            <Typography.Text type="danger">{queue.rocketMqError}</Typography.Text>
          ) : null}
        </Space>
      );
    }
    return <Tag>{t('health.rocketMqUnknown')}</Tag>;
  };

  const items: DescriptionsProps['items'] = [
    checkItem(t('health.database'), data?.database),
    checkItem(t('health.redis'), data?.redis),
    {
      key: 'redisMq',
      label: t('health.redisMq'),
      children: data ? (
        <Tag color={data.queue.redisMqEnabled ? 'green' : 'default'}>
          {data.queue.redisMqEnabled ? t('common.enabled') : t('common.disabled')}
        </Tag>
      ) : (
        '-'
      ),
    },
    {
      key: 'mainStream',
      label: t('health.mainStream'),
      children: formatNumber(data?.queue.mainStreamLength),
    },
    {
      key: 'retryStream',
      label: t('health.retryStream'),
      children: formatNumber(data?.queue.retryStreamLength),
    },
    {
      key: 'delayPending',
      label: t('health.delayPending'),
      children: formatNumber(data?.queue.delayPending),
    },
    {
      key: 'rocketMq',
      label: t('health.rocketMq'),
      children: data ? rocketMqStatus(data.queue) : '-',
    },
    {
      key: 'alert',
      label: t('health.alert'),
      children: data ? (
        <Space size="small" wrap>
          <Tag color={data.alert.enabled ? 'green' : 'default'}>
            {data.alert.enabled ? t('health.alertsEnabled') : t('health.alertsDisabled')}
          </Tag>
          <Tag color={data.alert.webhookConfigured ? 'blue' : 'default'}>
            {data.alert.webhookConfigured ? t('health.webhookConfigured') : t('health.webhookNotConfigured')}
          </Tag>
          <Typography.Text type="secondary">
            {t('health.lastAlertAt')}: {formatTime(data.alert.lastAlertAt)}
          </Typography.Text>
        </Space>
      ) : (
        '-'
      ),
    },
    {
      key: 'retention',
      label: t('health.retention'),
      children: data ? (
        <Space size="small" wrap>
          <Tag color={data.retention.cleanupEnabled ? 'green' : 'default'}>
            {data.retention.cleanupEnabled ? t('common.enabled') : t('common.disabled')}
          </Tag>
          <Typography.Text type="secondary">
            {t('health.lastCleanupAt')}: {formatTime(data.retention.lastCleanupAt)}
          </Typography.Text>
        </Space>
      ) : (
        '-'
      ),
    },
  ];

  const columns: TableColumnsType<CorpPullMetric> = [
    {
      title: t('health.corp'),
      key: 'corp',
      render: (_value, record) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text>{record.corpName || record.corpId}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {record.corpId}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: t('health.pullEnabled'),
      dataIndex: 'enabled',
      width: 110,
      render: (value: boolean) => (
        <Tag color={value ? 'green' : 'default'}>
          {value ? t('common.enabled') : t('common.disabled')}
        </Tag>
      ),
    },
    {
      title: t('health.lastPullAt'),
      dataIndex: 'lastEndAt',
      width: 180,
      render: (value: number | null) => formatTime(value),
    },
    {
      title: t('health.duration'),
      dataIndex: 'durationMs',
      width: 120,
      render: (value: number | null) =>
        value == null ? '-' : t('health.latency', { ms: value }),
    },
    {
      title: t('health.lastFetched'),
      dataIndex: 'lastFetched',
      width: 110,
      render: (value: number | null) => formatNumber(value),
    },
    {
      title: t('health.totalFetched'),
      dataIndex: 'totalFetched',
      width: 120,
      render: (value: number | null) => formatNumber(value),
    },
    {
      title: t('health.corpError'),
      key: 'lastError',
      render: (_value, record) =>
        record.lastError ? (
          <Space orientation="vertical" size={0}>
            <Typography.Text type="danger">{record.lastError}</Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {formatTime(record.lastErrorAt)}
            </Typography.Text>
          </Space>
        ) : (
          '-'
        ),
    },
  ];

  const uptime = data?.uptimeSeconds ?? 0;
  const days = Math.floor(uptime / 86400);
  const hours = Math.floor((uptime % 86400) / 3600);
  const minutes = Math.floor((uptime % 3600) / 60);

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Space size="middle">
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t('health.title')}
          </Typography.Title>
          <Typography.Text type="secondary">{t('health.autoRefreshHint')}</Typography.Text>
        </Space>
        <Button
          icon={<ReloadOutlined />}
          loading={healthQuery.isFetching}
          onClick={() => void healthQuery.refetch()}
        >
          {t('common.refresh')}
        </Button>
      </Space>

      {healthQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, healthQuery.error)} />
      ) : null}

      {data && !statusOk ? (
        <Alert type="error" showIcon title={t('health.degradedHint')} />
      ) : null}

      <Row gutter={16}>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={healthQuery.isLoading}>
            <Statistic
              title={t('health.status')}
              value={statusOk ? t('health.up') : t('health.degraded')}
              styles={{ content: { color: statusOk ? token.colorSuccess : token.colorError } }}
            />
            <Typography.Text type="secondary">
              {t('health.checkedAt')}: {formatTime(data?.checkedAt)}
            </Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={healthQuery.isLoading}>
            <Statistic title={t('health.uptime')} value={t('health.uptimeValue', { days, hours, minutes })} />
            <Typography.Text type="secondary">
              {t('app.version', { version: data?.version ?? '-' })}
            </Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={healthQuery.isLoading}>
            <Statistic title={t('health.pushSuccess')} value={formatNumber(data?.push.success)} />
            <Typography.Text type="secondary">
              {t('health.lastPushAt')}: {formatTime(data?.push.lastPushAt)}
            </Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={healthQuery.isLoading}>
            <Statistic title={t('health.pushFailure')} value={formatNumber(data?.push.failure)} />
            <Typography.Text type="secondary">
              {t('health.pushRetry')}: {formatNumber(data?.push.retry)}
            </Typography.Text>
          </Card>
        </Col>
      </Row>

      <Card title={t('health.checks')}>
        <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }} items={items} />
        {data?.push.lastError ? (
          <Alert
            type="warning"
            showIcon
            style={{ marginTop: 16 }}
            title={`${t('health.lastError')}: ${data.push.lastError}`}
            description={`${t('health.lastErrorAt')}: ${formatTime(data.push.lastErrorAt)}`}
          />
        ) : null}
      </Card>

      <Card title={t('health.corpsTitle')}>
        <Alert type="info" showIcon title={t('health.metricHint')} style={{ marginBottom: 16 }} />
        <Table<CorpPullMetric>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={data?.corps ?? []}
          loading={healthQuery.isLoading}
          pagination={false}
          scroll={{ x: 900 }}
          locale={{ emptyText: t('health.noCorps') }}
        />
      </Card>
    </Space>
  );
}
