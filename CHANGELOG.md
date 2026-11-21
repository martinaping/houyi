# 更新日志

## v0.11.0（2026-11-21）

### 新增

- 新增「Redis」管理页：运行概览（内存/客户端/QPS/Keyspace/INFO）、Streams/消费组/消费者/Pending/最近条目、SCAN Key 浏览与受限删除、企业拉取书签与运行指标、Redis MQ 生效配置。
- 新增「阿里云 MNS」管理页：队列状态/属性、队列列表、只读 Peek 消息、测试发送、备份开关与生效配置。
- 新增设置项 `mns.enabled`（主服务重启生效），并接入主服务启动配置注入。
- 新增审计动作：`REDIS_KEY_DELETE`、`MNS_TEST_SEND`。

### 安全

- Redis Key 浏览使用 SCAN，禁止 KEYS/FLUSH；删除仅允许 `houyi:`、`alert-sent:`、`corp-stall:` 前缀，`mq:` Streams 与 `last_seq_` 书签受保护。
- Redis Stream 只读查看，不提交 Offset、不 ACK、不删除消息。
- MNS 提供只读 Peek（不改变可见性）和受限大小的测试发送；删除消息/清空队列不在面板开放。

### 说明

- MNS 凭据优先读环境变量，其次读取管理端 `mns.*` 配置（敏感项加密存储）。
- 当前主服务发送链路仍以 RocketMQ 为准（RedisMqProducer 未接入 MessageProducerAdapter）；Redis MQ 参数主要影响重试/延迟处理器与管理端重试入队。

## v0.10.0（2026-11-21）

### 新增

- RocketMQ → 消息查询默认展示“最近消息”：无需填写 Key/MsgID，自动扫描业务 Topic 并展示最近 100 条。
- 支持「全部业务 Topic」或指定 Topic，按时间范围、关键字（MsgID/Key/Tag/消息体）和条数筛选。
- 新增 `GET /api/admin/rocketmq/messages/recent`：临时 Pull Consumer 扫描队列，不提交 Offset、不消费/删除原消息；单次最多 200 条、最多 20 个 Topic。
- 保留「精确查询」模式：按 MsgID 或 Key + Topic + 时间范围精确查询，并支持消息轨迹。
- 最近消息结果展示扫描 Topic 数、耗时、截断与队列读取错误提示。

### 说明

- 全部 Topic 浏览为有界扫描，多 Topic 时按 Topic 分配条数；更早消息请使用时间范围或精确查询。

## v0.9.0（2026-11-21）

### 新增

- 新增「RocketMQ」管理页：概览、消费组 Lag/队列进度/在线连接、Topic 拓扑、Broker/Proxy 运行时指标。
- 新增死信队列管理：DLQ Topic 与消息数、消息体/属性查看、指定目标 Topic 重投。
- 新增消息查询与轨迹：按 MsgID 或 Key + Topic + 时间范围查询，查看消息属性与消费轨迹。
- 新增 Redis 重试队列可视化：`delay_message` 状态/重试次数/投递时间/错误查看，支持立即重投与标记丢弃（状态 3，不删除记录）。
- 新增 RocketMQ 有效配置页与测试发送：展示配置来源/生效方式、主服务重启提示；测试消息最大 64 KB，禁止系统 Topic。
- 新增 RocketMQ 告警：消费组 Lag 阈值、DLQ 总数阈值接入现有 Webhook/冷却/静默时段。
- 新增设置项：`rocketmq.namesrv-addr`、`rocketmq.cluster-name`、`rocketmq.admin-timeout-ms`，以及 5 个 RocketMQ 告警配置。
- 新增审计动作：`ROCKETMQ_DLQ_RESEND`、`ROCKETMQ_MESSAGE_QUERY`、`ROCKETMQ_MESSAGE_TRACE`、`ROCKETMQ_TEST_SEND`、`RETRY_QUEUE_REQUEUE`、`RETRY_QUEUE_DISCARD`。

