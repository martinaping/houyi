import { ReloadOutlined, DatabaseOutlined } from '@ant-design/icons';
import { Alert, Button, Space, Tabs, Tag, Typography } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { redisApi } from '../../api/redis';
import { RedisConfigTab } from './ConfigTab';
import { RedisKeysTab } from './KeysTab';
import { RedisMetricsTab } from './MetricsTab';
import { RedisOverviewTab } from './OverviewTab';
import { RedisStreamsTab } from './StreamsTab';

export function RedisPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [activeKey, setActiveKey] = useState('overview');
  const overviewQuery = useQuery({
    queryKey: ['redis', 'overview'],
    queryFn: () => redisApi.overview(),
    refetchInterval: 30_000,
  });
  const overview = overviewQuery.data;

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }} align="start">
        <Space orientation="vertical" size={2}>
          <Typography.Title level={4} style={{ margin: 0 }}>
            <DatabaseOutlined /> {t('redis.title')}
          </Typography.Title>
          <Typography.Text type="secondary">{t('redis.hint')}</Typography.Text>
        </Space>
        <Space>
          {overview ? (
            <Tag color={overview.available ? 'green' : 'red'}>
              {overview.available ? t('redis.connected') : t('redis.unavailable')}
            </Tag>
          ) : null}
          <Button
            icon={<ReloadOutlined />}
            loading={overviewQuery.isFetching}
            onClick={() => void queryClient.invalidateQueries({ queryKey: ['redis'] })}
          >
            {t('common.refresh')}
          </Button>
        </Space>
      </Space>

      {overviewQuery.isError ? (
        <Alert type="error" showIcon title={t('redis.loadFailed')} />
      ) : null}
      {overview && !overview.available ? (
        <Alert
          type="warning"
          showIcon
          title={t('redis.unavailableHint')}
          description={
            <Space orientation="vertical" size={2}>
              <span>{overview.error}</span>
              <span>
                {overview.host}:{overview.port} / db{overview.database}
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
            label: t('redis.tabs.overview'),
            children: <RedisOverviewTab query={overviewQuery} />,
          },
          { key: 'streams', label: t('redis.tabs.streams'), children: <RedisStreamsTab /> },
          { key: 'keys', label: t('redis.tabs.keys'), children: <RedisKeysTab /> },
          { key: 'metrics', label: t('redis.tabs.metrics'), children: <RedisMetricsTab /> },
          { key: 'config', label: t('redis.tabs.config'), children: <RedisConfigTab /> },
        ]}
      />
    </Space>
  );
}
