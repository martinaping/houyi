# 后羿（houyi）API 文档

> 版本：0.7.0
> 适用服务：`admin-server`（管理端，默认 18081）、`houyi`（主服务，默认 18080）
> 最后更新：2026-11-20

本文档覆盖系统对外提供的全部 HTTP 接口，包括管理端后台接口与主服务运维接口。

---

## 1. 总览

| 服务 | 默认基址 | 说明 |
| --- | --- | --- |
| 管理端 `admin-server` | `http://127.0.0.1:18081` | 后台管理接口，除登录/刷新外均需 Bearer Token |
| 主服务 `houyi` | `http://127.0.0.1:18080` | 拉取任务与运维接口，无鉴权，仅限内网访问 |

管理端接口统一前缀 `/api/admin`；主服务运维接口见第 13 节。

### 1.1 通用约定

**请求**

- `Content-Type: application/json; charset=UTF-8`（CSV 导出除外）。
- 时间字段均为 **Unix 毫秒时间戳**（`long`），未取到值时可能为 `null`。
- 布尔配置值以字符串 `"true"` / `"false"` 传输。

**响应头**

| 响应头 | 说明 |
| --- | --- |
| `X-Trace-Id` | 本次请求的链路 ID。客户端可主动传入（`^[A-Za-z0-9_-]{8,64}$`），否则由服务端生成 |
| `X-App-Version` | 服务端版本号，当前为 `0.7.0` |

**错误响应**（非 2xx）

```json
{
  "code": "CORP_NOT_FOUND",
  "message": "企业不存在：id=999",
  "details": [],
  "traceId": "3f6c1c9a8d7e4b2a9c0f1e2d3a4b5c6d"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | string | 业务错误码，见第 14 节 |
| `message` | string | 可直接展示的错误描述 |
| `details` | string[] | 校验失败的字段级明细，通常为空 |
| `traceId` | string | 与响应头 `X-Trace-Id` 一致，便于查日志 |

**游标分页**

消息与审计日志使用 keyset 分页：

- 请求参数 `cursor` 为上一页返回的 `nextCursor`，首次请求不传。
- 响应中 `nextCursor` 为 `null` 表示没有下一页。

---

## 2. 认证（`/api/admin/auth`）

管理端使用「Access Token（内存）+ Refresh Token（HttpOnly Cookie）」双令牌：

- 登录/刷新成功后在响应头下发 `Set-Cookie: admin_refresh=...`，属性为 `HttpOnly; SameSite=Strict; Path=/api/admin/auth`，`Secure` 由配置决定。
- 业务请求携带 `Authorization: Bearer <accessToken>`。
- Access Token 过期后调用 `POST /api/admin/auth/refresh` 换新；刷新失败返回 401 并清除 Cookie。

### 2.1 登录

`POST /api/admin/auth/login`

请求体：

```json
{ "username": "admin", "password": "admin123" }
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 管理员账号 |
| `password` | string | 是 | 管理员密码 |

响应 `200`：

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "user": { "username": "admin", "version": "0.7.0" }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accessToken` | string | 访问令牌 |
| `tokenType` | string | 固定 `Bearer` |
| `expiresIn` | long | Access Token 有效期（秒） |
| `user.username` | string | 当前账号 |
| `user.version` | string | 服务端版本 |

错误：

- `401 INVALID_CREDENTIALS`：账号或密码错误。
- `429 LOGIN_LOCKED`：同一 IP 连续失败达到阈值（`security.login-max-failures`），返回 `Retry-After` 秒数。
- `400 VALIDATION_FAILED`：用户名或密码为空。

登录成功/失败都会写入审计日志（`LOGIN_SUCCESS` / `LOGIN_FAILED`）。

### 2.2 刷新令牌

`POST /api/admin/auth/refresh`

无请求体，依赖 Cookie `admin_refresh`。响应同 2.1，并轮换下发新的 Refresh Cookie。

- 无 Cookie 或令牌非法：`401 UNAUTHORIZED`，同时清除 Cookie。

### 2.3 退出登录

`POST /api/admin/auth/logout`

响应 `204 No Content`，并清除 Refresh Cookie。

### 2.4 当前用户

`GET /api/admin/auth/me`

响应 `200`：

```json
{ "username": "admin", "version": "0.7.0" }
```

---

## 3. 企业管理（`/api/admin/corps`）

企业删除采用「停用」而非物理删除；`secret` 读取时仅返回掩码，`prikey` 只返回是否已配置。

### 3.1 企业列表

`GET /api/admin/corps`

| 查询参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 否 | 按企业名称或企业 ID 模糊匹配 |
| `status` | long | 否 | `1` 启用，`0` 停用 |

响应 `200`：`CorpView[]`（最多 500 条，按 `id` 升序）

```json
[
  {
    "id": 1,
    "corpId": "ww1234567890",
    "corpName": "示例企业",
    "limits": 1000,
    "timeout": 60,
    "status": 1,
    "updateAt": 1763000000000,
    "lastSeq": 123456,
    "secretMasked": "****a1b2",
    "hasPrikey": true
  }
]
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 主键 |
| `corpId` | string | 企业 ID |
| `corpName` | string | 企业名称 |
| `limits` | long | 单次拉取条数上限 |
| `timeout` | long | 拉取超时（秒） |
| `status` | long | `1` 启用，`0` 停用 |
| `updateAt` | long | 最后更新时间 |
| `lastSeq` | long \| null | 已拉取到的最大序号（优先取 Redis） |
| `secretMasked` | string | Secret 掩码，如 `****a1b2` |
| `hasPrikey` | boolean | 是否已配置私钥 |

### 3.2 企业详情

`GET /api/admin/corps/{id}`

响应 `200`：`CorpDetailView`，在列表字段基础上追加：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `messageTotal` | long | 该企业消息总数 |
| `messagePending` | long | 未推送消息数 |
| `messageFailed` | long | 未推送且下载失败的消息数 |
| `messageToday` | long | 当日（`dateNo`）消息数 |
| `pullLastAt` | long \| null | 最近一次拉取结束时间（来自 Redis 指标） |
| `pullTotalFetched` | long \| null | 累计拉取条数 |
| `pullLastError` | string \| null | 最近一次拉取错误 |
| `redisReachable` | boolean | Redis 指标是否可读 |

错误：`404 CORP_NOT_FOUND`。

### 3.3 新增企业

`POST /api/admin/corps`

请求体：

```json
{
  "corpId": "ww1234567890",
  "corpName": "示例企业",
  "secret": "message-secret",
  "prikey": "-----BEGIN PRIVATE KEY-----...",
  "limits": 1000,
  "timeout": 60,
  "enabled": true
}
```