### 优化

- 消费组/Topic 列表默认隐藏 RocketMQ 内置组与系统 Topic，支持显式显示。
- 管理端引入 `rocketmq-tools`，通过 NameServer 读取消费进度、Topic、Broker 元数据；概览类数据缓存 15 秒。
- 设置项增至 60 项，分组扩展为 8 组。

### 安全

- 消息查询、轨迹、死信重投、测试发送、重试队列操作全部写入审计，审计不记录消息体。
- 测试发送禁止 `%DLQ%`、`RMQ_SYS_*`、`SCHEDULE_TOPIC_*` 等系统 Topic，并限制消息大小。
- DLQ 读取使用临时 Pull Consumer，不提交 Offset、不删除原消息；重投只发送新消息。

## v0.8.0（2026-11-20）

### 安全

- 移除 `src/main/resources/wework-corps.yml` 中已提交的真实企业 Secret，改为 `${WEWORK_CORPS_0_SECRET:}` 环境变量；启动同步在 secret 为空时保留数据库已有值，不会覆盖成空串。**该 Secret 仍存在于 Git 历史中，请立即在企业微信后台轮换。**
- 主服务 `CorpInfo` 的 `secret` 与 `prikey` 增加 Jackson 忽略与日志脱敏，`/api/open/status`、`/api/open/pull/corp` 不再输出企业密钥与私钥。
- 移除未被引用且硬编码数据库地址/账号密码的 `DataSource` 死代码。
- 主服务 Actuator 从 `include: "*"` 收紧为 `health,info,metrics,prometheus`，`health` 不再展示组件详情，避免 `env`、`heapdump`、`loggers` 等端点被未授权访问。
- 单条补拉接口不再以 WARN 级别记录完整消息内容，改为 DEBUG 级别记录 `corpId/msgId/seq`。
- 主服务新增可选共享令牌 `HOUYI_INTERNAL_TOKEN`：配置后 `/api/open/**` 与 `/sync` 需携带 `X-Internal-Token`，未配置时行为不变。
- 退出登录接口不再要求 access token，access token 过期后也能清除 HttpOnly refresh Cookie，避免“退出后刷新又自动登录”。
- 登录失败跟踪达到容量上限时不再清空整张表（防止大量来源重置其他 IP 的锁定），改为清理过期/空闲记录。
- 审计日志 `operator/action/target/client_ip/trace_id` 按数据库列宽截断，避免超长输入导致审计写入失败。
- CSV 公式注入防护覆盖前导空白（如 `"  =1+1"`）。
- 管理端统一增加 `X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`、`Permissions-Policy` 响应头，`/api/admin/**` 增加 `Cache-Control: no-store`。

### 修复

- 设置页未保存修改的站内跳转确认框不再重复弹出。
- 消息列表行支持 Enter/空格键打开详情；已归档（存在 OSS 路径）的消息不再显示重试按钮。
- 企业详情抽屉增加加载态；概览页最近失败列表补充空态文案；登录页恢复会话期间显示加载态，避免闪现登录表单。
- 退出登录与会话失效时清空 React Query 缓存，避免上一个会话的敏感数据残留在内存中。
- CSV/日志下载延迟释放 Blob URL，降低部分浏览器下载被中断的概率。
- 错误提示附带服务端字段校验明细，便于定位参数问题。
- 审计日志动作筛选改为下拉选择（展示中文动作名，实际按动作码筛选）。
- 监控指标页统计卡片改为响应式栅格，小屏不再挤压。

### 文档

- `API.md` 更新主服务接口不再返回 `secret`/`prikey`；`README.md`、`ARCHITECTURE.md` 同步 Actuator 暴露范围。

## v0.7.0（2026-11-20）

### 新增

