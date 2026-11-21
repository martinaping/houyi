import { Alert, Card, Col, Descriptions, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import type { UseQueryResult } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import type {
  RocketMqBroker,
  RocketMqGroupLag,
  RocketMqOverview,
} from '../../api/rocketmq';
import { formatCount, formatMs, formatTps, ProbeTag } from './shared';
import { formatTime } from '../../utils/format';

export function OverviewTab({ query }: { query: UseQueryResult<RocketMqOverview> }) {
  const { t } = useTranslation();
  const data = query.data;

  const groupColumns: TableColumnsType<RocketMqGroupLag> = [
    { title: t('rocketmq.fields.group'), dataIndex: 'group', ellipsis: true },
    {
      title: t('rocketmq.fields.diff'),
      dataIndex: 'diffTotal',
      width: 130,
      render: (value: number) => (
        <Typography.Text type={value > 0 ? 'danger' : undefined}>{formatCount(value)}</Typography.Text>
      ),
    },
    {
      title: t('rocketmq.fields.consumeTps'),
      dataIndex: 'consumeTps',
      width: 110,
      render: (value: number) => formatTps(value),
    },
    {
      title: t('rocketmq.fields.lastConsume'),
      dataIndex: 'lastTimestamp',
      width: 180,
      render: (value: number) => formatTime(value),
    },
  ];

  const brokerColumns: TableColumnsType<RocketMqBroker> = [
    { title: t('rocketmq.fields.cluster'), dataIndex: 'cluster', width: 150 },
    { title: t('rocketmq.fields.broker'), dataIndex: 'brokerName', width: 150 },
    {
      title: t('rocketmq.fields.role'),
      dataIndex: 'role',
      width: 100,
      render: (value: string) => <Tag color={value === 'MASTER' ? 'blue' : 'default'}>{value}</Tag>,
    },
    { title: t('rocketmq.fields.addr'), dataIndex: 'addr', ellipsis: true },
    {
      title: t('rocketmq.fields.version'),
      dataIndex: 'version',
      width: 110,
      render: (value: string) => value || '-',
    },
    {
      title: t('rocketmq.fields.active'),
      dataIndex: 'active',
      width: 100,
      render: (value: boolean) => (
        <Tag color={value ? 'green' : 'red'}>
          {value ? t('rocketmq.active') : t('rocketmq.inactive')}
        </Tag>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Row gutter={[16, 16]}>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic title={t('rocketmq.overview.topics')} value={data?.topicCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic title={t('rocketmq.overview.groups')} value={data?.groupCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic
              title={t('rocketmq.overview.totalLag')}
              value={formatCount(data?.totalLag)}
              valueStyle={{ color: (data?.totalLag ?? 0) > 0 ? '#cf1322' : undefined }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic
              title={t('rocketmq.overview.dlqMessages')}
              value={formatCount(data?.dlqMessageCount)}
              valueStyle={{ color: (data?.dlqMessageCount ?? 0) > 0 ? '#cf1322' : undefined }}
            />
          </Card>
        </Col>
      </Row>

      {data && !data.available ? (
        <Alert type="error" showIcon title={t('rocketmq.overview.loadFailed')} description={data.error} />
      ) : null}

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}>
          <Card title={t('rocketmq.overview.probe')}>
            <Descriptions
              column={1}
              size="small"
              items={[
                {
                  key: 'state',
                  label: t('rocketmq.fields.state'),
                  children: <ProbeTag state={data?.probe?.state} />,
                },
                {
                  key: 'endpoint',
                  label: t('rocketmq.fields.endpoint'),
                  children: <Typography.Text code>{data?.probe?.endpoint || '-'}</Typography.Text>,
                },
                {
                  key: 'latency',
                  label: t('rocketmq.fields.latency'),
                  children: formatMs(data?.probe?.latencyMs),
                },
                {
                  key: 'checkedAt',
                  label: t('rocketmq.fields.checkedAt'),
                  children: formatTime(data?.probe?.checkedAt),
                },
                {
                  key: 'message',
                  label: t('rocketmq.fields.message'),
                  children: data?.probe?.message || '-',
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card title={t('rocketmq.overview.config')}>
            <Descriptions
              column={1}
              size="small"
              items={[
                {
                  key: 'namesrv',
                  label: 'NameServer',
                  children: <Typography.Text code>{data?.namesrvAddr || '-'}</Typography.Text>,
                },
                { key: 'cluster', label: t('rocketmq.fields.cluster'), children: data?.clusterName || '-' },
                {
                  key: 'refreshedAt',
                  label: t('rocketmq.overview.refreshedAt'),
                  children: formatTime(data?.refreshedAt),
                },
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Card title={t('rocketmq.overview.topLag')}>
        <Table<RocketMqGroupLag>
          rowKey="group"
          size="small"
          columns={groupColumns}
          dataSource={data?.topLagGroups ?? []}
          loading={query.isLoading}
          pagination={false}
          locale={{ emptyText: t('rocketmq.overview.noLag') }}
          scroll={{ x: 720 }}
        />
      </Card>

      <Card title={t('rocketmq.overview.brokers')}>
        <Table<RocketMqBroker>
          rowKey={(record) => `${record.cluster}-${record.brokerName}-${record.brokerId}`}
          size="small"
          columns={brokerColumns}
          dataSource={data?.brokers ?? []}
          loading={query.isLoading}
          pagination={false}
          locale={{ emptyText: t('common.noData') }}
          scroll={{ x: 780 }}
        />
      </Card>
    </Space>
  );
}
