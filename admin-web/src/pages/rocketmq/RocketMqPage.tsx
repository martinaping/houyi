import { ReloadOutlined, RocketOutlined } from '@ant-design/icons';
import { Alert, Button, Space, Tabs, Tag, Typography } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { rocketMqApi } from '../../api/rocketmq';
import type { RocketMqOverview } from '../../api/rocketmq';
import { errorMessage } from '../../api/errors';
import { BrokersTab } from './BrokersTab';
import { ConfigTab } from './ConfigTab';
import { DlqTab } from './DlqTab';
import { GroupsTab } from './GroupsTab';
import { MessageQueryTab } from './MessageQueryTab';
import { OverviewTab } from './OverviewTab';
import { RetryTab } from './RetryTab';
import { TopicsTab } from './TopicsTab';

export function RocketMqPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [activeKey, setActiveKey] = useState('overview');

  const overviewQuery = useQuery<RocketMqOverview>({
    queryKey: ['rocketmq', 'overview'],
    queryFn: () => rocketMqApi.overview(),
    refetchInterval: 30_000,
  });

  const refreshAll = () => {
    void queryClient.invalidateQueries({ queryKey: ['rocketmq'] });
  };

  const overview = overviewQuery.data;

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }} align="start">
        <Space orientation="vertical" size={2}>
          <Typography.Title level={4} style={{ margin: 0 }}>
            <RocketOutlined /> {t('rocketmq.title')}
          </Typography.Title>
          <Typography.Text type="secondary">{t('rocketmq.hint')}</Typography.Text>
        </Space>
        <Space>
          {overview ? (
            <Tag color={overview.available ? 'green' : 'red'}>
              {overview.available ? t('rocketmq.connected') : t('rocketmq.unavailable')}
            </Tag>
          ) : null}
          <Button
            icon={<ReloadOutlined />}
            loading={overviewQuery.isFetching}
            onClick={refreshAll}
          >
            {t('common.refresh')}
          </Button>
        </Space>
      </Space>

      {overviewQuery.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, overviewQuery.error)} />
      ) : null}
      {overview && !overview.available ? (
        <Alert
          type="warning"
          showIcon
          title={t('rocketmq.unavailableHint')}
          description={
            <Space orientation="vertical" size={2}>
              <span>{overview.error}</span>
              <span>
                NameServer: <Typography.Text code>{overview.namesrvAddr}</Typography.Text>
              </span>
            </Space>
          }
        />
      ) : null}

      <Tabs
        activeKey={activeKey}
        onChange={setActiveKey}
        items={[
          {
            key: 'overview',
            label: t('rocketmq.tabs.overview'),
            children: <OverviewTab query={overviewQuery} />,
          },
          {
            key: 'groups',
            label: t('rocketmq.tabs.groups'),
            children: <GroupsTab />,
          },
          {
            key: 'dlq',
            label: t('rocketmq.tabs.dlq'),
            children: <DlqTab />,
          },
          {
            key: 'messages',
            label: t('rocketmq.tabs.messages'),
            children: <MessageQueryTab />,
          },
          {
            key: 'topics',
            label: t('rocketmq.tabs.topics'),
            children: <TopicsTab />,
          },
          {
            key: 'brokers',
            label: t('rocketmq.tabs.brokers'),
            children: <BrokersTab />,
          },
          {
            key: 'retry',
            label: t('rocketmq.tabs.retry'),
            children: <RetryTab />,
          },
          {
            key: 'config',
            label: t('rocketmq.tabs.config'),
            children: <ConfigTab />,
          },
        ]}
      />
    </Space>
  );
}