| 字段 | 类型 | 必填 | 校验 |
| --- | --- | --- | --- |
| `corpId` | string | 是 | 长度 3~64，仅允许字母、数字、下划线与短横线 |
| `corpName` | string | 否 | 为空时取 `corpId` |
| `secret` | string | 是 | 长度 8~512 |
| `prikey` | string | 是 | 不能为空，否则无法拉取会话存档 |
| `limits` | long | 否 | 不能为负，默认 `0` |
| `timeout` | long | 否 | 不能为负，默认 `0` |
| `enabled` | boolean | 否 | 缺省为 `true` |

响应 `200`：`CorpView`。审计动作 `CORP_CREATE`。

错误：`CORP_ID_INVALID`、`CORP_SECRET_INVALID`、`CORP_PRIKEY_INVALID`、`CORP_PARAM_INVALID`、`CORP_DUPLICATE`。

### 3.4 编辑企业

`PUT /api/admin/corps/{id}`

请求体（字段均可选，缺省保持原值；`secret`/`prikey` 传空字符串表示不修改）：

```json
{
  "corpName": "示例企业（改名）",
  "secret": "",
  "prikey": "",
  "limits": 2000,
  "timeout": 120,
  "enabled": false
}
```

响应 `200`：`CorpView`。审计动作 `CORP_UPDATE`。

错误：`404 CORP_NOT_FOUND`、`CORP_NAME_INVALID`、`CORP_SECRET_INVALID`、`CORP_PRIKEY_INVALID`、`CORP_PARAM_INVALID`。

### 3.5 单个企业启停

`PATCH /api/admin/corps/{id}/status`

```json
{ "enabled": false }
```

响应 `200`：`CorpView`。审计动作 `CORP_STATUS`。

### 3.6 批量启停

`PATCH /api/admin/corps/batch-status`

```json
{ "ids": [1, 2, 3], "enabled": true }
```

响应 `200`：

```json
{ "updated": 3 }
```

审计动作 `CORP_BATCH_STATUS`。

错误：`CORP_IDS_EMPTY`（未选择企业）、`CORP_STATUS_INVALID`（`enabled` 为空）。

### 3.7 轮换 Secret

`PUT /api/admin/corps/{id}/secret`

```json
{ "secret": "new-message-secret" }
```

响应 `200`：`CorpView`。审计动作 `CORP_SECRET_ROTATE`（审计明细中 Secret 固定为 `****`）。

### 3.8 查看 Secret 明文

`POST /api/admin/corps/{id}/reveal`

需要重新输入当前登录密码做二次校验：

```json
{ "password": "admin123" }
```

响应 `200`：

```json
{ "secret": "message-secret" }
```

审计动作 `CORP_SECRET_REVEAL`；密码错误时返回 `401 INVALID_CREDENTIALS` 并记录 `CORP_SECRET_REVEAL_FAILED`。

---

## 4. 消息管理（`/api/admin/messages`）

### 4.1 消息列表

`GET /api/admin/messages`

| 查询参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `corpId` | string | 否 | 精确匹配企业 ID |
| `msgId` | string | 否 | 精确匹配消息 ID |
| `msgType` | string | 否 | 精确匹配消息类型 |
| `status` | string | 否 | `PUSHED` / `PENDING` / `DOWNLOAD_FAILED` / `DOWNLOADED` |
| `startAt` | long | 否 | 归档时间（`create_at`）下界（含） |
| `endAt` | long | 否 | 归档时间（`create_at`）上界（含） |
| `cursor` | long | 否 | 上一页 `nextCursor` |
| `limit` | int | 否 | 每页条数，默认 20，最大 100 |

`status` 语义：

| 值 | 条件 |
| --- | --- |
| `PUSHED` | `push_at > 0` |
| `PENDING` | `push_at` 为空或 `<= 0` |
| `DOWNLOAD_FAILED` | `down_fail_at > 0` |
| `DOWNLOADED` | `down_finish_at > 0` |

响应 `200`：

```json
{
  "items": [
    {
      "id": 1024,
      "corpId": "ww1234567890",
      "msgId": "1234567890_1763000000",
      "seq": 123456,
      "msgType": "image",
      "dateNo": 20261119,
      "downFinishAt": 1763000100000,
      "downFailAt": 0,
      "pushAt": 1763000200000,
      "createAt": 1763000000000,
      "hasFile": true
    }
  ],
  "nextCursor": null
}
```

错误：`400 VALIDATION_ERROR`（`status` 不在白名单）。

### 4.2 导出消息 CSV

`GET /api/admin/messages/export`

参数同 4.1（忽略 `cursor`、`limit`），最多导出 **5000** 行。

响应：`text/csv;charset=UTF-8`，`Content-Disposition: attachment; filename=messages-yyyyMMdd-HHmmss.csv`。

列顺序：`ID, 企业ID, 消息ID, 消息类型, 序号, 归档时间, 下载完成时间, 下载失败时间, 推送时间, 是否含文件`；时间为 `yyyy-MM-dd HH:mm:ss`（Asia/Shanghai），空值为空字符串。审计动作 `MESSAGE_EXPORT`。

### 4.3 消息详情

`GET /api/admin/messages/{id}`

响应 `200`：

```json
{
  "id": 1024,
  "corpId": "ww1234567890",
  "msgId": "1234567890_1763000000",
  "seq": 123456,
  "content": "{\"msgid\":\"...\"}",
  "sdkFileId": "sdk-file-id",
  "dateNo": 20261119,
  "msgType": "image",
  "filePath": "/data/file/xxx",
  "downFinishAt": 1763000100000,
  "downFailAt": 0,
  "md5Sum": "9f2c...",
  "retryTimeout": 0,
  "ossPath": "oss://bucket/key",
  "createAt": 1763000000000,
  "pushAt": 1763000200000
}
```

错误：`404 MESSAGE_NOT_FOUND`。

### 4.4 手工重试单条消息

`POST /api/admin/messages/{id}/retry`

无请求体。仅对「含媒体文件、未推送、未归档」的消息生效；按服务端队列模式投递：

- Redis 模式：写入 `delay_message`（`mode=REDIS_DELAY_MESSAGE`）。
- RocketMQ 模式：直接发送到重试 Topic（`mode=ROCKETMQ`）。

响应 `200`：

```json
{ "mode": "REDIS_DELAY_MESSAGE", "messageKey": "1234567890_1763000000", "deliverTime": 1763000300000 }
```

错误：

| HTTP | code | 说明 |
| --- | --- | --- |
| 404 | `MESSAGE_NOT_FOUND` | 消息不存在 |
| 400 | `RETRY_NOT_APPLICABLE` | 消息不含媒体文件 |
| 409 | `ALREADY_PUSHED` | 已推送完成 |
| 409 | `ALREADY_ARCHIVED` | 已归档完成 |
| 409 | `RETRY_ALREADY_QUEUED` | 已在重试队列中 |
| 503 | `RETRY_DISABLED` | 重试功能关闭 |
| 503 | `RETRY_UNAVAILABLE` | 未启用 Redis 队列且未配置 RocketMQ |
| 502 | `MQ_SEND_FAILED` | RocketMQ 投递失败 |

