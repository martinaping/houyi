import { ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Col, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { request } from '../api/client';
import { errorMessage } from '../api/errors';
import type { DelayStatusCount, QueueOverview, RocketMqState } from '../api/types';
import { formatNumber, formatTime } from '../utils/format';

const ROCKET_STATE_COLOR: Record<RocketMqState, string> = {
  NOT_CONFIGURED: 'rgba(0, 0, 0, 0.45)',
  UP: '#3f8600',
  DOWN: '#cf1322',
};

const ROCKET_STATE_KEY: Record<RocketMqState, string> = {
  NOT_CONFIGURED: 'queue.rocketState.notConfigured',
  UP: 'queue.rocketState.up',
  DOWN: 'queue.rocketState.down',
};

export function QueuePage() {
  const { t } = useTranslation();
  const queueQuery = useQuery({
    queryKey: ['queue'],
    queryFn: () => request<QueueOverview>('/api/admin/queue/overview'),
  });

  const data = queueQuery.data;

  const statusLabel = (status: number): string => {
    if (status === 0) {
      return t('queue.delayPending');
    }
    if (status === 1) {
      return t('queue.delayDelivered');
    }
    return `${t('queue.delayUnknown')} (${status})`;
  };

  const columns: TableColumnsType<DelayStatusCount> = [
    {
      title: t('queue.status'),
      dataIndex: 'status',
      render: (value: number) => <Tag>{statusLabel(value)}</Tag>,
    },
    {
      title: t('queue.count'),
      dataIndex: 'count',
      width: 160,
      render: (value: number) => formatNumber(value),
    },
  ];

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('queue.title')}
        </Typography.Title>
        <Button
          icon={<ReloadOutlined />}
          loading={queueQuery.isFetching}
          onClick={() => void queueQuery.refetch()}
        >
          {t('common.refresh')}
        </Button>
      </Space>

      {queueQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, queueQuery.error)} />
      ) : null}

      <Row gutter={16}>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={queueQuery.isLoading}>
            <Statistic title={t('queue.redisEnabled')} value={data?.redisMqEnabled ? t('queue.on') : t('queue.off')} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={queueQuery.isLoading}>
            <Statistic title={t('queue.mainStream')} value={formatNumber(data?.mainStream.length)} />
            <Typography.Text type="secondary">{data?.mainStream.topic ?? '-'}</Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={queueQuery.isLoading}>
            <Statistic title={t('queue.retryStream')} value={formatNumber(data?.retryStream.length)} />
            <Typography.Text type="secondary">{data?.retryStream.topic ?? '-'}</Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={queueQuery.isLoading}>
            <Statistic
              title={t('queue.rocketMq')}
              value={t(ROCKET_STATE_KEY[data?.rocketMqState ?? 'NOT_CONFIGURED'])}
              styles={{
                content: {
                  color: ROCKET_STATE_COLOR[data?.rocketMqState ?? 'NOT_CONFIGURED'],
                },
              }}
            />
            <Typography.Text type="secondary" style={{ display: 'block' }}>
              {data?.rocketMqEndpoint || '-'}
            </Typography.Text>
            <Typography.Text type="secondary" style={{ display: 'block' }}>
              {t('queue.retryTopic')}: {data?.rocketMqRetryTopic || '-'}
            </Typography.Text>
            {data?.rocketMqState === 'UP' ? (
              <Typography.Text type="secondary" style={{ display: 'block' }}>
                {t('health.latency', { ms: data.rocketMqLatencyMs ?? '-' })} ·{' '}
                {formatTime(data.rocketMqCheckedAt)}
              </Typography.Text>
            ) : null}
            {data?.rocketMqState === 'DOWN' ? (
              <Typography.Text type="danger" style={{ display: 'block' }}>
                {data.rocketMqError || t('queue.rocketState.down')}
              </Typography.Text>
            ) : null}
          </Card>
        </Col>
      </Row>

      <Card title={t('queue.delayMessages')} loading={queueQuery.isLoading}>
        <Table<DelayStatusCount>
          rowKey="status"
          size="small"
          columns={columns}
          dataSource={data?.delayMessages ?? []}
          pagination={false}
          locale={{ emptyText: t('common.noData') }}
        />
      </Card>
    </Space>
  );
}
