import { Alert, Card, Descriptions, Space, Table, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { redisApi } from '../../api/redis';
import type { RedisLastSeq, RedisPullMetric } from '../../api/redis';
import { formatCount } from '../rocketmq/shared';
import { formatTime } from '../../utils/format';

export function RedisMetricsTab() {
  const { t } = useTranslation();
  const query = useQuery({
    queryKey: ['redis', 'metrics'],
    queryFn: () => redisApi.metrics(),
    refetchInterval: 30_000,
  });
  const data = query.data;

  const lastSeqColumns: TableColumnsType<RedisLastSeq> = [
    { title: t('redis.fields.corpId'), dataIndex: 'corpId', width: 220 },
    { title: t('redis.fields.key'), dataIndex: 'key', ellipsis: true },
    {
      title: t('redis.fields.value'),
      dataIndex: 'seq',
      width: 180,
      render: (value: number | null) => (value == null ? '-' : formatCount(value)),
    },
  ];

  const pullColumns: TableColumnsType<RedisPullMetric> = [
    { title: t('redis.fields.corpId'), dataIndex: 'corpId', width: 220 },
    {
      title: t('redis.metrics.lastEndAt'),
      dataIndex: 'fields',
      width: 180,
      render: (value: Record<string, string>) => {
        const timestamp = Number(value.lastEndAt);
        return Number.isFinite(timestamp) ? formatTime(timestamp) : '-';
      },
    },
    {
      title: t('redis.metrics.lastFetched'),
      dataIndex: 'fields',
      width: 120,
      render: (value: Record<string, string>) => value.lastFetched ?? '-',
    },
    {
      title: t('redis.metrics.totalFetched'),
      dataIndex: 'fields',
      width: 130,
      render: (value: Record<string, string>) => value.totalFetched ?? '-',
    },
    {
      title: t('redis.metrics.lastError'),
      dataIndex: 'fields',
      ellipsis: true,
      render: (value: Record<string, string>) => value.lastError || '-',
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      {query.isError ? <Alert type="error" showIcon title={t('redis.metrics.loadFailed')} /> : null}

      <Card title={t('redis.metrics.lastSeq')}>
        <Table<RedisLastSeq>
          rowKey="key"
          size="small"
          columns={lastSeqColumns}
          dataSource={data?.lastSeq ?? []}
          loading={query.isLoading}
          pagination={false}
          locale={{ emptyText: t('common.noData') }}
        />
      </Card>

      <Card title={t('redis.metrics.pull')}>
        <Table<RedisPullMetric>
          rowKey="corpId"
          size="small"
          columns={pullColumns}
          dataSource={data?.pullMetrics ?? []}
          loading={query.isLoading}
          pagination={false}
          scroll={{ x: 900 }}
          locale={{ emptyText: t('common.noData') }}
        />
      </Card>

      <Card title={t('redis.metrics.push')}>
        <Descriptions
          bordered
          size="small"
          column={{ xs: 1, sm: 2 }}
          items={Object.entries(data?.pushMetric?.fields ?? {}).map(([key, value]) => ({
            key,
            label: key,
            children: <Typography.Text code>{value}</Typography.Text>,
          }))}
        />
      </Card>

      <Card title={t('redis.metrics.alertStates')}>
        <Table
          rowKey="key"
          size="small"
          columns={[
            { title: t('redis.fields.key'), dataIndex: 'key', ellipsis: true },
            {
              title: t('redis.fields.value'),
              dataIndex: 'value',
              render: (value: string) => <Typography.Text code>{value || '-'}</Typography.Text>,
            },
          ]}
          dataSource={data?.alertStates ?? []}
          loading={query.isLoading}
          pagination={false}
          locale={{ emptyText: t('common.noData') }}
        />
      </Card>
    </Space>
  );
}
