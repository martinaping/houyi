import { DeleteOutlined, RedoOutlined, ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { rocketMqApi } from '../../api/rocketmq';
import type { RocketMqRetryDetail, RocketMqRetryMessage } from '../../api/rocketmq';
import { errorMessage } from '../../api/errors';
import { formatCount, MessageBody, statusColor, statusLabel } from './shared';
import { formatTime } from '../../utils/format';

const PAGE_SIZE = 50;

export function RetryTab() {
  const { t } = useTranslation();
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<number | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [offset, setOffset] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [discardId, setDiscardId] = useState<number | null>(null);
  const [discardReason, setDiscardReason] = useState('');

  const retryQuery = useQuery({
    queryKey: ['rocketmq', 'retry', status, search, offset],
    queryFn: () => rocketMqApi.retry({ status, keyword: search, offset, limit: PAGE_SIZE }),
    refetchInterval: 30_000,
  });

  const detailQuery = useQuery({
    queryKey: ['rocketmq', 'retry', 'detail', selectedId],
    queryFn: () => rocketMqApi.retryDetail(selectedId as number),
    enabled: selectedId !== null,
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['rocketmq', 'retry'] });
    void queryClient.invalidateQueries({ queryKey: ['rocketmq', 'overview'] });
  };

  const requeueMutation = useMutation({
    mutationFn: (id: number) => rocketMqApi.requeueRetry(id),
    onSuccess: () => {
      message.success(t('rocketmq.retry.requeueSuccess'));
      refresh();
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const discardMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      rocketMqApi.discardRetry(id, reason),
    onSuccess: () => {
      message.success(t('rocketmq.retry.discardSuccess'));
      setDiscardId(null);
      setDiscardReason('');
      refresh();
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const confirmRequeue = (record: RocketMqRetryMessage) => {
    modal.confirm({
      title: t('rocketmq.retry.requeueTitle'),
      content: t('rocketmq.retry.requeueHint', { key: record.messageKey || record.id }),
      onOk: () => requeueMutation.mutateAsync(record.id),
    });
  };

  const columns: TableColumnsType<RocketMqRetryMessage> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: t('rocketmq.fields.key'), dataIndex: 'messageKey', ellipsis: true },
    { title: t('rocketmq.fields.topic'), dataIndex: 'topic', ellipsis: true },
    {
      title: t('rocketmq.fields.status'),
      dataIndex: 'status',
      width: 110,
      render: (value: number) => <Tag color={statusColor(value)}>{statusLabel(t, value)}</Tag>,
    },
    {
      title: t('rocketmq.fields.retryCount'),
      dataIndex: 'retryCount',
      width: 100,
    },
    {
      title: t('rocketmq.fields.deliverTime'),
      dataIndex: 'deliverTime',
      width: 180,
      render: (value: number) => formatTime(value),
    },
    {
      title: t('rocketmq.fields.error'),
      dataIndex: 'errorMsg',
      ellipsis: true,
      render: (value: string | null) => value || '-',
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 200,
      render: (_value, record) => (
        <Space size="small" onClick={(event) => event.stopPropagation()}>
          <Button
            size="small"
            icon={<RedoOutlined />}
            loading={requeueMutation.isPending && requeueMutation.variables === record.id}
            onClick={() => confirmRequeue(record)}
          >
            {t('rocketmq.retry.requeue')}
          </Button>
          <Button
            size="small"
            danger
            icon={<DeleteOutlined />}
            disabled={record.status === 3}
            onClick={() => {
              setDiscardId(record.id);
              setDiscardReason('');
            }}
          >
            {t('rocketmq.retry.discard')}
          </Button>
        </Space>
      ),
    },
  ];

  const data = retryQuery.data;
  const detail: RocketMqRetryDetail | undefined = detailQuery.data;

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Alert type="info" showIcon title={t('rocketmq.retry.hint')} />

      <Card>
        <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
          <Space wrap>
            <Select<number>
              allowClear
              style={{ width: 150 }}
              placeholder={t('rocketmq.retry.statusPlaceholder')}
              value={status}
              onChange={(value) => {
                setStatus(value);
                setOffset(0);
              }}
              options={[0, 1, 2, 3].map((item) => ({
                value: item,
                label: statusLabel(t, item),
              }))}
            />
            <Input.Search
              allowClear
              style={{ width: 280 }}
              placeholder={t('rocketmq.retry.searchPlaceholder')}
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              onSearch={(value) => {
                setSearch(value.trim());
                setOffset(0);
              }}
            />
          </Space>
          <Button
            icon={<ReloadOutlined />}
            loading={retryQuery.isFetching}
            onClick={() => void retryQuery.refetch()}
          >
            {t('common.refresh')}
          </Button>
        </Space>
      </Card>

      {data && !data.available ? (
        <Alert type="warning" showIcon title={data.error} />
      ) : null}
      {retryQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, retryQuery.error)} />
      ) : null}

      <Card>
        <Table<RocketMqRetryMessage>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={data?.items ?? []}
          loading={retryQuery.isLoading}
          scroll={{ x: 1200 }}
          locale={{ emptyText: t('rocketmq.retry.empty') }}
          pagination={{
            current: Math.floor(offset / PAGE_SIZE) + 1,
            pageSize: PAGE_SIZE,
            total: data?.total ?? 0,
            showSizeChanger: false,
            onChange: (page) => setOffset((page - 1) * PAGE_SIZE),
          }}
          onRow={(record) => ({
            onClick: () => setSelectedId(record.id),
            tabIndex: 0,
            role: 'button',
            style: { cursor: 'pointer' },
            onKeyDown: (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                setSelectedId(record.id);
              }
            },
          })}
        />
      </Card>

      <Drawer
        title={t('rocketmq.retry.detailTitle', { id: selectedId ?? '' })}
        open={selectedId !== null}
        size={720}
        loading={detailQuery.isLoading}
        onClose={() => setSelectedId(null)}
        extra={
          detail ? (
            <Space>
              <Button
                icon={<RedoOutlined />}
                loading={requeueMutation.isPending}
                onClick={() => {
                  modal.confirm({
                    title: t('rocketmq.retry.requeueTitle'),
                    content: t('rocketmq.retry.requeueHint', {
                      key: detail.messageKey || detail.id,
                    }),
                    onOk: () => requeueMutation.mutateAsync(detail.id),
                  });
                }}
              >
                {t('rocketmq.retry.requeue')}
              </Button>
              <Button
                danger
                icon={<DeleteOutlined />}
                disabled={detail.status === 3}
                onClick={() => {
                  setDiscardId(detail.id);
                  setDiscardReason('');
                }}
              >
                {t('rocketmq.retry.discard')}
              </Button>
            </Space>
          ) : null
        }
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
                { key: 'id', label: 'ID', children: detail.id },
                { key: 'key', label: t('rocketmq.fields.key'), children: detail.messageKey || '-' },
                { key: 'topic', label: t('rocketmq.fields.topic'), children: detail.topic },
                {
                  key: 'status',
                  label: t('rocketmq.fields.status'),
                  children: <Tag color={statusColor(detail.status)}>{statusLabel(t, detail.status)}</Tag>,
                },
                { key: 'retry', label: t('rocketmq.fields.retryCount'), children: detail.retryCount },
                {
                  key: 'deliverTime',
                  label: t('rocketmq.fields.deliverTime'),
                  children: formatTime(detail.deliverTime),
                },
                {
                  key: 'deliverAt',
                  label: t('rocketmq.fields.deliveredAt'),
                  children: formatTime(detail.deliverAt),
                },
                {
                  key: 'createAt',
                  label: t('rocketmq.fields.createAt'),
                  children: formatTime(detail.createAt),
                },
                { key: 'tag', label: t('rocketmq.fields.tag'), children: detail.tag || '-' },
                { key: 'sharding', label: t('rocketmq.fields.shardingKey'), children: detail.shardingKey || '-' },
                { key: 'error', label: t('rocketmq.fields.error'), children: detail.errorMsg || '-' },
                {
                  key: 'size',
                  label: t('rocketmq.fields.bodyLength'),
                  children: formatCount(detail.body.length),
                },
              ]}
            />
            <Card size="small" title={t('rocketmq.fields.body')}>
              <MessageBody text={detail.body} maxHeight={420} />
            </Card>
          </Space>
        ) : null}
      </Drawer>

      <Modal
        title={t('rocketmq.retry.discardTitle')}
        open={discardId !== null}
        okText={t('rocketmq.retry.discard')}
        okButtonProps={{ danger: true }}
        confirmLoading={discardMutation.isPending}
        onCancel={() => setDiscardId(null)}
        onOk={() => {
          if (discardId !== null) {
            discardMutation.mutate({ id: discardId, reason: discardReason });
          }
        }}
      >
        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          <Alert type="warning" showIcon title={t('rocketmq.retry.discardHint')} />
          <Input
            value={discardReason}
            placeholder={t('rocketmq.retry.discardReason')}
            onChange={(event) => setDiscardReason(event.target.value)}
          />
          <Typography.Text type="secondary">
            {t('rocketmq.retry.discardNote')}
          </Typography.Text>
        </Space>
      </Modal>
    </Space>
  );
}
