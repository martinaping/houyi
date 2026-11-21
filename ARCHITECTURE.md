# 后羿（Houyi）系统架构文档

**当前版本：v0.10.0**

## 📋 系统概述

后羿（Houyi）是一个企业微信会话存档系统，用于自动拉取、处理、存储企业微信的会话数据。

### 核心功能

1. **会话数据拉取**: 从企业微信服务器拉取会话记录
2. **媒体文件下载**: 下载图片、视频、文件等媒体内容
3. **数据持久化**: 存储到 MySQL 数据库
4. **文件存储**: 上传到阿里云 OSS
5. **消息分发**: 通过 RocketMQ 分发给下游系统，并异步备份到阿里云 MNS
6. **失败重试**: 自动重试下载失败的文件

---

## 🏗️ 系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        企业微信官方服务器                          │
│                    (腾讯 WeWork API Server)                      │
└────────────────────────┬────────────────────────────────────────┘
                         │ 企业微信 SDK (JNI)
                         │ libWeWorkFinanceSdk_Java.so
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│                      后羿应用服务器 (Houyi)                        │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Start.java (启动入口)                                     │  │
│  │  - 初始化企业微信 SDK                                       │  │
│  │  - 为每个企业创建 Message 线程                              │  │
│  └────────────┬─────────────────────────────────────────────┘  │
│               ↓                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Message.java (消息拉取线程)                               │  │
│  │  - 定时拉取会话数据                                         │  │
│  │  - 解密消息内容                                             │  │
│  │  - 分发给 MsgHandler                                       │  │
│  └────────────┬─────────────────────────────────────────────┘  │
│               ↓                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  MsgHandler.java (消息处理器)                              │  │
│  │  - 判断消息类型                                             │  │
│  │  - 调用 MediaDownloader 下载文件                           │  │
│  │  - MD5 校验                                                │  │
│  │  - 上传到 OSS                                              │  │
│  │  - 发送到 RocketMQ                                         │  │
│  └────────────┬─────────────────────────────────────────────┘  │
│               ↓                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  MediaDownloader.java (媒体下载器)                         │  │
│  │  - 分片下载大文件                                           │  │
│  │  - MD5 校验                                                │  │
│  │  - 异常处理和重试                                           │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        ↓                ↓                ↓
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   MySQL      │  │   Redis      │  │  阿里云 OSS   │
│  (持久化)     │  │  (缓存)      │  │  (文件存储)   │
└──────────────┘  └──────────────┘  └──────────────┘
        ↓
┌─────────────────────────────────────────────────────────────────┐
│                     消息队列 (Message Queue)                      │
│  ┌──────────────────────┐      ┌──────────────────────┐        │
│  │  RocketMQ (TCP)      │      │  RocketMQ (HTTP)     │        │
│  │  主消息队列            │      │  重试队列             │        │
│  │  Topic: wechat-      │      │  Topic: msg_center   │        │
│  │  archive-msg-common  │      │                      │        │
│  └──────────┬───────────┘      └──────────┬───────────┘        │
│             │                             │                     │
│             │                             ↓                     │
│             │                  ┌──────────────────────┐        │
│             │                  │ HouyiMqHttpConsumer  │        │
│             │                  │ (重试消费者)          │        │
│             │                  └──────────┬───────────┘        │
│             │                             │                     │
│             │                             ↓                     │
│             │                  ┌──────────────────────┐        │
│             │                  │ DownloadThreadKeeper │        │
│             │                  │ (重新下载)            │        │
│             │                  └──────────────────────┘        │
│             │                                                  │
│             └── 发送成功后异步备份 ──> 阿里云 MNS (houyi-msgs)    │
└─────────────┼────────────────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────────────────────────────┐
│                        下游消费系统                               │
│                   (External Consumer Systems)                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 数据流转详解

### 1. 正常流程

