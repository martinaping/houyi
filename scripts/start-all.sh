#!/usr/bin/env bash
###############################################################################
# 后羿（Houyi）本地一键启动脚本
#
# 依次启动：MySQL → Redis → RocketMQ(NameServer/Broker/gRPC Proxy)
#          → admin-server(18081) → 主应用 houyi(8080, dev) → admin-web(5173)
#
# 已在运行的服务会自动跳过，可重复执行。
# 用法：bash scripts/start-all.sh
###############################################################################
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

LOCAL_DIR="${HOUYI_LOCAL_DIR:-$HOME/.houyi-local}"
LOG_DIR="$LOCAL_DIR/logs"
PID_DIR="$LOCAL_DIR/pids"
RMQ_CONF_DIR="$LOCAL_DIR/rocketmq"
mkdir -p "$LOG_DIR" "$PID_DIR" "$RMQ_CONF_DIR"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { printf "${GREEN}[INFO]${NC} %s\n" "$*"; }
warn()  { printf "${YELLOW}[WARN]${NC} %s\n" "$*"; }
error() { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; }

JDK21_HOME="${JAVA21_HOME:-/Users/jc/jdk21-temurin/jdk-21.0.2.jdk/Contents/Home}"
# 项目以 Java 21 编译，忽略 shell 中可能存在的低版本 JAVA_HOME
if [ -x "$JDK21_HOME/bin/java" ]; then
    export JAVA_HOME="$JDK21_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
else
    error "未找到 JDK21：$JDK21_HOME（可通过 JAVA21_HOME 指定）"
    exit 1
fi

RMQ_DIR="${ROCKETMQ_HOME:-}"
if [ -z "$RMQ_DIR" ]; then
    RMQ_DIR="$(find "$LOCAL_DIR/rocketmq" -maxdepth 1 -type d -name 'rocketmq-*-bin-release' 2>/dev/null | head -1 || true)"
fi

