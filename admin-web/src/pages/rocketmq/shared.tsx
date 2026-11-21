import { Tag, Typography } from 'antd';
import type { TFunction } from 'i18next';
import { useTranslation } from 'react-i18next';

export function probeColor(state: string | undefined): string {
  if (state === 'UP') {
    return 'green';
  }
  if (state === 'DOWN') {
    return 'red';
  }
  return 'default';
}

export function ProbeTag({ state }: { state: string | undefined }) {
  const { t } = useTranslation();
  const label = state ? t(`rocketmq.probeState.${state}`, { defaultValue: state }) : '-';
  return <Tag color={probeColor(state)}>{label}</Tag>;
}

export function SourceTag({ source }: { source: string }) {
  const { t } = useTranslation();
  const color = source === 'ENV' ? 'blue' : source === 'DB' ? 'green' : 'default';
  return <Tag color={color}>{t(`rocketmq.config.source.${source}`, { defaultValue: source })}</Tag>;
}

export function ApplyModeTag({ mode }: { mode: string }) {
  const { t } = useTranslation();
  const color = mode === 'HOT' ? 'blue' : mode === 'RESTART' ? 'orange' : 'green';
  return <Tag color={color}>{t(`settings.applyMode.${mode}`, { defaultValue: mode })}</Tag>;
}

export function formatTps(value: number | null | undefined): string {
  return value == null ? '-' : value.toFixed(2);
}

export function formatCount(value: number | null | undefined): string {
  return value == null ? '-' : value.toLocaleString();
}

export function formatMs(value: number | null | undefined): string {
  if (value == null) {
    return '-';
  }
  return value >= 1000 ? `${(value / 1000).toFixed(2)} s` : `${value} ms`;
}

export function formatBytes(value: number | null | undefined): string {
  if (value == null) {
    return '-';
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

export function MessageBody({
  text,
  truncated,
  maxHeight = 360,
}: {
  text: string | null | undefined;
  truncated?: boolean;
  maxHeight?: number;
}) {
  const { t } = useTranslation();
  return (
    <div>
      {truncated ? (
        <Typography.Text type="warning" style={{ display: 'block', marginBottom: 8 }}>
          {t('rocketmq.messages.bodyTruncated')}
        </Typography.Text>
      ) : null}
      <pre
        style={{
          margin: 0,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-all',
          maxHeight,
          overflow: 'auto',
          padding: 12,
          background: 'rgba(0, 0, 0, 0.03)',
          borderRadius: 6,
          fontSize: 12,
        }}
      >
        {text || '-'}
      </pre>
    </div>
  );
}

export function statusLabel(t: TFunction, status: number): string {
  return t(`rocketmq.retry.status.${status}`, { defaultValue: String(status) });
}

export function statusColor(status: number): string {
  if (status === 0) {
    return 'orange';
  }
  if (status === 1) {
    return 'green';
  }
  if (status === 2) {
    return 'red';
  }
  return 'default';
}