```
1. Start.java 启动
   ↓
2. 读取企业微信配置 (wework-corps.yml)
   ↓
3. 为每个企业创建 Message 线程
   ↓
4. Message 线程定时拉取会话数据
   ├─ Finance.GetChatData() → 企业微信服务器
   ├─ 解密消息 (RSA + AES)
   └─ 判断消息类型
      ├─ 文本消息 → 直接入库 + 发送 MQ
      └─ 媒体消息 → 提交到下载线程池
         ↓
5. DownloadThreadKeeper 执行下载任务
   ↓
6. MsgHandler.simpleDownMedia()
   ├─ MediaDownloader.download()
   │  ├─ Finance.GetMediaData() → 企业微信服务器
   │  ├─ 分片下载（每次 512KB）
   │  └─ 写入本地临时文件
   ├─ MD5 校验
   ├─ AMR 转 MP3（语音消息）
   ├─ 上传到阿里云 OSS
   │  └─ OssUtil.upload()
   ├─ 保存到 MySQL
   │  └─ OriginalMsg 表
   └─ 发送到消息队列
      ├─ RocketMQ (TCP) → 下游系统
      └─ 异步备份 → 阿里云 MNS
```

### 2. 失败重试流程

```
下载失败 / MD5 校验失败 / OSS 上传失败
   ↓
RetryUtil.sendRetryMessage()
   ↓
HouyiMqProducer.send()
   ├─ Topic: msg_center
   ├─ Tag: prod/dev
   ├─ 延迟: 30 秒
   └─ tryCount++
      ↓
HouyiMqHttpConsumer 消费
   ├─ 检查 tryCount (最多 16 次)
   ├─ 检查数据库是否已有 ossPath
   └─ 提交到 DownloadThreadKeeper
      ↓
重新执行下载流程
   ↓
成功 → 正常流程
失败 → 继续重试（直到 16 次）
```

---

## 🗄️ 数据库设计

### 核心表结构

#### 1. corplist (企业信息表)

```sql
CREATE TABLE corplist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    corpid VARCHAR(255) NOT NULL,           -- 企业微信 ID
    corpname VARCHAR(255),                  -- 企业名称
    secret VARCHAR(255),                    -- 企业微信 Secret
    prikey TEXT,                            -- RSA 私钥
    status BIGINT DEFAULT 1,                -- 状态 (1=启用)
    UNIQUE KEY uk_corpid (corpid)
);
```

#### 2. original_msg (原始消息表)

```sql
CREATE TABLE original_msg (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    corp_id VARCHAR(255),                   -- 企业微信 ID
    msg_id VARCHAR(255),                    -- 消息 ID
    seq BIGINT,                             -- 消息序列号
    msgtype VARCHAR(50),                    -- 消息类型
    msgtime BIGINT,                         -- 消息时间戳
    from_user VARCHAR(255),                 -- 发送者
    to_list TEXT,                           -- 接收者列表
    roomid VARCHAR(255),                    -- 群聊 ID
    oss_path TEXT,                          -- OSS 存储路径
    md5sum VARCHAR(255),                    -- 文件 MD5
    filesize BIGINT,                        -- 文件大小
    push_at BIGINT,                         -- 推送时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_corp_msg_seq (corp_id, msg_id, seq),
    INDEX idx_push_at (push_at),
    INDEX idx_msgtype (msgtype)
);
```

#### 3. md5_index (MD5 索引表)

```sql
CREATE TABLE md5_index (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    md5sum VARCHAR(255) NOT NULL,           -- 文件 MD5
    oss_path TEXT,                          -- OSS 存储路径
    filesize BIGINT,                        -- 文件大小
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_md5 (md5sum)
);
```

**用途**: 文件去重，相同 MD5 的文件只存储一次

---

## 🧵 线程池配置

### 1. Download Thread Pool (下载线程池)

- **Core Size**: 8
- **Max Size**: 100
- **Queue Capacity**: 10000
- **用途**: 并发下载媒体文件

### 2. OSS Thread Pool (OSS 上传线程池)

- **Core Size**: 4
- **Max Size**: 60
- **Queue Capacity**: 10000
- **用途**: 并发上传文件到 OSS

