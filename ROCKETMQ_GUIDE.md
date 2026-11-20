# RocketMQ 配置指南

## 📋 概述

后羿（Houyi）系统使用阿里云 RocketMQ 作为消息队列，用于异步处理企业微信会话数据的下载和重试机制。

系统同时使用了 **TCP 协议** 和 **HTTP 协议** 两种方式访问 RocketMQ。

---

## 🎯 RocketMQ 实例配置

### 需要的实例数量

**共需要 2 个 RocketMQ 实例**：

| 实例 ID | 协议类型 | 用途 | 环境 |
|---------|---------|------|------|
| `MQ_INST_1689796288110055_BXrMWl6w` | TCP | 主消息队列（构建完成的消息） | 开发/生产 |
| `MQ_INST_1689796288110055_BXkckLjP` | HTTP | 重试队列（下载失败的消息） | 生产 |

---

## 📊 Topic 配置

### 需要的 Topic 数量

**共需要 3 个 Topic**：

#### 1. TCP 协议 Topic（主消息队列）

| Topic 名称 | 环境 | 消息类型 | 实例 ID |
|-----------|------|---------|---------|
| `chat_data_test` | 开发 | 顺序消息 | `MQ_INST_1689796288110055_BXrMWl6w` |
| `wechat-archive-msg` | 生产（旧） | 顺序消息 | `MQ_INST_1689796288110055_BXrMWl6w` |
| `wechat-archive-msg-common` | 生产（新） | 顺序消息 | `MQ_INST_1689796288110055_BXrMWl6w` |

**说明**：
- `wechat-archive-msg`：旧的 Topic，目前代码中有但已不使用（`sendOld` 方法已注释）
- `wechat-archive-msg-common`：当前使用的 Topic（`send2` 方法）
- 两个 Topic 都支持 **分区顺序消息**（通过 `ShardingKey` 实现）

#### 2. HTTP 协议 Topic（重试队列）

| Topic 名称 | 环境 | 消息类型 | 实例 ID |
|-----------|------|---------|---------|
| `msg_center` | 生产 | 延迟消息 | `MQ_INST_1689796288110055_BXkckLjP` |

**说明**：
- 用于下载失败后的重试
- 支持 **延迟消息**（默认延迟 30 秒）
- 仅在生产环境启用

---

## 🏷️ Tag 配置

### Tag 列表

| Tag 名称 | 用途 | 环境 | 协议 |
|---------|------|------|------|
| `msg` | 开发环境消息标签 | 开发 | TCP |
| `ChatDataJava` | 生产环境消息标签 | 生产 | TCP |
| `prod` | 生产环境重试消息标签 | 生产 | HTTP |
| `dev` | 开发环境重试消息标签 | 开发 | HTTP |

**Tag 的作用**：
- 用于消息过滤和分类
- Consumer 可以根据 Tag 订阅特定类型的消息
- 通过 `spring.profiles.active` 自动选择对应的 Tag

---

## 👥 Consumer Group 配置

### Consumer Group 列表

| Group ID | 协议 | 订阅 Topic | 订阅 Tag | 用途 |
|----------|------|-----------|---------|------|
| `GID_tcp_chatdata_test` | TCP | `chat_data_test` | `msg` | 开发环境消费者 |
| `GID_http_chatdata_test` | HTTP | `chat_data_test` | `msg` | 开发环境 HTTP 消费者 |
| `${ROCKETMQ_GROUP_ID}` | TCP | `wechat-archive-msg-common` | `ChatDataJava` | 生产环境消费者 |
| `GID_msg_center_file_fail` | HTTP | `msg_center` | `prod` | 生产环境重试消费者 |

---

## 🔄 消息流转架构

### 正常流程

```
企业微信 SDK
    ↓
Message.java (拉取会话数据)
    ↓
MsgHandler.java (处理消息)
    ↓
下载媒体文件 + 上传 OSS
    ↓
HouyiTcpConstructionMessageProduct (TCP)
    ↓
RocketMQ Topic: wechat-archive-msg-common
    ↓
下游消费者（外部系统）
```

### 重试流程

```
下载失败 / MD5 校验失败 / OSS 上传失败
    ↓
HouyiMqProducer (HTTP)
    ↓
RocketMQ Topic: msg_center (延迟 30 秒)
    ↓
HouyiMqHttpConsumer
    ↓
DownloadThreadKeeper (重新下载)
    ↓
最多重试 16 次
```

---

## ⚙️ 配置文件

### 开发环境 (`application-dev.yml`)