- 数据清理新增「告警记录」（`ALERT_LOG`）类别，保留天数由 `retention.alert-log-days` 控制（默认 90 天，范围 7~3650 天）。
- 设置页支持拦截站内路由跳转：存在未保存修改时切换菜单或返回首页会弹出确认，避免修改被静默丢弃（路由迁移为数据路由 + `useBlocker`）。

### 优化

- 审计日志的动作码展示为中文（如 `CORP_CREATE` 显示为「新增企业」），行内悬停仍可查看原始动作码；未识别的动作码原样显示。
- 数据清理页的数据类别同步改为中文展示（已推送消息 / MD5 索引 / 审计日志 / 告警记录）。

## v0.6.0（2026-11-20）

### 修复

- 安全：非法 JWT（签名段 Base64 错误）此前会抛 500，现在统一返回 401；鉴权过滤器增加运行时异常兜底。
- 安全：默认不再信任 `X-Forwarded-For`，新增 `ADMIN_TRUST_FORWARDED_FOR`（默认 `false`），防止伪造该头绕过登录失败锁定；审计日志 IP 采用同一策略。
- 安全：关闭 `security.audit-enabled` 的配置变更会先强制写入审计记录，避免关开关动作本身丢失留痕。
- 接口健壮性：路径变量类型错误、请求体缺失/畸形、缺少必需参数、不支持的请求方法统一返回 400/405，不再出现 500。
- 告警：Webhook 投递增加 3 秒连接 / 5 秒读取超时，避免远端挂起长期占用定时线程。
- 数据清理：删除语句复检筛选条件并加事务，避免选中后记录状态变化导致误删或半删。
- 企业新增：并发重复时由唯一索引兜底返回 409，而非 500；列表单次上限由 200 提升到 1000，达到上限记录警告日志。
- RocketMQ 探活：缓存过期时先返回旧结果并在后台刷新，避免 `/overview`、`/health` 请求线程被网络探测阻塞。

### 优化

- 前端 401 仅在业务码为 `UNAUTHORIZED`（会话失效）时静默刷新；查看企业密钥时密码错误不再触发登出。
- 设置页刷新数据时保留其他分组未保存的修改；数字项空值改为必填校验；告警状态读取失败显示明确提示。
- 企业列表筛选条件变化后清空批量勾选，避免误操作已隐藏企业。
- 日志中心默认关闭自动刷新，行数输入改为失焦或回车后生效，避免打断阅读。
- 消息、审计表格增加横向滚动，消息详情抽屉增加加载态。
- 引入 dayjs 中文 locale；新增 404 页面与全局渲染兜底页（ErrorBoundary）。
- 补充日志中心、参数校验、登录锁定、设置只读等错误码的中英文文案；API 文档版本角标改用运行时版本。

## v0.5.2（2026-11-20）

### 变更

- 品牌名国际化：管理端标题中文显示「后羿 管理后台」，英文显示「houyi Admin」；浏览器标签页标题随界面语言切换同步更新（默认中文）。

## v0.5.1（2026-11-20）

### 修复

- 监控指标页「数据库连接池与线程池」卡片：线程池表格首列表头「线程池」与执行器名称（downloadExecutor、ossExecutor、taskScheduler）因卡片过窄被截断。改为连接池与 HTTP 卡片各占整行，并重设列宽（名称列 160px），小屏下表格横向滚动而非压缩，表头与内容完整显示。

## v0.5.0（2026-11-20）

### 新增

