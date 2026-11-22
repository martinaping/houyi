import { ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Col, Row, Space, Statistic, Table, Tabs, Tag, Typography, theme } from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { request } from '../api/client';
import { errorMessage } from '../api/errors';
import type { DelayStatusCount, QueueOverview, RocketMqState } from '../api/types';
import { MnsPage } from './mns/MnsPage';
import { RedisPage } from './redis/RedisPage';
import { RocketMqPage } from './rocketmq/RocketMqPage';
import { formatNumber, formatTime } from '../utils/format';

const ROCKET_STATE_KEY: Record<RocketMqState, string> = {
  NOT_CONFIGURED: 'queue.rocketState.notConfigured',
  UP: 'queue.rocketState.up',
  DOWN: 'queue.rocketState.down',
};

export function QueuePage() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const activeKey = searchParams.get('tab') ?? 'overview';

  const changeTab = (key: string) => {
    setSearchParams(key === 'overview' ? {} : { tab: key }, { replace: true });
  };

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space orientation="vertical" size={2}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('queue.title')}
        </Typography.Title>
        <Typography.Text type="secondary">{t('queue.hint')}</Typography.Text>
      </Space>

      <Tabs
        activeKey={activeKey}
        onChange={changeTab}
        items={[
          {
            key: 'overview',
            label: t('queue.tabs.overview'),
            children: <QueueOverviewTab />,
          },
          {
            key: 'rocketmq',
            label: t('queue.tabs.rocketmq'),
            children: <RocketMqPage embedded />,
          },
          {
            key: 'redis',
            label: t('queue.tabs.redis'),
            children: <RedisPage embedded />,
          },
          {
            key: 'mns',
            label: t('queue.tabs.mns'),
            children: <MnsPage embedded />,
          },
        ]}
      />
    </Space>
  );
}

function QueueOverviewTab() {
  const { t } = useTranslation();
  const { token } = theme.useToken();
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
      <Space style={{ justifyContent: 'flex-end', width: '100%' }}>
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
                  color:
                    data?.rocketMqState === 'UP'
                      ? token.colorSuccess
                      : data?.rocketMqState === 'DOWN'
                        ? token.colorError
                        : token.colorTextSecondary,
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