### 3. MNS Thread Pool (MNS 备份线程池)

- **Core Size**: 16
- **Max Size**: 64
- **Queue Capacity**: 1280
- **用途**: 异步把已发送成功的消息备份到阿里云 MNS，失败不影响主链路

---

## 🔌 外部依赖

### 1. 企业微信 SDK

- **类型**: JNI (Java Native Interface)
- **库文件**: `libWeWorkFinanceSdk_Java.so` (Linux)
- **协议**: 直连企业微信服务器（无代理）
- **限制**: 需要配置 IP 白名单

### 2. 阿里云 OSS

- **用途**: 存储媒体文件
- **Endpoint**: 
  - 公网: `oss-cn-shanghai.aliyuncs.com`
  - 内网: `oss-cn-shanghai-internal.aliyuncs.com`
- **Bucket**: `wechat-monitoring`

### 3. 阿里云 MNS（备份消息队列）

- **用途**: 主消息队列发送成功后异步备份一份，主队列不可用时可用于补发
- **Endpoint**: 形如 `http://1234567890.mns.cn-shanghai.aliyuncs.com/`
- **Queue**: `houyi-msgs`（默认）
- **降级**: 未配置 endpoint/AccessKey 时自动跳过，不影响主链路

### 4. 阿里云 RocketMQ

#### TCP 实例（主消息队列）
- **Instance ID**: `MQ_INST_1689796288110055_BXrMWl6w`
- **Topic**: `wechat-archive-msg-common`
- **Tag**: `ChatDataJava`
- **消息类型**: 顺序消息

#### HTTP 实例（重试队列）
- **Instance ID**: `MQ_INST_1689796288110055_BXkckLjP`
- **Topic**: `msg_center`
- **Tag**: `prod` / `dev`
- **消息类型**: 延迟消息（30 秒）

### 5. MySQL

- **Database**: `wechat-archive`
- **版本**: 8.0+
- **用途**: 持久化存储

### 6. Redis

- **版本**: 5.0+
- **用途**: 
  - 缓存 MD5 索引
  - 缓存最后拉取的 seq

---

## 📦 核心组件

### 启动组件

| 组件 | 类型 | 说明 |
|------|------|------|
| `HouyiApplication` | Spring Boot 主类 | 应用入口 |
| `Start` | CommandLineRunner | 启动时初始化 SDK 和线程 |
| `CorpConfigService` | Service | 加载企业配置到数据库 |

### 消息处理组件

| 组件 | 类型 | 说明 |
|------|------|------|
| `Message` | Thread | 拉取会话数据线程 |
| `MsgHandler` | Component | 消息处理器 |
| `MixedHandler` | Component | 混合消息处理器 |
| `MediaDownloader` | Component | 媒体文件下载器 |

### 线程池组件

| 组件 | 类型 | 说明 |
|------|------|------|
| `DownloadThreadKeeper` | Component | 下载线程池管理 |
| `OssThreadPool` | Component | OSS 上传线程池 |
| `ThreadPoolConfig` | Configuration | 线程池配置 |

### 消息队列组件

| 组件 | 类型 | 说明 |
|------|------|------|
| `HouyiTcpConstructionMessageProduct` | Component | TCP 生产者（主队列） |
| `HouyiHttpConstructionMessageProduct` | Service | HTTP 生产者 |
| `HouyiMqProducer` | Service | HTTP 生产者（重试队列） |
| `HouyiMqHttpConsumer` | Thread | HTTP 消费者（重试队列） |

### 工具组件

| 组件 | 类型 | 说明 |
|------|------|------|
| `OssUtil` | Component | OSS 上传工具 |
| `JedisUtil` | Component | Redis 工具 |
| `FileUtil` | Utility | 文件操作工具 |
| `DateUtil` | Utility | 日期格式化工具 |
| `RetryUtil` | Utility | 重试逻辑工具 |
| `HttpClientUtil` | Utility | HTTP 客户端工具 |

---

## 🔐 安全机制

