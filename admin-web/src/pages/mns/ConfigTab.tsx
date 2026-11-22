import { SendOutlined, SettingOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { mnsApi } from '../../api/mns';
import type { MnsConfigItem, MnsSendResult } from '../../api/mns';
import { errorMessage } from '../../api/errors';
import { Can } from '../../components/Can';

type TestForm = {
  queue?: string;
  body: string;
  delaySeconds?: number;
};

export function MnsConfigTab({ queue }: { queue: string }) {
  const { t } = useTranslation();
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm<TestForm>();
  const [result, setResult] = useState<MnsSendResult | null>(null);

  const query = useQuery({
    queryKey: ['mns', 'config'],
    queryFn: () => mnsApi.config(),
  });
  const config = query.data;

  const sendMutation = useMutation({
    mutationFn: (values: TestForm) =>
      mnsApi.testSend({
        queue: values.queue || queue || undefined,
        body: values.body,
        delaySeconds: values.delaySeconds,
      }),
    onSuccess: (data) => {
      setResult(data);
      message.success(t('mns.config.testSuccess', { messageId: data.messageId ?? '-' }));
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const submit = (values: TestForm) => {
    modal.confirm({
      title: t('mns.config.testConfirmTitle'),
      content: t('mns.config.testConfirmContent', {
        queue: values.queue || queue || config?.queueName || '-',
        bytes: new Blob([values.body]).size,
      }),
      okText: t('mns.config.testSend'),
      okButtonProps: { danger: true },
      onOk: () => sendMutation.mutateAsync(values),
    });
  };

  const columns: TableColumnsType<MnsConfigItem> = [
    { title: t('mns.fields.key'), dataIndex: 'key', width: 220 },
    {
      title: t('mns.fields.value'),
      dataIndex: 'value',
      ellipsis: true,
      render: (value: string) => <Typography.Text code>{value || '-'}</Typography.Text>,
    },
    {
      title: t('mns.fields.source'),
      dataIndex: 'source',
      width: 110,
      render: (value: string) => (
        <Tag color={value === 'ENV' ? 'blue' : value === 'DB' ? 'green' : 'default'}>
          {t(`mns.config.source.${value}`, { defaultValue: value })}
        </Tag>
      ),
    },
    {
      title: t('mns.fields.applyMode'),
      dataIndex: 'applyMode',
      width: 120,
      render: (value: string) => (
        <Tag color={value === 'RESTART' ? 'orange' : 'green'}>
          {t(`settings.applyMode.${value}`, { defaultValue: value })}
        </Tag>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      {query.isError ? <Alert type="error" showIcon title={t('mns.config.loadFailed')} /> : null}
      {config && !config.available ? (
        <Alert type="warning" showIcon title={t('mns.config.unavailable')} description={config.error} />
      ) : null}

      <Card
        title={t('mns.config.title')}
        extra={
          <Button icon={<SettingOutlined />} onClick={() => navigate('/settings')}>
            {t('mns.config.openSettings')}
          </Button>
        }
      >
        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          <Descriptions
            bordered
            size="small"
            column={{ xs: 1, sm: 2 }}
            items={[
              {
                key: 'enabled',
                label: t('mns.fields.enabled'),
                children: config?.enabled ? t('common.enabled') : t('common.disabled'),
              },
              {
                key: 'configured',
                label: t('mns.fields.configured'),
                children: config?.configured ? t('common.yes') : t('common.no'),
              },
              {
                key: 'available',
                label: t('mns.fields.available'),
                children: (
                  <Tag color={config?.available ? 'green' : 'red'}>
                    {config?.available ? t('common.yes') : t('common.no')}
                  </Tag>
                ),
              },
              {
                key: 'endpoint',
                label: t('mns.fields.endpoint'),
                children: <Typography.Text code>{config?.endpoint || '-'}</Typography.Text>,
              },
              { key: 'queue', label: t('mns.fields.queue'), children: config?.queueName || '-' },
            ]}
          />
          <Table<MnsConfigItem>
            rowKey="key"
            size="small"
            columns={columns}
            dataSource={config?.items ?? []}
            loading={query.isLoading}
            pagination={false}
            locale={{ emptyText: t('common.noData') }}
          />
          <Alert type="info" showIcon title={config?.note || t('mns.config.note')} />
        </Space>
      </Card>

      <Card title={t('mns.config.testTitle')}>
        <Alert type="warning" showIcon title={t('mns.config.testHint')} style={{ marginBottom: 16 }} />
        <Form<TestForm>
          form={form}
          layout="vertical"
          initialValues={{ queue: queue || undefined, delaySeconds: 0 }}
          onFinish={submit}
        >
          <Form.Item name="queue" label={t('mns.fields.queue')}>
            <Input placeholder={config?.queueName || 'houyi-msgs'} />
          </Form.Item>
          <Space size="large" wrap>
            <Form.Item name="delaySeconds" label={t('mns.fields.delaySeconds')}>
              <InputNumber min={0} max={604800} style={{ width: 180 }} />
            </Form.Item>
          </Space>
          <Form.Item
            name="body"
            label={t('mns.fields.body')}
            rules={[{ required: true, message: t('mns.config.bodyRequired') }]}
          >
            <Input.TextArea rows={5} style={{ fontFamily: 'monospace' }} placeholder='{"test":true}' />
          </Form.Item>
          <Can permission="mns.write">
            <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={sendMutation.isPending}>
              {t('mns.config.testSend')}
            </Button>
          </Can>
        </Form>
        {result ? (
          <Alert
            style={{ marginTop: 16 }}
            type="success"
            showIcon
            title={t('mns.config.testResult')}
            description={`MessageID: ${result.messageId ?? '-'}, queue: ${result.queueName}`}
          />
        ) : null}
      </Card>
    </Space>
  );
}