port_listening() {
    lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

wait_for_port() {
    local port="$1" name="$2" timeout="${3:-60}" pidfile="${4:-}" i=0
    while [ "$i" -lt "$timeout" ]; do
        if port_listening "$port"; then
            info "$name 已就绪 (端口 $port)"
            return 0
        fi
        if [ -n "$pidfile" ] && [ -f "$pidfile" ] && ! kill -0 "$(cat "$pidfile")" 2>/dev/null; then
            error "$name 进程已退出（端口 $port 未监听）"
            return 1
        fi
        sleep 1
        i=$((i + 1))
    done
    error "$name 启动超时（端口 $port 未监听）"
    return 1
}

# ---------------------------------------------------------------- 环境变量 ----
if [ -f "$PROJECT_DIR/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "$PROJECT_DIR/.env"
    set +a
    info "已加载环境变量: $PROJECT_DIR/.env"
else
    error "缺少 $PROJECT_DIR/.env，请先复制 .env.example 为 .env 并填写"
    exit 1
fi

: "${MYSQL_HOST:=127.0.0.1}" "${MYSQL_PORT:=3306}" "${MYSQL_DATABASE:=houyi}"
: "${REDIS_HOST:=127.0.0.1}" "${REDIS_PORT:=6379}"
: "${SERVER_PORT:=8080}"
: "${ADMIN_SERVER_PORT:=18081}"
: "${LOG_PATH:=$LOG_DIR/}"

if [ -z "${DB_USERNAME:-}" ] || [ -z "${DB_PASSWORD:-}" ]; then
    error ".env 中 DB_USERNAME / DB_PASSWORD 不能为空（需使用项目专用 MySQL 账号）"
    exit 1
fi
if [ -z "${ADMIN_PASSWORD:-}" ] || [ -z "${ADMIN_JWT_SECRET:-}" ]; then
    error ".env 中 ADMIN_PASSWORD / ADMIN_JWT_SECRET 不能为空，admin-server 会拒绝启动"
    exit 1
fi

# ------------------------------------------------------------------ MySQL ----
if port_listening "$MYSQL_PORT"; then
    info "MySQL 已在运行 (端口 $MYSQL_PORT)"
else
    info "启动 MySQL..."
    brew services start mysql >/dev/null 2>&1 || true
    wait_for_port "$MYSQL_PORT" "MySQL" 60
fi

if ! mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$DB_USERNAME" -p"$DB_PASSWORD" \
        -e "SELECT 1" "$MYSQL_DATABASE" >/dev/null 2>&1; then
    error "MySQL 专用账号无法连接，请确认库与账号已创建："
    error "  CREATE DATABASE IF NOT EXISTS $MYSQL_DATABASE DEFAULT CHARACTER SET utf8mb4;"
    error "  CREATE USER IF NOT EXISTS '$DB_USERNAME'@'127.0.0.1' IDENTIFIED BY '<密码>';"
    error "  GRANT ALL PRIVILEGES ON $MYSQL_DATABASE.* TO '$DB_USERNAME'@'127.0.0.1';"
    exit 1
fi
info "MySQL 连接正常 (${DB_USERNAME}@${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE})"

# ------------------------------------------------------------------ Redis ----
if port_listening "$REDIS_PORT"; then
    info "Redis 已在运行 (端口 $REDIS_PORT)"
else
    info "启动 Redis..."
    brew services start redis >/dev/null 2>&1 || true
    wait_for_port "$REDIS_PORT" "Redis" 30
fi

# --------------------------------------------------------------- RocketMQ ----
if [ -z "$RMQ_DIR" ] || [ ! -x "$RMQ_DIR/bin/mqnamesrv" ]; then
    error "未找到 RocketMQ，请将其解压到 $LOCAL_DIR/rocketmq/ 或设置 ROCKETMQ_HOME"
    exit 1
fi
info "RocketMQ 目录: $RMQ_DIR"

# RocketMQ 默认堆 4g，本地开发限小
export JAVA_OPT_EXT="-Xms256m -Xmx512m -Xmn128m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m"

if [ ! -f "$RMQ_CONF_DIR/broker.conf" ]; then
    cat > "$RMQ_CONF_DIR/broker.conf" <<EOF
brokerClusterName=DefaultCluster
brokerName=broker-a
brokerId=0
brokerIP1=127.0.0.1
listenPort=10911
namesrvAddr=127.0.0.1:9876
autoCreateTopicEnable=true
autoCreateSubscriptionGroup=true
deleteWhen=04
fileReservedTime=48
brokerRole=ASYNC_MASTER
flushDiskType=ASYNC_FLUSH
storePathRootDir=$LOCAL_DIR/rocketmq/store
storePathCommitLog=$LOCAL_DIR/rocketmq/store/commitlog
EOF
fi

if [ ! -f "$RMQ_CONF_DIR/proxy.json" ]; then
    cat > "$RMQ_CONF_DIR/proxy.json" <<EOF
{
  "rocketMQClusterName": "DefaultCluster",
  "grpcServerPort": 8081,
  "remotingListenPort": 8082,
  "namesrvAddr": "127.0.0.1:9876"
}
EOF
fi

if port_listening 9876; then
    info "RocketMQ NameServer 已在运行 (端口 9876)"
else
    info "启动 RocketMQ NameServer..."
    (cd "$RMQ_DIR" && sh bin/mqnamesrv >"$LOG_DIR/rmq-namesrv.log" 2>&1 & echo $! >"$PID_DIR/rmq-namesrv.pid")
    wait_for_port 9876 "RocketMQ NameServer" 60
fi

if port_listening 10911; then
    info "RocketMQ Broker 已在运行 (端口 10911)"
else
    info "启动 RocketMQ Broker..."
    (cd "$RMQ_DIR" && sh bin/mqbroker -c "$RMQ_CONF_DIR/broker.conf" >"$LOG_DIR/rmq-broker.log" 2>&1 & echo $! >"$PID_DIR/rmq-broker.pid")
    wait_for_port 10911 "RocketMQ Broker" 90
fi

if port_listening 8081; then
    info "RocketMQ gRPC Proxy 已在运行 (端口 8081)"
else
    # Proxy 启动时会在集群中创建系统 Topic，必须等 Broker 注册到 NameServer
    info "等待 Broker 注册到 NameServer..."
    i=0
    until (cd "$RMQ_DIR" && sh bin/mqadmin clusterList -n 127.0.0.1:9876 2>/dev/null | grep -q 'broker-a'); do
        i=$((i + 1))
        if [ "$i" -ge 60 ]; then
            error "Broker 未注册到 NameServer，请查看 $LOG_DIR/rmq-broker.log"
            exit 1
        fi
        sleep 1
    done

    info "启动 RocketMQ gRPC Proxy..."
    start_proxy() {
        (cd "$RMQ_DIR" && sh bin/mqproxy -pm cluster -n 127.0.0.1:9876 -pc "$RMQ_CONF_DIR/proxy.json" >"$LOG_DIR/rmq-proxy.log" 2>&1 & echo $! >"$PID_DIR/rmq-proxy.pid")
    }
    start_proxy
    if ! wait_for_port 8081 "RocketMQ gRPC Proxy" 60; then
        warn "Proxy 首次启动失败，5 秒后重试..."
        sleep 5
        start_proxy
        if ! wait_for_port 8081 "RocketMQ gRPC Proxy" 60; then
            error "RocketMQ gRPC Proxy 启动失败，日志如下："
            tail -20 "$LOG_DIR/rmq-proxy.log" >&2
            exit 1
        fi
    fi
fi

# 主应用启动时会立即拉取 Topic 路由，需提前创建并等待路由生效，否则应用启动失败
info "确保 RocketMQ Topic 存在..."
for topic in "${ROCKETMQ_TOPIC:-wechat-archive-msg}" "${ROCKETMQ_RETRY_TOPIC:-wechat-archive-retry}"; do
    (cd "$RMQ_DIR" && sh bin/mqadmin updateTopic -n 127.0.0.1:9876 -c DefaultCluster -t "$topic" >/dev/null 2>&1) \
        || warn "Topic 创建失败: $topic"
    i=0
    until (cd "$RMQ_DIR" && sh bin/mqadmin topicRoute -t "$topic" -n 127.0.0.1:9876 2>/dev/null | grep -q '"brokerName"'); do
        i=$((i + 1))
        if [ "$i" -ge 60 ]; then
            error "Topic 路由未就绪: $topic"
            exit 1
        fi
        sleep 1
    done
    info "Topic 就绪: $topic"
done

# ------------------------------------------------------------ admin-server ----
ADMIN_JAR="$PROJECT_DIR/admin-server/target/admin-server.jar"
if port_listening "$ADMIN_SERVER_PORT"; then
    info "admin-server 已在运行 (端口 $ADMIN_SERVER_PORT)"
else
    if [ ! -f "$ADMIN_JAR" ]; then
        info "未找到 admin-server.jar，开始构建..."
        (cd "$PROJECT_DIR/admin-server" && mvn -q -DskipTests package)
    fi
    info "启动 admin-server..."
    # 注意：.env 中的 SERVER_PORT 是主应用端口，会覆盖 admin-server 的 server.port，
    # 因此这里为 admin-server 单独指定端口
    (cd "$PROJECT_DIR/admin-server" && SERVER_PORT="$ADMIN_SERVER_PORT" ADMIN_SERVER_PORT="$ADMIN_SERVER_PORT" \
        nohup java -jar "$ADMIN_JAR" >"$LOG_DIR/admin-server.log" 2>&1 & echo $! >"$PID_DIR/admin-server.pid")
    if ! wait_for_port "$ADMIN_SERVER_PORT" "admin-server" 120 "$PID_DIR/admin-server.pid"; then
        error "admin-server 启动失败，日志如下："
        tail -20 "$LOG_DIR/admin-server.log" >&2
        exit 1
    fi
fi

# --------------------------------------------------------------- 主应用 ----
MAIN_JAR="$PROJECT_DIR/target/houyi-0.0.1-SNAPSHOT.jar"
if port_listening "$SERVER_PORT"; then
    info "主应用 houyi 已在运行 (端口 $SERVER_PORT)"
else
    if [ ! -f "$MAIN_JAR" ]; then
        info "未找到主应用 jar，开始构建..."
        (cd "$PROJECT_DIR" && mvn -q -DskipTests package)
    fi
    info "启动主应用 houyi (dev profile)..."
    (cd "$PROJECT_DIR" && nohup java -Dlog.path="$LOG_PATH" -jar "$MAIN_JAR" \
        --spring.profiles.active=dev >"$LOG_DIR/houyi.log" 2>&1 & echo $! >"$PID_DIR/houyi.pid")
    i=0
    until curl -sf "http://127.0.0.1:$SERVER_PORT/houyi-eye/health" >/dev/null 2>&1; do
        i=$((i + 1))
        if [ -f "$PID_DIR/houyi.pid" ] && ! kill -0 "$(cat "$PID_DIR/houyi.pid")" 2>/dev/null; then
            error "主应用进程已退出，日志如下："
            tail -20 "$LOG_DIR/houyi.log" >&2
            exit 1
        fi
        if [ "$i" -ge 120 ]; then
            error "主应用启动超时，查看日志: $LOG_DIR/houyi.log"
            exit 1
        fi
        sleep 1
    done
    info "主应用已就绪 (端口 $SERVER_PORT)"
fi

# --------------------------------------------------------------- admin-web ----
WEB_DIR="$PROJECT_DIR/admin-web"
if port_listening 5173; then
    info "admin-web 已在运行 (端口 5173)"
else
    if [ ! -d "$WEB_DIR/node_modules" ]; then
        info "安装 admin-web 依赖..."
        (cd "$WEB_DIR" && npm install)
    fi
    info "启动 admin-web..."
    (cd "$WEB_DIR" && nohup npm run dev -- --port 5173 --host 127.0.0.1 >"$LOG_DIR/admin-web.log" 2>&1 & echo $! >"$PID_DIR/admin-web.pid")
    wait_for_port 5173 "admin-web" 90
fi

# ------------------------------------------------------------------ 汇总 ----
echo
info "全部服务已启动："
printf "  管理后台前端   http://127.0.0.1:5173\n"
printf "  管理后台接口   http://127.0.0.1:%s/api/admin/health\n" "$ADMIN_SERVER_PORT"
printf "  主应用         http://127.0.0.1:%s/houyi-eye/health\n" "$SERVER_PORT"
printf "  RocketMQ gRPC  http://127.0.0.1:8081  (NameServer 9876 / Broker 10911)\n"
printf "  MySQL          %s:%s/%s\n" "$MYSQL_HOST" "$MYSQL_PORT" "$MYSQL_DATABASE"
printf "  Redis          %s:%s\n" "$REDIS_HOST" "$REDIS_PORT"
echo
printf "日志目录: %s\n" "$LOG_DIR"
