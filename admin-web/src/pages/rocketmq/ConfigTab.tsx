import { SendOutlined, SettingOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Select,
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
import { rocketMqApi } from '../../api/rocketmq';
import type { RocketMqConfigItem, RocketMqTestSendResult } from '../../api/rocketmq';
import { errorMessage } from '../../api/errors';
import { Can } from '../../components/Can';
import { ApplyModeTag, SourceTag } from './shared';

type TestSendForm = {
  topic: string;
  tag?: string;
  key?: string;
  body: string;
};

export function ConfigTab() {
  const { t } = useTranslation();
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm<TestSendForm>();
  const [result, setResult] = useState<RocketMqTestSendResult | null>(null);

  const configQuery = useQuery({
    queryKey: ['rocketmq', 'config'],
    queryFn: () => rocketMqApi.config(),
  });
  const topicsQuery = useQuery({
    queryKey: ['rocketmq', 'topics', 'test-select'],
    queryFn: () => rocketMqApi.topics({ includeSystem: false, limit: 500 }),
  });

  const testMutation = useMutation({
    mutationFn: (values: TestSendForm) => rocketMqApi.testSend(values),
    onSuccess: (data) => {
      setResult(data);
      message.success(t('rocketmq.config.testSuccess', { msgId: data.msgId }));
      void configQuery.refetch();
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const submitTest = (values: TestSendForm) => {
    modal.confirm({
      title: t('rocketmq.config.testConfirmTitle'),
      content: t('rocketmq.config.testConfirmContent', {
        topic: values.topic,
        bytes: new Blob([values.body]).size,
      }),
      okText: t('rocketmq.config.testSend'),
      okButtonProps: { danger: true },
      onOk: () => testMutation.mutateAsync(values),
    });
  };

  const columns: TableColumnsType<RocketMqConfigItem> = [
    { title: t('rocketmq.fields.key'), dataIndex: 'key', width: 230 },
    {
      title: t('rocketmq.fields.value'),
      dataIndex: 'value',
      ellipsis: true,
      render: (value: string) => <Typography.Text code>{value || '-'}</Typography.Text>,
    },
    {
      title: t('rocketmq.config.sourceLabel'),
      dataIndex: 'source',
      width: 110,
      render: (value: string) => <SourceTag source={value} />,
    },
    {
      title: t('rocketmq.config.applyMode'),
      dataIndex: 'applyMode',
      width: 120,
      render: (value: string) => <ApplyModeTag mode={value} />,
    },
    { title: t('rocketmq.fields.unit'), dataIndex: 'unit', width: 90 },
  ];

  const config = configQuery.data;

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      {configQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, configQuery.error)} />
      ) : null}
      {config && !config.available ? (
        <Alert type="warning" showIcon title={t('rocketmq.config.unavailable')} description={config.error} />
      ) : null}

      <Card
        title={t('rocketmq.config.title')}
        extra={
          <Button icon={<SettingOutlined />} onClick={() => navigate('/settings')}>
            {t('rocketmq.config.openSettings')}
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
                key: 'namesrv',
                label: 'NameServer',
                children: <Typography.Text code>{config?.namesrvAddr || '-'}</Typography.Text>,
              },
              {
                key: 'cluster',
                label: t('rocketmq.fields.cluster'),
                children: config?.clusterName || '-',
              },
              {
                key: 'timeout',
                label: t('rocketmq.fields.timeout'),
                children: `${config?.timeoutMs ?? '-'} ms`,
              },
              {
                key: 'available',
                label: t('rocketmq.fields.state'),
                children: (
                  <Tag color={config?.available ? 'green' : 'red'}>
                    {config?.available ? t('rocketmq.connected') : t('rocketmq.unavailable')}
                  </Tag>
                ),
              },
            ]}
          />
          <Table<RocketMqConfigItem>
            rowKey="key"
            size="small"
            columns={columns}
            dataSource={config?.items ?? []}
            loading={configQuery.isLoading}
            pagination={false}
            locale={{ emptyText: t('common.noData') }}
          />
          <Alert type="info" showIcon title={config?.note || t('rocketmq.config.note')} />
        </Space>
      </Card>

      <Card title={t('rocketmq.config.testTitle')}>
        <Alert type="warning" showIcon title={t('rocketmq.config.testHint')} style={{ marginBottom: 16 }} />
        <Form<TestSendForm>
          form={form}
          layout="vertical"
          initialValues={{ tag: 'ADMIN_TEST' }}
          onFinish={submitTest}
        >
          <Form.Item name="topic" label={t('rocketmq.fields.topic')} rules={[{ required: true }]}>
            <Select
              showSearch
              placeholder={t('rocketmq.config.testTopicPlaceholder')}
              loading={topicsQuery.isLoading}
              options={(topicsQuery.data ?? [])
                .filter((topic) => !topic.system)
                .map((topic) => ({ value: topic.topic, label: topic.topic }))}
            />
          </Form.Item>
          <Space size="large" wrap>
            <Form.Item name="tag" label={t('rocketmq.fields.tag')}>
              <Input style={{ width: 220 }} />
            </Form.Item>
            <Form.Item name="key" label={t('rocketmq.fields.key')}>
              <Input style={{ width: 280 }} />
            </Form.Item>
          </Space>
          <Form.Item
            name="body"
            label={t('rocketmq.config.testBody')}
            rules={[{ required: true, message: t('rocketmq.config.testBodyRequired') }]}
          >
            <Input.TextArea rows={5} style={{ fontFamily: 'monospace' }} placeholder='{"test":true}' />
          </Form.Item>
          <Can permission="rocketmq.write">
            <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={testMutation.isPending}>
              {t('rocketmq.config.testSend')}
            </Button>
          </Can>
        </Form>
        {result ? (
          <Alert
            style={{ marginTop: 16 }}
            type="success"
            showIcon
            title={t('rocketmq.config.testResult')}
            description={`MsgID: ${result.msgId}`}
          />
        ) : null}
      </Card>
    </Space>
  );
}