```yaml
rocketmq:
  # TCP 协议配置
  nameSrvAddr: ${ROCKETMQ_NAME_SRV_ADDR:http://MQ_INST_1689796288110055_BXrMWl6w.cn-qingdao.mq-internal.aliyuncs.com:8080}
  topic: ${ROCKETMQ_TOPIC:chat_data_test}
  tag: ${ROCKETMQ_TAG:msg}
  groupId: ${ROCKETMQ_GROUP_ID:GID_tcp_chatdata_test}
  
  # HTTP 协议配置
  httpgroupId: ${ROCKETMQ_HTTP_GROUP_ID:GID_http_chatdata_test}
  endpoint: ${ROCKETMQ_ENDPOINT:http://1689796288110055.mqrest.cn-qingdao.aliyuncs.com}
  instanceId: ${ROCKETMQ_INSTANCE_ID:MQ_INST_1689796288110055_BXrMWl6w}
```

### 生产环境 (`application-prod.yml`)

```yaml
rocketmq:
  # TCP 协议配置
  nameSrvAddr: ${ROCKETMQ_NAME_SRV_ADDR:}
  topic: ${ROCKETMQ_TOPIC:wechat-archive-msg}           # 旧 Topic（已废弃）
  topic2: ${ROCKETMQ_TOPIC2:wechat-archive-msg-common}  # 新 Topic（当前使用）
  groupId: ${ROCKETMQ_GROUP_ID:}
  tag: ${ROCKETMQ_TAG:ChatDataJava}
  
  # HTTP 协议配置
  endpoint: ${ROCKETMQ_ENDPOINT:}
  instanceId: ${ROCKETMQ_INSTANCE_ID:}
```

---

## 🔧 环境变量配置

### 必需的环境变量

```bash
# === TCP 协议（主消息队列）===
# NameServer 地址
ROCKETMQ_NAME_SRV_ADDR=http://MQ_INST_xxx.cn-shanghai.mq-internal.aliyuncs.com:8080

# Topic 名称
ROCKETMQ_TOPIC=wechat-archive-msg                    # 旧 Topic（可选）
ROCKETMQ_TOPIC2=wechat-archive-msg-common            # 新 Topic（必需）

# Consumer Group ID
ROCKETMQ_GROUP_ID=GID_wechat_archive_consumer

# Tag（消息标签）
ROCKETMQ_TAG=ChatDataJava

# === HTTP 协议（重试队列）===
# HTTP 端点
ROCKETMQ_ENDPOINT=http://1689796288110055.mqrest.cn-shanghai.aliyuncs.com

# 实例 ID
ROCKETMQ_INSTANCE_ID=MQ_INST_1689796288110055_BXkckLjP
```

### 配置示例 (`.env`)

```bash
# RocketMQ TCP 配置
ROCKETMQ_NAME_SRV_ADDR=http://MQ_INST_1689796288110055_BXrMWl6w.cn-shanghai-internal.aliyuncs.com:8080
ROCKETMQ_TOPIC2=wechat-archive-msg-common
ROCKETMQ_GROUP_ID=GID_wechat_archive_prod
ROCKETMQ_TAG=ChatDataJava

# RocketMQ HTTP 配置
ROCKETMQ_ENDPOINT=http://1689796288110055.mqrest.cn-shanghai-internal.aliyuncs.com
ROCKETMQ_INSTANCE_ID=MQ_INST_1689796288110055_BXkckLjP
```

---

## 📝 代码组件说明

### Producer（生产者）

#### 1. HouyiTcpConstructionMessageProduct（TCP 协议）

**文件**: `src/main/java/com/ruoran/houyi/mq/HouyiTcpConstructionMessageProduct.java`

**用途**: 发送构建完成的会话消息到下游系统

**特性**:
- 使用 TCP 协议（更高性能）
- 支持分区顺序消息（通过 `ShardingKey`）
- 自动从消息的 `from` 字段提取 `ShardingKey`

**使用的配置**:
- Topic: `${rocketmq.topic2}` (wechat-archive-msg-common)
- Tag: `${rocketmq.tag}` (ChatDataJava)

#### 2. HouyiHttpConstructionMessageProduct（HTTP 协议）

**文件**: `src/main/java/com/ruoran/houyi/mq/HouyiHttpConstructionMessageProduct.java`

**用途**: HTTP 方式发送消息（备用）

**特性**:
- 使用 HTTP 协议
- 异步发送 (`@Async`)

**使用的配置**:
- Topic: `${rocketmq.topic}`
- Tag: `${rocketmq.tag}`
- Endpoint: `${rocketmq.endpoint}`

