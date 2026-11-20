import { DownloadOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
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
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { DescriptionsProps, TableColumnsType } from 'antd';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { download, request } from '../api/client';
import { errorMessage } from '../api/errors';
import type {
  MessageDetail,
  MessagePage,
  MessageStatus,
  MessageSummary,
  RetryResult,
} from '../api/types';
import { formatTime } from '../utils/format';

const PAGE_SIZE = 20;

type Filters = {
  corpId: string;
  msgId: string;
  msgType: string;
  status: MessageStatus | '';
  startAt: number | null;
  endAt: number | null;
};

type FilterForm = {
  corpId?: string;
  msgId?: string;
  msgType?: string;
  status?: MessageStatus;
  range?: [Dayjs, Dayjs];
};

const EMPTY_FILTERS: Filters = {
  corpId: '',
  msgId: '',
  msgType: '',
  status: '',
  startAt: null,
  endAt: null,
};

function filterParams(filters: Filters, cursor: number | null = null): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.corpId) {
    params.set('corpId', filters.corpId);
  }
  if (filters.msgId) {
    params.set('msgId', filters.msgId);
  }
  if (filters.msgType) {
    params.set('msgType', filters.msgType);
  }
  if (filters.status) {
    params.set('status', filters.status);
  }
  if (filters.startAt !== null) {
    params.set('startAt', String(filters.startAt));
  }
  if (filters.endAt !== null) {
    params.set('endAt', String(filters.endAt));
  }
  if (cursor !== null) {
    params.set('cursor', String(cursor));
  }
  return params;
}

async function fetchMessages(filters: Filters, cursor: number | null): Promise<MessagePage> {
  const params = filterParams(filters, cursor);
  params.set('limit', String(PAGE_SIZE));
  return request<MessagePage>(`/api/admin/messages?${params.toString()}`);
}

