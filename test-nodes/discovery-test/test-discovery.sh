#!/bin/bash
# Kademlia DHT Node Discovery Testing
#
# Usage: ./test-discovery.sh [node_count] [duration_seconds]
#

set -e

NODE_COUNT=${1:-10}
TEST_DURATION=${2:-300}  # Default: 5 minutes
BASE_PORT=10000
JAR_FILE="../../target/xdagj-p2p-0.1.2-jar-with-dependencies.jar"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== Kademlia DHT Node Discovery Test ===${NC}"
echo -e "${GREEN}Node count: $NODE_COUNT${NC}"
echo -e "${GREEN}Test duration: ${TEST_DURATION} seconds${NC}"
echo ""

# Create directories
mkdir -p logs pids discovery-results

# Check JAR file
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${YELLOW}Building JAR file...${NC}"
    cd .. && mvn clean package -DskipTests && cd test-nodes
fi

# Clean up old processes
echo -e "${YELLOW}Stopping old nodes...${NC}"
pkill -9 -f "io.xdag.p2p.example.StartApp" 2>/dev/null || true
rm -f pids/*.pid logs/discovery-*.log
sleep 2

# Start nodes
echo -e "${BLUE}=== Starting Nodes ===${NC}"
for i in $(seq 0 $((NODE_COUNT-1))); do
    PORT=$((BASE_PORT + i))
    LOG_FILE="logs/discovery-$i.log"
    PID_FILE="pids/discovery-$i.pid"

    # Calculate seed nodes
    SEEDS=""
    if [ $i -eq 0 ]; then
        # Node 0: Bootstrap node, no seeds
        SEEDS=""
    elif [ $i -eq 1 ]; then
        # Node 1: Connect to Node 0
        SEEDS="127.0.0.1:$BASE_PORT"
    else
        # Other nodes: Connect to Node 0 and previous node
        PREV_PORT=$((BASE_PORT + i - 1))
        SEEDS="127.0.0.1:$BASE_PORT,127.0.0.1:$PREV_PORT"
    fi

    echo -e "${GREEN}Starting Node $i (port $PORT)${NC}"
    if [ -n "$SEEDS" ]; then
        echo -e "${BLUE}  Seed nodes: $SEEDS${NC}"
    else
        echo -e "${BLUE}  Bootstrap node (no seeds)${NC}"
    fi

    # Start node (-d 1 enables discovery)
    nohup java -Xms512m -Xmx1024m \
        -jar "$JAR_FILE" \
        -p $PORT \
        -d 1 \
        $([ -n "$SEEDS" ] && echo "-s $SEEDS") \
        > "$LOG_FILE" 2>&1 &

    PID=$!
    echo "$PID" > "$PID_FILE"

    # Verify process started
    sleep 2
    if ! ps -p $PID > /dev/null 2>&1; then
        echo -e "${RED}Error: Node $i failed to start!${NC}"
        tail -20 "$LOG_FILE"
        exit 1
    fi

    echo -e "${GREEN}Node $i started (PID $PID)${NC}"
    echo ""
done

echo -e "${GREEN}=== All Nodes Started ===${NC}"
echo -e "${YELLOW}Waiting 30 seconds for node initialization...${NC}"
sleep 30

# Monitor node discovery
echo ""
echo -e "${BLUE}=== Begin Monitoring Node Discovery ===${NC}"
echo ""

START_TIME=$(date +%s)
REPORT_FILE="discovery-results/report-$(date +%Y%m%d-%H%M%S).txt"

echo "Node Discovery Test Report" > "$REPORT_FILE"
echo "=========================" >> "$REPORT_FILE"
echo "Node count: $NODE_COUNT" >> "$REPORT_FILE"
echo "Test duration: ${TEST_DURATION} seconds" >> "$REPORT_FILE"
echo "Start time: $(date)" >> "$REPORT_FILE"
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
    echo "Time: ${t} seconds" | tee -a "$REPORT_FILE"
    echo "========================================" | tee -a "$REPORT_FILE"

    # Statistics for selected nodes
    for i in 0 $(((NODE_COUNT-1)/2)) $((NODE_COUNT-1)); do
        LOG="logs/discovery-$i.log"

        if [ ! -f "$LOG" ]; then
            continue
        fi

        # Collect key metrics
        PING_SENT=$(grep -c "Send PING" "$LOG" 2>/dev/null || echo "0")
        PONG_RECV=$(grep -c "Receive PONG" "$LOG" 2>/dev/null || echo "0")
        FIND_NODE=$(grep -c "Send FIND_NODE" "$LOG" 2>/dev/null || echo "0")
        NEIGHBORS=$(grep -c "Receive NEIGHBORS" "$LOG" 2>/dev/null || echo "0")

        # Get DHT node count
        DHT_NODES=$(grep "DHT nodes" "$LOG" 2>/dev/null | tail -1 | grep -o "DHT nodes: [0-9]*" | grep -o "[0-9]*" || echo "0")

        # Get discovered node count
        DISCOVERED=$(grep "nodes discovered" "$LOG" 2>/dev/null | tail -1 | grep -o "[0-9]* nodes" | grep -o "[0-9]*" || echo "0")

        # Get connection count
        CONNECTIONS=$(grep "Total channels" "$LOG" 2>/dev/null | tail -1 | grep -o "Total channels: [0-9]*" | grep -o "[0-9]*" || echo "0")

        echo "" | tee -a "$REPORT_FILE"
        echo "Node $i:" | tee -a "$REPORT_FILE"
        echo "  - PING sent: $PING_SENT" | tee -a "$REPORT_FILE"
        echo "  - PONG received: $PONG_RECV" | tee -a "$REPORT_FILE"
        echo "  - FIND_NODE: $FIND_NODE" | tee -a "$REPORT_FILE"
        echo "  - NEIGHBORS: $NEIGHBORS" | tee -a "$REPORT_FILE"
        echo "  - DHT nodes: $DHT_NODES" | tee -a "$REPORT_FILE"
        echo "  - Discovered: $DISCOVERED" | tee -a "$REPORT_FILE"
        echo "  - TCP connections: $CONNECTIONS" | tee -a "$REPORT_FILE"
    done

    echo "" | tee -a "$REPORT_FILE"
done

# Final statistics
echo "" | tee -a "$REPORT_FILE"
echo "========================================" | tee -a "$REPORT_FILE"
echo "Final Statistics (after ${TEST_DURATION} seconds)" | tee -a "$REPORT_FILE"
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

    echo "Node $i: DHT=$DHT_NODES, Discovered=$DISCOVERED, Connections=$CONNECTIONS" | tee -a "$REPORT_FILE"
done

AVG_DHT=$((TOTAL_DHT / NODE_COUNT))
AVG_DISCOVERED=$((TOTAL_DISCOVERED / NODE_COUNT))
AVG_CONNECTIONS=$((TOTAL_CONNECTIONS / NODE_COUNT))

echo "" | tee -a "$REPORT_FILE"
echo "Averages:" | tee -a "$REPORT_FILE"
echo "  - Average DHT nodes: $AVG_DHT" | tee -a "$REPORT_FILE"
echo "  - Average discovered: $AVG_DISCOVERED" | tee -a "$REPORT_FILE"
echo "  - Average connections: $AVG_CONNECTIONS" | tee -a "$REPORT_FILE"

# Calculate coverage
EXPECTED=$((NODE_COUNT - 1))  # Each node should discover all other nodes
if [ $EXPECTED -gt 0 ]; then
    COVERAGE=$((AVG_DISCOVERED * 100 / EXPECTED))
else
    COVERAGE=0
fi

echo "  - Discovery coverage: ${COVERAGE}%" | tee -a "$REPORT_FILE"

# Evaluate results
echo "" | tee -a "$REPORT_FILE"
echo "========================================" | tee -a "$REPORT_FILE"
echo "Test Evaluation" | tee -a "$REPORT_FILE"
echo "========================================" | tee -a "$REPORT_FILE"

if [ $COVERAGE -ge 90 ]; then
    echo -e "${GREEN}✅ Excellent: Discovery coverage ${COVERAGE}% >= 90%${NC}" | tee -a "$REPORT_FILE"
elif [ $COVERAGE -ge 70 ]; then
    echo -e "${YELLOW}⚠️  Good: Discovery coverage ${COVERAGE}% >= 70%${NC}" | tee -a "$REPORT_FILE"
elif [ $COVERAGE -ge 50 ]; then
    echo -e "${YELLOW}⚠️  Fair: Discovery coverage ${COVERAGE}% >= 50%${NC}" | tee -a "$REPORT_FILE"
else
    echo -e "${RED}❌ Poor: Discovery coverage ${COVERAGE}% < 50%${NC}" | tee -a "$REPORT_FILE"
fi

if [ $AVG_CONNECTIONS -ge 3 ]; then
    echo -e "${GREEN}✅ Connections normal: Average ${AVG_CONNECTIONS} connections${NC}" | tee -a "$REPORT_FILE"
else
    echo -e "${RED}❌ Insufficient connections: Only ${AVG_CONNECTIONS} average${NC}" | tee -a "$REPORT_FILE"
fi

echo "" | tee -a "$REPORT_FILE"
echo "End time: $(date)" >> "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

echo -e "${BLUE}Complete report saved to: $REPORT_FILE${NC}"
echo ""

# Command hints
echo -e "${YELLOW}Command hints:${NC}"
echo -e "  ${GREEN}View discovery logs:${NC} grep 'PING\|PONG\|FIND_NODE\|NEIGHBORS' logs/discovery-*.log"
echo -e "  ${GREEN}View DHT stats:${NC} grep 'DHT nodes' logs/discovery-*.log"
echo -e "  ${GREEN}Stop all nodes:${NC} ./stop-nodes.sh"
echo -e "  ${GREEN}View detailed report:${NC} cat $REPORT_FILE"
