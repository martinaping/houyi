export type RoleBrief = {
  code: string;
  name: string;
};

export type UserView = {
  username: string;
  displayName: string;
  builtin: boolean;
  roles: RoleBrief[];
  permissions: string[];
  version: string;
};

export type LoginResponse = {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserView;
};

export type ApiErrorBody = {
  code: string;
  message: string;
  details: string[];
  traceId: string;
};

export type CorpView = {
  id: number;
  corpId: string;
  corpName: string;
  limits: number;
  timeout: number;
  status: number;
  updateAt: number;
  lastSeq: number | null;
  secretMasked: string;
  hasPrikey: boolean;
};

export type CorpStats = {
  total: number;
  enabled: number;
};

export type MessageStatus = 'PUSHED' | 'PENDING' | 'DOWNLOAD_FAILED' | 'DOWNLOADED';

export type MessageSummary = {
  id: number;
  corpId: string;
  msgId: string;
  seq: number;
  msgType: string;
  dateNo: number;
  downFinishAt: number;
  downFailAt: number;
  pushAt: number | null;
  createAt: number;
  hasFile: boolean;
};

export type MessageDetail = MessageSummary & {
  content: string | null;
  sdkFileId: string | null;
  filePath: string | null;
  md5Sum: string | null;
  retryTimeout: number;
  ossPath: string | null;
};

export type MessagePage = {
  items: MessageSummary[];
  nextCursor: number | null;
};

export type RetryResult = {
  mode: string;
  messageKey: string;
  deliverTime: number;
};

export type StreamStats = {
  topic: string;
  length: number | null;
};

export type DelayStatusCount = {
  status: number;
  count: number;
};

export type RocketMqState = 'NOT_CONFIGURED' | 'UP' | 'DOWN';

export type QueueOverview = {
  redisMqEnabled: boolean;
  mainStream: StreamStats;
  retryStream: StreamStats;
  delayMessages: DelayStatusCount[];
  rocketMqConfigured: boolean;
  rocketMqRetryTopic: string;
  rocketMqState: RocketMqState;
  rocketMqEndpoint: string;
  rocketMqLatencyMs: number | null;
  rocketMqCheckedAt: number | null;
  rocketMqError: string | null;
};

export type OverviewView = {
  corps: CorpStats;
  queue: QueueOverview;
  recentFailures: MessageSummary[];
  version: string;
  serverTime: number;
};

export type SettingType = 'BOOLEAN' | 'NUMBER' | 'STRING';

export type SettingApplyMode = 'INSTANT' | 'HOT' | 'RESTART';

export type SettingView = {
  key: string;
  group: string;
  type: SettingType;
  value: string;
  defaultValue: string;
  description: string;
  sensitive: boolean;
  updatedBy: string | null;
  updatedAt: number | null;
  min: number;
  max: number;
  unit: string;
  applyMode: SettingApplyMode;
  editable: boolean;
  source: 'ENV' | 'DB' | 'DEFAULT';
};

export type RocketMqTestResult = {
  state: RocketMqState;
  configured: boolean;
  endpoint: string;
  latencyMs: number | null;
  checkedAt: number | null;
  message: string | null;
};

export type CorpDetailView = {
  id: number;
  corpId: string;
  corpName: string;
  limits: number;
  timeout: number;
  status: number;
  updateAt: number;
  lastSeq: number | null;
  secretMasked: string;
  hasPrikey: boolean;
  messageTotal: number;
  messagePending: number;
  messageFailed: number;
  messageToday: number;
  pullLastAt: number | null;
  pullTotalFetched: number | null;
  pullLastError: string | null;
  redisReachable: boolean;
};

export type CorpCreateRequest = {
  corpId: string;
  corpName: string;
  secret: string;
  prikey: string;
  limits?: number;
  timeout?: number;
  enabled?: boolean;
};

export type CorpUpdateRequest = {
  corpName?: string;
  secret?: string;
  prikey?: string;
  limits?: number;
  timeout?: number;
  enabled?: boolean;
};

export type CorpBatchStatusRequest = {
  ids: number[];
  enabled: boolean;
};

export type CorpBatchStatusResult = {
  updated: number;
};

export type RuntimeSettingView = {
  key: string;
  value: string;
  unit: string;
  applyMode: SettingApplyMode;
};

export type SystemInfoView = {
  version: string;
  uptimeSeconds: number;
  javaVersion: string;
  jdbcUrl: string;
  databaseReachable: boolean;
  redisHost: string;
  redisPort: number;
  redisDatabase: number;
  redisReachable: boolean;
  redisMqEnabled: boolean;
  rocketMqConfigured: boolean;
  corpTotal: number;
  corpEnabled: number;
  settingCount: number;
  runtimeSettings: RuntimeSettingView[];
};