export function MessagesPage() {
  const { t } = useTranslation();
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<FilterForm>();
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const messagesQuery = useInfiniteQuery({
    queryKey: ['messages', filters],
    queryFn: ({ pageParam }) => fetchMessages(filters, pageParam),
    initialPageParam: null as number | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  });

  const detailQuery = useQuery({
    queryKey: ['message', selectedId],
    queryFn: () => {
      if (selectedId === null) {
        throw new Error('message id is required');
      }
      return request<MessageDetail>(`/api/admin/messages/${selectedId}`);
    },
    enabled: selectedId !== null,
  });

  const retryMutation = useMutation({
    mutationFn: (id: number) =>
      request<RetryResult>(`/api/admin/messages/${id}/retry`, { method: 'POST' }),
    onSuccess: (result) => {
      message.success(t('messages.retrySuccess', { mode: result.mode }));
      void queryClient.invalidateQueries({ queryKey: ['messages'] });
      void queryClient.invalidateQueries({ queryKey: ['message', selectedId] });
      void queryClient.invalidateQueries({ queryKey: ['overview'] });
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const exportMutation = useMutation({
    mutationFn: () =>
      download(`/api/admin/messages/export?${filterParams(filters).toString()}`, {}, 'messages.csv'),
    onSuccess: () => message.success(t('common.exportSuccess')),
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const onSubmit = (values: FilterForm) => {
    const [start, end] = values.range ?? [];
    setFilters({
      corpId: values.corpId?.trim() ?? '',
      msgId: values.msgId?.trim() ?? '',
      msgType: values.msgType?.trim() ?? '',
      status: values.status ?? '',
      startAt: start ? start.valueOf() : null,
      endAt: end ? end.valueOf() : null,
    });
  };

  const onReset = () => {
    form.resetFields();
    setFilters(EMPTY_FILTERS);
  };

  const handleRetry = (detail: MessageDetail) => {
    modal.confirm({
      title: t('messages.retryConfirmTitle'),
      content: t('messages.retryConfirmContent', { msgId: detail.msgId }),
      onOk: () => retryMutation.mutateAsync(detail.id),
    });
  };

  const items = messagesQuery.data?.pages.flatMap((page) => page.items) ?? [];

  const columns: TableColumnsType<MessageSummary> = [
    { title: t('messages.id'), dataIndex: 'id', width: 90 },
    { title: t('messages.msgId'), dataIndex: 'msgId', ellipsis: true },
    { title: t('messages.corpId'), dataIndex: 'corpId', ellipsis: true },
    { title: t('messages.msgType'), dataIndex: 'msgType', width: 110 },
    { title: t('messages.seq'), dataIndex: 'seq', width: 100 },
    {
      title: t('messages.createAt'),
      dataIndex: 'createAt',
      width: 180,
      render: (value: number) => formatTime(value),
    },
    {
      title: t('messages.pushStatus'),
      dataIndex: 'pushAt',
      width: 110,
      render: (value: number | null) =>
        value != null && value > 0 ? (
          <Tag color="green">{t('messages.pushed')}</Tag>
        ) : (
          <Tag color="orange">{t('messages.pending')}</Tag>
        ),
    },
    {
      title: t('messages.downloadStatus'),
      key: 'downloadStatus',
      width: 120,
      render: (_value, record) => {
        if (record.downFailAt > 0) {
          return <Tag color="red">{t('messages.downloadFailed')}</Tag>;
        }
        if (record.downFinishAt > 0) {
          return <Tag color="green">{t('messages.downloaded')}</Tag>;
        }
        return <Tag>{t('messages.notDownloaded')}</Tag>;
      },
    },
    {
      title: t('messages.file'),
      dataIndex: 'hasFile',
      width: 110,
      render: (value: boolean) =>
        value ? <Tag color="blue">{t('messages.hasFile')}</Tag> : t('messages.noFile'),
    },
  ];

  const detailItems = (detail: MessageDetail): DescriptionsProps['items'] => [
    { key: 'id', label: 'ID', children: detail.id },
    { key: 'msgId', label: t('messages.msgId'), children: detail.msgId },
    { key: 'corpId', label: t('messages.corpId'), children: detail.corpId },
    { key: 'msgType', label: t('messages.msgType'), children: detail.msgType },
    { key: 'seq', label: t('messages.seq'), children: detail.seq },
    { key: 'dateNo', label: t('messages.dateNo'), children: detail.dateNo },
    { key: 'createAt', label: t('messages.createAt'), children: formatTime(detail.createAt) },
    { key: 'pushAt', label: t('messages.pushAt'), children: formatTime(detail.pushAt) },
    { key: 'downFinishAt', label: t('messages.downFinishAt'), children: formatTime(detail.downFinishAt) },
    { key: 'downFailAt', label: t('messages.downFailAt'), children: formatTime(detail.downFailAt) },
    { key: 'retryTimeout', label: t('messages.retryTimeout'), children: detail.retryTimeout },
    { key: 'sdkFileId', label: t('messages.sdkFileId'), children: detail.sdkFileId ?? '-' },
    { key: 'filePath', label: t('messages.filePath'), children: detail.filePath ?? '-' },
    { key: 'ossPath', label: t('messages.ossPath'), children: detail.ossPath ?? '-' },
    { key: 'md5Sum', label: t('messages.md5Sum'), children: detail.md5Sum ?? '-' },
  ];

  const detail = detailQuery.data;

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('messages.title')}
        </Typography.Title>
        <Space>
          <Button
            icon={<DownloadOutlined />}
            loading={exportMutation.isPending}
            onClick={() => exportMutation.mutate()}
          >
            {t('common.export')}
          </Button>
          <Button
            icon={<ReloadOutlined />}
            loading={messagesQuery.isFetching}
            onClick={() => void messagesQuery.refetch()}
          >
            {t('common.refresh')}
          </Button>
        </Space>
      </Space>

      <Card>
        <Form<FilterForm> form={form} layout="inline" onFinish={onSubmit} style={{ rowGap: 12 }}>
          <Form.Item name="corpId" label={t('messages.corpId')}>
            <Input allowClear placeholder={t('messages.corpIdPlaceholder')} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="msgId" label={t('messages.msgId')}>
            <Input allowClear placeholder={t('messages.msgIdPlaceholder')} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="msgType" label={t('messages.msgType')}>
            <Input allowClear placeholder={t('messages.msgTypePlaceholder')} style={{ width: 130 }} />
          </Form.Item>
          <Form.Item name="status" label={t('messages.statusPlaceholder')}>
            <Select<MessageStatus>
              allowClear
              style={{ width: 140 }}
              options={[
                { value: 'PUSHED', label: t('messages.pushed') },
                { value: 'PENDING', label: t('messages.pending') },
                { value: 'DOWNLOAD_FAILED', label: t('messages.downloadFailed') },
                { value: 'DOWNLOADED', label: t('messages.downloaded') },
              ]}
            />
          </Form.Item>
          <Form.Item name="range" label={t('messages.timeRange')}>
            <DatePicker.RangePicker showTime />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                {t('common.search')}
              </Button>
              <Button onClick={onReset}>{t('common.reset')}</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card>
        {messagesQuery.isError ? (
          <Alert type="error" showIcon title={errorMessage(t, messagesQuery.error)} />
        ) : null}

        <Table<MessageSummary>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={items}
          loading={messagesQuery.isLoading}
          pagination={false}
          scroll={{ x: 960 }}
          locale={{ emptyText: t('messages.empty') }}
          onRow={(record) => ({
            onClick: () => setSelectedId(record.id),
            style: { cursor: 'pointer' },
          })}
        />

        {messagesQuery.hasNextPage ? (
          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <Button
              loading={messagesQuery.isFetchingNextPage}
              onClick={() => void messagesQuery.fetchNextPage()}
            >
              {t('common.loadMore')}
            </Button>
          </div>
        ) : null}
      </Card>

      <Drawer
        title={t('messages.detailTitle')}
        width={720}
        open={selectedId !== null}
        loading={detailQuery.isLoading}
        onClose={() => setSelectedId(null)}
        extra={
          detail && detail.hasFile && !(detail.pushAt != null && detail.pushAt > 0) ? (
            <Button
              type="primary"
              loading={retryMutation.isPending}
              onClick={() => handleRetry(detail)}
            >
              {t('common.retry')}
            </Button>
          ) : null
        }
      >
        {detailQuery.isError ? (
          <Alert type="error" showIcon title={errorMessage(t, detailQuery.error)} />
        ) : null}
        {detail ? (
          <Space orientation="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions bordered size="small" column={1} items={detailItems(detail)} />
            <Card size="small" title={t('messages.content')}>
              <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all', maxHeight: 320, overflow: 'auto' }}>
                {detail.content ?? '-'}
              </pre>
            </Card>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
