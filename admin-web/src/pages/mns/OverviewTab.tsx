import { Alert, Card, Col, Descriptions, Row, Space, Statistic, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { mnsApi } from '../../api/mns';
import { formatCount, formatBytes } from '../rocketmq/shared';
import { formatTime } from '../../utils/format';

export function MnsOverviewTab({ queue }: { queue: string }) {
  const { t } = useTranslation();
  const query = useQuery({
    queryKey: ['mns', 'overview', queue],
    queryFn: () => mnsApi.overview(queue || undefined),
    refetchInterval: 30_000,
  });
  const data = query.data;
  const meta = data?.meta;

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      {query.isError ? <Alert type="error" showIcon title={t('mns.loadFailed')} /> : null}
      {data && !data.available ? (
        <Alert
          type="warning"
          showIcon
          title={t('mns.overview.unavailable')}
          description={data.error}
        />
      ) : null}
      {data && data.available && !data.queueExists ? (
        <Alert type="warning" showIcon title={t('mns.overview.queueMissing')} description={data.queueName} />
      ) : null}

      <Row gutter={[16, 16]}>
        <Col xs={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic title={t('mns.overview.active')} value={formatCount(meta?.activeMessages)} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic title={t('mns.overview.delayed')} value={formatCount(meta?.delayMessages)} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic title={t('mns.overview.inactive')} value={formatCount(meta?.inactiveMessages)} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card loading={query.isLoading}>
            <Statistic
              title={t('mns.overview.maxMessageSize')}
              value={formatBytes(meta?.maxMessageSize)}
            />
          </Card>
        </Col>
      </Row>

      <Card title={t('mns.overview.queueMeta')}>
        <Descriptions
          bordered
          size="small"
          column={{ xs: 1, sm: 2 }}
          items={[
            {
              key: 'endpoint',
              label: t('mns.fields.endpoint'),
              children: <Typography.Text code>{data?.endpoint || '-'}</Typography.Text>,
            },
            { key: 'queue', label: t('mns.fields.queue'), children: data?.queueName || '-' },
            {
              key: 'exists',
              label: t('mns.fields.exists'),
              children: data?.queueExists ? t('common.yes') : t('common.no'),
            },
            {
              key: 'retention',
              label: t('mns.fields.retention'),
              children: meta?.retentionPeriod == null ? '-' : `${meta.retentionPeriod} s`,
            },
            {
              key: 'visibility',
              label: t('mns.fields.visibility'),
              children: meta?.visibilityTimeout == null ? '-' : `${meta.visibilityTimeout} s`,
            },
            {
              key: 'delay',
              label: t('mns.fields.defaultDelay'),
              children: meta?.delaySeconds == null ? '-' : `${meta.delaySeconds} s`,
            },
            {
              key: 'polling',
              label: t('mns.fields.pollingWait'),
              children: meta?.pollingWaitSeconds == null ? '-' : `${meta.pollingWaitSeconds} s`,
            },
            {
              key: 'create',
              label: t('mns.fields.createTime'),
              children: formatTime(meta?.createTime ?? null),
            },
            {
              key: 'modify',
              label: t('mns.fields.modifyTime'),
              children: formatTime(meta?.lastModifyTime ?? null),
            },
            {
              key: 'url',
              label: t('mns.fields.queueUrl'),
              children: <Typography.Text code>{meta?.queueUrl || '-'}</Typography.Text>,
            },
          ]}
        />
      </Card>

      <Alert type="info" showIcon title={data?.note || t('mns.overview.note')} />
    </Space>
  );
}