#### 3. HouyiMqProducer（HTTP 协议 - 重试队列）

**文件**: `src/main/java/com/ruoran/houyi/mq/HouyiMqProducer.java`

**用途**: 发送下载失败的消息到重试队列

**特性**:
- 使用 HTTP 协议
- 支持延迟消息（默认 30 秒）
- 支持分区顺序消息
- 异步发送 (`@Async`)

**使用的配置**:
- Topic: `msg_center`（硬编码）
- Instance ID: `MQ_INST_1689796288110055_BXkckLjP`（硬编码）
- Tag: 动态（根据 `spring.profiles.active`，`dev` 或 `prod`）

**⚠️ 注意**: 该类中的 AccessKey 和 Secret 是硬编码的，建议改为环境变量！

### Consumer（消费者）

#### HouyiMqHttpConsumer（HTTP 协议）

**文件**: `src/main/java/com/ruoran/houyi/mq/HouyiMqHttpConsumer.java`

**用途**: 消费重试队列中的消息，重新下载失败的文件

**特性**:
- 使用 HTTP 协议
- 仅在生产环境启用（`dev` 环境会直接返回）
- 顺序消费（`consumeMessageOrderly`）
- 批量消费（每次最多 16 条消息）
- 自动 ACK 确认

**使用的配置**:
- Topic: `msg_center`（硬编码）
- Group ID: `GID_msg_center_file_fail`（硬编码）
- Instance ID: `MQ_INST_1689796288110055_BXkckLjP`（硬编码）
- Tag: `prod`（硬编码）

**⚠️ 注意**: 该类中的配置都是硬编码的，建议改为配置文件！

---

## 🔐 安全建议

### 当前存在的问题

1. **硬编码的 AccessKey/Secret**:
   - `HouyiMqProducer.java` 中硬编码了阿里云凭证
   - `HouyiMqHttpConsumer.java` 中硬编码了阿里云凭证

2. **硬编码的实例配置**:
   - Topic、Instance ID、Group ID 都是硬编码的
   - 不利于多环境部署

### 建议改进

1. **将所有硬编码配置移到配置文件**:

```yaml
# application.yml
houyi:
  mq:
    retry:
      endpoint: ${HOUYI_MQ_RETRY_ENDPOINT:http://1689796288110055.mqrest.cn-shanghai.aliyuncs.com}
      topic: ${HOUYI_MQ_RETRY_TOPIC:msg_center}
      group-id: ${HOUYI_MQ_RETRY_GROUP_ID:GID_msg_center_file_fail}
      instance-id: ${HOUYI_MQ_RETRY_INSTANCE_ID:MQ_INST_1689796288110055_BXkckLjP}
      tag: ${HOUYI_MQ_RETRY_TAG:prod}
```

2. **使用环境变量管理凭证**:

```bash
HOUYI_MQ_RETRY_ACCESS_KEY=your_key
HOUYI_MQ_RETRY_ACCESS_SECRET=your_secret
```

---

## 📊 消息格式

### 主消息（构建完成的消息）

```json
{
  "msgid": "6992861591924236370_1684661243991",
  "corp_id": "ww0aad5bd009edd8e0",
  "seq": 1234567890,
  "msgtype": "image",
  "from": "user001",
  "ossPath": "https://bucket.oss-cn-shanghai.aliyuncs.com/...",
  "md5sum": "abc123...",
  "filesize": 102400,
  "msgtime": 1684661243991
}
```

### 重试消息

```json
{
  "msgid": "6992861591924236370_1684661243991",
  "corp_id": "ww0aad5bd009edd8e0",
  "seq": 1234567890,
  "secret": "your_corp_secret",
  "tryCount": 3,
  "down_fail_at": 1684661243991,
  "sdkfileid": "...",
  "md5sum": "abc123..."
}
```

**字段说明**:
- `msgid`: 消息 ID
- `corp_id`: 企业微信 ID
- `seq`: 消息序列号
- `secret`: 企业微信 Secret（用于重新初始化 SDK）
- `tryCount`: 重试次数（最多 16 次）
- `down_fail_at`: 下载失败时间戳

---

## 🎯 在阿里云控制台创建资源

### 1. 创建 RocketMQ 实例

#### TCP 实例（主消息队列）

1. 登录阿里云 RocketMQ 控制台
2. 点击"创建实例"
3. 选择配置:
   - **实例类型**: 标准版
   - **网络类型**: VPC（生产环境使用内网）
   - **规格**: 根据消息量选择
