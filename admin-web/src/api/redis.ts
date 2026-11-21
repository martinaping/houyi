import { request } from './client';

export type RedisKeyspace = {
  name: string;
  keys: number;
  expires: number;
  avgTtl: number | null;
};

export type RedisStreamSummary = {
  key: string;
  length: number;
  groups: number;
  firstEntryId: string;
  lastEntryId: string;
};

export type RedisStreamConsumer = {
  name: string;
  pending: number;
  idleMs: number;
};

export type RedisPendingSummary = {
  group: string;
  total: number;
  smallestId: string;
  largestId: string;
};

export type RedisStreamGroup = {
  name: string;
  consumers: number;
  pending: number;
  lastDeliveredId: string;
  consumerDetails: RedisStreamConsumer[];
  pendingSummary: RedisPendingSummary | null;
};

export type RedisStreamEntry = {
  id: string;
  fields: Record<string, string>;
};

export type RedisStreamDetail = {
  summary: RedisStreamSummary;
  groups: RedisStreamGroup[];
  recentEntries: RedisStreamEntry[];
};

export type RedisOverview = {
  available: boolean;
  error: string | null;
  host: string;
  port: number;
  database: number;
  latencyMs: number;
  dbSize: number;
  info: Record<string, string>;
  keyspaces: RedisKeyspace[];
  streams: RedisStreamSummary[];
  lastSeqCount: number;
  pullMetricCount: number;
  pushMetricSummary: string;
  note: string;
};

export type RedisKey = {
  key: string;
  type: string;
  ttl: number;
  size: number | null;
  preview: string;
  previewTruncated: boolean;
  deletable: boolean;
};

export type RedisKeyPage = {
  keys: RedisKey[];
  truncated: boolean;
  scanned: number;
};

export type RedisLastSeq = {
  corpId: string;
  key: string;
  seq: number | null;
};

export type RedisPullMetric = {
  corpId: string;
  fields: Record<string, string>;
};

export type RedisMetrics = {
  lastSeq: RedisLastSeq[];
  pullMetrics: RedisPullMetric[];
  pushMetric: { fields: Record<string, string> };
  alertStates: { key: string; value: string }[];
};

export type RedisConfigItem = {
  key: string;
  value: string;
  source: string;
  applyMode: string;
  unit: string;
  sensitive: boolean;
  editable: boolean;
};

export type RedisConfigView = {
  host: string;
  port: number;
  database: number;
  available: boolean;
  error: string | null;
  latencyMs: number;
  redisMqItems: RedisConfigItem[];
  note: string;
};

export type RedisDeleteResult = {
  key: string;
  deleted: boolean;
};

export const redisApi = {
  overview: () => request<RedisOverview>('/api/admin/redis/overview'),
  streams: () => request<RedisStreamSummary[]>('/api/admin/redis/streams'),
  streamDetail: (key: string) =>
    request<RedisStreamDetail>(`/api/admin/redis/streams/detail?key=${encodeURIComponent(key)}`),
  keys: (pattern: string, limit = 100) =>
    request<RedisKeyPage>(
      `/api/admin/redis/keys?pattern=${encodeURIComponent(pattern)}&limit=${limit}`,
    ),
  deleteKey: (key: string) =>
    request<RedisDeleteResult>('/api/admin/redis/keys/delete', {
      method: 'POST',
      body: JSON.stringify({ key }),
    }),
  metrics: () => request<RedisMetrics>('/api/admin/redis/metrics'),
  config: () => request<RedisConfigView>('/api/admin/redis/config'),
};