审计动作 `MESSAGE_RETRY`。

---

## 5. 队列概览（`/api/admin/queue`）

### 5.1 队列运行指标

`GET /api/admin/queue/overview`

响应 `200`：

```json
{
  "redisMqEnabled": true,
  "mainStream": { "topic": "houyi:stream:msg", "length": 12 },
  "retryStream": { "topic": "houyi:stream:retry", "length": 0 },
  "delayMessages": [ { "status": 0, "count": 3 }, { "status": 1, "count": 10 } ],
  "rocketMqConfigured": true,
  "rocketMqRetryTopic": "wechat-archive-retry",
  "rocketMqState": "UP",
  "rocketMqEndpoint": "http://127.0.0.1:8081",
  "rocketMqLatencyMs": 5,
  "rocketMqCheckedAt": 1763000000000,
  "rocketMqError": "Endpoint 可达，客户端创建成功"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `redisMqEnabled` | boolean | 是否启用 Redis 队列模式 |
| `mainStream.topic/length` | string / long | 主 Stream 名称与长度，Redis 不可用时 `length` 为 `null` |
| `retryStream.topic/length` | string / long | 重试 Stream 名称与长度 |
| `delayMessages[]` | array | 延迟消息按状态分组计数（`status`：0 待投递，1 已投递） |
| `rocketMqConfigured` | boolean | 是否配置了 RocketMQ（后台设置优先，留空回退环境变量） |
| `rocketMqRetryTopic` | string \| null | RocketMQ 重试 Topic |
| `rocketMqState` | string | 探测状态：`NOT_CONFIGURED` / `UP` / `DOWN` |
| `rocketMqEndpoint` | string | 当前生效的 RocketMQ Endpoint |
| `rocketMqLatencyMs` | long \| null | TCP 连通性探测耗时（毫秒），未配置时为 `null` |
| `rocketMqCheckedAt` | long \| null | 最近一次探测时间 |
| `rocketMqError` | string \| null | 探测返回的说明信息，`DOWN` 时为错误原因 |

---

## 6. 首页概览（`/api/admin/overview`）

### 6.1 概览数据

`GET /api/admin/overview`

响应 `200`：

```json
{
  "corps": { "total": 12, "enabled": 10 },
  "queue": { "redisMqEnabled": true, "mainStream": { "topic": "houyi:stream:msg", "length": 12 },
             "retryStream": { "topic": "houyi:stream:retry", "length": 0 },
             "delayMessages": [], "rocketMqConfigured": true,
             "rocketMqRetryTopic": "wechat-archive-retry", "rocketMqState": "UP",
             "rocketMqEndpoint": "http://127.0.0.1:8081", "rocketMqLatencyMs": 5,
             "rocketMqCheckedAt": 1763000000000, "rocketMqError": "Endpoint 可达，客户端创建成功" },
  "recentFailures": [],
  "version": "0.7.0",
  "serverTime": 1763000000000
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `corps.total` / `corps.enabled` | long | 企业总数 / 启用数 |
| `queue` | object | 同 5.1 `QueueOverview` |
| `recentFailures` | MessageSummary[] | 最近 5 条下载失败消息（`status=DOWNLOAD_FAILED`） |
| `version` | string | 管理端版本 |
| `serverTime` | long | 服务端当前时间 |

---

## 7. 健康巡检（`/api/admin/health`）

### 7.1 巡检报告

`GET /api/admin/health`

只做只读探测，不改变任何状态。`status` 为 `UP`（数据库与 Redis 均正常）或 `DEGRADED`。

响应 `200`：

```json
{
  "status": "UP",
  "version": "0.7.0",
  "uptimeSeconds": 3600,
  "checkedAt": 1763000000000,
  "database": { "ok": true, "latencyMs": 1, "message": "ok" },
  "redis": { "ok": true, "latencyMs": 1, "message": "ok" },
  "queue": { "redisMqEnabled": true, "mainStreamLength": 12, "retryStreamLength": 0,
             "delayPending": 3, "rocketMqConfigured": true, "rocketMqState": "UP",
             "rocketMqLatencyMs": 5, "rocketMqCheckedAt": 1763000000000,
             "rocketMqError": "Endpoint 可达，客户端创建成功" },
  "alert": { "enabled": true, "webhookConfigured": true, "lastAlertAt": 1762990000000 },
  "retention": { "cleanupEnabled": true, "lastCleanupAt": 1762900000000 },
  "corps": [
    { "id": 1, "corpId": "ww1234567890", "corpName": "示例企业", "enabled": true,
      "lastEndAt": 1762999990000, "durationMs": 820, "lastFetched": 30,
      "totalFetched": 12000, "lastError": null, "lastErrorAt": null }
  ],
  "push": { "success": 11000, "failure": 12, "retry": 3, "lastPushAt": 1762999995000,
            "lastError": null, "lastErrorAt": null }
}
```

| 字段 | 说明 |
| --- | --- |
| `database` / `redis` | `Check(ok, latencyMs, message)` |
| `queue` | Stream 长度、待投递延迟消息数；Redis 异常时长度类字段为 `null`；`rocketMqState/LatencyMs/CheckedAt/Error` 为 RocketMQ 探测结果，不可用不影响整体 `status` |
| `alert.lastAlertAt` | 最近一次告警记录时间 |
| `retention.lastCleanupAt` | 最近一次执行清理的时间（取审计 `RETENTION_EXECUTE`） |
| `corps[]` | 主服务写入 Redis 的企业拉取指标：`lastEndAt`、`durationMs`、`lastFetched`、`totalFetched`、`lastError`、`lastErrorAt` |
| `push` | 主服务推送指标：成功 / 失败 / 重试次数、最后推送时间与最后错误；无数据时各字段为 `null` |

---

## 8. 系统信息（`/api/admin/system`）

### 8.1 只读系统信息

`GET /api/admin/system`

敏感字段已脱敏（连接串中不包含密码）。

响应 `200`：

```json
{
  "version": "0.7.0",
  "uptimeSeconds": 3600,
  "javaVersion": "21.0.2",
  "jdbcUrl": "jdbc:h2:file:/Users/jc/.houyi-local/db/houyi",
  "databaseReachable": true,
  "redisHost": "127.0.0.1",
  "redisPort": 6379,
  "redisDatabase": 5,
  "redisReachable": true,
  "redisMqEnabled": true,
  "rocketMqConfigured": true,
  "corpTotal": 12,
  "corpEnabled": 10,
  "settingCount": 31,
  "runtimeSettings": [
    { "key": "runtime.message-interval-ms", "value": "2000", "unit": "毫秒", "applyMode": "HOT" }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `runtimeSettings[]` | 应用于主服务的运行参数，`applyMode` 见 9.2 |

---

## 9. 系统设置（`/api/admin/settings`）

只允许读写白名单内的 Key（见 9.3），其它 Key 一律拒绝，避免管理端变成任意写库入口。

### 9.1 读取设置项

`GET /api/admin/settings`

响应 `200`：`SettingView[]`（51 项，按分组顺序返回）

```json
[
  {
    "key": "alert.enabled",
    "group": "alert",
    "type": "BOOLEAN",
    "value": "false",
    "defaultValue": "false",
    "description": "启用告警通知（定时检查队列积压、下载失败与企业拉取停滞）",
    "sensitive": false,
    "updatedBy": "admin",
    "updatedAt": 1763000000000,
    "min": 0,
    "max": 0,
    "unit": "",
    "applyMode": "INSTANT",
    "editable": true,
    "source": "DB"
  }
]
```

| 字段 | 说明 |
| --- | --- |
| `group` | `alert` / `oss` / `mns` / `redismq` / `rocketmq` / `security` / `runtime` / `retention` |
| `type` | `BOOLEAN` / `NUMBER` / `STRING` |
| `sensitive` | 为 `true` 时 `value` 仅返回掩码（如 `****a1b2`），数据库中以密文存储 |
| `min` / `max` | `NUMBER` 类型的取值范围，其它类型为 `0` |
| `unit` | 数值单位（分钟/秒/毫秒/条/天/次/个/行/字符） |
| `applyMode` | 生效方式，见 9.2 |
| `editable` | 是否允许在后台修改；`false` 时仅展示，不可更新或恢复默认；来源为环境变量时也为 `false` |
| `source` | 当前生效值来源：`ENV`（环境变量）/ `DB`（管理端配置）/ `DEFAULT`（默认值），见 9.7 |

### 9.2 生效方式

| applyMode | 含义 |
| --- | --- |
| `INSTANT` | 管理端保存后立即生效（告警、保留、安全、RocketMQ） |
| `HOT` | 主服务每 30 秒轮询 `admin_config` 生效，保存后通常半分钟内生效 |
| `RESTART` | 保存后需人工重启主服务（OSS/MNS/Redis 队列，以及只读的 `rocketmq.retry-topic`） |

### 9.3 修改设置项

`PUT /api/admin/settings`

```json
{ "key": "alert.enabled", "value": "true" }
```

响应 `200`：更新后的 `SettingView`。审计动作 `SETTING_UPDATE`。

校验规则：

- `BOOLEAN`：仅接受 `"true"` / `"false"`（大小写不敏感，统一存为小写）。
- `NUMBER`：必须为整数，且在 `min` ~ `max` 之间。
- `STRING`：长度不超过 **2000** 个字符。
- `alert.quiet-hours`：不为空时必须是 `HH:mm-HH:mm` 格式（如 `22:00-08:00`）。
- `alert.webhook-url`：不为空时必须以 `http://` 或 `https://` 开头。
- 敏感项（如 `alert.webhook-url`、`oss.access-key`）不接受以 `****` 开头的值，避免把掩码当成真实值写入。
- 敏感项写入前使用 `HOUYI_CONFIG_KEY` 派生的 AES-256-GCM 密钥加密，库中存储 `enc:v1:<base64>`；未设置该环境变量时写入返回 `SETTING_KEY_MISSING`。
- 敏感项读取仅返回掩码 `****` + 后 4 位（原文长度 ≤ 4 时为 `****`）。
- 只读项（`editable = false`，如 `rocketmq.retry-topic`）不接受更新或恢复默认。
- 由环境变量提供的项（`source = ENV`）不接受更新或恢复默认，须修改环境变量后重启。

错误：

| HTTP | code | 说明 |
| --- | --- | --- |
| 400 | `SETTING_NOT_FOUND` | Key 不在白名单 |
| 400 | `SETTING_INVALID` | 取值不符合类型、范围或格式 |
| 400 | `SETTING_READONLY` | 配置项为只读，不支持后台修改 |
| 400 | `SETTING_ENV_OVERRIDDEN` | 该配置由环境变量提供，后台不可修改 |
| 400 | `SETTING_KEY_MISSING` | 缺少 `HOUYI_CONFIG_KEY`，敏感配置不可写入 |

### 9.4 恢复默认值

`POST /api/admin/settings/reset`

```json
{ "key": "alert.cooldown-minutes" }
```

响应 `200`：恢复后的 `SettingView`。审计动作 `SETTING_RESET`。

错误：`400 SETTING_NOT_FOUND` / `400 SETTING_READONLY`。

### 9.5 配置项清单（51 项）

**告警（alert）**

| Key | 名称 | 类型 | 默认值 | 范围/单位 |
| --- | --- | --- | --- | --- |
| `alert.enabled` | 启用告警 | BOOLEAN | `false` | - |
| `alert.webhook-url` | Webhook 地址（敏感） | STRING | 空 | - |
| `alert.cooldown-minutes` | 告警冷却 | NUMBER | `30` | 1~1440 分钟 |
| `alert.quiet-hours` | 静默时段 | STRING | 空 | 如 `22:00-08:00` |
| `alert.queue-enabled` | 启用队列积压告警 | BOOLEAN | `true` | - |
| `alert.download-enabled` | 启用下载失败告警 | BOOLEAN | `true` | - |
| `alert.corp-stall-enabled` | 启用企业停滞告警 | BOOLEAN | `true` | - |
| `alert.queue-backlog-threshold` | 延迟消息积压阈值 | NUMBER | `1000` | 1~10000000 条 |
| `alert.download-failure-threshold` | 下载失败阈值 | NUMBER | `50` | 1~10000000 条 |
| `alert.corp-stall-minutes` | 企业停滞判定 | NUMBER | `60` | 5~10080 分钟 |

**数据保留（retention）**

| Key | 名称 | 类型 | 默认值 | 范围/单位 |
| --- | --- | --- | --- | --- |
| `retention.cleanup-enabled` | 启用数据清理 | BOOLEAN | `false` | - |
| `retention.original-msg-days` | 消息保留天数 | NUMBER | `365` | 30~3650 天 |
| `retention.md5-index-days` | MD5 索引保留天数 | NUMBER | `365` | 30~3650 天 |
| `retention.audit-retention-days` | 审计日志保留天数 | NUMBER | `180` | 30~3650 天 |
| `retention.alert-log-days` | 告警记录保留天数 | NUMBER | `90` | 7~3650 天 |
| `retention.batch-size` | 单次清理上限 | NUMBER | `1000` | 10~10000 行 |

**安全（security）**

| Key | 名称 | 类型 | 默认值 | 范围/单位 |
| --- | --- | --- | --- | --- |
| `security.login-max-failures` | 登录失败上限 | NUMBER | `5` | 1~100 次 |
| `security.login-lock-minutes` | 锁定时长 | NUMBER | `5` | 1~1440 分钟 |
| `security.audit-enabled` | 启用审计日志 | BOOLEAN | `true` | - |
| `security.audit-detail-max-length` | 审计详情最大长度 | NUMBER | `2000` | 200~10000 字符 |

**运行参数（runtime，热更新）**

| Key | 名称 | 类型 | 默认值 | 范围/单位 |
| --- | --- | --- | --- | --- |
| `runtime.corp-refresh-seconds` | 企业配置扫描间隔 | NUMBER | `30` | 5~3600 秒 |
| `runtime.message-interval-ms` | 消息拉取间隔 | NUMBER | `200` | 20~60000 毫秒 |
| `runtime.download-max-retry` | 下载最大重试次数 | NUMBER | `16` | 1~100 次 |
| `runtime.big-file-timeout-ms` | 大文件超时 | NUMBER | `300000` | 10000~3600000 毫秒 |
| `runtime.download-core-size` | 下载线程核心数 | NUMBER | `8` | 1~200 个 |
| `runtime.download-max-size` | 下载线程最大数 | NUMBER | `100` | 1~500 个 |
| `runtime.oss-core-size` | OSS 上传核心线程数 | NUMBER | `4` | 1~200 个 |
| `runtime.oss-max-size` | OSS 上传最大线程数 | NUMBER | `60` | 1~500 个 |

**RocketMQ（rocketmq，管理端重试投递）**

后台配置仅影响管理端手动重试投递；留空时回退到环境变量 `ROCKETMQ_ENDPOINT` / `ROCKETMQ_USERNAME` / `ROCKETMQ_PASSWORD`。主应用重试通道仍优先 Redis，不受此处配置影响。

| Key | 名称 | 类型 | 默认值 | 范围/单位 |
| --- | --- | --- | --- | --- |
| `rocketmq.endpoint` | gRPC Proxy 地址 | STRING | 空 | 如 `http://127.0.0.1:8081`，即时生效 |
| `rocketmq.username` | 访问用户名 | STRING | 空 | 留空回退环境变量，即时生效 |
| `rocketmq.password` | 访问密码（敏感） | STRING | 空 | 留空回退环境变量，即时生效 |
| `rocketmq.retry-topic` | 重试 Topic（只读） | STRING | 空 | 须与主应用 `ROCKETMQ_RETRY_TOPIC` 一致，重启生效 |

**阿里云 OSS（oss，重启生效）**

对应环境变量 `ALIYUN_OSS_BUCKET` / `ALIYUN_OSS_ENDPOINT` / `ALIYUN_OSS_ACCESS_KEY` / `ALIYUN_OSS_ACCESS_SECRET`。

| Key | 名称 | 类型 | 默认值 | 范围/单位 |
| --- | --- | --- | --- | --- |
| `oss.bucket` | Bucket | STRING | `wechat-monitoring` | - |
| `oss.endpoint` | Endpoint | STRING | `oss-cn-shanghai.aliyuncs.com` | - |
| `oss.access-key` | AccessKey ID（敏感） | STRING | 空 | 密文存储 |
| `oss.access-secret` | AccessKey Secret（敏感） | STRING | 空 | 密文存储 |

**阿里云 MNS 备份队列（mns，重启生效）**

主消息发送成功后异步备份一份到 MNS；未配置时自动跳过，不影响主链路。

对应环境变量 `ALIYUN_MNS_ENDPOINT` / `ALIYUN_MNS_QUEUE_NAME` / `ALIYUN_MNS_ACCESS_KEY` / `ALIYUN_MNS_ACCESS_SECRET`（开关为 `ALIYUN_MNS_ENABLED`，仅环境变量）。

| Key | 名称 | 类型 | 默认值 | 范围/单位 |
| --- | --- | --- | --- | --- |
| `mns.endpoint` | Endpoint | STRING | 空 | 如 `http://1234567890.mns.cn-shanghai.aliyuncs.com/` |
| `mns.queue-name` | 队列名称 | STRING | `houyi-msgs` | - |
| `mns.access-key` | AccessKey ID（敏感） | STRING | 空 | 密文存储 |
| `mns.access-secret` | AccessKey Secret（敏感） | STRING | 空 | 密文存储 |

**Redis 消息队列（redismq，重启生效）**

对应环境变量 `REDIS_MQ_*`；Redis 连接本身（地址/密码/DB）不在白名单中，只能通过环境变量配置。

| Key | 名称 | 类型 | 默认值 | 范围/单位 |
| --- | --- | --- | --- | --- |
| `redismq.enabled` | 启用 Redis 队列 | BOOLEAN | `true` | - |
| `redismq.topic` | 主队列 Stream Key | STRING | `mq:topic:main` | - |
| `redismq.retry-topic` | 重试队列 Stream Key | STRING | `mq:topic:retry` | - |
| `redismq.delay-queue-key` | 延迟队列 Key | STRING | `mq:delay:queue` | - |
| `redismq.consumer-group` | 消费组 | STRING | `houyi-consumer-group` | - |
| `redismq.retry-consumer-group` | 重试消费组 | STRING | `houyi-retry-consumer-group` | - |
| `redismq.tag` | 消息 Tag | STRING | `default` | - |
| `redismq.retry-delay-ms` | 重试延迟 | NUMBER | `30000` | 1000~3600000 毫秒 |
| `redismq.batch-size` | 批处理大小 | NUMBER | `10` | 1~1000 条 |
| `redismq.poll-interval-ms` | 轮询间隔 | NUMBER | `1000` | 100~60000 毫秒 |
| `redismq.delay-scan-interval-ms` | 延迟扫描间隔 | NUMBER | `1000` | 100~60000 毫秒 |
| `redismq.enable-message-backup` | 消息备份 | BOOLEAN | `true` | - |

### 9.6 RocketMQ 连接测试

`POST /api/admin/settings/rocketmq/test`

请求体可选；字段为空或密码为掩码回传（`****` 开头）时回退到当前生效配置：

```json
{ "endpoint": "http://127.0.0.1:8081", "username": "rocketmq", "password": "****abcd" }
```

响应 `200`：`ProbeResult`。审计动作 `ROCKETMQ_TEST`，`target` 为实际测试的 endpoint，`detail` 包含 `state` 与 `message`。

```json
{
  "state": "UP",
  "configured": true,
  "endpoint": "http://127.0.0.1:8081",
  "latencyMs": 5,
  "checkedAt": 1763000000000,
  "message": "Endpoint 可达，客户端创建成功"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `state` | string | `NOT_CONFIGURED` / `UP` / `DOWN` |
| `configured` | boolean | 实际测试的 endpoint 是否非空 |
| `endpoint` | string | 实际测试的 endpoint |
| `latencyMs` | long \| null | TCP 连通性探测耗时，未配置时为 `null` |
| `checkedAt` | long | 本次测试时间 |
| `message` | string | 成功说明或错误原因（最多 300 字符） |

说明：测试只做 TCP 连通性探测并创建/关闭一次生产者，不会向业务 Topic 发送消息。

### 9.7 配置优先级与加密

配置取值遵循三层优先级：

| 优先级 | 来源 | 说明 |
| --- | --- | --- |
| 1 | 环境变量 | 对应环境变量有值时，后台只读展示，修改后台返回 `SETTING_ENV_OVERRIDDEN` |
| 2 | 管理端配置（`admin_config`） | 敏感项以 `enc:v1:` 密文存储，密钥由 `HOUYI_CONFIG_KEY` 派生 |
| 3 | 默认值 | 白名单中声明的默认值 |

主应用启动时由 `AdminConfigEnvironmentPostProcessor` 读取 `admin_config`，把非环境变量来源的值注入 Spring Environment（优先级低于系统环境变量、高于 `application.yml`），因此：

- 生产环境可继续只使用环境变量，管理端仅作只读展示；
- 测试或轻量部署可直接在后台填写 OSS/Redis 队列参数，重启主应用后生效；
- 已有敏感配置需要轮换主密钥时，修改 `HOUYI_CONFIG_KEY` 后原密文将无法解密，须在后台重新填写，因此变更前应先备份旧密钥。

> `HOUYI_CONFIG_KEY` 至少 16 个字符，建议使用随机生成的 32 字节 Base64 字符串；该密钥不得与数据库备份存放在同一位置。

---

## 10. 告警（`/api/admin/alerts`）

告警由管理端定时任务产生，触发条件受 `alert.*` 配置控制，发送记录落库后可通过本节接口查询。

### 10.1 告警状态

`GET /api/admin/alerts/status`

响应 `200`：

```json
{ "enabled": true, "webhookConfigured": true }
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `enabled` | boolean | 对应 `alert.enabled` |
| `webhookConfigured` | boolean | 是否已配置非空 `alert.webhook-url` |

### 10.2 最近告警记录

`GET /api/admin/alerts/logs?limit=50`

| 查询参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `limit` | int | 否 | 返回条数，默认 50，取值会被收敛到 1~200 |

响应 `200`：`AlertLogView[]`（按 `id` 倒序）

```json
[
  {
    "id": 12,
    "alertKey": "queue_backlog",
    "severity": "WARNING",
    "title": "延迟消息积压",
    "message": "待投递延迟消息 1200 条，超过阈值 1000",
    "metricValue": 1200,
    "thresholdValue": 1000,
    "delivered": true,
    "error": null,
    "createdAt": 1763000000000
  }
]
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `alertKey` | string | 告警类型标识 |
| `severity` | string | 级别，如 `WARNING` / `CRITICAL` |
| `metricValue` | long | 触发时的实际指标值 |
| `thresholdValue` | long | 配置的阈值 |
| `delivered` | boolean | Webhook 是否投递成功 |
| `error` | string \| null | 投递失败原因 |

### 10.3 发送测试告警

`POST /api/admin/alerts/test`

无请求体。使用当前 `alert.webhook-url` 发送一条测试消息。审计动作 `ALERT_TEST`。

响应 `200`：

```json
{ "delivered": true, "message": null }
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `delivered` | boolean | 是否投递成功 |
| `message` | string \| null | 投递失败时的错误信息 |

错误：`400 ALERT_NOT_CONFIGURED`（Webhook 未配置）。

---

## 11. 数据保留（`/api/admin/retention`）

清理范围由 `retention.*` 配置决定，执行前必须先预览确认。

### 11.1 清理预览

`GET /api/admin/retention/preview`

只统计候选行，不做删除。

响应 `200`：

```json
{
  "cleanupEnabled": true,
  "batchSize": 1000,
  "originalMsgDays": 365,
  "md5IndexDays": 365,
  "auditLogDays": 180,
  "alertLogDays": 90,
  "categories": [
    { "category": "ORIGINAL_MSG", "candidates": 1200, "earliest": 1700000000000 },
    { "category": "MD5_INDEX", "candidates": 0, "earliest": null },
    { "category": "AUDIT_LOG", "candidates": 30, "earliest": 1710000000000 },
    { "category": "ALERT_LOG", "candidates": 12, "earliest": 1715000000000 }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `cleanupEnabled` | 对应 `retention.cleanup-enabled`，为 `false` 时不允许执行 |
| `batchSize` | 单次清理上限（`retention.batch-size`） |
| `originalMsgDays` / `md5IndexDays` / `auditLogDays` / `alertLogDays` | 各类数据的保留天数 |
| `categories[].category` | `ORIGINAL_MSG` / `MD5_INDEX` / `AUDIT_LOG` / `ALERT_LOG` |
| `categories[].candidates` | 超过保留期、可被清理的行数 |
| `categories[].earliest` | 候选行中最早的时间戳，无候选时为 `null` |

### 11.2 执行清理

`POST /api/admin/retention/execute`

```json
{ "category": "ORIGINAL_MSG", "confirm": "DELETE" }
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `category` | string | 是 | `ORIGINAL_MSG` / `MD5_INDEX` / `AUDIT_LOG` / `ALERT_LOG` |
| `confirm` | string | 是 | 必须为 `DELETE`（大小写不敏感） |

响应 `200`：

```json
{ "category": "ORIGINAL_MSG", "deleted": 1000, "remaining": 200, "executedAt": 1763000000000 }
```

单次最多删除 `retention.batch-size` 行，`remaining` 为执行后仍超过保留期的行数，可重复调用直至为 0。审计动作 `RETENTION_EXECUTE`。

错误：

| HTTP | code | 说明 |
| --- | --- | --- |
| 400 | `RETENTION_DISABLED` | `retention.cleanup-enabled` 为 `false` |
| 400 | `RETENTION_CONFIRM_REQUIRED` | `confirm` 不等于 `DELETE` |
| 400 | `RETENTION_INVALID_CATEGORY` | `category` 不在白名单 |
| 400 | `VALIDATION_FAILED` | `category` / `confirm` 为空 |

---

## 12. 审计日志（`/api/admin/audit-logs`）

所有写操作与敏感读取都会记录审计日志（`security.audit-enabled=false` 时停止记录）。

### 12.1 查询审计日志

`GET /api/admin/audit-logs`

| 查询参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `action` | string | 否 | 精确匹配动作，如 `CORP_UPDATE` |
| `operator` | string | 否 | 精确匹配操作人 |
| `cursor` | long | 否 | 上一页 `nextCursor` |
| `limit` | int | 否 | 每页条数，默认 50，最大 200 |
| `startAt` | long | 否 | 时间下界（含） |
| `endAt` | long | 否 | 时间上界（含） |

响应 `200`：

```json
{
  "items": [
    {
      "id": 88,
      "operator": "admin",
      "action": "SETTING_UPDATE",
      "target": "alert.enabled",
      "detail": "true",
      "clientIp": "127.0.0.1",
      "traceId": "3f6c1c9a8d7e4b2a9c0f1e2d3a4b5c6d",
      "createdAt": 1763000000000
    }
  ],
  "nextCursor": null
}
```

| 字段 | 说明 |
| --- | --- |
| `action` | 动作标识，如 `LOGIN_SUCCESS` / `CORP_CREATE` / `MESSAGE_RETRY` / `SETTING_UPDATE` |
| `target` | 操作对象（企业 ID、消息 ID、配置 Key 等） |
| `detail` | 变更摘要，敏感值统一记录为 `****` |
| `clientIp` | 客户端 IP |
| `traceId` | 请求链路 ID |

### 12.2 导出审计日志 CSV

`GET /api/admin/audit-logs/export`

参数同 12.1（忽略 `cursor`、`limit`），最多导出 **5000** 行。

响应：`text/csv;charset=UTF-8`，`Content-Disposition: attachment; filename=audit-logs-yyyyMMdd-HHmmss.csv`。

列顺序：`ID, 时间, 操作人, 动作, 对象, 详情, 客户端IP, TraceID`。审计动作 `AUDIT_EXPORT`。

---

## 13. 主服务 API（默认 18080）

主服务接口面向内部运维与联调，**无鉴权、无统一错误结构**，必须通过内网或网关限制来源。除特别说明外均使用 `GET`。

### 13.1 存活检查

`GET /status`

响应 `200`，响应体为整数：

```json
200
```

### 13.2 触发企业同步

`GET /sync`

执行一次企业配置同步（`SyncCorpService.sync()`）。

响应 `200`，响应体为字符串 `"ok"`。

### 13.3 拉取企业信息

`GET /api/open/pull/corp?corpId={corpId}`

调用企业微信接口拉取企业信息并写入 `corplist`（同时更新 `corpname`、`secret`、`prikey`，并将 `status` 置为 `1`）。

响应 `200`：

- 成功：返回 `CorpInfo` 对象（字段见 13.7，**包含 `secret` 与 `prikey` 明文**）。
- 失败：返回错误信息字符串（非 JSON 错误结构）。

### 13.4 触发企业拉取任务

`GET /api/open/trigger/corp?corpId={corpId}`

在事件总线上触发该企业的消息拉取。

响应 `200`：

- 成功：响应体为传入的 `corpId` 字符串。
- 失败：返回错误信息字符串。

### 13.5 下载单条消息

`GET /api/open/single/msg?corpId={corpId}&msgId={msgId}&seq={seq}`

按 `corpId + msgId + seq` 查找已归档消息，并提交媒体文件下载任务。

响应 `200`：

- 成功：返回消息内容 JSON 对象（解析自 `original_msg.content`）。
- 企业不存在：返回 `corpInfo not exists for corpId:{corpId}` 字符串。
- 消息不存在：返回 `msg not exists:{msgId}` 字符串。

### 13.6 企业运行状态

`GET /api/open/status`

响应 `200`：

```json
{
  "succ": [ { "id": 1, "corpid": "ww1234567890", "corpname": "示例企业", "status": 1 } ],
  "fail": []
}
```

| 字段 | 说明 |
| --- | --- |
| `succ` | 最近一次拉取成功的 `CorpInfo` 列表 |
| `fail` | 最近一次拉取失败的 `CorpInfo` 列表 |

### 13.7 CorpInfo 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 主键 |
| `corpid` | string | 企业 ID |
| `secret` | string | 会话存档 Secret（**明文**） |
| `corpname` | string | 企业名称 |
| `prikey` | string | 会话存档私钥（**明文**） |
| `limits` | long | 单次拉取条数上限 |
| `timeout` | long | 拉取超时（秒） |
| `status` | long | `1` 启用，`0` 停用 |
| `update` | long | 最后更新时间（毫秒时间戳） |
| `lastSeq` | long | 已拉取到的最大序号 |

> 注意：主服务返回的 `CorpInfo` 包含 `secret` 与 `prikey` 明文，切勿将该接口暴露到公网。

---

## 14. 日志中心（`/api/admin/logs`）

只读接口，用于在线查看与下载服务日志。来源与文件均按服务端扫描白名单解析，不接受任意路径。

### 14.1 日志来源与文件清单

```
GET /api/admin/logs/sources
```

响应：

```json
{
  "sources": [
    {
      "key": "admin-server",
      "name": "管理端服务",
      "description": "admin-server 运行日志",
      "available": true,
      "files": [
        { "name": "admin-server.log", "path": "/tmp/admin-server.log", "size": 128394, "modifiedAt": 1795074108094 }
      ]
    }
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `key` | string | 来源标识，用于 `tail` / `download` 的 `source` 参数 |
| `available` | boolean | 该来源当前是否存在可读文件 |
| `files[].path` | string | 服务端绝对路径，作为文件唯一标识 |
| `files[].size` / `modifiedAt` | long | 字节数与最后修改时间（毫秒时间戳） |

### 14.2 读取日志尾部

```
GET /api/admin/logs/tail?source=admin-server&file=/tmp/admin-server.log&lines=200&level=ERROR&keyword=timeout
```

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `source` | 否 | 来源 key，缺省取第一个可用来源 |
| `file` | 否 | 文件绝对路径（须属于该来源），缺省取最新文件 |
| `lines` | 否 | 返回行数，默认 200，最大 2000 |
| `level` | 否 | 级别过滤：`ERROR` / `WARN` / `INFO` / `DEBUG` |
| `keyword` | 否 | 关键字过滤（大小写不敏感） |

响应为 `LogTailView`，单次读取上限 8 MB：

```json
{
  "sourceKey": "admin-server",
  "sourceName": "管理端服务",
  "file": "admin-server.log",
  "path": "/tmp/admin-server.log",
  "size": 128394,
  "modifiedAt": 1795074108094,
  "truncated": false,
  "matched": 12,
  "lines": ["{\"ts\":\"2026-11-20T10:14:12.642+08:00\",\"level\":\"INFO\",...}"]
}
```

### 14.3 下载日志文件

```
GET /api/admin/logs/download?source=admin-server&file=/tmp/admin-server.log
```

以 `text/plain; charset=UTF-8` 附件返回完整文件，文件名见 `Content-Disposition`。仅允许下载扫描白名单内的文件，每次下载写审计动作 `LOG_DOWNLOAD`。

---

## 15. 可观测性指标（`/api/admin/monitor`）

### 15.1 指标汇总

```
GET /api/admin/monitor/overview
```

管理端抓取主应用 `MAIN_APP_BASE_URL` + `MAIN_APP_METRICS_PATH`（默认 `http://127.0.0.1:8080/houyi-eye/prometheus`，连接超时 2 秒、请求超时 5 秒），解析并汇总后返回：

```json
{
  "available": true,
  "error": null,
  "source": "http://127.0.0.1:8080/houyi-eye/prometheus",
  "scrapedAt": 1795074108094,
  "jvm": {
    "heapUsedBytes": 71489536, "heapMaxBytes": 4294967296, "nonHeapUsedBytes": 175456256,
    "threadsLive": 71, "threadsPeak": 71, "classesLoaded": 23168,
    "uptimeSeconds": 1279, "processCpuUsage": 0.001, "systemCpuUsage": 0.302,
    "systemCpuCount": 12, "gcPauseCount": 2, "gcPauseSeconds": 0.425
  },
  "http": {
    "total": 10, "serverErrors": 0, "clientErrors": 0,
    "totalSeconds": 1.22, "maxSeconds": 0.246,
    "byUri": [
      { "method": "GET", "uri": "/houyi-eye/prometheus", "count": 6, "errors": 0, "avgSeconds": 0.075, "maxSeconds": 0.09 }
    ]
  },
  "pool": { "active": 0, "idle": 10, "max": 10, "pending": 0, "timeoutTotal": 0 },
  "executors": [
    { "name": "downloadExecutor", "active": 0, "poolSize": 8, "queued": 0, "completed": 120 }
  ],
  "customMetrics": [
    { "name": "dayu_timediff_ms", "description": "处理耗时", "labels": "type=push", "value": 12.0 }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `available` | 是否成功抓取；失败时 `false`，`error` 为原因，其余指标字段为 `null` / 空数组 |
| `jvm.processCpuUsage` / `systemCpuUsage` | CPU 使用率，0~1 小数，前端换算为百分比 |
| `http.byUri` | 按 `method + uri` 聚合，按请求数倒序取前 12 条 |
| `customMetrics` | `dayu_` 前缀的业务指标，最多 200 条，`labels` 为 `k=v, k=v` 形式 |
| `pool` | HikariCP 连接池：活跃 / 空闲 / 上限 / 等待 / 超时累计 |
| `executors` | 线程池执行器：活跃线程、池大小、排队任务、已完成任务 |

> admin-server 自身也暴露 `/actuator/prometheus`（不在 `/api/admin` 鉴权范围内），外部 Prometheus 可分别抓取主应用 `/houyi-eye/prometheus` 与本服务 `/actuator/prometheus`。

---

## 16. 错误码总表

| HTTP | code | 说明 | 出现位置 |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | 未登录或令牌失效 | 所有需鉴权接口 |
| 401 | `INVALID_CREDENTIALS` | 账号或密码错误 / 二次校验密码错误 | 登录、查看 Secret |
| 429 | `LOGIN_LOCKED` | 登录失败次数超限，响应含 `Retry-After` | 登录 |
| 400 | `VALIDATION_FAILED` | 必填参数为空或请求体校验失败 | 登录、保留清理 |
| 400 | `VALIDATION_ERROR` | 参数取值不合法（如消息 `status` 白名单外） | 消息列表 |
| 400 | `CORP_ID_INVALID` | `corpId` 格式不合法 | 新增企业 |
| 400 | `CORP_NAME_INVALID` | `corpName` 不合法 | 编辑企业 |
| 400 | `CORP_SECRET_INVALID` | `secret` 长度或格式不合法 | 新增/编辑/轮换 Secret |
| 400 | `CORP_PRIKEY_INVALID` | `prikey` 为空或不合法 | 新增/编辑企业 |
| 400 | `CORP_PARAM_INVALID` | `limits` / `timeout` 等参数不合法 | 新增/编辑企业 |
| 400 | `CORP_IDS_EMPTY` | 批量启停未选择企业 | 批量启停 |
| 400 | `CORP_STATUS_INVALID` | 启停参数 `enabled` 为空 | 单个/批量启停 |
| 409 | `CORP_DUPLICATE` | 企业 ID 已存在 | 新增企业 |
| 404 | `CORP_NOT_FOUND` | 企业不存在 | 企业详情/编辑/重试 |
| 404 | `MESSAGE_NOT_FOUND` | 消息不存在 | 消息详情/重试 |
| 400 | `RETRY_NOT_APPLICABLE` | 消息不含媒体文件，无需重试 | 消息重试 |
| 409 | `ALREADY_PUSHED` | 消息已推送完成 | 消息重试 |
| 409 | `ALREADY_ARCHIVED` | 媒体文件已归档完成 | 消息重试 |
| 409 | `RETRY_ALREADY_QUEUED` | 该消息已在重试队列中 | 消息重试 |
| 503 | `RETRY_DISABLED` | 重试功能关闭 | 消息重试 |
| 503 | `RETRY_UNAVAILABLE` | 未启用 Redis 队列且未配置 RocketMQ | 消息重试 |
| 502 | `MQ_SEND_FAILED` | RocketMQ 投递失败 | 消息重试 |
| 400 | `SETTING_NOT_FOUND` | 配置 Key 不在白名单 | 修改/重置设置 |
| 400 | `SETTING_INVALID` | 配置值不符合类型、范围或格式 | 修改设置 |
| 400 | `ALERT_NOT_CONFIGURED` | 未配置 Webhook，无法发送测试告警 | 告警测试 |
| 400 | `RETENTION_DISABLED` | 数据清理未启用 | 执行清理 |
| 400 | `RETENTION_CONFIRM_REQUIRED` | `confirm` 不等于 `DELETE` | 执行清理 |
| 400 | `RETENTION_INVALID_CATEGORY` | 清理类别不在白名单 | 执行清理 |
| 400 | `PARAM_INVALID` | 路径或查询参数类型不合法（如 `id=abc`） | 全局 |
| 400 | `PARAM_MISSING` | 缺少必需的查询参数 | 全局 |
| 400 | `BODY_INVALID` | 请求体缺失或 JSON 格式不正确 | 全局 |
| 405 | `METHOD_NOT_ALLOWED` | 接口不支持该 HTTP 方法 | 全局 |
| 404 | `NOT_FOUND` | 请求的接口不存在 | 全局 |
| 401 | `UNAUTHORIZED` | 未携带或携带了非法/过期的 access token | 全局（除登录/刷新） |
| 500 | `INTERNAL_ERROR` | 未预期的服务端错误 | 全局兜底 |
