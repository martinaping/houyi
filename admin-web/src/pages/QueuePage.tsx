import { ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Col, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { request } from '../api/client';
import { errorMessage } from '../api/errors';
import type { DelayStatusCount, QueueOverview } from '../api/types';
import { formatNumber } from '../utils/format';

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
              value={data?.rocketMqConfigured ? t('queue.rocketConfigured') : t('queue.notConfigured')}
            />
            <Typography.Text type="secondary">
              {t('queue.retryTopic')}: {data?.rocketMqRetryTopic || '-'}
            </Typography.Text>
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