### 1. 环境变量管理

所有敏感信息通过环境变量配置：
- 阿里云 AccessKey/Secret
- 数据库密码
- Redis 密码
- 企业微信 Secret 和私钥

### 2. 数据加密

- **传输加密**: 企业微信 SDK 使用 HTTPS
- **消息解密**: RSA + AES 混合加密
- **私钥存储**: 数据库加密存储（建议）

### 3. 访问控制

- **IP 白名单**: 企业微信会话存档需要配置 IP 白名单
- **AK/SK 权限**: 阿里云 AK 最小权限原则

---

## 📊 监控指标

### Prometheus 指标

系统暴露以下监控指标：

| 指标名称 | 类型 | 说明 |
|---------|------|------|
| `houyi_pushed_msg_total` | Counter | 推送消息总数（`service="mns"` 时为 MNS 备份结果，`result="success"/"failure"`） |
| `houyi_push_cost_seconds` | Summary | 推送耗时（`type="mns"` 为 MNS 备份耗时） |
| `houyi_shard_key_total` | Counter | ShardingKey 使用统计 |

### Actuator 端点

- `/houyi-eye/health` - 健康检查
- `/houyi-eye/metrics` - 应用指标
- `/houyi-eye/prometheus` - Prometheus 格式指标
- `/houyi-eye/info` - 应用信息

主应用仅暴露 `health`、`info`、`metrics`、`prometheus` 四个只读端点，且 `health` 不返回组件详情，避免泄露连接信息。管理端 `admin-server` 同样引入 `micrometer-registry-prometheus`，自带 `/actuator/prometheus`（不在 `/api/admin` 登录过滤器范围内，需通过内网或网关限制来源），并作为抓取方汇总主应用指标供后台页面展示。

---

## 🚀 部署架构

### 单机部署

```
┌─────────────────────────────────────┐
│     后羿应用服务器 (Houyi Server)     │
│  ┌─────────────────────────────┐   │
│  │  JVM (Java 21)              │   │
│  │  - Spring Boot 3.2.11       │   │
│  │  - 8 个 Message 线程         │   │
│  │  - 100 个下载线程            │   │
│  │  - 60 个 OSS 上传线程        │   │
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘
         ↓           ↓           ↓
    ┌────────┐  ┌────────┐  ┌────────┐
    │ MySQL  │  │ Redis  │  │  OSS   │
    └────────┘  └────────┘  └────────┘
```

### 高可用部署（推荐）

```
        ┌─────────────┐
        │   Nginx     │
        │ Load Balancer│
        └──────┬──────┘
               │
      ┌────────┼────────┐
      ↓        ↓        ↓
┌──────────┐ ┌──────────┐ ┌──────────┐
│ Houyi-1  │ │ Houyi-2  │ │ Houyi-3  │
│ (主节点)  │ │ (备节点)  │ │ (备节点)  │
└──────────┘ └──────────┘ └──────────┘
      ↓        ↓        ↓
┌─────────────────────────────────┐
│     MySQL (主从复制)              │
│     Redis (哨兵模式)              │
│     OSS (自动高可用)              │
│     RocketMQ (集群模式)           │
└─────────────────────────────────┘
```

**注意**: 由于 Message 线程会定时拉取数据，多节点部署时需要：
1. 使用 Redis 分布式锁避免重复拉取
2. 或者按企业 ID 分片部署（推荐）

---

## 🔧 配置文件层级

```
application.yml (基础配置)
    ├─ application-dev.yml (开发环境)
    └─ application-prod.yml (生产环境)

wework-corps.yml (企业微信配置)

logback-spring.xml (日志配置)

.env (环境变量，不提交到 Git)
```

---

## 📚 相关文档

- [README.md](README.md) - 项目总览和快速开始
- [ROCKETMQ_GUIDE.md](ROCKETMQ_GUIDE.md) - RocketMQ 详细配置
- [WEWORK_CONFIG_GUIDE.md](WEWORK_CONFIG_GUIDE.md) - 企业微信配置
- [SECURITY.md](SECURITY.md) - 安全配置指南
- [UPGRADE_SUMMARY.md](UPGRADE_SUMMARY.md) - 技术栈升级记录
- [FINAL_REPORT.md](FINAL_REPORT.md) - 代码质量报告

