import { ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Space,
  Table,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { redisApi } from '../../api/redis';
import type {
  RedisStreamConsumer,
  RedisStreamDetail,
  RedisStreamSummary,
} from '../../api/redis';
import { formatCount } from '../rocketmq/shared';

export function RedisStreamsTab() {
  const { t } = useTranslation();
  const [selectedKey, setSelectedKey] = useState<string | null>(null);

  const streamsQuery = useQuery({
    queryKey: ['redis', 'streams'],
    queryFn: () => redisApi.streams(),
    refetchInterval: 30_000,
  });
  const detailQuery = useQuery({
    queryKey: ['redis', 'stream', selectedKey],
    queryFn: () => redisApi.streamDetail(selectedKey as string),
    enabled: selectedKey !== null,
  });

  const columns: TableColumnsType<RedisStreamSummary> = [
    { title: t('redis.fields.stream'), dataIndex: 'key', ellipsis: true },
    {
      title: t('redis.fields.length'),
      dataIndex: 'length',
      width: 110,
      render: (value: number) => formatCount(value),
    },
    { title: t('redis.fields.groups'), dataIndex: 'groups', width: 100 },
    { title: t('redis.fields.firstId'), dataIndex: 'firstEntryId', width: 180, ellipsis: true },
    { title: t('redis.fields.lastId'), dataIndex: 'lastEntryId', width: 180, ellipsis: true },
  ];

  const consumerColumns: TableColumnsType<RedisStreamConsumer> = [
    { title: t('redis.fields.consumer'), dataIndex: 'name', ellipsis: true },
    {
      title: t('redis.fields.pending'),
      dataIndex: 'pending',
      width: 120,
      render: (value: number) => formatCount(value),
    },
    {
      title: t('redis.fields.idle'),
      dataIndex: 'idleMs',
      width: 140,
      render: (value: number) => `${formatCount(value)} ms`,
    },
  ];

  const detail: RedisStreamDetail | undefined = detailQuery.data;

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Text type="secondary">{t('redis.streams.hint')}</Typography.Text>
        <Button
          icon={<ReloadOutlined />}
          loading={streamsQuery.isFetching}
          onClick={() => void streamsQuery.refetch()}
        >
          {t('common.refresh')}
        </Button>
      </Space>

      {streamsQuery.isError ? (
        <Alert type="error" showIcon title={t('redis.streams.loadFailed')} />
      ) : null}

      <Card>
        <Table<RedisStreamSummary>
          rowKey="key"
          size="small"
          columns={columns}
          dataSource={streamsQuery.data ?? []}
          loading={streamsQuery.isLoading}
          pagination={false}
          locale={{ emptyText: t('redis.streams.empty') }}
          scroll={{ x: 800 }}
          onRow={(record) => ({
            onClick: () => setSelectedKey(record.key),
            tabIndex: 0,
            role: 'button',
            style: { cursor: 'pointer' },
            onKeyDown: (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                setSelectedKey(record.key);
              }
            },
          })}
        />
      </Card>

      <Drawer
        title={t('redis.streams.detailTitle', { key: selectedKey ?? '' })}
        open={selectedKey !== null}
        size={820}
        loading={detailQuery.isLoading}
        onClose={() => setSelectedKey(null)}
      >
        {detailQuery.isError ? (
          <Alert type="error" showIcon title={t('redis.streams.loadFailed')} />
        ) : null}
        {detail ? (
          <Space orientation="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions
              bordered
              size="small"
              column={2}
              items={[
                { key: 'key', label: t('redis.fields.stream'), children: detail.summary.key },
                {
                  key: 'length',
                  label: t('redis.fields.length'),
                  children: formatCount(detail.summary.length),
                },
                {
                  key: 'groups',
                  label: t('redis.fields.groups'),
                  children: formatCount(detail.summary.groups),
                },
                {
                  key: 'first',
                  label: t('redis.fields.firstId'),
                  children: detail.summary.firstEntryId,
                },
                {
                  key: 'last',
                  label: t('redis.fields.lastId'),
                  children: detail.summary.lastEntryId,
                },
              ]}
            />

            {detail.groups.map((group) => (
              <Card key={group.name} size="small" title={`${t('redis.fields.group')}: ${group.name}`}>
                <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
                  <Descriptions
                    size="small"
                    column={3}
                    items={[
                      {
                        key: 'consumers',
                        label: t('redis.fields.consumers'),
                        children: formatCount(group.consumers),
                      },
                      {
                        key: 'pending',
                        label: t('redis.fields.pending'),
                        children: formatCount(group.pending),
                      },
                      {
                        key: 'lastDelivered',
                        label: t('redis.fields.lastDelivered'),
                        children: group.lastDeliveredId,
                      },
                    ]}
                  />
                  {group.pendingSummary ? (
                    <Typography.Text type="secondary">
                      {t('redis.fields.pendingRange', {
                        total: group.pendingSummary.total,
                        min: group.pendingSummary.smallestId || '-',
                        max: group.pendingSummary.largestId || '-',
                      })}
                    </Typography.Text>
                  ) : null}
                  <Table<RedisStreamConsumer>
                    rowKey="name"
                    size="small"
                    columns={consumerColumns}
                    dataSource={group.consumerDetails}
                    pagination={false}
                    locale={{ emptyText: t('redis.streams.noConsumers') }}
                  />
                </Space>
              </Card>
            ))}

            <Card size="small" title={t('redis.streams.recentEntries')}>
              <Table
                rowKey="id"
                size="small"
                columns={[
                  { title: 'ID', dataIndex: 'id', width: 180 },
                  {
                    title: t('redis.fields.fields'),
                    dataIndex: 'fields',
                    render: (value: Record<string, string>) => (
                      <Typography.Text code style={{ fontSize: 12 }}>
                        {JSON.stringify(value)}
                      </Typography.Text>
                    ),
                  },
                ]}
                dataSource={detail.recentEntries}
                pagination={false}
                locale={{ emptyText: t('redis.streams.noEntries') }}
                scroll={{ x: 680 }}
              />
            </Card>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
