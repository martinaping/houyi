import { SettingOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Descriptions, Space, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { redisApi } from '../../api/redis';
import type { RedisConfigItem } from '../../api/redis';

export function RedisConfigTab() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const query = useQuery({
    queryKey: ['redis', 'config'],
    queryFn: () => redisApi.config(),
  });
  const config = query.data;

  const columns: TableColumnsType<RedisConfigItem> = [
    { title: t('redis.fields.key'), dataIndex: 'key', width: 260 },
    {
      title: t('redis.fields.value'),
      dataIndex: 'value',
      ellipsis: true,
      render: (value: string) => <Typography.Text code>{value || '-'}</Typography.Text>,
    },
    {
      title: t('redis.fields.source'),
      dataIndex: 'source',
      width: 110,
      render: (value: string) => (
        <Tag color={value === 'ENV' ? 'blue' : value === 'DB' ? 'green' : 'default'}>
          {t(`redis.config.source.${value}`, { defaultValue: value })}
        </Tag>
      ),
    },
    {
      title: t('redis.fields.applyMode'),
      dataIndex: 'applyMode',
      width: 120,
      render: (value: string) => (
        <Tag color={value === 'HOT' ? 'blue' : value === 'RESTART' ? 'orange' : 'green'}>
          {t(`settings.applyMode.${value}`, { defaultValue: value })}
        </Tag>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      {query.isError ? <Alert type="error" showIcon title={t('redis.config.loadFailed')} /> : null}
      {config && !config.available ? (
        <Alert type="warning" showIcon title={t('redis.config.unavailable')} description={config.error} />
      ) : null}

      <Card
        title={t('redis.config.title')}
        extra={
          <Button icon={<SettingOutlined />} onClick={() => navigate('/settings')}>
            {t('redis.config.openSettings')}
          </Button>
        }
      >
        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          <Descriptions
            bordered
            size="small"
            column={{ xs: 1, sm: 2 }}
            items={[
              { key: 'host', label: t('redis.fields.host'), children: config?.host ?? '-' },
              { key: 'port', label: t('redis.fields.port'), children: config?.port ?? '-' },
              { key: 'database', label: t('redis.fields.database'), children: config?.database ?? '-' },
              {
                key: 'latency',
                label: t('redis.fields.latency'),
                children: `${config?.latencyMs ?? '-'} ms`,
              },
            ]}
          />
          <Table<RedisConfigItem>
            rowKey="key"
            size="small"
            columns={columns}
            dataSource={config?.redisMqItems ?? []}
            loading={query.isLoading}
            pagination={false}
            locale={{ emptyText: t('common.noData') }}
          />
          <Alert type="info" showIcon title={config?.note || t('redis.config.note')} />
        </Space>
      </Card>
    </Space>
  );
}
