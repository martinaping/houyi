import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
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
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { rocketMqApi } from '../../api/rocketmq';
import type { RocketMqMessage, RocketMqMessageTrace } from '../../api/rocketmq';
import { errorMessage } from '../../api/errors';
import { formatCount, MessageBody } from './shared';
import { formatTime } from '../../utils/format';

type QueryMode = 'recent' | 'exact';

type RecentForm = {
  topic?: string;
  range?: [Dayjs, Dayjs];
  keyword?: string;
  limit?: number;
};

type ExactForm = {
  topic: string;
  queryType: 'msgId' | 'key';
  query: string;
  range?: [Dayjs, Dayjs];
  max?: number;
};

type RecentFilters = {
  topic: string;
  beginTimestamp?: number;
  endTimestamp?: number;
  keyword: string;
  limit: number;
};

const RECENT_ALL = '__all__';

export function MessageQueryTab() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [mode, setMode] = useState<QueryMode>('recent');
  const [recentForm] = Form.useForm<RecentForm>();
  const [exactForm] = Form.useForm<ExactForm>();
  const [recentFilters, setRecentFilters] = useState<RecentFilters>({
    topic: '',
    keyword: '',
    limit: 100,
  });
  const [exactResults, setExactResults] = useState<RocketMqMessage[] | null>(null);
  const [selected, setSelected] = useState<RocketMqMessage | null>(null);
  const [tracks, setTracks] = useState<RocketMqMessageTrace[] | null>(null);

  const topicsQuery = useQuery({
    queryKey: ['rocketmq', 'topics', 'message-query-select'],
    queryFn: () => rocketMqApi.topics({ includeSystem: false, limit: 500 }),
  });
  const businessTopics = useMemo(
    () => (topicsQuery.data ?? []).filter((topic) => !topic.system),
    [topicsQuery.data],
  );

  const recentQuery = useQuery({
    queryKey: ['rocketmq', 'messages', 'recent', recentFilters],
    queryFn: () => rocketMqApi.recentMessages(recentFilters),
    enabled: mode === 'recent',
  });

  const exactMutation = useMutation({
    mutationFn: (values: ExactForm) => {
      const [start, end] = values.range ?? [];
      const payload: {
        topic: string;
        msgId?: string;
        key?: string;
        beginTimestamp?: number;
        endTimestamp?: number;
        max?: number;
      } = { topic: values.topic, max: values.max };
      if (values.queryType === 'msgId') {
        payload.msgId = values.query.trim();
      } else {
        payload.key = values.query.trim();
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
      setExactResults(data);
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

  const applyRecent = (values: RecentForm) => {
    const [start, end] = values.range ?? [];
    setRecentFilters({
      topic: values.topic && values.topic !== RECENT_ALL ? values.topic : '',
      beginTimestamp: start ? start.valueOf() : undefined,
      endTimestamp: end ? end.valueOf() : undefined,
      keyword: values.keyword?.trim() ?? '',
      limit: values.limit ?? 100,
    });
  };

  const resetRecent = () => {
    recentForm.resetFields();
    recentForm.setFieldsValue({ topic: RECENT_ALL, limit: 100 });
    setRecentFilters({ topic: '', keyword: '', limit: 100 });
  };

  const columns: TableColumnsType<RocketMqMessage> = [
    { title: 'MsgID', dataIndex: 'msgId', ellipsis: true },
    { title: t('rocketmq.fields.topic'), dataIndex: 'topic', width: 180, ellipsis: true },
    { title: t('rocketmq.fields.tag'), dataIndex: 'tags', width: 120, ellipsis: true },
    { title: t('rocketmq.fields.key'), dataIndex: 'keys', width: 180, ellipsis: true },
    { title: t('rocketmq.fields.queueId'), dataIndex: 'queueId', width: 85 },
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

  const recentMeta = recentQuery.data;
  const rows = mode === 'recent' ? recentMeta?.messages ?? [] : exactResults ?? [];
  const loading = mode === 'recent' ? recentQuery.isLoading : exactMutation.isPending;

  const topicOptions = businessTopics.map((topic) => ({ value: topic.topic, label: topic.topic }));

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          <Segmented<QueryMode>
            value={mode}
            onChange={(value) => {
              setMode(value);
              setTracks(null);
            }}
            options={[
              { value: 'recent', label: t('rocketmq.messages.mode.recent') },
              { value: 'exact', label: t('rocketmq.messages.mode.exact') },
            ]}
          />

          {mode === 'recent' ? (
            <>
              <Form<RecentForm>
                form={recentForm}
                layout="inline"
                initialValues={{ topic: RECENT_ALL, limit: 100 }}
                style={{ rowGap: 12 }}
                onFinish={applyRecent}
              >
                <Form.Item name="topic" label={t('rocketmq.fields.topic')}>
                  <Select
                    showSearch
                    style={{ width: 280 }}
                    options={[{ value: RECENT_ALL, label: t('rocketmq.messages.allTopics') }, ...topicOptions]}
                  />
                </Form.Item>
                <Form.Item name="range" label={t('rocketmq.messages.timeRange')}>
                  <DatePicker.RangePicker showTime />
                </Form.Item>
                <Form.Item name="keyword" label={t('rocketmq.messages.keyword')}>
                  <Input allowClear style={{ width: 260 }} placeholder={t('rocketmq.messages.keywordPlaceholder')} />
                </Form.Item>
                <Form.Item name="limit" label={t('rocketmq.messages.max')}>
                  <InputNumber min={10} max={200} step={10} style={{ width: 100 }} />
                </Form.Item>
                <Form.Item>
                  <Space>
                    <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                      {t('common.search')}
                    </Button>
                    <Button onClick={resetRecent}>{t('common.reset')}</Button>
                    <Button
                      icon={<ReloadOutlined />}
                      loading={recentQuery.isFetching}
                      onClick={() => void recentQuery.refetch()}
                    >
                      {t('common.refresh')}
                    </Button>
                  </Space>
                </Form.Item>
              </Form>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {t('rocketmq.messages.recentHint')}
              </Typography.Text>
            </>
          ) : (
            <>
              <Form<ExactForm>
                form={exactForm}
                layout="inline"
                initialValues={{ queryType: 'key', max: 20 }}
                style={{ rowGap: 12 }}
                onFinish={(values) => exactMutation.mutate(values)}
              >
                <Form.Item name="topic" label={t('rocketmq.fields.topic')} rules={[{ required: true }]}>
                  <Select
                    showSearch
                    style={{ width: 260 }}
                    placeholder={t('rocketmq.messages.topicPlaceholder')}
                    loading={topicsQuery.isLoading}
                    options={topicOptions}
                  />
                </Form.Item>
                <Form.Item name="queryType" label={t('rocketmq.messages.queryType')}>
                  <Segmented<'msgId' | 'key'>
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
                  <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={exactMutation.isPending}>
                    {t('common.search')}
                  </Button>
                </Form.Item>
              </Form>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {t('rocketmq.messages.hint')}
              </Typography.Text>
            </>
          )}
        </Space>
      </Card>

      {mode === 'recent' && recentQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, recentQuery.error)} />
      ) : null}
      {mode === 'exact' && exactMutation.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, exactMutation.error)} />
      ) : null}

      {mode === 'recent' && recentMeta ? (
        <Alert
          type={recentMeta.notes.length > 0 || recentMeta.truncated ? 'warning' : 'info'}
          showIcon
          title={t('rocketmq.messages.scanSummary', {
            topics: recentMeta.scannedTopics,
            count: recentMeta.messages.length,
            ms: recentMeta.elapsedMs,
          })}
          description={
            recentMeta.notes.length > 0 ? (
              <Space orientation="vertical" size={2}>
                {recentMeta.notes.map((note) => (
                  <span key={note}>{note}</span>
                ))}
              </Space>
            ) : undefined
          }
        />
      ) : null}

      <Card title={mode === 'recent' ? t('rocketmq.messages.recentTitle') : t('rocketmq.messages.exactTitle')}>
        <Table<RocketMqMessage>
          rowKey="msgId"
          size="small"
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={mode === 'recent' ? { pageSize: 20, showSizeChanger: false } : false}
          scroll={{ x: 1050 }}
          locale={{ emptyText: mode === 'recent' ? t('rocketmq.messages.recentEmpty') : t('rocketmq.messages.empty') }}
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