---

## 🎯 性能指标

### 吞吐量

- **消息拉取**: 500 条/次，约 10 秒/次
- **文件下载**: 100 并发，平均 2 秒/文件
- **OSS 上传**: 60 并发，平均 1 秒/文件
- **消息推送**: 1000+ 条/秒

### 资源占用

- **CPU**: 4-8 核（推荐）
- **内存**: 4-8 GB（推荐）
- **磁盘**: 50 GB+（临时文件）
- **网络**: 100 Mbps+

---

## 🐛 常见问题

### 1. 消息拉取失败

**原因**: 
- IP 未加入企业微信白名单
- Secret 或私钥错误
- SDK 初始化失败

**解决**: 
- 检查企业微信后台配置
- 查看日志 `Finance.Init()` 返回值

### 2. 文件下载失败

**原因**:
- 网络不稳定
- sdkFileId 过期
- 企业微信服务器限流

**解决**:
- 自动重试机制（最多 16 次）
- 检查网络连接
- 查看重试队列消费情况

### 3. OSS 上传失败

**原因**:
- AccessKey/Secret 错误
- Bucket 不存在
- 网络问题

**解决**:
- 检查阿里云凭证
- 检查 Bucket 权限
- 使用内网 Endpoint

### 4. 消息堆积

**原因**:
- 下游消费者处理慢
- 下载线程池满
- 数据库写入慢

**解决**:
- 扩容下游消费者
- 增加线程池大小
- 优化数据库索引

---

## 🖥️ 管理与运维端（admin）

管理端是一个**独立部署、与主服务零代码耦合**的运维子系统，只共享 MySQL、Redis 和消息队列，不修改主程序代码、不参与主程序构建。

### 目录结构

```
houyi/
├── src/                 # 主服务（保持原状）
├── admin-server/        # 独立 Spring Boot 管理 API（端口 18081）
│   ├── pom.xml          # 独立 Maven 项目，不加入根 pom
│   └── src/main/java/com/ruoran/houyi/admin/
├── admin-web/           # React 19 + Ant Design 6 + Vite 7 前端
└── scripts/admin_api_test.rb  # 管理端 API 冒烟测试
```

### 部署拓扑

```
浏览器 ──> Nginx ──┬─> admin-web 静态资源（SPA）
                    └─> /api 反向代理 ──> admin-server :18081
                                                  │
                          ┌───────────────────────┼───────────────────────┐
                          ↓                       ↓                       ↓
                       MySQL                    Redis                  RocketMQ
                  （corplist / original_msg / delay_message）
```

主服务 `houyi` 与 `admin-server` 互不调用，可分别升级、重启和回滚。

