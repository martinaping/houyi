import { ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Space,
  Table,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { rocketMqApi } from '../../api/rocketmq';
import type { RocketMqDlqTopic, RocketMqMessage, RocketMqResendResult } from '../../api/rocketmq';
import { errorMessage } from '../../api/errors';
import { formatCount, MessageBody } from './shared';
import { formatTime } from '../../utils/format';

type ResendForm = {
  targetTopic: string;
  tag?: string;
  key?: string;
};

export function DlqTab() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [selectedTopic, setSelectedTopic] = useState<string | null>(null);
  const [selectedMessage, setSelectedMessage] = useState<RocketMqMessage | null>(null);
  const [resendOpen, setResendOpen] = useState(false);
  const [resendForm] = Form.useForm<ResendForm>();

  const topicsQuery = useQuery({
    queryKey: ['rocketmq', 'dlq', 'topics'],
    queryFn: () => rocketMqApi.dlqTopics(),
    refetchInterval: 30_000,
  });

  const messagesQuery = useQuery({
    queryKey: ['rocketmq', 'dlq', 'messages', selectedTopic],
    queryFn: () => rocketMqApi.dlqMessages(selectedTopic as string, 100),
    enabled: selectedTopic !== null,
  });

  const resendMutation = useMutation({
    mutationFn: (values: ResendForm) => {
      if (!selectedMessage) {
        throw new Error('message not selected');
      }
      return rocketMqApi.resendDlq({
        topic: selectedMessage.topic,
        msgId: selectedMessage.msgId,
        targetTopic: values.targetTopic,
        tag: values.tag,
        key: values.key,
      });
    },
    onSuccess: (result: RocketMqResendResult) => {
      message.success(t('rocketmq.dlq.resendSuccess', { msgId: result.msgId }));
      setResendOpen(false);
      void queryClient.invalidateQueries({ queryKey: ['rocketmq', 'dlq'] });
      void queryClient.invalidateQueries({ queryKey: ['rocketmq', 'overview'] });
    },
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const topicColumns: TableColumnsType<RocketMqDlqTopic> = [
    {
      title: t('rocketmq.fields.dlqTopic'),
      dataIndex: 'topic',
      ellipsis: true,
    },
    { title: t('rocketmq.fields.consumerGroup'), dataIndex: 'consumerGroup', width: 220 },
    {
      title: t('rocketmq.fields.messageCount'),
      dataIndex: 'messageCount',
      width: 130,
      render: (value: number) => (
        <Typography.Text type={value > 0 ? 'danger' : undefined}>{formatCount(value)}</Typography.Text>
      ),
    },
    {
      title: t('rocketmq.fields.lastUpdate'),
      dataIndex: 'lastUpdateTimestamp',
      width: 180,
      render: (value: number) => formatTime(value),
    },
  ];

  const messageColumns: TableColumnsType<RocketMqMessage> = [
    { title: t('rocketmq.fields.queueId'), dataIndex: 'queueId', width: 80 },
    {
      title: t('rocketmq.fields.queueOffset'),
      dataIndex: 'queueOffset',
      width: 110,
      render: (value: number) => formatCount(value),
    },
    { title: 'MsgID', dataIndex: 'msgId', ellipsis: true },
    { title: t('rocketmq.fields.tag'), dataIndex: 'tags', width: 130, ellipsis: true },
    { title: t('rocketmq.fields.key'), dataIndex: 'keys', width: 160, ellipsis: true },
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

  const openResend = () => {
    if (!selectedMessage) {
      return;
    }
    resendForm.setFieldsValue({
      targetTopic: selectedMessage.targetTopic ?? '',
      tag: selectedMessage.tags ?? '',
      key: selectedMessage.keys ?? '',
    });
    setResendOpen(true);
  };

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Text type="secondary">{t('rocketmq.dlq.hint')}</Typography.Text>
        <Button
          icon={<ReloadOutlined />}
          loading={topicsQuery.isFetching}
          onClick={() => void topicsQuery.refetch()}
        >
          {t('common.refresh')}
        </Button>
      </Space>

      {topicsQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, topicsQuery.error)} />
      ) : null}

      <Card title={t('rocketmq.dlq.topics')}>
        <Table<RocketMqDlqTopic>
          rowKey="topic"
          size="small"
          columns={topicColumns}
          dataSource={topicsQuery.data?.topics ?? []}
          loading={topicsQuery.isLoading}
          pagination={false}
          locale={{ emptyText: t('rocketmq.dlq.noTopics') }}
          onRow={(record) => ({
            onClick: () => setSelectedTopic(record.topic),
            tabIndex: 0,
            role: 'button',
            style: { cursor: 'pointer' },
            onKeyDown: (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                setSelectedTopic(record.topic);
              }
            },
          })}
        />
      </Card>

      {selectedTopic ? (
        <Card title={selectedTopic}>
          {messagesQuery.isError ? (
            <Alert type="error" showIcon title={errorMessage(t, messagesQuery.error)} />
          ) : null}
          <Table<RocketMqMessage>
            rowKey="msgId"
            size="small"
            columns={messageColumns}
            dataSource={messagesQuery.data ?? []}
            loading={messagesQuery.isLoading}
            pagination={false}
            scroll={{ x: 1000 }}
            locale={{ emptyText: t('rocketmq.dlq.noMessages') }}
            onRow={(record) => ({
              onClick: () => setSelectedMessage(record),
              tabIndex: 0,
              role: 'button',
              style: { cursor: 'pointer' },
              onKeyDown: (event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  setSelectedMessage(record);
                }
              },
            })}
          />
        </Card>
      ) : null}

      <Drawer
        title={t('rocketmq.dlq.messageDetail')}
        open={selectedMessage !== null}
        size={720}
        onClose={() => setSelectedMessage(null)}
        extra={
          selectedMessage ? (
            <Button type="primary" onClick={openResend}>
              {t('rocketmq.dlq.resend')}
            </Button>
          ) : null
        }
      >
        {selectedMessage ? (
          <Space orientation="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions
              bordered
              size="small"
              column={1}
              items={[
                { key: 'msgId', label: 'MsgID', children: selectedMessage.msgId },
                { key: 'topic', label: t('rocketmq.fields.topic'), children: selectedMessage.topic },
                {
                  key: 'target',
                  label: t('rocketmq.dlq.targetTopic'),
                  children: selectedMessage.targetTopic || '-',
                },
                { key: 'tags', label: t('rocketmq.fields.tag'), children: selectedMessage.tags || '-' },
                { key: 'keys', label: t('rocketmq.fields.key'), children: selectedMessage.keys || '-' },
                {
                  key: 'queue',
                  label: t('rocketmq.fields.queue'),
                  children: `${selectedMessage.queueId} / ${formatCount(selectedMessage.queueOffset)}`,
                },
                {
                  key: 'born',
                  label: t('rocketmq.fields.bornTime'),
                  children: formatTime(selectedMessage.bornTimestamp),
                },
                {
                  key: 'store',
                  label: t('rocketmq.fields.storeTime'),
                  children: formatTime(selectedMessage.storeTimestamp),
                },
                {
                  key: 'reconsume',
                  label: t('rocketmq.fields.reconsumeTimes'),
                  children: selectedMessage.reconsumeTimes,
                },
              ]}
            />
            <Card size="small" title={t('rocketmq.fields.body')}>
              <MessageBody text={selectedMessage.body} truncated={selectedMessage.bodyTruncated} />
            </Card>
          </Space>
        ) : null}
      </Drawer>

      <Modal
        title={t('rocketmq.dlq.resendTitle')}
        open={resendOpen}
        okText={t('rocketmq.dlq.resend')}
        okButtonProps={{ danger: true, htmlType: 'submit' }}
        confirmLoading={resendMutation.isPending}
        onCancel={() => setResendOpen(false)}
        onOk={() => resendForm.submit()}
        destroyOnHidden
      >
        <Alert type="warning" showIcon title={t('rocketmq.dlq.resendHint')} style={{ marginBottom: 16 }} />
        <Form<ResendForm> form={resendForm} layout="vertical" onFinish={(values) => resendMutation.mutate(values)}>
          <Form.Item
            name="targetTopic"
            label={t('rocketmq.dlq.targetTopic')}
            rules={[{ required: true, message: t('rocketmq.dlq.targetRequired') }]}
          >
            <Input placeholder="wechat-archive-msg" />
          </Form.Item>
          <Form.Item name="tag" label={t('rocketmq.fields.tag')}>
            <Input />
          </Form.Item>
          <Form.Item name="key" label={t('rocketmq.fields.key')}>
            <Input />
          </Form.Item>
        </Form>
        {selectedMessage ? (
          <Typography.Text type="secondary">
            {t('rocketmq.dlq.sourceMessage', { msgId: selectedMessage.msgId })}
          </Typography.Text>
        ) : null}
      </Modal>
    </Space>
  );
}