4. 记录实例 ID: `MQ_INST_xxx`

#### HTTP 实例（重试队列）

1. 同样方式创建第二个实例
2. 记录实例 ID

### 2. 创建 Topic

在每个实例中创建对应的 Topic:

#### TCP 实例中创建:
- Topic 名称: `wechat-archive-msg-common`
- 消息类型: **顺序消息**
- 分区数: 16（根据并发量调整）

#### HTTP 实例中创建:
- Topic 名称: `msg_center`
- 消息类型: **普通消息**（支持延迟）

### 3. 创建 Consumer Group

在对应的 Topic 下创建 Consumer Group:

- `GID_wechat_archive_prod`（TCP）
- `GID_msg_center_file_fail`（HTTP）

### 4. 配置权限

确保 AccessKey 有以下权限:
- `AliyunMQFullAccess`（完整权限）
- 或者自定义权限：发布消息、订阅消息

---

## 🧪 测试

### 测试 TCP Producer

```bash
# 查看日志
tail -f /var/log/houyi/houyi.log | grep "houyi_pushed_msg"

# 应该看到类似输出:
# [INFO] houyi_pushed_msg{service=common} count=1
```

### 测试 HTTP Consumer

```bash
# 查看消费日志
tail -f /var/log/houyi/houyi.log | grep "MqHttp"

# 应该看到:
# [ERROR] MqHttp thread started
# [ERROR] consumer 消费 tag:prod
# [ERROR] got 5 messages
```

### 监控指标

系统暴露了以下 Prometheus 指标:

- `houyi_pushed_msg_total{service="common"}`: 发送到 RocketMQ 的消息数
- `houyi_push_cost_seconds{type="rocket"}`: RocketMQ 发送耗时
- `houyi_pushed_msg_total{service="mns",result="success|failure"}`: 发送成功后异步备份到阿里云 MNS 的结果
- `houyi_push_cost_seconds{type="mns"}`: MNS 备份耗时
- `houyi_shard_key_total`: ShardingKey 使用统计

访问: `http://localhost:8080/houyi-eye/prometheus`

---

## 🔍 故障排查

### 问题 1: 消息发送失败

**症状**: 日志中出现"发布message时出错了"

**排查步骤**:
1. 检查 AccessKey/Secret 是否正确
2. 检查 NameServer 地址是否可达
3. 检查 Topic 是否存在
4. 检查网络连接（内网 vs 公网）

### 问题 2: 消费者不消费

**症状**: 消息堆积，消费者无响应

**排查步骤**:
1. 检查 Consumer Group 是否创建
2. 检查 Tag 是否匹配
3. 检查环境变量 `spring.profiles.active`
4. 查看消费者线程是否启动: `grep "MqHttp thread started" /var/log/houyi/houyi.log`

### 问题 3: 重试次数过多

**症状**: 消息重试超过 16 次

**排查步骤**:
1. 检查企业微信 SDK 是否正常
2. 检查 OSS 上传是否正常
3. 检查网络稳定性
4. 查看 `down_fail_at` 时间戳，判断失败原因

---

## 📚 相关文档

- [阿里云 RocketMQ 文档](https://help.aliyun.com/product/29530.html)
- [SECURITY.md](SECURITY.md) - 安全配置指南
- [README.md](README.md) - 项目总览
- [WEWORK_CONFIG_GUIDE.md](WEWORK_CONFIG_GUIDE.md) - 企业微信配置指南

---

## 📝 总结

### 快速清单

- ✅ **2 个 RocketMQ 实例**（TCP + HTTP）
- ✅ **3 个 Topic**（1 个开发 + 2 个生产）
- ✅ **4 个 Tag**（dev、msg、ChatDataJava、prod）
- ✅ **4 个 Consumer Group**
- ✅ **3 个 Producer** + **1 个 Consumer**

### 推荐配置（生产环境）

```bash
# TCP 主队列
ROCKETMQ_NAME_SRV_ADDR=http://MQ_INST_xxx.cn-shanghai-internal.aliyuncs.com:8080
ROCKETMQ_TOPIC2=wechat-archive-msg-common
ROCKETMQ_GROUP_ID=GID_wechat_archive_prod
ROCKETMQ_TAG=ChatDataJava

# HTTP 重试队列
ROCKETMQ_ENDPOINT=http://xxx.mqrest.cn-shanghai-internal.aliyuncs.com
ROCKETMQ_INSTANCE_ID=MQ_INST_xxx
```

---

**最后更新**: 2025-12-29  
**维护者**: Houyi Team