export type AuditLogView = {
  id: number;
  operator: string;
  action: string;
  target: string | null;
  detail: string | null;
  clientIp: string | null;
  traceId: string | null;
  createdAt: number;
};

export type AuditLogPage = {
  items: AuditLogView[];
  nextCursor: number | null;
};

export type RetentionCategoryPreview = {
  category: string;
  candidates: number;
  earliest: number | null;
};

export type RetentionPreview = {
  cleanupEnabled: boolean;
  batchSize: number;
  originalMsgDays: number;
  md5IndexDays: number;
  auditLogDays: number;
  alertLogDays: number;
  categories: RetentionCategoryPreview[];
};

export type CleanupResult = {
  category: string;
  deleted: number;
  remaining: number;
  executedAt: number;
};

export type AlertStatus = {
  enabled: boolean;
  webhookConfigured: boolean;
};

export type AlertTestResult = {
  delivered: boolean;
  message: string | null;
};

export type HealthCheck = {
  ok: boolean;
  latencyMs: number;
  message: string | null;
};

export type HealthQueueStats = {
  redisMqEnabled: boolean;
  mainStreamLength: number | null;
  retryStreamLength: number | null;
  delayPending: number | null;
  rocketMqConfigured: boolean;
  rocketMqState: RocketMqState;
  rocketMqLatencyMs: number | null;
  rocketMqCheckedAt: number | null;
  rocketMqError: string | null;
};

export type HealthAlertStats = {
  enabled: boolean;
  webhookConfigured: boolean;
  lastAlertAt: number | null;
};

export type HealthRetentionStats = {
  cleanupEnabled: boolean;
  lastCleanupAt: number | null;
};

export type CorpPullMetric = {
  id: number;
  corpId: string;
  corpName: string;
  enabled: boolean;
  lastEndAt: number | null;
  durationMs: number | null;
  lastFetched: number | null;
  totalFetched: number | null;
  lastError: string | null;
  lastErrorAt: number | null;
};

export type PushMetric = {
  success: number | null;
  failure: number | null;
  retry: number | null;
  lastPushAt: number | null;
  lastError: string | null;
  lastErrorAt: number | null;
};

export type HealthReport = {
  status: string;
  version: string;
  uptimeSeconds: number;
  checkedAt: number;
  database: HealthCheck;
  redis: HealthCheck;
  queue: HealthQueueStats;
  alert: HealthAlertStats;
  retention: HealthRetentionStats;
  corps: CorpPullMetric[];
  push: PushMetric;
};

export type AlertLogView = {
  id: number;
  alertKey: string;
  severity: string;
  title: string;
  message: string;
  metricValue: number;
  thresholdValue: number;
  delivered: boolean;
  error: string | null;
  createdAt: number;
};

export type LogFileView = {
  name: string;
  path: string;
  size: number;
  modifiedAt: number;
};

export type LogSourceView = {
  key: string;
  name: string;
  description: string;
  available: boolean;
  files: LogFileView[];
};

export type LogSourcesView = {
  sources: LogSourceView[];
};

export type LogTailView = {
  sourceKey: string;
  sourceName: string;
  file: string;
  path: string;
  size: number;
  modifiedAt: number;
  truncated: boolean;
  matched: number;
  lines: string[];
};

export type MonitorJvm = {
  heapUsedBytes: number | null;
  heapMaxBytes: number | null;
  nonHeapUsedBytes: number | null;
  threadsLive: number | null;
  threadsPeak: number | null;
  classesLoaded: number | null;
  uptimeSeconds: number | null;
  processCpuUsage: number | null;
  systemCpuUsage: number | null;
  systemCpuCount: number | null;
  gcPauseCount: number | null;
  gcPauseSeconds: number | null;
};

export type MonitorHttpUri = {
  method: string;
  uri: string;
  count: number;
  errors: number;
  avgSeconds: number;
  maxSeconds: number;
};

export type MonitorHttp = {
  total: number;
  serverErrors: number;
  clientErrors: number;
  totalSeconds: number;
  maxSeconds: number;
  byUri: MonitorHttpUri[];
};

export type MonitorPool = {
  active: number | null;
  idle: number | null;
  max: number | null;
  pending: number | null;
  timeoutTotal: number | null;
};

export type MonitorExecutor = {
  name: string;
  active: number | null;
  poolSize: number | null;
  queued: number | null;
  completed: number | null;
};

export type MonitorCustomMetric = {
  name: string;
  description: string | null;
  labels: string;
  value: number;
};

export type MonitorOverview = {
  available: boolean;
  error: string | null;
  source: string;
  scrapedAt: number;
  jvm: MonitorJvm | null;
  http: MonitorHttp | null;
  pool: MonitorPool | null;
  executors: MonitorExecutor[];
  customMetrics: MonitorCustomMetric[];
};
