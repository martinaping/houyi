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
  RocketMqConnection,
  RocketMqGroupDetail,
  RocketMqGroupLag,
  RocketMqQueueOffset,
} from '../../api/rocketmq';
import { errorMessage } from '../../api/errors';
import { formatCount, formatTps } from './shared';
import { formatTime } from '../../utils/format';

export function GroupsTab() {
  const { t } = useTranslation();
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [includeInternal, setIncludeInternal] = useState(false);
  const [selectedGroup, setSelectedGroup] = useState<string | null>(null);

  const groupsQuery = useQuery({
    queryKey: ['rocketmq', 'groups', search, includeInternal],
    queryFn: () => rocketMqApi.groups({ keyword: search, includeInternal }),
    refetchInterval: 30_000,
  });

  const detailQuery = useQuery({
    queryKey: ['rocketmq', 'group', selectedGroup],
    queryFn: () => rocketMqApi.group(selectedGroup as string),
    enabled: selectedGroup !== null,
  });

  const columns: TableColumnsType<RocketMqGroupLag> = [
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
    {
      title: t('rocketmq.fields.brokerAddr'),
      dataIndex: 'brokerAddr',
      width: 180,
      ellipsis: true,
    },
    {
      title: t('rocketmq.fields.note'),
      dataIndex: 'note',
      ellipsis: true,
      render: (value: string | null) => value || '-',
    },
  ];

  const queueColumns: TableColumnsType<RocketMqQueueOffset> = [
    { title: t('rocketmq.fields.topic'), dataIndex: 'topic', ellipsis: true },
    { title: t('rocketmq.fields.broker'), dataIndex: 'brokerName', width: 120 },
    { title: t('rocketmq.fields.queueId'), dataIndex: 'queueId', width: 80 },
    {
      title: t('rocketmq.fields.brokerOffset'),
      dataIndex: 'brokerOffset',
      width: 130,
      render: (value: number) => formatCount(value),
    },
    {
      title: t('rocketmq.fields.consumerOffset'),
      dataIndex: 'consumerOffset',
      width: 130,
      render: (value: number) => formatCount(value),
    },
    {
      title: t('rocketmq.fields.diff'),
      dataIndex: 'diff',
      width: 110,
      render: (value: number) => (
        <Typography.Text type={value > 0 ? 'danger' : undefined}>{formatCount(value)}</Typography.Text>
      ),
    },
    {
      title: t('rocketmq.fields.lastUpdate'),
      dataIndex: 'lastTimestamp',
      width: 180,
      render: (value: number) => formatTime(value),
    },
  ];

  const connectionColumns: TableColumnsType<RocketMqConnection> = [
    { title: t('rocketmq.fields.clientId'), dataIndex: 'clientId', ellipsis: true },
    { title: t('rocketmq.fields.clientAddr'), dataIndex: 'clientAddr', width: 180 },
    { title: t('rocketmq.fields.language'), dataIndex: 'language', width: 110 },
    { title: t('rocketmq.fields.version'), dataIndex: 'version', width: 100 },
    {
      title: t('rocketmq.fields.subscriptions'),
      dataIndex: 'subscriptions',
      render: (value: string[]) =>
        value.length > 0 ? (
          <Space size={[0, 4]} wrap>
            {value.map((item) => (
              <Tag key={item}>{item}</Tag>
            ))}
          </Space>
        ) : (
          '-'
        ),
    },
  ];

  const detail: RocketMqGroupDetail | undefined = detailQuery.data;

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
          <Input.Search
            allowClear
            value={keyword}
            placeholder={t('rocketmq.groups.searchPlaceholder')}
            style={{ width: 320 }}
            onChange={(event) => setKeyword(event.target.value)}
            onSearch={(value) => setSearch(value.trim())}
          />
          <Space size={6}>
            <Typography.Text type="secondary">{t('rocketmq.groups.includeInternal')}</Typography.Text>
            <Switch checked={includeInternal} onChange={setIncludeInternal} />
          </Space>
          <Button
            icon={<ReloadOutlined />}
            loading={groupsQuery.isFetching}
            onClick={() => void groupsQuery.refetch()}
          >
            {t('common.refresh')}
          </Button>
        </Space>
      </Card>

      {groupsQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, groupsQuery.error)} />
      ) : null}

      <Card>
        <Table<RocketMqGroupLag>
          rowKey="group"
          size="small"
          columns={columns}
          dataSource={groupsQuery.data ?? []}
          loading={groupsQuery.isLoading}
          pagination={{ pageSize: 20, showSizeChanger: false }}
          scroll={{ x: 1080 }}
          locale={{ emptyText: t('rocketmq.groups.empty') }}
          onRow={(record) => ({
            onClick: () => setSelectedGroup(record.group),
            tabIndex: 0,
            role: 'button',
            style: { cursor: 'pointer' },
            onKeyDown: (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                setSelectedGroup(record.group);
              }
            },
          })}
        />
      </Card>

      <Drawer
        title={t('rocketmq.groups.detailTitle', { group: selectedGroup ?? '' })}
        open={selectedGroup !== null}
        size={760}
        loading={detailQuery.isLoading}
        onClose={() => setSelectedGroup(null)}
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
                { key: 'group', label: t('rocketmq.fields.group'), children: detail.summary.group },
                {
                  key: 'diff',
                  label: t('rocketmq.fields.diff'),
                  children: formatCount(detail.summary.diffTotal),
                },
                {
                  key: 'tps',
                  label: t('rocketmq.fields.consumeTps'),
                  children: formatTps(detail.summary.consumeTps),
                },
                {
                  key: 'last',
                  label: t('rocketmq.fields.lastConsume'),
                  children: formatTime(detail.summary.lastTimestamp),
                },
                {
                  key: 'broker',
                  label: t('rocketmq.fields.brokerAddr'),
                  children: detail.summary.brokerAddr,
                },
                {
                  key: 'topics',
                  label: t('rocketmq.groups.subscribedTopics'),
                  children:
                    detail.topics.length > 0 ? (
                      <Space size={[0, 4]} wrap>
                        {detail.topics.map((topic) => (
                          <Tag key={topic}>{topic}</Tag>
                        ))}
                      </Space>
                    ) : (
                      '-'
                    ),
                },
              ]}
            />

            <Card size="small" title={t('rocketmq.groups.config')}>
              {detail.config ? (
                <Descriptions
                  bordered
                  size="small"
                  column={2}
                  items={[
                    {
                      key: 'enable',
                      label: t('rocketmq.groups.consumeEnable'),
                      children: detail.config.consumeEnable ? t('common.yes') : t('common.no'),
                    },
                    {
                      key: 'fromMin',
                      label: t('rocketmq.groups.consumeFromMin'),
                      children: detail.config.consumeFromMinEnable ? t('common.yes') : t('common.no'),
                    },
                    {
                      key: 'broadcast',
                      label: t('rocketmq.groups.broadcast'),
                      children: detail.config.consumeBroadcastEnable ? t('common.yes') : t('common.no'),
                    },
                    {
                      key: 'orderly',
                      label: t('rocketmq.groups.orderly'),
                      children: detail.config.consumeMessageOrderly ? t('common.yes') : t('common.no'),
                    },
                    {
                      key: 'retryQueue',
                      label: t('rocketmq.groups.retryQueueNums'),
                      children: detail.config.retryQueueNums,
                    },
                    {
                      key: 'retryMax',
                      label: t('rocketmq.groups.retryMaxTimes'),
                      children: detail.config.retryMaxTimes,
                    },
                    {
                      key: 'timeout',
                      label: t('rocketmq.groups.consumeTimeout'),
                      children: t('rocketmq.groups.minutes', {
                        value: detail.config.consumeTimeoutMinute,
                      }),
                    },
                    {
                      key: 'brokerId',
                      label: t('rocketmq.fields.brokerId'),
                      children: detail.config.brokerId,
                    },
                  ]}
                />
              ) : (
                <Typography.Text type="secondary">-</Typography.Text>
              )}
            </Card>

            <Card size="small" title={t('rocketmq.groups.connections')}>
              <Table<RocketMqConnection>
                rowKey="clientId"
                size="small"
                columns={connectionColumns}
                dataSource={detail.connections}
                pagination={false}
                locale={{ emptyText: t('rocketmq.groups.noConnections') }}
                scroll={{ x: 720 }}
              />
            </Card>

            <Card size="small" title={t('rocketmq.groups.queues')}>
              <Table<RocketMqQueueOffset>
                rowKey={(record) => `${record.topic}-${record.brokerName}-${record.queueId}`}
                size="small"
                columns={queueColumns}
                dataSource={detail.queues}
                pagination={false}
                locale={{ emptyText: t('common.noData') }}
                scroll={{ x: 900 }}
              />
            </Card>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