### 管理端 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/admin/auth/login` | 账号密码登录，返回 access token 并下发 refresh cookie |
| POST | `/api/admin/auth/refresh` | 用 HttpOnly cookie 换取新的 access token |
| POST | `/api/admin/auth/logout` | 清除 refresh cookie |
| GET | `/api/admin/auth/me` | 当前登录用户与版本 |
| GET | `/api/admin/overview` | 企业统计、队列概览、最近失败消息 |
| GET | `/api/admin/corps` | 企业列表（关键词/状态筛选，附 Redis 拉取进度） |
| POST | `/api/admin/corps` | 新增企业（CorpID 唯一，校验密钥/私钥与限流参数） |
| GET | `/api/admin/corps/{id}` | 企业详情（消息统计、拉取指标、凭据状态、Redis 可达性） |
| PUT | `/api/admin/corps/{id}` | 编辑企业（名称、密钥、私钥、限流、状态） |
| PATCH | `/api/admin/corps/{id}/status` | 启用/停用企业（写 `corplist.status`） |
| PATCH | `/api/admin/corps/batch-status` | 批量启用/停用企业，返回实际更新行数 |
| PUT | `/api/admin/corps/{id}/secret` | 轮换企业密钥（8~512 字符，写 `corplist.secret`） |
| POST | `/api/admin/corps/{id}/reveal` | 管理员密码二次校验后返回原始密钥 |
| GET | `/api/admin/messages` | 归档消息分页查询（keyset 游标） |
| GET | `/api/admin/messages/export` | 按筛选条件导出消息 CSV（最多 5000 行，写审计） |
| GET | `/api/admin/messages/{id}` | 消息详情 |
| POST | `/api/admin/messages/{id}/retry` | 失败消息重试（写 Redis 延迟队列或发 RocketMQ） |
| GET | `/api/admin/queue/overview` | Stream 长度与延迟消息积压 |
| GET | `/api/admin/settings` | 配置项列表（白名单、范围/单位/生效方式，敏感值掩码） |
| PUT | `/api/admin/settings` | 更新配置项（类型/范围校验，写审计） |
| POST | `/api/admin/settings/reset` | 按 key 恢复默认值（写审计 `SETTING_RESET`） |
| GET | `/api/admin/system` | 系统信息（版本、运行时长、连接状态、运行参数，只读脱敏） |
| GET | `/api/admin/alerts/status` | 告警开关与 Webhook 配置状态 |
| GET | `/api/admin/alerts/logs` | 最近告警记录 |
| POST | `/api/admin/alerts/test` | 发送测试告警消息 |
| GET | `/api/admin/audit-logs` | 审计日志（keyset 游标，可按动作/操作人/时间筛选） |
| GET | `/api/admin/audit-logs/export` | 按筛选条件导出审计日志 CSV（写审计） |
| GET | `/api/admin/health` | 健康巡检：DB/Redis 探测、队列、告警、清理与运行指标 |
| GET | `/api/admin/logs/sources` | 日志来源与文件清单（按来源分组，附大小/修改时间） |
| GET | `/api/admin/logs/tail` | 读取日志尾部（来源/文件/级别/关键字过滤，8 MB、2000 行上限） |
| GET | `/api/admin/logs/download` | 下载日志文件（路径白名单校验，写审计） |
| GET | `/api/admin/monitor/overview` | 抓取主应用 Prometheus 指标并汇总 JVM/HTTP/连接池/线程池/业务指标 |
| GET | `/api/admin/rocketmq/overview` | RocketMQ 集群、Topic/消费组、Lag、DLQ、Broker 与 gRPC 探测 |
| GET | `/api/admin/rocketmq/groups` | 消费组清单与 Lag（默认隐藏 RocketMQ 内置组） |
| GET | `/api/admin/rocketmq/groups/{group}` | 消费组配置、队列 Offset、在线连接、订阅 Topic |
| GET | `/api/admin/rocketmq/topics` | Topic 清单（可含系统 Topic） |
| GET | `/api/admin/rocketmq/topic` | Topic 路由、队列配置/位点、订阅组 |
| GET | `/api/admin/rocketmq/brokers` | Broker 列表、运行时指标与 gRPC 探测 |
| GET | `/api/admin/rocketmq/dlq/topics` | 死信 Topic 与消息数 |
| GET | `/api/admin/rocketmq/dlq/messages` | 拉取死信消息（不消费、不删除） |
| POST | `/api/admin/rocketmq/dlq/resend` | 死信重投到目标 Topic（写审计） |
| GET | `/api/admin/rocketmq/messages/recent` | 最近消息浏览（全部业务 Topic/时间/关键字，不消费） |
| POST | `/api/admin/rocketmq/messages/query` | 按 MsgID 或 Key + 时间范围查询消息（写审计） |
| POST | `/api/admin/rocketmq/messages/trace` | 查询消息轨迹（写审计） |
| GET | `/api/admin/rocketmq/retry` | Redis 模式 `delay_message` 重试队列 |
| POST | `/api/admin/rocketmq/retry/{id}/requeue` | 立即重投并重置重试次数（写审计） |
| POST | `/api/admin/rocketmq/retry/{id}/discard` | 标记丢弃（状态 3，不删除记录，写审计） |
| POST | `/api/admin/rocketmq/test-send` | 发送测试消息（≤64 KB，禁止系统 Topic，写审计） |
| GET | `/api/admin/rocketmq/config` | 管理端 RocketMQ 生效配置、来源与重启提示 |
| GET | `/api/admin/retention/preview` | 预览可清理数据量与最早时间 |
| POST | `/api/admin/retention/execute` | 执行清理（需开启开关并输入 `DELETE` 确认） |

