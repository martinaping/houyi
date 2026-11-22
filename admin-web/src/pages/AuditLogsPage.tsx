import { DownloadOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useInfiniteQuery, useMutation } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import type { TFunction } from 'i18next';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { download, request } from '../api/client';
import { errorMessage } from '../api/errors';
import type { AuditLogPage, AuditLogView } from '../api/types';
import { formatTime } from '../utils/format';
import { Can } from '../components/Can';

const PAGE_SIZE = 50;

type Filters = {
  action: string;
  operator: string;
  startAt: number | null;
  endAt: number | null;
};

type FilterForm = {
  action?: string;
  operator?: string;
  range?: [Dayjs, Dayjs];
};

/** 动作码落库为英文常量，展示时映射为当前语言；未知动作码原样显示。 */
function actionLabel(t: TFunction, action: string): string {
  return t(`audit.actions.${action}`, { defaultValue: action });
}

const EMPTY_FILTERS: Filters = {
  action: '',
  operator: '',
  startAt: null,
  endAt: null,
};

const AUDIT_ACTIONS = [
  'LOGIN_SUCCESS',
  'LOGIN_FAILED',
  'CORP_CREATE',
  'CORP_UPDATE',
  'CORP_STATUS',
  'CORP_BATCH_STATUS',
  'CORP_SECRET_ROTATE',
  'CORP_SECRET_REVEAL',
  'CORP_SECRET_REVEAL_FAILED',
  'MESSAGE_EXPORT',
  'MESSAGE_RETRY',
  'SETTING_UPDATE',
  'SETTING_RESET',
  'ALERT_TEST',
  'RETENTION_EXECUTE',
  'AUDIT_EXPORT',
  'LOG_DOWNLOAD',
  'ROCKETMQ_TEST',
];

function filterParams(filters: Filters, cursor: number | null = null): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.action) {
    params.set('action', filters.action);
  }
  if (filters.operator) {
    params.set('operator', filters.operator);
  }
  if (filters.startAt !== null) {
    params.set('startAt', String(filters.startAt));
  }
  if (filters.endAt !== null) {
    params.set('endAt', String(filters.endAt));
  }
  if (cursor !== null) {
    params.set('cursor', String(cursor));
  }
  return params;
}

async function fetchLogs(filters: Filters, cursor: number | null): Promise<AuditLogPage> {
  const params = filterParams(filters, cursor);
  params.set('limit', String(PAGE_SIZE));
  return request<AuditLogPage>(`/api/admin/audit-logs?${params.toString()}`);
}

export function AuditLogsPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [form] = Form.useForm<FilterForm>();
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);

  const logsQuery = useInfiniteQuery({
    queryKey: ['auditLogs', filters],
    queryFn: ({ pageParam }) => fetchLogs(filters, pageParam),
    initialPageParam: null as number | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  });

  const exportMutation = useMutation({
    mutationFn: () =>
      download(`/api/admin/audit-logs/export?${filterParams(filters).toString()}`, {}, 'audit-logs.csv'),
    onSuccess: () => message.success(t('common.exportSuccess')),
    onError: (error) => message.error(errorMessage(t, error)),
  });

  const onSubmit = (values: FilterForm) => {
    const [start, end] = values.range ?? [];
    setFilters({
      action: values.action?.trim() ?? '',
      operator: values.operator?.trim() ?? '',
      startAt: start ? start.valueOf() : null,
      endAt: end ? end.valueOf() : null,
    });
  };

  const onReset = () => {
    form.resetFields();
    setFilters(EMPTY_FILTERS);
  };

  const items = logsQuery.data?.pages.flatMap((page) => page.items) ?? [];

  const columns: TableColumnsType<AuditLogView> = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    {
      title: t('audit.time'),
      dataIndex: 'createdAt',
      width: 170,
      render: (value: number) => formatTime(value),
    },
    { title: t('audit.operator'), dataIndex: 'operator', width: 120 },
    {
      title: t('audit.action'),
      dataIndex: 'action',
      width: 180,
      render: (value: string) => (
        <Tag color="blue" title={value}>
          {actionLabel(t, value)}
        </Tag>
      ),
    },
    {
      title: t('audit.target'),
      dataIndex: 'target',
      width: 180,
      ellipsis: true,
      render: (value: string | null) => value ?? '-',
    },
    {
      title: t('audit.detail'),
      dataIndex: 'detail',
      ellipsis: true,
      render: (value: string | null) => value ?? '-',
    },
    {
      title: t('audit.clientIp'),
      dataIndex: 'clientIp',
      width: 140,
      render: (value: string | null) => value ?? '-',
    },
    {
      title: t('audit.traceId'),
      dataIndex: 'traceId',
      width: 200,
      ellipsis: true,
      render: (value: string | null) => value ?? '-',
    },
  ];

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('audit.title')}
        </Typography.Title>
        <Space>
          <Can permission="audit.export">
            <Button
              icon={<DownloadOutlined />}
              loading={exportMutation.isPending}
              onClick={() => exportMutation.mutate()}
            >
              {t('common.export')}
            </Button>
          </Can>
          <Button
            icon={<ReloadOutlined />}
            loading={logsQuery.isFetching}
            onClick={() => void logsQuery.refetch()}
          >
            {t('common.refresh')}
          </Button>
        </Space>
      </Space>

      <Card>
        <Form<FilterForm> form={form} layout="inline" onFinish={onSubmit} style={{ rowGap: 12 }}>
          <Form.Item name="action" label={t('audit.action')}>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder={t('audit.actionPlaceholder')}
              style={{ width: 220 }}
              options={AUDIT_ACTIONS.map((code) => ({
                value: code,
                label: `${actionLabel(t, code)} · ${code}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="operator" label={t('audit.operator')}>
            <Input allowClear placeholder={t('audit.operatorPlaceholder')} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="range" label={t('audit.timeRange')}>
            <DatePicker.RangePicker showTime />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                {t('common.search')}
              </Button>
              <Button onClick={onReset}>{t('common.reset')}</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card>
        {logsQuery.isError ? (
          <Alert type="error" showIcon title={errorMessage(t, logsQuery.error)} />
        ) : null}

        <Table<AuditLogView>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={items}
          loading={logsQuery.isLoading}
          pagination={false}
          scroll={{ x: 1200 }}
          locale={{ emptyText: t('audit.empty') }}
        />

        {logsQuery.hasNextPage ? (
          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <Button
              loading={logsQuery.isFetchingNextPage}
              onClick={() => void logsQuery.fetchNextPage()}
            >
              {t('common.loadMore')}
            </Button>
          </div>
        ) : null}
      </Card>
    </Space>
  );
}
