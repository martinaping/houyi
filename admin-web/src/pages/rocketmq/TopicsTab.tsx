import { ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Input,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { rocketMqApi } from '../../api/rocketmq';
import type {
  RocketMqBrokerRoute,
  RocketMqQueueOffset,
  RocketMqTopicDetail,
  RocketMqTopicQueue,
  RocketMqTopicSummary,
} from '../../api/rocketmq';
import { errorMessage } from '../../api/errors';
import { formatCount } from './shared';
import { formatTime } from '../../utils/format';

export function TopicsTab() {
  const { t } = useTranslation();
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [includeSystem, setIncludeSystem] = useState(false);
  const [selectedTopic, setSelectedTopic] = useState<string | null>(null);

  const topicsQuery = useQuery({
    queryKey: ['rocketmq', 'topics', search, includeSystem],
    queryFn: () => rocketMqApi.topics({ keyword: search, includeSystem }),
    refetchInterval: 30_000,
  });

  const detailQuery = useQuery({
    queryKey: ['rocketmq', 'topic', selectedTopic],
    queryFn: () => rocketMqApi.topic(selectedTopic as string),
    enabled: selectedTopic !== null,
  });

  const columns: TableColumnsType<RocketMqTopicSummary> = [
    { title: t('rocketmq.fields.topic'), dataIndex: 'topic', ellipsis: true },
    {
      title: t('rocketmq.fields.type'),
      key: 'type',
      width: 140,
      render: (_value, record) => {
        if (record.dlq) {
          return <Tag color="red">DLQ</Tag>;
        }
        return record.system ? <Tag>system</Tag> : <Tag color="blue">business</Tag>;
      },
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 100,
      render: () => t('common.detail'),
    },
  ];

  const queueColumns: TableColumnsType<RocketMqTopicQueue> = [
    { title: t('rocketmq.fields.broker'), dataIndex: 'brokerName', width: 160 },
    { title: t('rocketmq.fields.readQueues'), dataIndex: 'readQueueNums', width: 110 },
    { title: t('rocketmq.fields.writeQueues'), dataIndex: 'writeQueueNums', width: 110 },
    {
      title: t('rocketmq.fields.perm'),
      dataIndex: 'perm',
      width: 90,
      render: (value: number) => (value === 6 ? 'RW' : value === 4 ? 'R' : value === 2 ? 'W' : value),
    },
  ];

  const brokerColumns: TableColumnsType<RocketMqBrokerRoute> = [
    { title: t('rocketmq.fields.cluster'), dataIndex: 'cluster', width: 150 },
    { title: t('rocketmq.fields.broker'), dataIndex: 'brokerName', width: 150 },
    { title: t('rocketmq.fields.brokerId'), dataIndex: 'brokerId', width: 100 },
    { title: t('rocketmq.fields.addr'), dataIndex: 'addr', ellipsis: true },
  ];

  const offsetColumns: TableColumnsType<RocketMqQueueOffset> = [
    { title: t('rocketmq.fields.broker'), dataIndex: 'brokerName', width: 130 },
    { title: t('rocketmq.fields.queueId'), dataIndex: 'queueId', width: 80 },
    {
      title: t('rocketmq.fields.maxOffset'),
      dataIndex: 'brokerOffset',
      width: 130,
      render: (value: number) => formatCount(value),
    },
    {
      title: t('rocketmq.fields.messageCount'),
      dataIndex: 'diff',
      width: 130,
      render: (value: number) => formatCount(value),
    },
    {
      title: t('rocketmq.fields.lastUpdate'),
      dataIndex: 'lastTimestamp',
      width: 180,
      render: (value: number) => formatTime(value),
    },
  ];

  const detail: RocketMqTopicDetail | undefined = detailQuery.data;

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
          <Space wrap>
            <Input.Search
              allowClear
              value={keyword}
              placeholder={t('rocketmq.topics.searchPlaceholder')}
              style={{ width: 320 }}
              onChange={(event) => setKeyword(event.target.value)}
              onSearch={(value) => setSearch(value.trim())}
            />
            <Space size={6}>
              <Typography.Text type="secondary">{t('rocketmq.topics.includeSystem')}</Typography.Text>
              <Switch checked={includeSystem} onChange={setIncludeSystem} />
            </Space>
          </Space>
          <Button
            icon={<ReloadOutlined />}
            loading={topicsQuery.isFetching}
            onClick={() => void topicsQuery.refetch()}
          >
            {t('common.refresh')}
          </Button>
        </Space>
      </Card>

      {topicsQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, topicsQuery.error)} />
      ) : null}

      <Card>
        <Table<RocketMqTopicSummary>
          rowKey="topic"
          size="small"
          columns={columns}
          dataSource={topicsQuery.data ?? []}
          loading={topicsQuery.isLoading}
          pagination={{ pageSize: 20, showSizeChanger: false }}
          locale={{ emptyText: t('rocketmq.topics.empty') }}
          onRow={(record) => ({
            onClick: () => setSelectedTopic(record.topic),
            tabIndex: 0,
            role: 'button',
            style: { cursor: 'pointer' },
            onKeyDown: (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                setSelectedTopic(record.topic);
              }
            },
          })}
        />
      </Card>

      <Drawer
        title={t('rocketmq.topics.detailTitle', { topic: selectedTopic ?? '' })}
        open={selectedTopic !== null}
        size={760}
        loading={detailQuery.isLoading}
        onClose={() => setSelectedTopic(null)}
      >
        {detailQuery.isError ? (
          <Alert type="error" showIcon title={errorMessage(t, detailQuery.error)} />
        ) : null}
        {detail ? (
          <Space orientation="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions
              bordered
              size="small"
              column={1}
              items={[
                { key: 'topic', label: t('rocketmq.fields.topic'), children: detail.topic },
                {
                  key: 'type',
                  label: t('rocketmq.fields.type'),
                  children: detail.system ? <Tag>system</Tag> : <Tag color="blue">business</Tag>,
                },
                {
                  key: 'config',
                  label: t('rocketmq.topics.topicConfig'),
                  children: detail.config
                    ? `${detail.config.readQueueNums}R / ${detail.config.writeQueueNums}W / perm=${detail.config.perm}${detail.config.order ? ' / order' : ''}`
                    : '-',
                },
                {
                  key: 'groups',
                  label: t('rocketmq.topics.consumerGroups'),
                  children:
                    detail.consumerGroups.length > 0 ? (
                      <Space size={[0, 4]} wrap>
                        {detail.consumerGroups.map((group) => (
                          <Tag key={group}>{group}</Tag>
                        ))}
                      </Space>
                    ) : (
                      '-'
                    ),
                },
              ]}
            />

            <Card size="small" title={t('rocketmq.topics.queues')}>
              <Table<RocketMqTopicQueue>
                rowKey={(record) => `${record.brokerName}-${record.readQueueNums}-${record.writeQueueNums}`}
                size="small"
                columns={queueColumns}
                dataSource={detail.queues}
                pagination={false}
                locale={{ emptyText: t('common.noData') }}
              />
            </Card>

            <Card size="small" title={t('rocketmq.topics.routes')}>
              <Table<RocketMqBrokerRoute>
                rowKey={(record) => `${record.brokerName}-${record.brokerId}`}
                size="small"
                columns={brokerColumns}
                dataSource={detail.brokers}
                pagination={false}
                locale={{ emptyText: t('common.noData') }}
              />
            </Card>

            <Card size="small" title={t('rocketmq.topics.offsets')}>
              <Table<RocketMqQueueOffset>
                rowKey={(record) => `${record.brokerName}-${record.queueId}`}
                size="small"
                columns={offsetColumns}
                dataSource={detail.offsets}
                pagination={false}
                locale={{ emptyText: t('common.noData') }}
                scroll={{ x: 720 }}
              />
            </Card>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
