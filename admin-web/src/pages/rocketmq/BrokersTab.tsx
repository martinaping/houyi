import { ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { rocketMqApi } from '../../api/rocketmq';
import type { RocketMqBroker } from '../../api/rocketmq';
import { errorMessage } from '../../api/errors';
import { formatMs, ProbeTag } from './shared';
import { formatTime } from '../../utils/format';

export function BrokersTab() {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<RocketMqBroker | null>(null);
  const query = useQuery({
    queryKey: ['rocketmq', 'brokers'],
    queryFn: () => rocketMqApi.brokers(),
    refetchInterval: 30_000,
  });

  const data = query.data;
  const primary = data?.brokers.find((broker) => broker.role === 'MASTER' && broker.active)
    ?? data?.brokers[0];
  const runtime = primary?.runtime ?? {};

  const columns: TableColumnsType<RocketMqBroker> = [
    { title: t('rocketmq.fields.cluster'), dataIndex: 'cluster', width: 150 },
    { title: t('rocketmq.fields.broker'), dataIndex: 'brokerName', width: 150 },
    {
      title: t('rocketmq.fields.role'),
      dataIndex: 'role',
      width: 100,
      render: (value: string) => <Tag color={value === 'MASTER' ? 'blue' : 'default'}>{value}</Tag>,
    },
    { title: t('rocketmq.fields.addr'), dataIndex: 'addr', ellipsis: true },
    {
      title: t('rocketmq.fields.version'),
      dataIndex: 'version',
      width: 110,
      render: (value: string) => value || '-',
    },
    {
      title: t('rocketmq.fields.active'),
      dataIndex: 'active',
      width: 100,
      render: (value: boolean) => (
        <Tag color={value ? 'green' : 'red'}>
          {value ? t('rocketmq.active') : t('rocketmq.inactive')}
        </Tag>
      ),
    },
  ];

  const runtimeColumns: TableColumnsType<{ key: string; value: string }> = [
    { title: t('rocketmq.fields.metric'), dataIndex: 'key', width: 280 },
    {
      title: t('rocketmq.fields.value'),
      dataIndex: 'value',
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Space>
          <Typography.Text type="secondary">{t('rocketmq.brokers.probe')}</Typography.Text>
          <ProbeTag state={data?.probe?.state} />
          <Typography.Text type="secondary">
            {formatMs(data?.probe?.latencyMs)} · {formatTime(data?.probe?.checkedAt)}
          </Typography.Text>
        </Space>
        <Button
          icon={<ReloadOutlined />}
          loading={query.isFetching}
          onClick={() => void query.refetch()}
        >
          {t('common.refresh')}
        </Button>
      </Space>

      {query.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, query.error)} />
      ) : null}

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card loading={query.isLoading}>
            <Statistic
              title={t('rocketmq.brokers.putToday')}
              value={runtime.msgPutTotalTodayNow ?? '-'}
            />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card loading={query.isLoading}>
            <Statistic
              title={t('rocketmq.brokers.getToday')}
              value={runtime.msgGetTotalTodayNow ?? '-'}
            />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card loading={query.isLoading}>
            <Statistic
              title={t('rocketmq.brokers.dispatchBehind')}
              value={runtime.dispatchBehindBytes ?? '-'}
            />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card loading={query.isLoading}>
            <Statistic
              title={t('rocketmq.brokers.pageCacheLock')}
              value={runtime.pageCacheLockTimeMills ?? '-'}
              suffix="ms"
            />
          </Card>
        </Col>
      </Row>

      <Card title={t('rocketmq.brokers.disk')} loading={query.isLoading}>
        <Descriptions
          column={1}
          size="small"
          items={[
            {
              key: 'capacity',
              label: t('rocketmq.brokers.commitLogCapacity'),
              children: runtime.commitLogDirCapacity ?? '-',
            },
            {
              key: 'ratio',
              label: t('rocketmq.brokers.diskRatio'),
              children: runtime.commitLogDiskRatio ?? '-',
            },
            {
              key: 'latency',
              label: t('rocketmq.brokers.putLatency'),
              children: `${runtime.putLatency99 ?? '-'} / ${runtime.putLatency999 ?? '-'}`,
            },
          ]}
        />
      </Card>

      <Card>
        <Table<RocketMqBroker>
          rowKey={(record) => `${record.cluster}-${record.brokerName}-${record.brokerId}`}
          size="small"
          columns={columns}
          dataSource={data?.brokers ?? []}
          loading={query.isLoading}
          pagination={false}
          locale={{ emptyText: t('common.noData') }}
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
        title={t('rocketmq.brokers.detailTitle', {
          broker: selected ? `${selected.cluster}/${selected.brokerName}` : '',
        })}
        open={selected !== null}
        size={620}
        onClose={() => setSelected(null)}
      >
        <Table<{ key: string; value: string }>
          rowKey="key"
          size="small"
          columns={runtimeColumns}
          dataSource={Object.entries(selected?.runtime ?? {})
            .map(([key, value]) => ({ key, value }))
            .sort((left, right) => left.key.localeCompare(right.key))}
          pagination={{ pageSize: 30, showSizeChanger: false }}
          locale={{ emptyText: t('common.noData') }}
        />
      </Drawer>
    </Space>
  );
}
