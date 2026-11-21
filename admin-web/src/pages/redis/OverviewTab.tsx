import { Alert, Card, Col, Descriptions, Row, Space, Statistic, Table, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import type { UseQueryResult } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import type { RedisKeyspace, RedisOverview, RedisStreamSummary } from '../../api/redis';
import { formatCount } from '../rocketmq/shared';

export function RedisOverviewTab({ query }: { query: UseQueryResult<RedisOverview> }) {
  const { t } = useTranslation();
  const data = query.data;
  const info = data?.info ?? {};

  const keyspaceColumns: TableColumnsType<RedisKeyspace> = [
    { title: t('redis.fields.db'), dataIndex: 'name', width: 100 },
    {
      title: t('redis.fields.keys'),
      dataIndex: 'keys',
      width: 120,
      render: (value: number) => formatCount(value),
    },
    {
      title: t('redis.fields.expires'),
      dataIndex: 'expires',
      width: 120,
      render: (value: number) => formatCount(value),
    },
    {
      title: t('redis.fields.avgTtl'),
      dataIndex: 'avgTtl',
      width: 140,
      render: (value: number | null) => (value == null ? '-' : `${value} ms`),
    },
  ];

  const streamColumns: TableColumnsType<RedisStreamSummary> = [
    { title: t('redis.fields.stream'), dataIndex: 'key', ellipsis: true },
    {
      title: t('redis.fields.length'),
      dataIndex: 'length',
      width: 110,
      render: (value: number) => formatCount(value),
    },
    {
      title: t('redis.fields.groups'),
      dataIndex: 'groups',
      width: 100,
    },
    { title: t('redis.fields.firstId'), dataIndex: 'firstEntryId', width: 180, ellipsis: true },
    { title: t('redis.fields.lastId'), dataIndex: 'lastEntryId', width: 180, ellipsis: true },
  ];

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Row gutter={[16, 16]}>
        <Col xs={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic title={t('redis.overview.memory')} value={info.used_memory_human ?? '-'} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic title={t('redis.overview.clients')} value={info.connected_clients ?? '-'} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic title={t('redis.overview.ops')} value={info.instantaneous_ops_per_sec ?? '-'} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic title={t('redis.overview.dbSize')} value={formatCount(data?.dbSize)} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic title={t('redis.overview.lastSeq')} value={formatCount(data?.lastSeqCount)} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic title={t('redis.overview.pullMetrics')} value={formatCount(data?.pullMetricCount)} />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card loading={query.isLoading} title={t('redis.overview.pushMetric')}>
            <Typography.Text code>{data?.pushMetricSummary ?? '-'}</Typography.Text>
            <div style={{ marginTop: 8 }}>
              <Typography.Text type="secondary">
                {t('redis.overview.latency', { ms: data?.latencyMs ?? '-' })}
              </Typography.Text>
            </div>
          </Card>
        </Col>
      </Row>

      <Alert type="info" showIcon title={data?.note ?? t('redis.overview.note')} />

      <Card title={t('redis.overview.info')}>
        <Descriptions
          bordered
          size="small"
          column={{ xs: 1, sm: 2, xl: 3 }}
          items={Object.entries(info).map(([key, value]) => ({
            key,
            label: <Typography.Text code>{key}</Typography.Text>,
            children: value,
          }))}
        />
      </Card>

      <Card title={t('redis.overview.keyspaces')}>
        <Table<RedisKeyspace>
          rowKey="name"
          size="small"
          columns={keyspaceColumns}
          dataSource={data?.keyspaces ?? []}
          loading={query.isLoading}
          pagination={false}
          locale={{ emptyText: t('common.noData') }}
        />
      </Card>

      <Card title={t('redis.overview.streams')}>
        <Table<RedisStreamSummary>
          rowKey="key"
          size="small"
          columns={streamColumns}
          dataSource={data?.streams ?? []}
          loading={query.isLoading}
          pagination={false}
          locale={{ emptyText: t('common.noData') }}
          scroll={{ x: 760 }}
        />
      </Card>
    </Space>
  );
}
