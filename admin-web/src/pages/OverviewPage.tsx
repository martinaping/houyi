import { ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Col, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { request } from '../api/client';
import { errorMessage } from '../api/errors';
import type { MessageSummary, OverviewView } from '../api/types';
import { formatNumber, formatTime } from '../utils/format';

export function OverviewPage() {
  const { t } = useTranslation();
  const overviewQuery = useQuery({
    queryKey: ['overview'],
    queryFn: () => request<OverviewView>('/api/admin/overview'),
  });

  const data = overviewQuery.data;
  const pendingRetry = data?.queue.delayMessages.find((item) => item.status === 0)?.count ?? 0;

  const columns: TableColumnsType<MessageSummary> = [
    { title: t('messages.id'), dataIndex: 'id', width: 90 },
    {
      title: t('messages.msgId'),
      dataIndex: 'msgId',
      ellipsis: true,
    },
    { title: t('messages.corpId'), dataIndex: 'corpId', ellipsis: true },
    { title: t('messages.msgType'), dataIndex: 'msgType', width: 110 },
    {
      title: t('messages.downFailAt'),
      dataIndex: 'downFailAt',
      width: 180,
      render: (value: number) => formatTime(value),
    },
    {
      title: t('messages.createAt'),
      dataIndex: 'createAt',
      width: 180,
      render: (value: number) => formatTime(value),
    },
  ];

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('overview.title')}
        </Typography.Title>
        <Button
          icon={<ReloadOutlined />}
          loading={overviewQuery.isFetching}
          onClick={() => void overviewQuery.refetch()}
        >
          {t('common.refresh')}
        </Button>
      </Space>

      {overviewQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, overviewQuery.error)} />
      ) : null}

      {data && !data.queue.redisMqEnabled ? (
        <Alert type="info" showIcon title={t('overview.queueDisabled')} />
      ) : null}

      <Row gutter={16}>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={overviewQuery.isLoading}>
            <Statistic title={t('overview.corpTotal')} value={data?.corps.total ?? 0} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={overviewQuery.isLoading}>
            <Statistic title={t('overview.corpEnabled')} value={data?.corps.enabled ?? 0} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={overviewQuery.isLoading}>
            <Statistic
              title={t('overview.mainStream')}
              value={formatNumber(data?.queue.mainStream.length)}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={overviewQuery.isLoading}>
            <Statistic title={t('overview.pendingRetry')} value={pendingRetry} />
          </Card>
        </Col>
      </Row>

      <Card
        title={t('overview.recentFailures')}
        extra={
          data ? (
            <Tag color="red">{t('overview.retryStream')}: {formatNumber(data.queue.retryStream.length)}</Tag>
          ) : null
        }
      >
        <Table<MessageSummary>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={data?.recentFailures ?? []}
          loading={overviewQuery.isLoading}
          pagination={false}
        />
      </Card>
    </Space>
  );
}
