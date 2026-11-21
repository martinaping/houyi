# admin-server

后羿（Houyi）管理端后端，独立 Spring Boot 服务，与主服务 `houyi` 零代码耦合，只共享 MySQL、Redis 与消息队列。

- 技术栈：Java 21、Spring Boot 3.2.11、JdbcTemplate、Spring Data Redis、RocketMQ Client 5.0.7
- 端口：`18081`
- 版本：`0.12.0`

## 构建与启动

```bash
export JAVA_HOME=/path/to/jdk-21
mvn -f admin-server/pom.xml package

DB_URL='jdbc:mysql://127.0.0.1:3306/houyi?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false' \
DB_USERNAME=houyi DB_PASSWORD=*** \
REDIS_HOST=127.0.0.1 REDIS_PORT=6379 REDIS_DATABASE=5 \
ADMIN_USERNAME=admin ADMIN_PASSWORD=*** ADMIN_JWT_SECRET='至少32位随机字符串' \
java -jar admin-server/target/admin-server.jar
```

`ADMIN_PASSWORD` 与 `ADMIN_JWT_SECRET` 未配置时服务会拒绝启动。

### 本地开发（H2 文件库）

无 MySQL 时可用 H2 文件库启动，与主服务本地开发共用同一份数据：

```bash
DB_URL='jdbc:h2:file:/Users/jc/.houyi-local/db/houyi;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE' \
DB_USERNAME=sa DB_PASSWORD='' REDIS_DATABASE=5 \
ADMIN_PASSWORD=admin123 ADMIN_JWT_SECRET='houyi-local-dev-jwt-secret-0123456789' \
java -jar admin-server/target/admin-server.jar
```

H2 开启 `AUTO_SERVER=TRUE` 后可与主服务同时连接；仅用于本地验证，生产环境请使用 MySQL。

