import {
  KeyOutlined,
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType, TableProps } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { request } from '../api/client';
import { errorMessage } from '../api/errors';
import type {
  CorpBatchStatusResult,
  CorpCreateRequest,
  CorpDetailView,
  CorpUpdateRequest,
  CorpView,
} from '../api/types';
import { formatNumber, formatTime } from '../utils/format';

type RotateForm = {
  secret: string;
};

type CorpForm = {
  corpId: string;
  corpName: string;
  secret?: string;
  prikey?: string;
  limits?: number | null;
  timeout?: number | null;
  enabled: boolean;
};

export function CorpsPage() {
  const { t } = useTranslation();
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const [rotateForm] = Form.useForm<RotateForm>();
  const [corpForm] = Form.useForm<CorpForm>();
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<number | undefined>(undefined);
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<CorpView | null>(null);
  const [detailTarget, setDetailTarget] = useState<CorpView | null>(null);
  const [revealTarget, setRevealTarget] = useState<CorpView | null>(null);
  const [revealPassword, setRevealPassword] = useState('');
  const [revealedSecret, setRevealedSecret] = useState<string | null>(null);
  const [rotateTarget, setRotateTarget] = useState<CorpView | null>(null);

  const corpsQuery = useQuery({
    queryKey: ['corps', keyword, statusFilter],
    queryFn: () => {
      const params = new URLSearchParams();
      if (keyword.trim()) {
        params.set('keyword', keyword.trim());
      }
      if (statusFilter !== undefined) {
        params.set('status', String(statusFilter));
      }
      const query = params.toString();
      return request<CorpView[]>(`/api/admin/corps${query ? `?${query}` : ''}`);
    },
  });

  const detailQuery = useQuery({
    queryKey: ['corpDetail', detailTarget?.id],
    queryFn: () => request<CorpDetailView>(`/api/admin/corps/${detailTarget?.id}`),
    enabled: detailTarget !== null,
  });

  const refreshList = () => {
    void queryClient.invalidateQueries({ queryKey: ['corps'] });
    void queryClient.invalidateQueries({ queryKey: ['overview'] });
    void queryClient.invalidateQueries({ queryKey: ['health'] });
  };

  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      request<CorpView>(`/api/admin/corps/${id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ enabled }),
      }),
    onSuccess: () => {
      message.success(t('corps.toggleSuccess'));
      refreshList();
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const batchMutation = useMutation({
    mutationFn: ({ ids, enabled }: { ids: number[]; enabled: boolean }) =>
      request<CorpBatchStatusResult>('/api/admin/corps/batch-status', {
        method: 'PATCH',
        body: JSON.stringify({ ids, enabled }),
      }),
    onSuccess: (result, variables) => {
      message.success(
        t(variables.enabled ? 'corps.batchEnableSuccess' : 'corps.batchDisableSuccess', {
          count: result.updated,
        }),
      );
      setSelectedRowKeys([]);
      refreshList();
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const createMutation = useMutation({
    mutationFn: (payload: CorpCreateRequest) =>
      request<CorpView>('/api/admin/corps', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    onSuccess: () => {
      message.success(t('corps.createSuccess'));
      setCreateOpen(false);
      corpForm.resetFields();
      refreshList();
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: CorpUpdateRequest }) =>
      request<CorpView>(`/api/admin/corps/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      }),
    onSuccess: () => {
      message.success(t('corps.updateSuccess'));
      setEditTarget(null);
      corpForm.resetFields();
      refreshList();
      void queryClient.invalidateQueries({ queryKey: ['corpDetail'] });
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const revealMutation = useMutation({
    mutationFn: ({ id, password }: { id: number; password: string }) =>
      request<{ secret: string }>(`/api/admin/corps/${id}/reveal`, {
        method: 'POST',
        body: JSON.stringify({ password }),
      }),
    onSuccess: (result) => setRevealedSecret(result.secret),
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const rotateMutation = useMutation({
    mutationFn: ({ id, secret }: { id: number; secret: string }) =>
      request<CorpView>(`/api/admin/corps/${id}/secret`, {
        method: 'PUT',
        body: JSON.stringify({ secret }),
      }),
    onSuccess: () => {
      message.success(t('corps.rotateSuccess'));
      setRotateTarget(null);
      rotateForm.resetFields();
      refreshList();
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const handleToggle = (record: CorpView, checked: boolean) => {
    if (checked) {
      toggleMutation.mutate({ id: record.id, enabled: true });
      return;
    }
    modal.confirm({
      title: t('corps.disableConfirmTitle'),
      content: t('corps.disableConfirmContent', { name: record.corpName || record.corpId }),
      onOk: () => toggleMutation.mutateAsync({ id: record.id, enabled: false }),
    });
  };

  const handleBatch = (enabled: boolean) => {
    if (selectedRowKeys.length === 0) {
      return;
    }
    modal.confirm({
      title: enabled ? t('corps.batchEnableTitle') : t('corps.batchDisableTitle'),
      content: t('corps.batchConfirmContent', { count: selectedRowKeys.length }),
      onOk: () => batchMutation.mutateAsync({ ids: selectedRowKeys, enabled }),
    });
  };

  const openCreate = () => {
    corpForm.resetFields();
    corpForm.setFieldsValue({ enabled: true });
    setCreateOpen(true);
  };

  const openEdit = (record: CorpView) => {
    corpForm.resetFields();
    corpForm.setFieldsValue({
      corpId: record.corpId,
      corpName: record.corpName,
      limits: record.limits,
      timeout: record.timeout,
      enabled: record.status > 0,
    });
    setEditTarget(record);
  };

  const submitCorpForm = (values: CorpForm) => {
    if (editTarget) {
      const payload: CorpUpdateRequest = {
        corpName: values.corpName || undefined,
        limits: values.limits ?? undefined,
        timeout: values.timeout ?? undefined,
        enabled: values.enabled,
      };
      if (values.secret) {
        payload.secret = values.secret;
      }
      if (values.prikey) {
        payload.prikey = values.prikey;
      }
      updateMutation.mutate({ id: editTarget.id, payload });
      return;
    }
    createMutation.mutate({
      corpId: values.corpId,
      corpName: values.corpName,
      secret: values.secret ?? '',
      prikey: values.prikey ?? '',
      limits: values.limits ?? undefined,
      timeout: values.timeout ?? undefined,
      enabled: values.enabled,
    });
  };

  const openReveal = (record: CorpView) => {
    setRevealTarget(record);
    setRevealPassword('');
    setRevealedSecret(null);
  };

  const closeReveal = () => {
    setRevealTarget(null);
    setRevealPassword('');
    setRevealedSecret(null);
  };

  const openRotate = (record: CorpView) => {
    setRotateTarget(record);
    rotateForm.resetFields();
  };

  const columns: TableColumnsType<CorpView> = [
    { title: t('corps.id'), dataIndex: 'id', width: 80 },
    { title: t('corps.corpName'), dataIndex: 'corpName', ellipsis: true },
    { title: t('corps.corpId'), dataIndex: 'corpId', ellipsis: true },
    {
      title: t('corps.lastSeq'),
      dataIndex: 'lastSeq',
      width: 140,
      render: (value: number | null) => (value == null ? '-' : formatNumber(value)),
    },
    { title: t('corps.limits'), dataIndex: 'limits', width: 100 },
    {
      title: t('corps.secret'),
      dataIndex: 'secretMasked',
      width: 140,
      render: (value: string) => value || '-',
    },
    {
      title: t('corps.prikey'),
      dataIndex: 'hasPrikey',
      width: 110,
      render: (value: boolean) =>
        value ? (
          <Tag color="blue">{t('corps.configured')}</Tag>
        ) : (
          <Tag>{t('corps.notConfigured')}</Tag>
        ),
    },
    {
      title: t('corps.updateAt'),
      dataIndex: 'updateAt',
      width: 180,
      render: (value: number) => formatTime(value),
    },
    {
      title: t('corps.status'),
      dataIndex: 'status',
      width: 100,
      render: (value: number) =>
        value > 0 ? <Tag color="green">{t('common.enabled')}</Tag> : <Tag>{t('common.disabled')}</Tag>,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 380,
      render: (_value, record) => (
        <Space size="small" wrap>
          <Switch
            checked={record.status > 0}
            loading={toggleMutation.isPending && toggleMutation.variables?.id === record.id}
            onChange={(checked) => handleToggle(record, checked)}
          />
          <Button size="small" onClick={() => setDetailTarget(record)}>
            {t('common.detail')}
          </Button>
          <Button size="small" onClick={() => openEdit(record)}>
            {t('common.edit')}
          </Button>
          <Button size="small" icon={<KeyOutlined />} onClick={() => openReveal(record)}>
            {t('corps.reveal')}
          </Button>
          <Button size="small" onClick={() => openRotate(record)}>
            {t('corps.rotate')}
          </Button>
        </Space>
      ),
    },
  ];

  const rowSelection: TableProps<CorpView>['rowSelection'] = {
    selectedRowKeys,
    onChange: (keys) => setSelectedRowKeys(keys as number[]),
  };

  const detail = detailQuery.data;

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('corps.title')}
        </Typography.Title>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('corps.create')}
          </Button>
          <Button
            icon={<ReloadOutlined />}
            loading={corpsQuery.isFetching}
            onClick={() => void corpsQuery.refetch()}
          >
            {t('common.refresh')}
          </Button>
        </Space>
      </Space>

      <Card>
        <Space wrap style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
          <Space wrap>
            <Input.Search
              allowClear
              placeholder={t('corps.keywordPlaceholder')}
              style={{ width: 280 }}
              onSearch={(value) => setKeyword(value)}
            />
            <Select<number>
              allowClear
              placeholder={t('corps.statusFilter')}
              style={{ width: 160 }}
              value={statusFilter}
              onChange={(value) => setStatusFilter(value)}
              options={[
                { value: 1, label: t('common.enabled') },
                { value: 0, label: t('common.disabled') },
              ]}
            />
            {selectedRowKeys.length > 0 ? (
              <Tag color="blue">{t('corps.selected', { count: selectedRowKeys.length })}</Tag>
            ) : null}
          </Space>
          <Space>
            <Button
              icon={<PlusOutlined />}
              disabled={selectedRowKeys.length === 0}
              loading={batchMutation.isPending && batchMutation.variables?.enabled === true}
              onClick={() => handleBatch(true)}
            >
              {t('corps.batchEnable')}
            </Button>
            <Button
              icon={<StopOutlined />}
              danger
              disabled={selectedRowKeys.length === 0}
              loading={batchMutation.isPending && batchMutation.variables?.enabled === false}
              onClick={() => handleBatch(false)}
            >
              {t('corps.batchDisable')}
            </Button>
          </Space>
        </Space>

        {corpsQuery.isError ? (
          <Alert type="error" showIcon title={errorMessage(t, corpsQuery.error)} />
        ) : null}

        <Table<CorpView>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={corpsQuery.data ?? []}
          loading={corpsQuery.isLoading}
          rowSelection={rowSelection}
          pagination={{ pageSize: 20, showSizeChanger: false }}
          scroll={{ x: 1400 }}
        />
      </Card>

      <Modal
        title={editTarget ? t('corps.editTitle') : t('corps.createTitle')}
        open={createOpen || editTarget !== null}
        okText={t('common.save')}
        okButtonProps={{ htmlType: 'submit' }}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        onCancel={() => {
          setCreateOpen(false);
          setEditTarget(null);
          corpForm.resetFields();
        }}
        onOk={() => corpForm.submit()}
        destroyOnHidden
        width={640}
      >
        <Form<CorpForm> form={corpForm} layout="vertical" onFinish={submitCorpForm}>
          <Form.Item
            name="corpId"
            label={t('corps.corpId')}
            rules={[
              { required: true, message: t('corps.corpIdRequired') },
              {
                pattern: /^[A-Za-z0-9_-]{3,64}$/,
                message: t('corps.corpIdInvalid'),
              },
            ]}
          >
            <Input disabled={editTarget !== null} placeholder="ww1234567890abcdef" />
          </Form.Item>
          <Form.Item
            name="corpName"
            label={t('corps.corpName')}
            rules={[{ required: true, message: t('corps.corpNameRequired') }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item
            name="secret"
            label={editTarget ? t('corps.secretOptional') : t('corps.secretLabel')}
            rules={
              editTarget
                ? [{ min: 8, max: 512, message: t('corps.secretLength') }]
                : [
                    { required: true, message: t('corps.secretRequiredLabel') },
                    { min: 8, max: 512, message: t('corps.secretLength') },
                  ]
            }
          >
            <Input.Password autoComplete="new-password" placeholder={t('corps.secretPlaceholder')} />
          </Form.Item>
          <Form.Item
            name="prikey"
            label={editTarget ? t('corps.prikeyOptional') : t('corps.prikeyLabel')}
            rules={editTarget ? [] : [{ required: true, message: t('corps.prikeyRequired') }]}
          >
            <Input.TextArea
              rows={4}
              placeholder={t('corps.prikeyPlaceholder')}
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
          <Space size="large" wrap>
            <Form.Item name="limits" label={t('corps.limits')}>
              <InputNumber min={0} precision={0} style={{ width: 180 }} placeholder="0" />
            </Form.Item>
            <Form.Item name="timeout" label={t('corps.timeout')}>
              <InputNumber min={0} precision={0} style={{ width: 180 }} placeholder="0" />
            </Form.Item>
            <Form.Item name="enabled" label={t('corps.status')} valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Drawer
        title={t('corps.detailTitle', {
          name: detailTarget?.corpName || detailTarget?.corpId || '',
        })}
        open={detailTarget !== null}
        size={520}
        onClose={() => setDetailTarget(null)}
      >
        {detailQuery.isError ? (
          <Alert type="error" showIcon title={errorMessage(t, detailQuery.error)} />
        ) : null}
        <Descriptions
          column={1}
          size="small"
          bordered
          items={
            detail
              ? [
                  { key: 'id', label: t('corps.id'), children: detail.id },
                  { key: 'corpId', label: t('corps.corpId'), children: detail.corpId },
                  { key: 'corpName', label: t('corps.corpName'), children: detail.corpName || '-' },
                  {
                    key: 'status',
                    label: t('corps.status'),
                    children:
                      detail.status > 0 ? (
                        <Tag color="green">{t('common.enabled')}</Tag>
                      ) : (
                        <Tag>{t('common.disabled')}</Tag>
                      ),
                  },
                  { key: 'limits', label: t('corps.limits'), children: detail.limits },
                  { key: 'timeout', label: t('corps.timeout'), children: detail.timeout },
                  {
                    key: 'lastSeq',
                    label: t('corps.lastSeq'),
                    children: detail.lastSeq == null ? '-' : formatNumber(detail.lastSeq),
                  },
                  { key: 'secret', label: t('corps.secret'), children: detail.secretMasked || '-' },
                  {
                    key: 'prikey',
                    label: t('corps.prikey'),
                    children: detail.hasPrikey ? (
                      <Tag color="blue">{t('corps.configured')}</Tag>
                    ) : (
                      <Tag>{t('corps.notConfigured')}</Tag>
                    ),
                  },
                  {
                    key: 'messageTotal',
                    label: t('corps.messageTotal'),
                    children: formatNumber(detail.messageTotal),
                  },
                  {
                    key: 'messageToday',
                    label: t('corps.messageToday'),
                    children: formatNumber(detail.messageToday),
                  },
                  {
                    key: 'messagePending',
                    label: t('corps.messagePending'),
                    children: formatNumber(detail.messagePending),
                  },
                  {
                    key: 'messageFailed',
                    label: t('corps.messageFailed'),
                    children: formatNumber(detail.messageFailed),
                  },
                  {
                    key: 'pullLastAt',
                    label: t('corps.pullLastAt'),
                    children: detail.redisReachable
                      ? detail.pullLastAt
                        ? formatTime(detail.pullLastAt)
                        : '-'
                      : t('corps.redisUnavailable'),
                  },
                  {
                    key: 'pullTotalFetched',
                    label: t('corps.pullTotalFetched'),
                    children:
                      detail.pullTotalFetched == null
                        ? '-'
                        : formatNumber(detail.pullTotalFetched),
                  },
                  {
                    key: 'pullLastError',
                    label: t('corps.pullLastError'),
                    children: detail.pullLastError || '-',
                  },
                  {
                    key: 'updateAt',
                    label: t('corps.updateAt'),
                    children: formatTime(detail.updateAt),
                  },
                ]
              : []
          }
        />
      </Drawer>

      <Modal
        title={t('corps.revealTitle', { name: revealTarget?.corpName ?? revealTarget?.corpId ?? '' })}
        open={revealTarget !== null}
        onCancel={closeReveal}
        footer={
          revealedSecret === null ? (
            <Space>
              <Button onClick={closeReveal}>{t('common.cancel')}</Button>
              <Button
                type="primary"
                disabled={!revealPassword || revealMutation.isPending}
                loading={revealMutation.isPending}
                onClick={() => {
                  if (revealTarget) {
                    revealMutation.mutate({ id: revealTarget.id, password: revealPassword });
                  }
                }}
              >
                {t('corps.revealSubmit')}
              </Button>
            </Space>
          ) : (
            <Button type="primary" onClick={closeReveal}>
              {t('common.close')}
            </Button>
          )
        }
      >
        {revealedSecret === null ? (
          <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
            <Alert type="warning" showIcon title={t('corps.revealHint')} />
            <Input.Password
              value={revealPassword}
              placeholder={t('corps.adminPassword')}
              onChange={(event) => setRevealPassword(event.target.value)}
              onPressEnter={() => {
                if (revealTarget && revealPassword && !revealMutation.isPending) {
                  revealMutation.mutate({ id: revealTarget.id, password: revealPassword });
                }
              }}
            />
          </Space>
        ) : (
          <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
            <Typography.Text type="secondary">{t('corps.revealResultHint')}</Typography.Text>
            <Typography.Paragraph copyable style={{ marginBottom: 0 }} code>
              {revealedSecret}
            </Typography.Paragraph>
          </Space>
        )}
      </Modal>

      <Modal
        title={t('corps.rotateTitle', { name: rotateTarget?.corpName ?? rotateTarget?.corpId ?? '' })}
        open={rotateTarget !== null}
        okText={t('corps.rotateSubmit')}
        okButtonProps={{ htmlType: 'submit' }}
        confirmLoading={rotateMutation.isPending}
        onCancel={() => {
          setRotateTarget(null);
          rotateForm.resetFields();
        }}
        onOk={() => rotateForm.submit()}
        destroyOnHidden
      >
        <Form<RotateForm>
          form={rotateForm}
          layout="vertical"
          onFinish={(values) => {
            if (rotateTarget) {
              rotateMutation.mutate({ id: rotateTarget.id, secret: values.secret });
            }
          }}
        >
          <Alert type="warning" showIcon title={t('corps.rotateHint')} style={{ marginBottom: 16 }} />
          <Form.Item
            name="secret"
            label={t('corps.newSecret')}
            rules={[
              { required: true, message: t('corps.secretRequired') },
              { min: 8, max: 512, message: t('corps.secretLength') },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
