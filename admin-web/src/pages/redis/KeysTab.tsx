import { DeleteOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Input,
  InputNumber,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import type { TFunction } from 'i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { redisApi } from '../../api/redis';
import type { RedisKey } from '../../api/redis';
import { errorMessage } from '../../api/errors';
import { formatCount } from '../rocketmq/shared';
import { Can } from '../../components/Can';

function ttlText(t: TFunction, ttl: number): string {
  if (ttl === -1) {
    return t('redis.keys.noExpiry');
  }
  if (ttl === -2) {
    return t('redis.keys.missing');
  }
  return `${formatCount(ttl)} s`;
}

export function RedisKeysTab() {
  const { t } = useTranslation();
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const [patternInput, setPatternInput] = useState('*');
  const [pattern, setPattern] = useState('*');
  const [limit, setLimit] = useState(100);
  const [selected, setSelected] = useState<RedisKey | null>(null);

  const keysQuery = useQuery({
    queryKey: ['redis', 'keys', pattern, limit],
    queryFn: () => redisApi.keys(pattern, limit),
  });

  const deleteMutation = useMutation({
    mutationFn: (key: string) => redisApi.deleteKey(key),
    onSuccess: (result) => {
      message.success(t('redis.keys.deleteSuccess', { key: result.key }));
      setSelected(null);
      void queryClient.invalidateQueries({ queryKey: ['redis', 'keys'] });
      void queryClient.invalidateQueries({ queryKey: ['redis', 'overview'] });
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const confirmDelete = (key: string) => {
    modal.confirm({
      title: t('redis.keys.deleteTitle'),
      content: t('redis.keys.deleteHint', { key }),
      okText: t('redis.keys.delete'),
      okButtonProps: { danger: true },
      onOk: () => deleteMutation.mutateAsync(key),
    });
  };

  const columns: TableColumnsType<RedisKey> = [
    { title: t('redis.fields.key'), dataIndex: 'key', ellipsis: true },
    { title: t('redis.fields.type'), dataIndex: 'type', width: 100 },
    {
      title: t('redis.fields.ttl'),
      dataIndex: 'ttl',
      width: 120,
      render: (value: number) => ttlText(t, value),
    },
    {
      title: t('redis.fields.size'),
      dataIndex: 'size',
      width: 100,
      render: (value: number | null) => (value == null ? '-' : formatCount(value)),
    },
    {
      title: t('redis.fields.preview'),
      dataIndex: 'preview',
      ellipsis: true,
      render: (value: string) => value || '-',
    },
    {
      title: t('redis.fields.deletable'),
      dataIndex: 'deletable',
      width: 110,
      render: (value: boolean) =>
        value ? <Tag color="blue">{t('redis.keys.allowed')}</Tag> : <Tag>{t('redis.keys.protected')}</Tag>,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 150,
      render: (_value, record) => (
        <Space size="small" onClick={(event) => event.stopPropagation()}>
          <Button size="small" onClick={() => setSelected(record)}>
            {t('common.detail')}
          </Button>
          <Tooltip title={record.deletable ? undefined : t('redis.keys.protectedHint')}>
            <Button
              size="small"
              danger
              icon={<DeleteOutlined />}
              disabled={!record.deletable}
              loading={deleteMutation.isPending && deleteMutation.variables === record.key}
              onClick={() => confirmDelete(record.key)}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Alert type="warning" showIcon title={t('redis.keys.hint')} />

      <Card>
        <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
          <Space wrap>
            <Input
              value={patternInput}
              onChange={(event) => setPatternInput(event.target.value)}
              onPressEnter={() => setPattern(patternInput.trim() || '*')}
              placeholder={t('redis.keys.patternPlaceholder')}
              style={{ width: 320 }}
              allowClear
            />
            <InputNumber
              min={10}
              max={200}
              value={limit}
              onChange={(value) => setLimit(value ?? 100)}
              style={{ width: 100 }}
            />
            <Button
              type="primary"
              icon={<SearchOutlined />}
              onClick={() => setPattern(patternInput.trim() || '*')}
            >
              {t('common.search')}
            </Button>
          </Space>
          <Button
            icon={<ReloadOutlined />}
            loading={keysQuery.isFetching}
            onClick={() => void keysQuery.refetch()}
          >
            {t('common.refresh')}
          </Button>
        </Space>
      </Card>

      {keysQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, keysQuery.error)} />
      ) : null}

      <Card title={t('redis.keys.result', { scanned: keysQuery.data?.scanned ?? 0 })}>
        {keysQuery.data?.truncated ? (
          <Alert type="info" showIcon title={t('redis.keys.truncated')} style={{ marginBottom: 12 }} />
        ) : null}
        <Table<RedisKey>
          rowKey="key"
          size="small"
          columns={columns}
          dataSource={keysQuery.data?.keys ?? []}
          loading={keysQuery.isLoading}
          pagination={{ pageSize: 20, showSizeChanger: false }}
          scroll={{ x: 1000 }}
          locale={{ emptyText: t('redis.keys.empty') }}
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
        title={selected?.key ?? t('redis.keys.detailTitle')}
        open={selected !== null}
        size={720}
        onClose={() => setSelected(null)}
        extra={
          selected?.deletable ? (
            <Can permission="redis.write">
              <Button
                danger
                icon={<DeleteOutlined />}
                onClick={() => confirmDelete(selected.key)}
              >
                {t('redis.keys.delete')}
              </Button>
            </Can>
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
                { key: 'key', label: t('redis.fields.key'), children: selected.key },
                { key: 'type', label: t('redis.fields.type'), children: selected.type },
                {
                  key: 'ttl',
                  label: t('redis.fields.ttl'),
                  children: ttlText(t, selected.ttl),
                },
                {
                  key: 'size',
                  label: t('redis.fields.size'),
                  children: selected.size == null ? '-' : formatCount(selected.size),
                },
                {
                  key: 'deletable',
                  label: t('redis.fields.deletable'),
                  children: selected.deletable ? t('redis.keys.allowed') : t('redis.keys.protected'),
                },
              ]}
            />
            <Card size="small" title={t('redis.fields.preview')}>
              {selected.previewTruncated ? (
                <Typography.Text type="warning" style={{ display: 'block', marginBottom: 8 }}>
                  {t('redis.keys.previewTruncated')}
                </Typography.Text>
              ) : null}
              <pre
                style={{
                  margin: 0,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all',
                  maxHeight: 420,
                  overflow: 'auto',
                  fontSize: 12,
                }}
              >
                {selected.preview || '-'}
              </pre>
            </Card>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
