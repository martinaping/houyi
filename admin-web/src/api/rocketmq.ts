import { request } from './client';

export type RocketMqProbe = {
  state: string;
  configured: boolean;
  endpoint: string;
  latencyMs: number | null;
  checkedAt: number | null;
  message: string | null;
};

export type RocketMqGroupLag = {
  group: string;
  brokerAddr: string;
  diffTotal: number;
  consumeTps: number;
  lastTimestamp: number;
  note: string | null;
};

export type RocketMqGroupConfig = {
  consumeEnable: boolean;
  consumeFromMinEnable: boolean;
  consumeBroadcastEnable: boolean;
  consumeMessageOrderly: boolean;
  retryQueueNums: number;
  retryMaxTimes: number;
  consumeTimeoutMinute: number;
  whichBrokerWhenConsumeSlowly: number;
  brokerId: number;
};

export type RocketMqQueueOffset = {
  topic: string;
  brokerName: string;
  queueId: number;
  brokerOffset: number;
  consumerOffset: number;
  diff: number;
  lastTimestamp: number;
};

export type RocketMqConnection = {
  clientId: string;
  clientAddr: string;
  language: string;
  version: number;
  consumeType: string;
  messageModel: string;
  subscriptions: string[];
};

export type RocketMqGroupDetail = {
  summary: RocketMqGroupLag;
  config: RocketMqGroupConfig | null;
  queues: RocketMqQueueOffset[];
  connections: RocketMqConnection[];
  topics: string[];
};

export type RocketMqTopicSummary = {
  topic: string;
  system: boolean;
  dlq: boolean;
};

export type RocketMqTopicConfig = {
  readQueueNums: number;
  writeQueueNums: number;
  perm: number;
  order: boolean;
};

export type RocketMqTopicQueue = {
  brokerName: string;
  readQueueNums: number;
  writeQueueNums: number;
  perm: number;
};

export type RocketMqBrokerRoute = {
  cluster: string;
  brokerName: string;
  brokerId: number;
  addr: string;
};

export type RocketMqTopicDetail = {
  topic: string;
  system: boolean;
  config: RocketMqTopicConfig | null;
  queues: RocketMqTopicQueue[];
  consumerGroups: string[];
  brokers: RocketMqBrokerRoute[];
  offsets: RocketMqQueueOffset[];
};

export type RocketMqBroker = {
  cluster: string;
  brokerName: string;
  brokerId: number;
  addr: string;
  role: string;
  active: boolean;
  version: string;
  runtime: Record<string, string>;
};

export type RocketMqBrokerStatus = {
  brokers: RocketMqBroker[];
  probe: RocketMqProbe;
};

export type RocketMqOverview = {
  available: boolean;
  error: string | null;
  namesrvAddr: string;
  clusterName: string;
  refreshedAt: number;
  topicCount: number;
  groupCount: number;
  dlqTopicCount: number;
  totalLag: number;
  dlqMessageCount: number;
  topLagGroups: RocketMqGroupLag[];
  brokers: RocketMqBroker[];
  probe: RocketMqProbe;
};

export type RocketMqDlqTopic = {
  topic: string;
  consumerGroup: string;
  messageCount: number;
  lastUpdateTimestamp: number;
};

export type RocketMqDlqList = {
  topics: RocketMqDlqTopic[];
  totalMessages: number;
};

export type RocketMqMessage = {
  msgId: string;
  topic: string;
  tags: string | null;
  keys: string | null;
  queueId: number;
  queueOffset: number;
  bornTimestamp: number;
  storeTimestamp: number;
  reconsumeTimes: number;
  body: string;
  bodyTruncated: boolean;
  properties: Record<string, string>;
  targetTopic: string | null;
};

export type RocketMqMessageTrace = {
  consumerGroup: string;
  trackType: string;
  exceptionDesc: string | null;
};

export type RocketMqResendResult = {
  msgId: string;
  targetTopic: string;
  sentAt: number;
};

export type RocketMqTestSendResult = {
  msgId: string;
  topic: string;
  tag: string | null;
  key: string | null;
  sentAt: number;
};

