import { DownloadOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { download, request } from '../api/client';
import { errorMessage } from '../api/errors';
import type { LogSourcesView, LogTailView } from '../api/types';
import { formatTime } from '../utils/format';

const LEVEL_OPTIONS = ['', 'ERROR', 'WARN', 'INFO', 'DEBUG'];

function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function lineClass(line: string): string {
  if (/"level"\s*:\s*"ERROR"/i.test(line) || /\bERROR\b/i.test(line)) {
    return 'log-line log-error';
  }
  if (/"level"\s*:\s*"WARN"/i.test(line) || /\bWARN(ING)?\b/i.test(line)) {
    return 'log-line log-warn';
  }
  if (/\bDEBUG\b|\bTRACE\b/i.test(line)) {
    return 'log-line log-debug';
  }
  return 'log-line log-info';
}

export function LogsPage() {
  const { t } = useTranslation();
  const { message } = App.useApp();

  const [sourceKey, setSourceKey] = useState('');
  const [filePath, setFilePath] = useState('');
  const [level, setLevel] = useState('');
  const [lines, setLines] = useState(500);
  const [linesDraft, setLinesDraft] = useState<number | null>(500);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  // 默认关闭自动刷新，避免打断日志阅读与选中复制。
  const [autoRefresh, setAutoRefresh] = useState(false);

  const sourcesQuery = useQuery({
    queryKey: ['logSources'],
    queryFn: () => request<LogSourcesView>('/api/admin/logs/sources'),
    refetchInterval: 30_000,
  });

  const sources = sourcesQuery.data?.sources ?? [];

  useEffect(() => {
    if (sourceKey || sources.length === 0) {
      return;
    }
    const first = sources.find((item) => item.available) ?? sources[0];
    if (first) {
      setSourceKey(first.key);
    }
  }, [sourceKey, sources]);

  const activeSource = useMemo(
    () => sources.find((item) => item.key === sourceKey),
    [sources, sourceKey],
  );

  const tailQuery = useQuery({
    queryKey: ['logTail', sourceKey, filePath, level, keyword, lines],
    queryFn: () => {
      const params = new URLSearchParams({ source: sourceKey, lines: String(lines) });
      if (filePath) {
        params.set('file', filePath);
      }
      if (level) {
        params.set('level', level);
      }
      if (keyword) {
        params.set('keyword', keyword);
      }
      return request<LogTailView>(`/api/admin/logs/tail?${params.toString()}`);
    },
    enabled: Boolean(sourceKey && activeSource?.available),
    refetchInterval: autoRefresh ? 5_000 : false,
  });

  const onSourceChange = (value: string) => {
    setSourceKey(value);
    setFilePath('');
  };

  const onDownload = async () => {
    if (!sourceKey) {
      return;
    }
    const params = new URLSearchParams({ source: sourceKey });
    if (filePath) {
      params.set('file', filePath);
    }
    try {
      await download(
        `/api/admin/logs/download?${params.toString()}`,
        {},
        `${activeSource?.key ?? 'houyi'}.log`,
      );
    } catch (error) {
      message.error(errorMessage(t, error));
    }
  };

  const data = tailQuery.data;

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Space orientation="vertical" size={0}>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t('logs.title')}
          </Typography.Title>
          <Typography.Text type="secondary">{t('logs.hint')}</Typography.Text>
        </Space>
        <Space>
          <Space size={6}>
            <Typography.Text type="secondary">{t('logs.autoRefresh')}</Typography.Text>
            <Switch size="small" checked={autoRefresh} onChange={setAutoRefresh} />
          </Space>
          <Button
            icon={<DownloadOutlined />}
            disabled={!activeSource?.available}
            onClick={() => void onDownload()}
          >
            {t('logs.download')}
          </Button>
          <Button
            icon={<ReloadOutlined />}
            loading={tailQuery.isFetching}
            onClick={() => {
              void sourcesQuery.refetch();
              void tailQuery.refetch();
            }}
          >
            {t('common.refresh')}
          </Button>
        </Space>
      </Space>

      <Card>
        <Space wrap size="middle">
          <Space size={6}>
            <Typography.Text type="secondary">{t('logs.source')}</Typography.Text>
            <Select
              style={{ width: 220 }}
              value={sourceKey || undefined}
              loading={sourcesQuery.isLoading}
              onChange={onSourceChange}
              placeholder={t('logs.source')}
              options={sources.map((item) => ({
                value: item.key,
                label: t(`logs.sources.${item.key}`, { defaultValue: item.name }),
              }))}
            />
          </Space>
          <Space size={6}>
            <Typography.Text type="secondary">{t('logs.file')}</Typography.Text>
            <Select
              style={{ width: 300 }}
              value={filePath || undefined}
              allowClear
              disabled={!activeSource?.available}
              onChange={(value) => setFilePath(value ?? '')}
              placeholder={t('logs.emptySource')}
              options={(activeSource?.files ?? []).map((item) => ({
                value: item.path,
                label: `${item.name} · ${formatSize(item.size)}`,
              }))}
            />
          </Space>
          <Space size={6}>
            <Typography.Text type="secondary">{t('logs.level')}</Typography.Text>
            <Select
              style={{ width: 120 }}
              value={level}
              onChange={setLevel}
              options={LEVEL_OPTIONS.map((item) => ({
                value: item,
                label: item || t('common.all'),
              }))}
            />
          </Space>
          <Space size={6}>
            <Typography.Text type="secondary">{t('logs.lines')}</Typography.Text>
            <InputNumber
              min={50}
              max={2000}
              step={100}
              value={linesDraft}
              onChange={(value) => setLinesDraft(value)}
              onBlur={() => setLines(linesDraft ?? 500)}
              onPressEnter={() => setLines(linesDraft ?? 500)}
              style={{ width: 100 }}
            />
          </Space>
          <Space.Compact>
            <Input
              allowClear
              style={{ width: 280 }}
              value={keywordInput}
              placeholder={t('logs.keywordPlaceholder')}
              onChange={(event) => setKeywordInput(event.target.value)}
              onPressEnter={() => setKeyword(keywordInput.trim())}
            />
            <Button
              type="primary"
              icon={<SearchOutlined />}
              onClick={() => setKeyword(keywordInput.trim())}
            >
              {t('common.search')}
            </Button>
            <Button
              onClick={() => {
                setKeywordInput('');
                setKeyword('');
              }}
            >
              {t('common.reset')}
            </Button>
          </Space.Compact>
        </Space>
        <div style={{ marginTop: 12 }}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {t('logs.viewerHint')}
          </Typography.Text>
        </div>
      </Card>

      <Card
        title={
          <Space size="middle">
            <span>{data?.file ?? activeSource?.name ?? t('logs.title')}</span>
            {data ? (
              <>
                <Tag>{formatSize(data.size)}</Tag>
                <Tag color="blue">{formatTime(data.modifiedAt)}</Tag>
                <Tag color="geekblue">{t('logs.matched', { count: data.matched })}</Tag>
              </>
            ) : null}
          </Space>
        }
        extra={
          data?.path ? (
            <Tooltip title={data.path}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {t('logs.path')}
              </Typography.Text>
            </Tooltip>
          ) : null
        }
        styles={{ body: { padding: 0 } }}
      >
        {sourcesQuery.isError ? (
          <div style={{ padding: 16 }}>
            <Alert type="error" showIcon title={errorMessage(t, sourcesQuery.error)} />
          </div>
        ) : null}

        {tailQuery.isError ? (
          <div style={{ padding: 16 }}>
            <Alert type="error" showIcon title={errorMessage(t, tailQuery.error)} />
          </div>
        ) : null}

        {activeSource && !activeSource.available ? (
          <div style={{ padding: 16 }}>
            <Alert
              type="warning"
              showIcon
              title={t('logs.notAvailable')}
              description={activeSource.description}
            />
          </div>
        ) : null}

        {data?.truncated ? (
          <div style={{ padding: '12px 16px 0' }}>
            <Alert type="info" showIcon title={t('logs.truncated', { count: data.lines.length })} />
          </div>
        ) : null}

        {data && data.lines.length > 0 ? (
          <div className="log-viewer">
            {data.lines.map((line, index) => (
              <div key={index} className={lineClass(line)}>
                {line || ' '}
              </div>
            ))}
          </div>
        ) : null}

        {data && data.lines.length === 0 && !tailQuery.isError ? (
          <div style={{ padding: 32, textAlign: 'center' }}>
            <Typography.Text type="secondary">{t('logs.empty')}</Typography.Text>
          </div>
        ) : null}
      </Card>
    </Space>
  );
}
