#!/bin/bash
# Kademlia DHT 节点发现测试
#
# 用法: ./test-discovery.sh [node_count] [duration_seconds]
#

set -e

NODE_COUNT=${1:-10}
TEST_DURATION=${2:-300}  # 默认5分钟
BASE_PORT=10000
JAR_FILE="../../target/xdagj-p2p-0.1.2-jar-with-dependencies.jar"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== Kademlia DHT 节点发现测试 ===${NC}"
echo -e "${GREEN}节点数: $NODE_COUNT${NC}"
echo -e "${GREEN}测试时长: ${TEST_DURATION}秒${NC}"
echo ""

# 创建目录
mkdir -p logs pids discovery-results

# 检查JAR
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${YELLOW}构建JAR文件...${NC}"
    cd .. && mvn clean package -DskipTests && cd test-nodes
fi

# 清理旧进程
echo -e "${YELLOW}停止旧节点...${NC}"
pkill -9 -f "io.xdag.p2p.example.StartApp" 2>/dev/null || true
rm -f pids/*.pid logs/discovery-*.log
sleep 2

# 启动节点
echo -e "${BLUE}=== 启动节点 ===${NC}"
for i in $(seq 0 $((NODE_COUNT-1))); do
    PORT=$((BASE_PORT + i))
    LOG_FILE="logs/discovery-$i.log"
    PID_FILE="pids/discovery-$i.pid"

    # 计算种子节点
    SEEDS=""
    if [ $i -eq 0 ]; then
        # Node 0: Bootstrap节点，无种子
        SEEDS=""
    elif [ $i -eq 1 ]; then
        # Node 1: 连接Node 0
        SEEDS="127.0.0.1:$BASE_PORT"
    else
        # 其他节点: 连接Node 0和前一个节点
        PREV_PORT=$((BASE_PORT + i - 1))
        SEEDS="127.0.0.1:$BASE_PORT,127.0.0.1:$PREV_PORT"
    fi

    echo -e "${GREEN}启动 Node $i (端口 $PORT)${NC}"
    if [ -n "$SEEDS" ]; then
        echo -e "${BLUE}  种子节点: $SEEDS${NC}"
    else
        echo -e "${BLUE}  Bootstrap节点 (无种子)${NC}"
    fi

    # 启动节点 (-d 1 启用发现)
    nohup java -Xms512m -Xmx1024m \
        -jar "$JAR_FILE" \
        -p $PORT \
        -d 1 \
        $([ -n "$SEEDS" ] && echo "-s $SEEDS") \
        > "$LOG_FILE" 2>&1 &

    PID=$!
    echo "$PID" > "$PID_FILE"

    # 验证进程
    sleep 2
    if ! ps -p $PID > /dev/null 2>&1; then
        echo -e "${RED}错误: Node $i 启动失败!${NC}"
        tail -20 "$LOG_FILE"
        exit 1
    fi

    echo -e "${GREEN}Node $i 已启动 (PID $PID)${NC}"
    echo ""
done

echo -e "${GREEN}=== 所有节点已启动 ===${NC}"
echo -e "${YELLOW}等待30秒让节点初始化...${NC}"
sleep 30

# 监控节点发现
echo ""
echo -e "${BLUE}=== 开始监控节点发现 ===${NC}"
echo ""

START_TIME=$(date +%s)
REPORT_FILE="discovery-results/report-$(date +%Y%m%d-%H%M%S).txt"

echo "节点发现测试报告" > "$REPORT_FILE"
echo "================" >> "$REPORT_FILE"
echo "节点数: $NODE_COUNT" >> "$REPORT_FILE"
echo "测试时长: ${TEST_DURATION}秒" >> "$REPORT_FILE"
echo "开始时间: $(date)" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

# 监控循环
for t in 30 60 120 180 300; do
    if [ $t -gt $TEST_DURATION ]; then
        break
    fi

    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))

    if [ $ELAPSED -lt $t ]; then
        WAIT_TIME=$((t - ELAPSED))
        sleep $WAIT_TIME
    fi

    echo "========================================" | tee -a "$REPORT_FILE"
    echo "时间: ${t}秒" | tee -a "$REPORT_FILE"
    echo "========================================" | tee -a "$REPORT_FILE"

    # 统计每个节点的发现情况
    for i in 0 $(((NODE_COUNT-1)/2)) $((NODE_COUNT-1)); do
        LOG="logs/discovery-$i.log"

        if [ ! -f "$LOG" ]; then
            continue
        fi

        # 统计关键指标
        PING_SENT=$(grep -c "Send PING" "$LOG" 2>/dev/null || echo "0")
        PONG_RECV=$(grep -c "Receive PONG" "$LOG" 2>/dev/null || echo "0")
        FIND_NODE=$(grep -c "Send FIND_NODE" "$LOG" 2>/dev/null || echo "0")
        NEIGHBORS=$(grep -c "Receive NEIGHBORS" "$LOG" 2>/dev/null || echo "0")

        # 获取DHT节点数
        DHT_NODES=$(grep "DHT nodes" "$LOG" 2>/dev/null | tail -1 | grep -o "DHT nodes: [0-9]*" | grep -o "[0-9]*" || echo "0")

        # 获取已发现节点数
        DISCOVERED=$(grep "nodes discovered" "$LOG" 2>/dev/null | tail -1 | grep -o "[0-9]* nodes" | grep -o "[0-9]*" || echo "0")

        # 获取连接数
        CONNECTIONS=$(grep "Total channels" "$LOG" 2>/dev/null | tail -1 | grep -o "Total channels: [0-9]*" | grep -o "[0-9]*" || echo "0")

        echo "" | tee -a "$REPORT_FILE"
        echo "Node $i:" | tee -a "$REPORT_FILE"
        echo "  - PING发送: $PING_SENT" | tee -a "$REPORT_FILE"
        echo "  - PONG接收: $PONG_RECV" | tee -a "$REPORT_FILE"
        echo "  - FIND_NODE: $FIND_NODE" | tee -a "$REPORT_FILE"
        echo "  - NEIGHBORS: $NEIGHBORS" | tee -a "$REPORT_FILE"
        echo "  - DHT节点数: $DHT_NODES" | tee -a "$REPORT_FILE"
        echo "  - 已发现: $DISCOVERED" | tee -a "$REPORT_FILE"
        echo "  - TCP连接: $CONNECTIONS" | tee -a "$REPORT_FILE"
    done

    echo "" | tee -a "$REPORT_FILE"
done

# 最终统计
echo "" | tee -a "$REPORT_FILE"
echo "========================================" | tee -a "$REPORT_FILE"
echo "最终统计 (${TEST_DURATION}秒后)" | tee -a "$REPORT_FILE"
echo "========================================" | tee -a "$REPORT_FILE"

TOTAL_DHT=0
TOTAL_DISCOVERED=0
TOTAL_CONNECTIONS=0

for i in $(seq 0 $((NODE_COUNT-1))); do
    LOG="logs/discovery-$i.log"

    if [ ! -f "$LOG" ]; then
        continue
    fi

    DHT_NODES=$(grep "DHT nodes" "$LOG" 2>/dev/null | tail -1 | grep -o "DHT nodes: [0-9]*" | grep -o "[0-9]*" || echo "0")
    DISCOVERED=$(grep "nodes discovered" "$LOG" 2>/dev/null | tail -1 | grep -o "[0-9]* nodes" | grep -o "[0-9]*" || echo "0")
    CONNECTIONS=$(grep "Total channels" "$LOG" 2>/dev/null | tail -1 | grep -o "Total channels: [0-9]*" | grep -o "[0-9]*" || echo "0")

    TOTAL_DHT=$((TOTAL_DHT + DHT_NODES))
    TOTAL_DISCOVERED=$((TOTAL_DISCOVERED + DISCOVERED))
    TOTAL_CONNECTIONS=$((TOTAL_CONNECTIONS + CONNECTIONS))

    echo "Node $i: DHT=$DHT_NODES, 发现=$DISCOVERED, 连接=$CONNECTIONS" | tee -a "$REPORT_FILE"
done

AVG_DHT=$((TOTAL_DHT / NODE_COUNT))
AVG_DISCOVERED=$((TOTAL_DISCOVERED / NODE_COUNT))
AVG_CONNECTIONS=$((TOTAL_CONNECTIONS / NODE_COUNT))

echo "" | tee -a "$REPORT_FILE"
echo "平均值:" | tee -a "$REPORT_FILE"
echo "  - 平均DHT节点数: $AVG_DHT" | tee -a "$REPORT_FILE"
echo "  - 平均发现数: $AVG_DISCOVERED" | tee -a "$REPORT_FILE"
echo "  - 平均连接数: $AVG_CONNECTIONS" | tee -a "$REPORT_FILE"

# 计算覆盖率
EXPECTED=$((NODE_COUNT - 1))  # 每个节点应该发现其他所有节点
if [ $EXPECTED -gt 0 ]; then
    COVERAGE=$((AVG_DISCOVERED * 100 / EXPECTED))
else
    COVERAGE=0
fi

echo "  - 发现覆盖率: ${COVERAGE}%" | tee -a "$REPORT_FILE"

# 评估结果
echo "" | tee -a "$REPORT_FILE"
echo "========================================" | tee -a "$REPORT_FILE"
echo "测试评估" | tee -a "$REPORT_FILE"
echo "========================================" | tee -a "$REPORT_FILE"

if [ $COVERAGE -ge 90 ]; then
    echo -e "${GREEN}✅ 优秀: 发现覆盖率 ${COVERAGE}% >= 90%${NC}" | tee -a "$REPORT_FILE"
elif [ $COVERAGE -ge 70 ]; then
    echo -e "${YELLOW}⚠️  良好: 发现覆盖率 ${COVERAGE}% >= 70%${NC}" | tee -a "$REPORT_FILE"
elif [ $COVERAGE -ge 50 ]; then
    echo -e "${YELLOW}⚠️  一般: 发现覆盖率 ${COVERAGE}% >= 50%${NC}" | tee -a "$REPORT_FILE"
else
    echo -e "${RED}❌ 较差: 发现覆盖率 ${COVERAGE}% < 50%${NC}" | tee -a "$REPORT_FILE"
fi

if [ $AVG_CONNECTIONS -ge 3 ]; then
    echo -e "${GREEN}✅ 连接正常: 平均 ${AVG_CONNECTIONS} 个连接${NC}" | tee -a "$REPORT_FILE"
else
    echo -e "${RED}❌ 连接不足: 平均仅 ${AVG_CONNECTIONS} 个连接${NC}" | tee -a "$REPORT_FILE"
fi

echo "" | tee -a "$REPORT_FILE"
echo "结束时间: $(date)" >> "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

echo -e "${BLUE}完整报告已保存到: $REPORT_FILE${NC}"
echo ""

# 提示
echo -e "${YELLOW}命令提示:${NC}"
echo -e "  ${GREEN}查看发现日志:${NC} grep 'PING\|PONG\|FIND_NODE\|NEIGHBORS' logs/discovery-*.log"
echo -e "  ${GREEN}查看DHT统计:${NC} grep 'DHT nodes' logs/discovery-*.log"
echo -e "  ${GREEN}停止所有节点:${NC} ./stop-nodes.sh"
echo -e "  ${GREEN}查看详细报告:${NC} cat $REPORT_FILE"