export type RocketMqRetryMessage = {
  id: number;
  topic: string;
  messageKey: string | null;
  tag: string | null;
  shardingKey: string | null;
  deliverTime: number;
  status: number;
  retryCount: number;
  createAt: number;
  deliverAt: number | null;
  errorMsg: string | null;
  bodyPreview: string;
  bodyLength: number;
};

export type RocketMqRetryDetail = {
  id: number;
  topic: string;
  messageKey: string | null;
  tag: string | null;
  shardingKey: string | null;
  deliverTime: number;
  status: number;
  retryCount: number;
  createAt: number;
  deliverAt: number | null;
  errorMsg: string | null;
  body: string;
};

export type RocketMqRetryPage = {
  items: RocketMqRetryMessage[];
  total: number;
  offset: number;
  limit: number;
  available: boolean;
  error: string | null;
};

export type RocketMqConfigItem = {
  key: string;
  value: string;
  source: string;
  applyMode: string;
  unit: string;
  sensitive: boolean;
  editable: boolean;
};

export type RocketMqConfigView = {
  namesrvAddr: string;
  clusterName: string;
  timeoutMs: number;
  available: boolean;
  error: string | null;
  items: RocketMqConfigItem[];
  note: string;
};

function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

export const rocketMqApi = {
  overview: (refresh = false) =>
    request<RocketMqOverview>(`/api/admin/rocketmq/overview${query({ refresh })}`),
  groups: (params: {
    keyword?: string;
    includeInternal?: boolean;
    refresh?: boolean;
    limit?: number;
  } = {}) => request<RocketMqGroupLag[]>(`/api/admin/rocketmq/groups${query(params)}`),
  group: (group: string) =>
    request<RocketMqGroupDetail>(`/api/admin/rocketmq/groups/${encodeURIComponent(group)}`),
  topics: (params: { keyword?: string; includeSystem?: boolean; refresh?: boolean; limit?: number } = {}) =>
    request<RocketMqTopicSummary[]>(`/api/admin/rocketmq/topics${query(params)}`),
  topic: (topic: string) =>
    request<RocketMqTopicDetail>(`/api/admin/rocketmq/topic${query({ topic })}`),
  brokers: (refresh = false) =>
    request<RocketMqBrokerStatus>(`/api/admin/rocketmq/brokers${query({ refresh })}`),
  dlqTopics: (refresh = false) =>
    request<RocketMqDlqList>(`/api/admin/rocketmq/dlq/topics${query({ refresh })}`),
  dlqMessages: (topic: string, limit = 50) =>
    request<RocketMqMessage[]>(`/api/admin/rocketmq/dlq/messages${query({ topic, limit })}`),
  resendDlq: (payload: {
    topic: string;
    msgId: string;
    targetTopic?: string;
    tag?: string;
    key?: string;
  }) =>
    request<RocketMqResendResult>('/api/admin/rocketmq/dlq/resend', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  queryMessages: (payload: {
    topic: string;
    msgId?: string;
    key?: string;
    beginTimestamp?: number;
    endTimestamp?: number;
    max?: number;
  }) =>
    request<RocketMqMessage[]>('/api/admin/rocketmq/messages/query', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  trace: (payload: { topic: string; msgId: string }) =>
    request<{ tracks: RocketMqMessageTrace[] }>('/api/admin/rocketmq/messages/trace', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  retry: (params: {
    status?: number;
    keyword?: string;
    offset?: number;
    limit?: number;
  } = {}) => request<RocketMqRetryPage>(`/api/admin/rocketmq/retry${query(params)}`),
  retryDetail: (id: number) => request<RocketMqRetryDetail>(`/api/admin/rocketmq/retry/${id}`),
  requeueRetry: (id: number) =>
    request<RocketMqRetryDetail>(`/api/admin/rocketmq/retry/${id}/requeue`, { method: 'POST' }),
  discardRetry: (id: number, reason: string) =>
    request<RocketMqRetryDetail>(`/api/admin/rocketmq/retry/${id}/discard`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    }),
  testSend: (payload: { topic: string; tag?: string; key?: string; body: string }) =>
    request<RocketMqTestSendResult>('/api/admin/rocketmq/test-send', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  config: () => request<RocketMqConfigView>('/api/admin/rocketmq/config'),
};