- 日志中心：新增 `/api/admin/logs/sources`、`/api/admin/logs/tail`、`/api/admin/logs/download`，集中查看 admin-server、主服务应用/启动、消息拉取/下载、RocketMQ、一键脚本与前端开发日志；支持来源、文件、级别、关键字过滤，单次读取上限 8 MB / 2000 行，下载仅允许白名单文件并写审计 `LOG_DOWNLOAD`。
- 前端新增「日志中心」页：来源与文件切换、级别/关键字过滤、自动刷新、行号与等宽展示、下载入口。
- admin-server 日志落盘：新增 `FILE` appender，输出 `${LOG_PATH}/admin-server.log`，按大小与时间滚动（50 MB / 14 天 / 500 MB 上限）。
- 可观测性：admin-server 引入 `micrometer-registry-prometheus` 并暴露 `/actuator/prometheus`；新增 `/api/admin/monitor/overview` 抓取主应用 `/houyi-eye/prometheus`，汇总 JVM（堆/非堆、线程、GC、CPU、运行时长、类加载）、HTTP 请求（总数/4xx/5xx/平均/最大、按 method+URI Top12）、HikariCP 连接池、线程池与 `dayu_*` 业务指标；抓取失败返回 `available=false` 并附错误信息。
- 前端新增「监控指标」页：4 张统计卡、HTTP 明细表、连接池/线程池卡与业务指标表，15 秒自动刷新。
- 新增一键启动脚本 `scripts/start-all.sh`，按顺序拉起 MySQL、Redis、RocketMQ、主服务、admin-server 与 admin-web。

### 修复

- 修复 `.gitignore` 的 `logs/` 规则误忽略 `admin-server/src/main/java/.../admin/logs` Java 包的问题，改为仅忽略根目录 `/logs/`。
- 修复敏感配置值含反引号时的解析问题。
- 修复未知路径 404 未统一走标准错误响应的问题。

## v0.4.0（2026-11-19）

### 新增

- 配置中心扩展：白名单从 12 项增至 27 项，新增安全组（登录失败锁定次数/时长、审计开关、审计明细长度）与运行参数组（企业扫描间隔、拉取间隔、下载重试次数、大文件超时、下载/OSS 线程池核心与最大线程数）。
- 配置元数据：每项返回最小值、最大值、单位与生效方式（`INSTANT` 即时 / `HOT` 热更新 / `RESTART` 需重启），并支持按 key 一键恢复默认值（`POST /api/admin/settings/reset`，写审计 `SETTING_RESET`）。
- 主服务热更新：新增 `DynamicRuntimeConfig`，每 30 秒从 `admin_config` 读取 `runtime` 组配置，动态应用线程池参数、下载重试次数、下载超时与消息拉取间隔；下载次数上限默认提升到 16 次。
- 企业动态上下线：新增 `CorpManager`，按配置间隔扫描启用企业，自动启停对应消息拉取线程，无需重启主服务。
- 企业 CRUD：新增企业创建、编辑与详情接口及页面；详情集中展示归档消息数、待处理/失败数、今日拉取、最近拉取指标、凭据状态与 Redis 可达性；支持批量启用/停用（`PATCH /api/admin/corps/batch-status`）。企业“删除”采用停用（软下架），不提供物理删除。
- 系统信息页：只读展示版本、运行时长、Java 版本、数据库/Redis 连接状态、MQ 集成状态、数据统计与当前生效的运行参数；JDBC URL 自动脱敏。
- 设置页体验：配置项搜索、未保存数量提示、离开页面拦截、数值范围校验、单位后缀、生效方式标签与恢复默认。
- 冒烟测试扩展到 30 个用例，覆盖企业详情/创建校验/批量启停、配置恢复默认与系统信息。

### 说明

- `HOT` 类配置保存后由主服务在 30 秒内拉取生效；`RESTART` 类配置需人工重启主服务。
- 运行参数超出范围时保存直接拒绝，避免误配导致线程池异常。
- 本地开发可用 H2 文件库启动 admin-server，详见 `admin-server/README.md`。

## v0.3.0（2026-11-19）

### 新增

