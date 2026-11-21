import { request } from './client';

export type MnsQueueMeta = {
  queueName: string;
  delaySeconds: number | null;
  retentionPeriod: number | null;
  maxMessageSize: number | null;
  pollingWaitSeconds: number | null;
  visibilityTimeout: number | null;
  activeMessages: number | null;
  inactiveMessages: number | null;
  delayMessages: number | null;
  createTime: number | null;
  lastModifyTime: number | null;
  queueUrl: string | null;
};

export type MnsOverview = {
  available: boolean;
  error: string | null;
  enabled: boolean;
  configured: boolean;
  endpoint: string;
  queueName: string;
  queueExists: boolean;
  meta: MnsQueueMeta | null;
  note: string;
};

export type MnsQueueSummary = {
  queueName: string;
  activeMessages: number | null;
  inactiveMessages: number | null;
  delayMessages: number | null;
  retentionPeriod: number | null;
  maxMessageSize: number | null;
  createTime: number | null;
  lastModifyTime: number | null;
  queueUrl: string | null;
};

export type MnsMessage = {
  messageId: string | null;
  body: string | null;
  bodyTruncated: boolean;
  enqueueTime: number | null;
  nextVisibleTime: number | null;
  firstDequeueTime: number | null;
  dequeueCount: number | null;
  priority: number | null;
};

export type MnsSendResult = {
  messageId: string | null;
  queueName: string;
  sentAt: number;
};

export type MnsConfigItem = {
  key: string;
  value: string;
  source: string;
  applyMode: string;
  unit: string;
  sensitive: boolean;
  editable: boolean;
};

export type MnsConfigView = {
  available: boolean;
  error: string | null;
  enabled: boolean;
  configured: boolean;
  endpoint: string;
  queueName: string;
  items: MnsConfigItem[];
  note: string;
};

export const mnsApi = {
  overview: (queue?: string) =>
    request<MnsOverview>(`/api/admin/mns/overview${queue ? `?queue=${encodeURIComponent(queue)}` : ''}`),
  queues: (prefix = '', limit = 20) =>
    request<MnsQueueSummary[]>(
      `/api/admin/mns/queues?prefix=${encodeURIComponent(prefix)}&limit=${limit}`,
    ),
  messages: (queue?: string, limit = 10) =>
    request<MnsMessage[]>(
      `/api/admin/mns/messages?limit=${limit}${queue ? `&queue=${encodeURIComponent(queue)}` : ''}`,
    ),
  testSend: (payload: { queue?: string; body: string; delaySeconds?: number }) =>
    request<MnsSendResult>('/api/admin/mns/test-send', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  config: () => request<MnsConfigView>('/api/admin/mns/config'),
};
