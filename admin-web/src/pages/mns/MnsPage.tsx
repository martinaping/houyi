import { CloudServerOutlined, ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Select, Space, Tabs, Tag, Typography } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { mnsApi } from '../../api/mns';
import { MnsConfigTab } from './ConfigTab';
import { MnsMessagesTab } from './MessagesTab';
import { MnsOverviewTab } from './OverviewTab';
import { MnsQueuesTab } from './QueuesTab';

export function MnsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [activeKey, setActiveKey] = useState('overview');
  const [selectedQueue, setSelectedQueue] = useState('');

  const configQuery = useQuery({
    queryKey: ['mns', 'config'],
    queryFn: () => mnsApi.config(),
  });
  const config = configQuery.data;
  const queuesQuery = useQuery({
    queryKey: ['mns', 'queues', ''],
    queryFn: () => mnsApi.queues('', 50),
    enabled: Boolean(config?.configured && config?.enabled),
  });

  useEffect(() => {
    if (!selectedQueue && config?.queueName) {
      setSelectedQueue(config.queueName);
    }
  }, [config?.queueName, selectedQueue]);

  const queueOptions = Array.from(new Set([
    ...(config?.queueName ? [config.queueName] : []),
    ...(queuesQuery.data ?? []).map((queue) => queue.queueName),
  ]));

  const statusTag = !config?.configured ? (
    <Tag>{t('mns.notConfigured')}</Tag>
  ) : (
    <Tag color={config.available ? 'green' : 'red'}>
      {config.available ? t('mns.connected') : t('mns.unavailable')}
    </Tag>
  );

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }} align="start">
        <Space orientation="vertical" size={2}>
          <Typography.Title level={4} style={{ margin: 0 }}>
            <CloudServerOutlined /> {t('mns.title')}
          </Typography.Title>
          <Typography.Text type="secondary">{t('mns.hint')}</Typography.Text>
        </Space>
        <Space>
          {statusTag}
          <Select
            showSearch
            style={{ width: 260 }}
            value={selectedQueue || undefined}
            placeholder={t('mns.queuePlaceholder')}
            loading={queuesQuery.isLoading}
            onChange={setSelectedQueue}
            options={queueOptions.map((queue) => ({ value: queue, label: queue }))}
          />
          <Button
            icon={<ReloadOutlined />}
            loading={configQuery.isFetching}
            onClick={() => void queryClient.invalidateQueries({ queryKey: ['mns'] })}
          >
            {t('common.refresh')}
          </Button>
        </Space>
      </Space>

      {configQuery.isError ? (
        <Alert type="error" showIcon title={t('mns.loadFailed')} />
      ) : null}
      {config && !config.configured ? (
        <Alert type="info" showIcon title={t('mns.notConfiguredHint')} />
      ) : null}

      <Tabs
        activeKey={activeKey}
        onChange={setActiveKey}
        items={[
          {
            key: 'overview',
            label: t('mns.tabs.overview'),
            children: <MnsOverviewTab queue={selectedQueue} />,
          },
          {
            key: 'queues',
            label: t('mns.tabs.queues'),
            children: (
              <MnsQueuesTab
                onSelect={(queue) => {
                  setSelectedQueue(queue);
                  setActiveKey('overview');
                }}
              />
            ),
          },
          {
            key: 'messages',
            label: t('mns.tabs.messages'),
            children: <MnsMessagesTab queue={selectedQueue} />,
          },
          {
            key: 'config',
            label: t('mns.tabs.config'),
            children: <MnsConfigTab queue={selectedQueue} />,
          },
        ]}
      />
    </Space>
  );
}