- P2 查询导出：消息列表与审计日志支持按当前筛选条件导出 CSV（UTF-8 BOM、全字段引号、公式注入防护），单次最多 5000 行，导出行为写入审计。
- P2 健康巡检：新增 `/api/admin/health` 与前端“健康巡检”页，展示数据库/Redis 探测耗时、Redis MQ 与 RocketMQ 状态、主/重试队列积压、延迟消息待投递数、告警与清理最近时间，以及运行时长与版本。
- P2 主服务运行指标：主服务按企业写入拉取耗时、本次/累计条数、最近错误，推送写入成功/失败/重试计数，统一落在 Redis（`houyi:metric:pull:<corpId>`、`houyi:metric:push`），健康巡检页读取展示。
- P2 登录审计：登录成功与失败均写入 `admin_audit_log`（`LOGIN_SUCCESS` / `LOGIN_FAILED`）。
- P2 安全体验：会话失效后跳转登录页并提示“登录状态已失效”；消息重试、企业停用等敏感操作保留二次确认。
- 健康巡检页每 30 秒自动刷新，支持手动刷新。
- 单元测试新增运行指标记录器与 CSV 生成用例；冒烟测试扩展到 24 个用例，覆盖健康巡检与两类导出。

### 说明

- 运行指标为“尽力写入”：Redis 异常时静默降级，不影响主服务拉取与推送。
- 本地默认企业为禁用状态，未启用企业不产生拉取指标，健康巡检页不显示对应行。

## v0.2.0（2026-11-19）

### 新增

- P0 系统设置：管理端自建 `admin_config` 表，白名单键值配置（告警、保留、安全三组），支持类型校验、敏感值掩码与更新人记录。
- P0 告警 Webhook：定时巡检延迟队列积压、下载失败、企业拉取停滞，按冷却时间推送企业微信文本消息，记录 `admin_alert_log`，支持发送测试消息。
- P1 审计日志：登录、配置变更、企业状态与密钥操作、密钥查看失败、消息重试、数据清理均写入 `admin_audit_log`，按 `id` keyset 分页查询。
- P1 数据保留与清理：预览三类可清理数据（已推送消息、已归档 MD5 索引、过期审计日志），默认关闭，需在设置中开启并输入 `DELETE` 确认，按批次上限删除。
- P1 企业密钥安全：企业列表仅返回掩码与私钥是否配置；查看原始密钥需管理员密码二次校验；支持轮换密钥。
- 前端新增系统设置、审计日志、数据清理页面，企业管理新增密钥查看与轮换入口。
- 管理端自建表 `admin_config`、`admin_audit_log`、`admin_alert_log`、`admin_runtime_state`，不改动主库表结构。
- 冒烟测试扩展到 21 个用例，覆盖设置、告警、审计、清理与密钥安全接口。

### 说明

- 数据清理默认关闭，建议先在预发环境预览候选量与最早时间。
- 告警与清理均只读取主库数据，写入仅发生在管理端自建表。

## v0.1.0（2026-11-19）

### 新增

- 独立管理端后端 `admin-server`（Java 21 / Spring Boot 3.2.11，端口 18081），与主服务解耦，共享 MySQL、Redis 和消息队列。
- 管理端鉴权：账号密码登录、JWT access token、HttpOnly refresh cookie、登录失败锁定、`X-Trace-Id` 与 `X-App-Version` 响应头。
- 运行概览接口：企业统计、队列积压、最近失败消息。
- 企业管理接口：关键词/状态筛选、Redis 拉取进度、启用/停用企业。
- 消息接口：keyset 游标分页查询、消息详情、失败消息重试（Redis 延迟队列或 RocketMQ）。
- 队列概览接口：Stream 长度与延迟消息状态统计。
- 管理后台前端 `admin-web`（React 19、Ant Design 6、Vite 7），包含登录、概览、企业、消息、队列页面，支持中英文切换。
- Nginx 部署示例与 API 冒烟测试脚本 `scripts/admin_api_test.rb`（输出 JSON 与 Markdown 报告）。
- 管理端核心逻辑单元测试：JWT、登录校验、失败锁定。

### 说明

- 主服务代码与部署方式不变，管理端可独立升级和回滚。
- 管理端默认监听 `18081`，生产环境通过 Nginx 同源反代 `/api`。
