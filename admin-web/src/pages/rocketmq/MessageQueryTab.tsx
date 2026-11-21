import { SearchOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useMutation, useQuery } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { rocketMqApi } from '../../api/rocketmq';
import type { RocketMqMessage, RocketMqMessageTrace } from '../../api/rocketmq';
import { errorMessage } from '../../api/errors';
import { formatCount, MessageBody } from './shared';
import { formatTime } from '../../utils/format';

type QueryType = 'msgId' | 'key';

type QueryForm = {
  topic: string;
  queryType: QueryType;
  query?: string;
  range?: [Dayjs, Dayjs];
  max?: number;
};

export function MessageQueryTab() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [form] = Form.useForm<QueryForm>();
  const [results, setResults] = useState<RocketMqMessage[] | null>(null);
  const [selected, setSelected] = useState<RocketMqMessage | null>(null);
  const [tracks, setTracks] = useState<RocketMqMessageTrace[] | null>(null);

  const topicsQuery = useQuery({
    queryKey: ['rocketmq', 'topics', 'query-select'],
    queryFn: () => rocketMqApi.topics({ includeSystem: false, limit: 500 }),
  });

  const queryMutation = useMutation({
    mutationFn: (values: QueryForm) => {
      const [start, end] = values.range ?? [];
      const payload: {
        topic: string;
        msgId?: string;
        key?: string;
        beginTimestamp?: number;
        endTimestamp?: number;
        max?: number;
      } = {
        topic: values.topic,
        max: values.max,
      };
      if (values.queryType === 'msgId') {
        payload.msgId = values.query?.trim();
      } else {
        payload.key = values.query?.trim();
      }
      if (start) {
        payload.beginTimestamp = start.valueOf();
      }
      if (end) {
        payload.endTimestamp = end.valueOf();
      }
      return rocketMqApi.queryMessages(payload);
    },
    onSuccess: (data) => {
      setResults(data);
      setTracks(null);
      if (data.length === 0) {
        message.info(t('rocketmq.messages.empty'));
      }
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const traceMutation = useMutation({
    mutationFn: (item: RocketMqMessage) =>
      rocketMqApi.trace({ topic: item.topic, msgId: item.msgId }),
    onSuccess: (data) => setTracks(data.tracks),
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const columns: TableColumnsType<RocketMqMessage> = [
    { title: 'MsgID', dataIndex: 'msgId', ellipsis: true },
    { title: t('rocketmq.fields.tag'), dataIndex: 'tags', width: 130, ellipsis: true },
    { title: t('rocketmq.fields.key'), dataIndex: 'keys', width: 180, ellipsis: true },
    {
      title: t('rocketmq.fields.queueId'),
      dataIndex: 'queueId',
      width: 90,
    },
    {
      title: t('rocketmq.fields.bornTime'),
      dataIndex: 'bornTimestamp',
      width: 180,
      render: (value: number) => formatTime(value),
    },
    {
      title: t('rocketmq.fields.reconsumeTimes'),
      dataIndex: 'reconsumeTimes',
      width: 110,
    },
  ];

  const traceColumns: TableColumnsType<RocketMqMessageTrace> = [
    { title: t('rocketmq.fields.consumerGroup'), dataIndex: 'consumerGroup', width: 240 },
    {
      title: t('rocketmq.fields.trackType'),
      dataIndex: 'trackType',
      width: 160,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    {
      title: t('rocketmq.fields.exception'),
      dataIndex: 'exceptionDesc',
      ellipsis: true,
      render: (value: string | null) => value || '-',
    },
  ];

  const openDetail = (item: RocketMqMessage) => {
    setSelected(item);
    setTracks(null);
  };

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Form<QueryForm>
          form={form}
          layout="inline"
          initialValues={{ queryType: 'key', max: 20 }}
          style={{ rowGap: 12 }}
          onFinish={(values) => queryMutation.mutate(values)}
        >
          <Form.Item name="topic" label={t('rocketmq.fields.topic')} rules={[{ required: true }]}>
            <Select
              showSearch
              style={{ width: 260 }}
              placeholder={t('rocketmq.messages.topicPlaceholder')}
              loading={topicsQuery.isLoading}
              options={(topicsQuery.data ?? [])
                .filter((topic) => !topic.system)
                .map((topic) => ({ value: topic.topic, label: topic.topic }))}
            />
          </Form.Item>
          <Form.Item name="queryType" label={t('rocketmq.messages.queryType')}>
            <Segmented<QueryType>
              options={[
                { value: 'key', label: t('rocketmq.messages.byKey') },
                { value: 'msgId', label: t('rocketmq.messages.byMsgId') },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="query"
            label={t('rocketmq.messages.queryValue')}
            rules={[{ required: true, message: t('rocketmq.messages.queryRequired') }]}
          >
            <Input allowClear style={{ width: 280 }} placeholder={t('rocketmq.messages.queryPlaceholder')} />
          </Form.Item>
          <Form.Item name="range" label={t('rocketmq.messages.timeRange')}>
            <DatePicker.RangePicker showTime />
          </Form.Item>
          <Form.Item name="max" label={t('rocketmq.messages.max')}>
            <InputNumber min={1} max={50} style={{ width: 90 }} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={queryMutation.isPending}>
              {t('common.search')}
            </Button>
          </Form.Item>
        </Form>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {t('rocketmq.messages.hint')}
        </Typography.Text>
      </Card>

      {queryMutation.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, queryMutation.error)} />
      ) : null}

      {results !== null ? (
        <Card title={t('rocketmq.messages.results', { count: results.length })}>
          <Table<RocketMqMessage>
            rowKey="msgId"
            size="small"
            columns={columns}
            dataSource={results}
            pagination={false}
            scroll={{ x: 900 }}
            locale={{ emptyText: t('rocketmq.messages.empty') }}
            onRow={(record) => ({
              onClick: () => openDetail(record),
              tabIndex: 0,
              role: 'button',
              style: { cursor: 'pointer' },
              onKeyDown: (event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  openDetail(record);
                }
              },
            })}
          />
        </Card>
      ) : null}

      <Drawer
        title={t('rocketmq.messages.detailTitle')}
        open={selected !== null}
        size={760}
        onClose={() => setSelected(null)}
        extra={
          selected ? (
            <Button
              loading={traceMutation.isPending}
              onClick={() => traceMutation.mutate(selected)}
            >
              {t('rocketmq.messages.trace')}
            </Button>
          ) : null
        }
      >
        {selected ? (
          <Space orientation="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions
              bordered
              size="small"
              column={1}
              items={[
                { key: 'msgId', label: 'MsgID', children: selected.msgId },
                { key: 'topic', label: t('rocketmq.fields.topic'), children: selected.topic },
                { key: 'tags', label: t('rocketmq.fields.tag'), children: selected.tags || '-' },
                { key: 'keys', label: t('rocketmq.fields.key'), children: selected.keys || '-' },
                {
                  key: 'queue',
                  label: t('rocketmq.fields.queue'),
                  children: `${selected.queueId} / ${formatCount(selected.queueOffset)}`,
                },
                {
                  key: 'born',
                  label: t('rocketmq.fields.bornTime'),
                  children: formatTime(selected.bornTimestamp),
                },
                {
                  key: 'store',
                  label: t('rocketmq.fields.storeTime'),
                  children: formatTime(selected.storeTimestamp),
                },
                {
                  key: 'reconsume',
                  label: t('rocketmq.fields.reconsumeTimes'),
                  children: selected.reconsumeTimes,
                },
              ]}
            />
            <Card size="small" title={t('rocketmq.fields.body')}>
              <MessageBody text={selected.body} truncated={selected.bodyTruncated} />
            </Card>
            {Object.keys(selected.properties).length > 0 ? (
              <Card size="small" title={t('rocketmq.fields.properties')}>
                <Descriptions
                  bordered
                  size="small"
                  column={1}
                  items={Object.entries(selected.properties).map(([key, value]) => ({
                    key,
                    label: key,
                    children: <Typography.Text code>{value}</Typography.Text>,
                  }))}
                />
              </Card>
            ) : null}
            {tracks !== null ? (
              <Card size="small" title={t('rocketmq.messages.tracks')}>
                <Table<RocketMqMessageTrace>
                  rowKey={(record) => `${record.consumerGroup}-${record.trackType}`}
                  size="small"
                  columns={traceColumns}
                  dataSource={tracks}
                  pagination={false}
                  locale={{ emptyText: t('rocketmq.messages.noTracks') }}
                />
              </Card>
            ) : null}
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