### RocketMQ 管理

- **连接方式**：主应用与管理端重试使用 gRPC Endpoint（`ROCKETMQ_ENDPOINT`）；管理端查询消费进度、Topic、Broker 使用 remoting NameServer（`rocketmq.namesrv-addr` / `ROCKETMQ_NAMESRV_ADDR`）。两者是不同入口。
- **客户端**：`RocketMqAdminService` 基于 `rocketmq-tools` 的 `DefaultMQAdminExt`，按 NameServer/超时变化懒加载并复用；调用失败只返回本页面错误，不影响其他管理功能。
- **缓存**：概览、消费组、Topic、DLQ、Broker 清单缓存 15 秒；写操作后由前端主动刷新。
- **DLQ 读取**：使用临时 Pull Consumer 读取 `%DLQ%<group>` 的消息，不提交 Offset、不删除原消息；重投会向目标 Topic 发送新消息。
- **最近消息浏览**：使用临时 Pull Consumer 按 Topic 队列读取最近消息；全部业务 Topic 时最多扫描 20 个 Topic，单次最多 200 条，按时间范围/关键字过滤；不提交 Offset、不消费原消息。
- **审计**：DLQ 重投、消息查询/轨迹、测试发送、重试队列重投/丢弃均写 `admin_audit_log`，不记录消息体本身。
- **告警**：`AlertService` 复用现有 Webhook/冷却/静默时段，新增消费组 Lag 与 DLQ 总数阈值；RocketMQ 不可用时只记录 WARN。

### 管理端自建表

| 表 | 用途 |
| --- | --- |
| `admin_config` | 键值配置（类型、分组、敏感标记、更新人），由 `AdminSchemaInitializer` 启动时创建并 seed 默认值 |
| `admin_audit_log` | 审计日志（操作人、动作、对象、详情、IP、traceId） |
| `admin_alert_log` | 告警发送记录（指标值、阈值、是否送达、错误信息） |
| `admin_runtime_state` | 运行时状态（如企业拉取进度快照，用于停滞判断） |

四张表由管理端维护；`admin_config` 的 `runtime` 组由主服务以只读方式定时轮询（见“配置热更新”），其余表主服务无需感知，管理端升级不会改变主库既有表结构。

### 配置热更新

```
admin-web 设置页 ──PUT /api/admin/settings──> admin_config(group='runtime')
                                                     │
                                     30s 轮询（只读）
                                                     ↓
主服务 DynamicRuntimeConfig ──> 线程池参数 / 下载重试 / 下载超时 / 拉取间隔
                                                     │
                                                     ↓
                         CorpManager ──> 按间隔扫描启用企业，动态启停 Message 线程
```

| 生效方式 | 含义 | 涉及配置 |
| --- | --- | --- |
| `INSTANT` | 管理端读取时立即生效 | 告警、保留、安全组 |
| `HOT` | 主服务 30 秒内轮询生效 | `runtime.*`（企业扫描间隔、拉取间隔、下载重试/超时、下载与 OSS 线程池） |
| `RESTART` | 保存后需人工重启 | 当前无默认项，预留给端口等启动期参数 |

- 主服务轮询失败时沿用上一次成功配置，不影响拉取与推送。
- 线程池核心/最大线程数变更后按新值重建或调整，已提交任务不受影响。

### 运行指标

