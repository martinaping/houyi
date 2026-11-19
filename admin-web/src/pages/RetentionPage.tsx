import { DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Input,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { request } from '../api/client';
import { errorMessage } from '../api/errors';
import type { CleanupResult, RetentionCategoryPreview, RetentionPreview } from '../api/types';
import { formatTime } from '../utils/format';

export function RetentionPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [target, setTarget] = useState<string | null>(null);
  const [confirmText, setConfirmText] = useState('');

  const previewQuery = useQuery({
    queryKey: ['retentionPreview'],
    queryFn: () => request<RetentionPreview>('/api/admin/retention/preview'),
  });

  const executeMutation = useMutation({
    mutationFn: ({ category, confirm }: { category: string; confirm: string }) =>
      request<CleanupResult>('/api/admin/retention/execute', {
        method: 'POST',
        body: JSON.stringify({ category, confirm }),
      }),
    onSuccess: (result) => {
      message.success(
        t('retention.executeSuccess', { category: result.category, deleted: result.deleted }),
      );
      setTarget(null);
      setConfirmText('');
      void queryClient.invalidateQueries({ queryKey: ['retentionPreview'] });
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const closeModal = () => {
    setTarget(null);
    setConfirmText('');
  };

  const preview = previewQuery.data;

  const columns: TableColumnsType<RetentionCategoryPreview> = [
    {
      title: t('retention.category'),
      dataIndex: 'category',
      render: (value: string) => <Tag color="blue">{value}</Tag>,
    },
    {
      title: t('retention.candidates'),
      dataIndex: 'candidates',
      width: 160,
      render: (value: number) => value.toLocaleString(),
    },
    {
      title: t('retention.earliest'),
      dataIndex: 'earliest',
      width: 180,
      render: (value: number | null) => formatTime(value),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 140,
      render: (_value, record) => (
        <Button
          danger
          size="small"
          icon={<DeleteOutlined />}
          disabled={!preview?.cleanupEnabled || record.candidates === 0}
          onClick={() => setTarget(record.category)}
        >
          {t('retention.execute')}
        </Button>
      ),
    },
  ];

  const items = preview
    ? [
        {
          key: 'enabled',
          label: t('retention.cleanupEnabled'),
          children: preview.cleanupEnabled ? (
            <Tag color="green">{t('common.enabled')}</Tag>
          ) : (
            <Tag color="red">{t('common.disabled')}</Tag>
          ),
        },
        {
          key: 'batch',
          label: t('retention.batchSize'),
          children: preview.batchSize,
        },
        {
          key: 'original',
          label: t('retention.originalMsgDays'),
          children: t('retention.days', { days: preview.originalMsgDays }),
        },
        {
          key: 'md5',
          label: t('retention.md5IndexDays'),
          children: t('retention.days', { days: preview.md5IndexDays }),
        },
        {
          key: 'audit',
          label: t('retention.auditLogDays'),
          children: t('retention.days', { days: preview.auditLogDays }),
        },
      ]
    : [];

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('retention.title')}
        </Typography.Title>
        <Button
          icon={<ReloadOutlined />}
          loading={previewQuery.isFetching}
          onClick={() => void previewQuery.refetch()}
        >
          {t('common.refresh')}
        </Button>
      </Space>

      <Alert type="warning" showIcon title={t('retention.warning')} />

      {preview && !preview.cleanupEnabled ? (
        <Alert type="info" showIcon title={t('retention.disabledHint')} />
      ) : null}

      {previewQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, previewQuery.error)} />
      ) : null}

      <Card title={t('retention.policy')}>
        <Descriptions bordered size="small" column={2} items={items} />
      </Card>

      <Card title={t('retention.preview')}>
        <Table<RetentionCategoryPreview>
          rowKey="category"
          size="small"
          columns={columns}
          dataSource={preview?.categories ?? []}
          loading={previewQuery.isLoading}
          pagination={false}
          locale={{ emptyText: t('common.noData') }}
        />
      </Card>

      <Modal
        title={t('retention.executeTitle', { category: target ?? '' })}
        open={target !== null}
        okText={t('retention.execute')}
        okButtonProps={{
          danger: true,
          disabled: confirmText.trim().toUpperCase() !== 'DELETE',
        }}
        confirmLoading={executeMutation.isPending}
        onOk={() => {
          if (target !== null) {
            executeMutation.mutate({ category: target, confirm: confirmText });
          }
        }}
        onCancel={closeModal}
      >
        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Paragraph style={{ marginBottom: 0 }}>
            {t('retention.executeHint')}
          </Typography.Paragraph>
          <Input
            value={confirmText}
            placeholder="DELETE"
            onChange={(event) => setConfirmText(event.target.value)}
          />
        </Space>
      </Modal>
    </Space>
  );
}