## 配置项

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ADMIN_SERVER_PORT` | `18081` | HTTP 端口 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 本地 MySQL | 数据库连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DATABASE` | `127.0.0.1`/`6379`/空/`0` | Redis 连接，应与主服务一致 |
| `ADMIN_USERNAME` | `admin` | 管理员账号 |
| `ADMIN_PASSWORD` | 无 | 必填 |
| `ADMIN_JWT_SECRET` | 无 | 必填，至少 32 字符 |
| `ADMIN_COOKIE_SECURE` | `false` | HTTPS 部署时设为 `true` |
| `ADMIN_TRUST_FORWARDED_FOR` | `false` | 仅在可信反向代理之后才设为 `true`，否则客户端可伪造来源 IP |
| `ADMIN_CORS_ORIGINS` | 本地 5173 两个来源 | 逗号分隔，生产建议留空并由 Nginx 同源代理 |
| `ADMIN_RETRY_ENABLED` | `true` | 是否允许触发消息重试 |
| `REDIS_MQ_ENABLED` | `true` | 主服务发送链路使用 Redis Streams，管理端重试同样入 Redis 延迟队列；`false` 使用 RocketMQ |
| `REDIS_MQ_TOPIC` / `REDIS_MQ_RETRY_TOPIC` | `mq:topic:main` / `mq:topic:retry` | 与主服务保持一致 |
| `ROCKETMQ_ENDPOINT` / `ROCKETMQ_USERNAME` / `ROCKETMQ_PASSWORD` | 空 | 配置后重试改发 RocketMQ |
| `ROCKETMQ_RETRY_TOPIC` / `ROCKETMQ_TAG` | `wechat-archive-retry` / `default` | 重试主题与 Tag |
| `ROCKETMQ_NAMESRV_ADDR` | `127.0.0.1:9876` | 管理端查询消费进度/Topic/Broker 的 NameServer |
| `ROCKETMQ_CLUSTER_NAME` | `DefaultCluster` | 管理端展示的集群名称 |
| `ROCKETMQ_ADMIN_TIMEOUT_MS` | `5000` | 管理端访问 NameServer/Broker 的超时时间 |
| `ALIYUN_MNS_ENABLED` | `true` | 主服务是否启用 MNS 备份（管理端可在线修改，需重启） |

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/admin/auth/login` | 登录 |
| POST | `/api/admin/auth/refresh` | 刷新 access token |
| POST | `/api/admin/auth/logout` | 退出登录 |
| GET | `/api/admin/auth/me` | 当前用户 |
| GET | `/api/admin/overview` | 运行概览 |
| GET | `/api/admin/corps` | 企业列表 |
| POST | `/api/admin/corps` | 新增企业 |
| GET | `/api/admin/corps/{id}` | 企业详情（消息统计、拉取指标、Redis 可达性） |
| PUT | `/api/admin/corps/{id}` | 编辑企业 |
| PATCH | `/api/admin/corps/{id}/status` | 启用/停用企业 |
| PATCH | `/api/admin/corps/batch-status` | 批量启用/停用企业 |
| PUT | `/api/admin/corps/{id}/secret` | 轮换企业密钥 |
| POST | `/api/admin/corps/{id}/reveal` | 管理员密码二次校验后查看密钥 |
| GET | `/api/admin/messages` | 消息分页查询 |
| GET | `/api/admin/messages/export` | 消息 CSV 导出（按筛选条件，最多 5000 行） |
| GET | `/api/admin/messages/{id}` | 消息详情 |
| POST | `/api/admin/messages/{id}/retry` | 触发重试 |
| GET | `/api/admin/queue/overview` | 队列概览 |
| GET | `/api/admin/rocketmq/overview` | RocketMQ 概览（集群/Lag/DLQ/Broker） |
| GET | `/api/admin/rocketmq/groups` | 消费组清单与 Lag |
| GET | `/api/admin/rocketmq/groups/{group}` | 消费组详情 |
| GET | `/api/admin/rocketmq/topics` | Topic 清单 |
| GET | `/api/admin/rocketmq/topic` | Topic 详情与位点 |
| GET | `/api/admin/rocketmq/brokers` | Broker 运行状态 |
| GET | `/api/admin/rocketmq/dlq/topics` | 死信 Topic 与消息数 |
| GET | `/api/admin/rocketmq/dlq/messages` | 死信消息（只读） |
| POST | `/api/admin/rocketmq/dlq/resend` | 死信重投 |
| GET | `/api/admin/rocketmq/messages/recent` | 最近消息浏览（默认全部业务 Topic） |
| POST | `/api/admin/rocketmq/messages/query` | 按 MsgID/Key 查询消息 |
| POST | `/api/admin/rocketmq/messages/trace` | 消息轨迹 |
| GET | `/api/admin/rocketmq/retry` | 重试队列（delay_message） |
| GET | `/api/admin/rocketmq/retry/{id}` | 重试消息详情 |
| POST | `/api/admin/rocketmq/retry/{id}/requeue` | 重新投递 |
| POST | `/api/admin/rocketmq/retry/{id}/discard` | 丢弃重试消息 |
| POST | `/api/admin/rocketmq/test-send` | 发送测试消息 |
| GET | `/api/admin/rocketmq/config` | RocketMQ 生效配置 |
| GET | `/api/admin/redis/overview` | Redis 运行概览 |
| GET | `/api/admin/redis/streams` | Redis Streams 概览 |
| GET | `/api/admin/redis/streams/detail` | Stream 组/消费者/Pending/最近条目 |
| GET | `/api/admin/redis/keys` | SCAN Key 浏览 |
| POST | `/api/admin/redis/keys/delete` | 删除应用自有 Key（写审计） |
| GET | `/api/admin/redis/metrics` | 企业拉取书签与运行指标 |
| GET | `/api/admin/redis/config` | Redis 连接与 Redis MQ 配置 |
| GET | `/api/admin/mns/overview` | MNS 队列状态与属性 |
| GET | `/api/admin/mns/queues` | MNS 队列列表 |
| GET | `/api/admin/mns/messages` | MNS Peek 消息（只读） |
| POST | `/api/admin/mns/test-send` | 发送 MNS 测试消息（写审计） |
| GET | `/api/admin/mns/config` | MNS 生效配置 |
| GET | `/api/admin/settings` | 配置项列表（范围/单位/生效方式） |
| PUT | `/api/admin/settings` | 更新配置项 |
| POST | `/api/admin/settings/reset` | 按 key 恢复默认值 |
| GET | `/api/admin/system` | 系统信息（只读脱敏） |
| GET | `/api/admin/alerts/status` | 告警状态 |
| GET | `/api/admin/alerts/logs` | 告警记录 |
| POST | `/api/admin/alerts/test` | 发送测试告警 |
| GET | `/api/admin/audit-logs` | 审计日志（keyset 分页） |
| GET | `/api/admin/audit-logs/export` | 审计日志 CSV 导出 |
| GET | `/api/admin/health` | 健康巡检（DB/Redis/队列/告警/运行指标） |
| GET | `/api/admin/retention/preview` | 清理预览 |
| POST | `/api/admin/retention/execute` | 执行清理 |
| GET | `/actuator/health` | 健康检查 |

## 运行指标（Redis）

主服务写入、管理端健康巡检读取，均为“尽力而为”，Redis 异常不影响主流程：

| Key | 字段 | 说明 |
| --- | --- | --- |
| `houyi:metric:pull:<corpId>` | `lastStartAt` / `lastEndAt` / `lastDurationMs` / `lastFetched` / `totalFetched` / `lastError` / `lastErrorAt` | 企业拉取；无新消息时最多每 30 秒心跳写入一次 |
| `houyi:metric:push` | `success` / `failure` / `retry` / `lastPushAt` / `lastError` / `lastErrorAt` | MQ 推送与重试计数 |

## 自建表

启动时由 `AdminSchemaInitializer` 自动创建（`CREATE TABLE IF NOT EXISTS`）：

| 表 | 用途 |
| --- | --- |
| `admin_config` | 白名单配置项，默认值由 `SettingsCatalog` seed |
| `admin_audit_log` | 审计日志 |
| `admin_alert_log` | 告警发送记录 |
| `admin_runtime_state` | 运行时状态快照（企业拉取进度） |

## 运维开关

- 告警：系统设置页开启 `alert.enabled` 并填写 `alert.webhook-url`（企业微信机器人地址）。
- 清理：默认关闭，需开启 `retention.cleanup-enabled`，执行时输入 `DELETE` 确认；单次删除条数受 `retention.batch-size` 限制。可清理已推送消息、MD5 索引、审计日志与告警记录。
- 密钥查看：需再次输入管理员密码，失败会记录审计。

## 配置分组与生效方式

`SettingsCatalog` 为白名单（61 项），分为八组：

| 分组 | 内容 | 生效方式 |
| --- | --- | --- |
| `alert` | 开关、Webhook、冷却、静默时段、积压/失败/停滞阈值、RocketMQ Lag/DLQ 阈值 | 即时 |
| `retention` | 清理开关、各类保留天数、单批条数 | 即时 |
| `security` | 登录失败锁定次数/时长、审计开关、明细长度 | 即时 |
| `runtime` | 企业扫描间隔、拉取间隔、下载重试/超时、下载与 OSS 线程池 | 热更新（主服务 30 秒轮询） |
| `rocketmq` | gRPC Endpoint、NameServer、集群、超时、重试 Topic | 管理端即时；主服务需重启 |
| `oss` / `mns` / `redismq` | 对象存储、备份队列与 Redis MQ 参数 | 主服务重启 |

- `runtime.*` 写入后由主服务 `DynamicRuntimeConfig` 读取并应用，无需重启。
- 每项均带最小值/最大值/单位，超范围直接拒绝保存；支持按 key 恢复默认。

## 测试

```bash
# 单元测试
JAVA_HOME=/path/to/jdk-21 mvn -f admin-server/pom.xml test

# API 冒烟测试（需要服务已启动）
ADMIN_PASSWORD=*** ruby scripts/admin_api_test.rb
```

冒烟测试报告输出到 `tmp/admin-api-test-report.json` 与 `tmp/admin-api-test-report.md`。

## 注意事项

- 管理端直接读写主服务的数据表，请勿在管理端执行与主服务不兼容的 DDL。
- 重试操作有副作用：会向 Redis 延迟队列插入记录或向 RocketMQ 发送消息，生产环境请确认下游消费正常后再使用。
- 服务日志为 JSON 行格式，包含 `traceId`，不记录账号密码、企业 Secret 与消息正文。