主服务新增 `RuntimeMetricRecorder`，把拉取与推送指标写入 Redis，管理端健康巡检只读读取。

| Key | 主要字段 | 写入方 |
| --- | --- | --- |
| `houyi:metric:pull:<corpId>` | `lastStartAt`、`lastEndAt`、`lastDurationMs`、`lastFetched`、`totalFetched`、`lastError`、`lastErrorAt` | `Message.getList()` |
| `houyi:metric:push` | `success`、`failure`、`retry`、`lastPushAt`、`lastError`、`lastErrorAt` | `MessageProducerAdapter` |

- 无新消息时按企业最多每 30 秒心跳写入一次，避免高频写 Redis。
- 指标写入失败按 60 秒节流记录 warn，不阻断拉取与推送。
- 管理端健康巡检每 30 秒自动刷新，展示企业维度的拉取耗时、条数与错误。

### 日志中心与可观测性

- **日志来源**：按来源扫描固定路径（admin-server 自身日志、主服务应用日志、消息拉取/下载、RocketMQ、一键脚本、前端开发日志），每个来源配置多个候选文件，按修改时间倒序。
- **读取与下载**：`tail` 读取尾部（单次最多 8 MB / 2000 行），支持来源、文件、级别、关键字过滤；下载仅允许扫描白名单内的文件，并写审计。
- **日志落盘**：admin-server 通过 `logback-spring.xml` 的 `FILE` appender 输出到 `${LOG_PATH}/admin-server.log`，按大小与时间滚动（50 MB / 14 天 / 500 MB 上限）。
- **指标链路**：后台请求 `/api/admin/monitor/overview` → `MonitorService` 抓取主应用 `/houyi-eye/prometheus`（连接 2 秒、请求 5 秒超时）→ 解析 Prometheus 文本 → 聚合 JVM、HTTP（按 method+uri，Top12）、HikariCP、线程池与 `dayu_*` 业务指标；抓取失败返回 `available=false` 并附带错误与目标端点。
- **外部采集**：Prometheus 可分别抓取主应用 `/houyi-eye/prometheus` 与 admin-server `/actuator/prometheus`，后台页面只做只读展示。

### 鉴权与安全

- 单管理员账号，账号密码通过 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 环境变量注入，不落库、不写日志。
- access token 为 HS256 JWT，仅存前端内存；refresh token 放 `HttpOnly` + `SameSite=Strict` cookie，有效期 12 小时。
- 同 IP 连续登录失败默认 5 次锁定 5 分钟，次数与时长可在系统设置调整；密码比较使用常量时间算法。
- 登录成功与失败均写审计（`LOGIN_SUCCESS` / `LOGIN_FAILED`）。
- 会话失效时前端跳转登录页并一次性提示“登录状态已失效”。
- 所有响应带 `X-Trace-Id` 与 `X-App-Version`；服务日志为 JSON 行格式，敏感字段不落日志。
- 生产环境建议 Nginx 同源反代，并通过 `ADMIN_CORS_ORIGINS` 收紧来源。

### 关键配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ADMIN_SERVER_PORT` | `18081` | 管理 API 端口 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 无 | 与主服务共用的数据库 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_DATABASE` | 无 | 与主服务共用的 Redis |
| `ADMIN_USERNAME` | `admin` | 管理员账号 |
| `ADMIN_PASSWORD` | 无 | 必填，未配置时拒绝启动 |
| `ADMIN_JWT_SECRET` | 无 | 必填，至少 32 字符 |
| `ADMIN_CORS_ORIGINS` | 本地 5173 两个来源 | 逗号分隔的前端来源 |
| `ADMIN_RETRY_ENABLED` | `true` | 是否允许管理端触发重试 |
| `ROCKETMQ_ENDPOINT` 等 | 空 | 配置后重试走 RocketMQ，否则走 Redis Streams |

更多说明见 [admin-server/README.md](admin-server/README.md) 与 [admin-web/README.md](admin-web/README.md)。

---

**最后更新**: 2026-11-20  
**维护者**: Houyi Team

