import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Input, Space, Table, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { mnsApi } from '../../api/mns';
import type { MnsQueueSummary } from '../../api/mns';
import { errorMessage } from '../../api/errors';
import { formatBytes, formatCount } from '../rocketmq/shared';
import { formatTime } from '../../utils/format';

export function MnsQueuesTab({ onSelect }: { onSelect: (queue: string) => void }) {
  const { t } = useTranslation();
  const [prefix, setPrefix] = useState('');
  const [search, setSearch] = useState('');

  const query = useQuery({
    queryKey: ['mns', 'queues', search],
    queryFn: () => mnsApi.queues(search, 50),
    retry: false,
  });

  const columns: TableColumnsType<MnsQueueSummary> = [
    { title: t('mns.fields.queue'), dataIndex: 'queueName', ellipsis: true },
    {
      title: t('mns.overview.active'),
      dataIndex: 'activeMessages',
      width: 110,
      render: (value: number | null) => formatCount(value),
    },
    {
      title: t('mns.overview.delayed'),
      dataIndex: 'delayMessages',
      width: 110,
      render: (value: number | null) => formatCount(value),
    },
    {
      title: t('mns.overview.inactive'),
      dataIndex: 'inactiveMessages',
      width: 110,
      render: (value: number | null) => formatCount(value),
    },
    {
      title: t('mns.fields.retention'),
      dataIndex: 'retentionPeriod',
      width: 120,
      render: (value: number | null) => (value == null ? '-' : `${value} s`),
    },
    {
      title: t('mns.overview.maxMessageSize'),
      dataIndex: 'maxMessageSize',
      width: 140,
      render: (value: number | null) => formatBytes(value),
    },
    {
      title: t('mns.fields.createTime'),
      dataIndex: 'createTime',
      width: 180,
      render: (value: number | null) => formatTime(value),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 100,
      render: (_value, record) => (
        <Button size="small" onClick={() => onSelect(record.queueName)}>
          {t('common.detail')}
        </Button>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
          <Space>
            <Input
              allowClear
              value={prefix}
              placeholder={t('mns.queues.prefixPlaceholder')}
              style={{ width: 280 }}
              onChange={(event) => setPrefix(event.target.value)}
              onPressEnter={() => setSearch(prefix.trim())}
            />
            <Button type="primary" icon={<SearchOutlined />} onClick={() => setSearch(prefix.trim())}>
              {t('common.search')}
            </Button>
          </Space>
          <Button
            icon={<ReloadOutlined />}
            loading={query.isFetching}
            onClick={() => void query.refetch()}
          >
            {t('common.refresh')}
          </Button>
        </Space>
      </Card>

      {query.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, query.error)} />
      ) : null}

      <Card title={t('mns.queues.title')}>
        <Table<MnsQueueSummary>
          rowKey="queueName"
          size="small"
          columns={columns}
          dataSource={query.data ?? []}
          loading={query.isLoading}
          pagination={false}
          scroll={{ x: 1100 }}
          locale={{ emptyText: t('mns.queues.empty') }}
          onRow={(record) => ({
            onClick: () => onSelect(record.queueName),
            tabIndex: 0,
            role: 'button',
            style: { cursor: 'pointer' },
            onKeyDown: (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onSelect(record.queueName);
              }
            },
          })}
        />
      </Card>

      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        {t('mns.queues.hint')}
      </Typography.Text>
    </Space>
  );
}
