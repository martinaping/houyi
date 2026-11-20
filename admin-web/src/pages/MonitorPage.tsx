import { ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Col,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { request } from '../api/client';
import { errorMessage } from '../api/errors';
import type {
  MonitorCustomMetric,
  MonitorExecutor,
  MonitorHttpUri,
  MonitorOverview,
} from '../api/types';
import { formatTime } from '../utils/format';

const numberFormat = new Intl.NumberFormat();

function formatBytes(value: number | null): string {
  if (value == null) {
    return '-';
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  if (value < 1024 * 1024 * 1024) {
    return `${(value / 1024 / 1024).toFixed(1)} MB`;
  }
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

function formatPercent(value: number | null): string {
  return value == null ? '-' : `${(value * 100).toFixed(1)}%`;
}

function formatMillis(seconds: number | null): string {
  if (seconds == null) {
    return '-';
  }
  return seconds >= 1 ? `${seconds.toFixed(2)} s` : `${(seconds * 1000).toFixed(0)} ms`;
}

function formatDuration(seconds: number | null): string {
  if (seconds == null) {
    return '-';
  }
  const total = Math.floor(seconds);
  const days = Math.floor(total / 86400);
  const hours = Math.floor((total % 86400) / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  if (days > 0) {
    return `${days}d ${hours}h`;
  }
  if (hours > 0) {
    return `${hours}h ${minutes}m`;
  }
  return `${minutes}m ${total % 60}s`;
}

export function MonitorPage() {
  const { t } = useTranslation();

  const query = useQuery({
    queryKey: ['monitorOverview'],
    queryFn: () => request<MonitorOverview>('/api/admin/monitor/overview'),
    refetchInterval: 15_000,
  });

  const data = query.data;
  const jvm = data?.jvm ?? null;
  const http = data?.http ?? null;
  const pool = data?.pool ?? null;
  const heapPercent =
    jvm?.heapUsedBytes != null && jvm?.heapMaxBytes
      ? Math.min(100, (jvm.heapUsedBytes / jvm.heapMaxBytes) * 100)
      : null;

  const uriColumns = [
    {
      title: t('monitor.method'),
      dataIndex: 'method',
      width: 90,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    {
      title: t('monitor.uri'),
      dataIndex: 'uri',
      ellipsis: true,
    },
    {
      title: t('monitor.count'),
      dataIndex: 'count',
      width: 100,
      align: 'right' as const,
      render: (value: number) => numberFormat.format(value),
    },
    {
      title: t('monitor.errors'),
      dataIndex: 'errors',
      width: 90,
      align: 'right' as const,
      render: (value: number) =>
        value > 0 ? <Typography.Text type="danger">{numberFormat.format(value)}</Typography.Text> : '0',
    },
    {
      title: t('monitor.avgLatency'),
      dataIndex: 'avgSeconds',
      width: 100,
      align: 'right' as const,
      render: (value: number) => formatMillis(value),
    },
    {
      title: t('monitor.maxLatency'),
      dataIndex: 'maxSeconds',
      width: 100,
      align: 'right' as const,
      render: (value: number) => formatMillis(value),
    },
  ];

  const executorColumns = [
    { title: t('monitor.executor'), dataIndex: 'name', width: 160, ellipsis: true },
    {
      title: t('monitor.active'),
      dataIndex: 'active',
      width: 72,
      align: 'right' as const,
      render: (value: number | null) => value ?? '-',
    },
    {
      title: t('monitor.poolSize'),
      dataIndex: 'poolSize',
      width: 80,
      align: 'right' as const,
      render: (value: number | null) => value ?? '-',
    },
    {
      title: t('monitor.queued'),
      dataIndex: 'queued',
      width: 80,
      align: 'right' as const,
      render: (value: number | null) => value ?? '-',
    },
    {
      title: t('monitor.completed'),
      dataIndex: 'completed',
      width: 96,
      align: 'right' as const,
      render: (value: number | null) => (value == null ? '-' : numberFormat.format(value)),
    },
  ];

  const customColumns = [
    {
      title: t('monitor.metric'),
      dataIndex: 'name',
      width: 260,
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: t('monitor.description'),
      dataIndex: 'description',
      ellipsis: true,
      render: (value: string | null) => value ?? '-',
    },
    { title: t('monitor.labels'), dataIndex: 'labels', ellipsis: true },
    {
      title: t('monitor.value'),
      dataIndex: 'value',
      width: 120,
      align: 'right' as const,
      render: (value: number) => numberFormat.format(Number(value.toFixed(2))),
    },
  ];

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Space orientation="vertical" size={0}>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t('monitor.title')}
          </Typography.Title>
          <Typography.Text type="secondary">{t('monitor.hint')}</Typography.Text>
        </Space>
        <Space>
          {data?.scrapedAt ? (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {t('monitor.scrapedAt')}: {formatTime(data.scrapedAt)}
            </Typography.Text>
          ) : null}
          <Button icon={<ReloadOutlined />} loading={query.isFetching} onClick={() => void query.refetch()}>
            {t('common.refresh')}
          </Button>
        </Space>
      </Space>

      {query.isError ? (
        <Alert type="error" showIcon title={errorMessage(t, query.error)} />
      ) : null}

      {data && !data.available ? (
        <Alert
          type="warning"
          showIcon
          title={t('monitor.unavailable')}
          description={
            <Space orientation="vertical" size={2}>
              <span>{data.error}</span>
              <span>
                {t('monitor.unavailableHint')}
                <Typography.Text code>{data.source}</Typography.Text>
              </span>
            </Space>
          }
        />
      ) : null}

      {data?.available ? (
        <>
          <Row gutter={[16, 16]}>
            <Col xs={24} md={12} xl={6}>
              <Card>
                <Statistic
                  title={t('monitor.heap')}
                  value={formatBytes(jvm?.heapUsedBytes ?? null)}
                  suffix={
                    jvm?.heapMaxBytes ? (
                      <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                        / {formatBytes(jvm.heapMaxBytes)}
                      </Typography.Text>
                    ) : null
                  }
                />
                {heapPercent != null ? (
                  <Progress
                    percent={Number(heapPercent.toFixed(1))}
                    size="small"
                    status={heapPercent > 90 ? 'exception' : 'normal'}
                  />
                ) : null}
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {t('monitor.nonHeap')}: {formatBytes(jvm?.nonHeapUsedBytes ?? null)}
                </Typography.Text>
              </Card>
            </Col>
            <Col xs={24} md={12} xl={6}>
              <Card>
                <Statistic title={t('monitor.threads')} value={jvm?.threadsLive ?? '-'} />
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {t('monitor.threadsPeak')}: {jvm?.threadsPeak ?? '-'} · GC{' '}
                  {jvm?.gcPauseCount ?? 0} {t('monitor.times')} / {formatMillis(jvm?.gcPauseSeconds ?? null)}
                </Typography.Text>
              </Card>
            </Col>
            <Col xs={24} md={12} xl={6}>
              <Card>
                <Statistic title={t('monitor.processCpu')} value={formatPercent(jvm?.processCpuUsage ?? null)} />
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {t('monitor.systemCpu')}: {formatPercent(jvm?.systemCpuUsage ?? null)} ·{' '}
                  {jvm?.systemCpuCount ?? '-'} {t('monitor.cores')}
                </Typography.Text>
              </Card>
            </Col>
            <Col xs={24} md={12} xl={6}>
              <Card>
                <Statistic title={t('monitor.uptime')} value={formatDuration(jvm?.uptimeSeconds ?? null)} />
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {t('monitor.classesLoaded')}: {jvm?.classesLoaded ?? '-'}
                </Typography.Text>
              </Card>
            </Col>
          </Row>

          <Row gutter={[16, 16]}>
            <Col xs={24}>
              <Card title={t('monitor.httpTitle')} styles={{ body: { paddingTop: 16 } }}>
                <Row gutter={[16, 16]}>
                  <Col xs={12} sm={6}>
                    <Statistic title={t('monitor.totalRequests')} value={http?.total ?? 0} />
                  </Col>
                  <Col xs={12} sm={6}>
                    <Statistic
                      title={t('monitor.serverErrors')}
                      value={http?.serverErrors ?? 0}
                      valueStyle={{ color: (http?.serverErrors ?? 0) > 0 ? '#cf1322' : undefined }}
                    />
                  </Col>
                  <Col xs={12} sm={6}>
                    <Statistic title={t('monitor.clientErrors')} value={http?.clientErrors ?? 0} />
                  </Col>
                  <Col xs={12} sm={6}>
                    <Statistic
                      title={t('monitor.avgLatency')}
                      value={formatMillis(
                        http && http.total > 0 ? http.totalSeconds / http.total : null,
                      )}
                    />
                  </Col>
                </Row>
                <Table<MonitorHttpUri>
                  style={{ marginTop: 16 }}
                  size="small"
                  rowKey={(record) => `${record.method} ${record.uri}`}
                  columns={uriColumns}
                  dataSource={http?.byUri ?? []}
                  pagination={false}
                  locale={{ emptyText: t('monitor.noData') }}
                />
              </Card>
            </Col>
            <Col xs={24}>
              <Card title={t('monitor.poolTitle')}>
                <Row gutter={[16, 16]}>
                  <Col xs={12} sm={8}>
                    <Statistic title={t('monitor.active')} value={pool?.active ?? '-'} />
                  </Col>
                  <Col xs={12} sm={8}>
                    <Statistic title={t('monitor.idle')} value={pool?.idle ?? '-'} />
                  </Col>
                  <Col xs={12} sm={8}>
                    <Statistic title={t('monitor.max')} value={pool?.max ?? '-'} />
                  </Col>
                  <Col xs={12} sm={8}>
                    <Statistic title={t('monitor.pending')} value={pool?.pending ?? '-'} />
                  </Col>
                  <Col xs={12} sm={8}>
                    <Statistic
                      title={t('monitor.timeout')}
                      value={pool?.timeoutTotal ?? 0}
                      valueStyle={{ color: (pool?.timeoutTotal ?? 0) > 0 ? '#cf1322' : undefined }}
                    />
                  </Col>
                </Row>
                {data.executors.length > 0 ? (
                  <Table<MonitorExecutor>
                    style={{ marginTop: 16 }}
                    size="small"
                    rowKey="name"
                    columns={executorColumns}
                    dataSource={data.executors}
                    pagination={false}
                    scroll={{ x: 488 }}
                  />
                ) : null}
              </Card>
            </Col>
          </Row>

          <Card
            title={t('monitor.businessTitle')}
            extra={
              <Tooltip title={data.source}>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {t('monitor.rawEndpoint')}
                </Typography.Text>
              </Tooltip>
            }
          >
            <Table<MonitorCustomMetric>
              size="small"
              rowKey={(record) => `${record.name}|${record.labels}`}
              columns={customColumns}
              dataSource={data.customMetrics}
              pagination={false}
              locale={{ emptyText: t('monitor.noBusiness') }}
            />
          </Card>
        </>
      ) : null}
    </Space>
  );
}
