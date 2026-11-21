import { ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  InputNumber,
  Space,
  Table,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { mnsApi } from '../../api/mns';
import type { MnsMessage } from '../../api/mns';
import { errorMessage } from '../../api/errors';
import { formatCount, MessageBody } from '../rocketmq/shared';
import { formatTime } from '../../utils/format';

export function MnsMessagesTab({ queue }: { queue: string }) {
  const { t } = useTranslation();
  const [limit, setLimit] = useState(10);
  const [selected, setSelected] = useState<MnsMessage | null>(null);

  const query = useQuery({
    queryKey: ['mns', 'messages', queue, limit],
    queryFn: () => mnsApi.messages(queue || undefined, limit),
  });

  const columns: TableColumnsType<MnsMessage> = [
    { title: t('mns.fields.messageId'), dataIndex: 'messageId', ellipsis: true },
    {
      title: t('mns.fields.dequeueCount'),
      dataIndex: 'dequeueCount',
      width: 120,
    },
    {
      title: t('mns.fields.priority'),
      dataIndex: 'priority',
      width: 100,
    },
    {
      title: t('mns.fields.enqueueTime'),
      dataIndex: 'enqueueTime',
      width: 180,
      render: (value: number | null) => formatTime(value),
    },
    {
      title: t('mns.fields.nextVisibleTime'),
      dataIndex: 'nextVisibleTime',
      width: 180,
      render: (value: number | null) => formatTime(value),
    },
    {
      title: t('mns.fields.body'),
      dataIndex: 'body',
      ellipsis: true,
      render: (value: string | null) => value || '-',
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Alert type="info" showIcon title={t('mns.messages.hint')} />

      <Card>
        <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
          <Space>
            <Typography.Text type="secondary">{t('mns.fields.queue')}:</Typography.Text>
            <Typography.Text code>{queue || '-'}</Typography.Text>
            <Typography.Text type="secondary">{t('mns.messages.count')}:</Typography.Text>
            <InputNumber min={1} max={16} value={limit} onChange={(value) => setLimit(value ?? 10)} />
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

      <Card title={t('mns.messages.title')}>
        <Table<MnsMessage>
          rowKey={(record) => record.messageId ?? `${record.enqueueTime}-${record.dequeueCount}`}
          size="small"
          columns={columns}
          dataSource={query.data ?? []}
          loading={query.isLoading}
          pagination={false}
          scroll={{ x: 900 }}
          locale={{ emptyText: t('mns.messages.empty') }}
          onRow={(record) => ({
            onClick: () => setSelected(record),
            tabIndex: 0,
            role: 'button',
            style: { cursor: 'pointer' },
            onKeyDown: (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                setSelected(record);
              }
            },
          })}
        />
      </Card>

      <Drawer
        title={t('mns.messages.detailTitle')}
        open={selected !== null}
        size={720}
        onClose={() => setSelected(null)}
      >
        {selected ? (
          <Space orientation="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions
              bordered
              size="small"
              column={1}
              items={[
                { key: 'id', label: t('mns.fields.messageId'), children: selected.messageId || '-' },
                {
                  key: 'dequeue',
                  label: t('mns.fields.dequeueCount'),
                  children: formatCount(selected.dequeueCount),
                },
                { key: 'priority', label: t('mns.fields.priority'), children: selected.priority ?? '-' },
                {
                  key: 'enqueue',
                  label: t('mns.fields.enqueueTime'),
                  children: formatTime(selected.enqueueTime),
                },
                {
                  key: 'next',
                  label: t('mns.fields.nextVisibleTime'),
                  children: formatTime(selected.nextVisibleTime),
                },
                {
                  key: 'first',
                  label: t('mns.fields.firstDequeueTime'),
                  children: formatTime(selected.firstDequeueTime),
                },
              ]}
            />
            <Card size="small" title={t('mns.fields.body')}>
              <MessageBody text={selected.body} truncated={selected.bodyTruncated} />
            </Card>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
