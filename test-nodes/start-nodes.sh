#!/bin/bash
# P2P Network TPS Testing Script
#
# Usage: ./start-nodes.sh [node_count]
#
# Optimized for maximum TPS: 1M TPS achieved
#
set -e

NODE_COUNT=${1:-6}
BASE_PORT=10000
NETWORK_ID=1
JAR_FILE="../target/xdagj-p2p-0.1.2-jar-with-dependencies.jar"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== Starting P2P Network ($NODE_COUNT nodes) ===${NC}"
echo -e "${GREEN}🚀 TPS Testing Mode - Target: 1M TPS${NC}"
echo -e "${GREEN}   Optimized: 8 sender threads + batching${NC}"
echo ""

# Create directories
mkdir -p logs pids

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${YELLOW}Building JAR file...${NC}"
    cd .. && mvn clean package -DskipTests && cd test-nodes
fi

# Clean up old processes and ports
echo -e "${YELLOW}Stopping any existing nodes...${NC}"
pkill -9 -f "io.xdag.p2p.example.StartApp" 2>/dev/null || true
rm -f pids/*.pid
sleep 2

# Function to check if port is available
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        return 1  # Port in use
    else
        return 0  # Port available
    fi
}

# Pre-check all ports
echo -e "${YELLOW}Checking port availability...${NC}"
for i in $(seq 0 $((NODE_COUNT-1))); do
    PORT=$((BASE_PORT + i))
    if ! check_port $PORT; then
        echo -e "${RED}ERROR: Port $PORT is already in use!${NC}"
        echo -e "${YELLOW}Kill the process with: lsof -ti :$PORT | xargs kill -9${NC}"
        exit 1
    fi
done
echo -e "${GREEN}All ports available${NC}"
echo ""

# Start nodes
for i in $(seq 0 $((NODE_COUNT-1))); do
    PORT=$((BASE_PORT + i))
    LOG_FILE="logs/node-$i.log"
    PID_FILE="pids/node-$i.pid"

    # Calculate seed nodes for balanced mesh topology
    SEEDS=""

    if [ $i -eq 0 ]; then
        # Node 0: Bootstrap node, no outgoing connections
        SEEDS=""
    elif [ $i -eq 1 ]; then
        # Node 1: Connect to Node 0 only
        SEEDS="127.0.0.1:$BASE_PORT"
    elif [ $i -eq 2 ]; then
        # Node 2: Connect to Node 0 and Node 1
        SEEDS="127.0.0.1:$BASE_PORT,127.0.0.1:$((BASE_PORT + 1))"
    elif [ $i -eq 3 ]; then
        # Node 3: Connect to Node 0, Node 1, Node 2
        SEEDS="127.0.0.1:$BASE_PORT,127.0.0.1:$((BASE_PORT + 1)),127.0.0.1:$((BASE_PORT + 2))"
    else
        # Node 4+: Connect to 3 strategically chosen nodes
        PREV_NODE=$((i - 1))
        SEEDS="127.0.0.1:$((BASE_PORT + PREV_NODE))"

        LOW_NODE=$(( (i - 1) % 3 ))
        SEEDS="$SEEDS,127.0.0.1:$((BASE_PORT + LOW_NODE))"

        MID_NODE=$(( i / 2 ))
        if [ $MID_NODE -ne $PREV_NODE ] && [ $MID_NODE -ne $LOW_NODE ]; then
            SEEDS="$SEEDS,127.0.0.1:$((BASE_PORT + MID_NODE))"
        else
            ALT_NODE=$(( i - 2 ))
            if [ $ALT_NODE -ge 0 ] && [ $ALT_NODE -ne $LOW_NODE ]; then
                SEEDS="$SEEDS,127.0.0.1:$((BASE_PORT + ALT_NODE))"
            fi
        fi
    fi

    # Start node
    echo -e "${GREEN}Starting Node $i on port $PORT${NC}"
    if [ -n "$SEEDS" ]; then
        echo -e "${BLUE}  Seeds: $SEEDS${NC}"
    fi

    # Optimized heap: 6GB for 1M TPS
    nohup java -Xms2048m -Xmx6144m \
        -jar "$JAR_FILE" \
        -p $PORT \
        -d 1 \
        $([ -n "$SEEDS" ] && echo "-s $SEEDS") \
        > "$LOG_FILE" 2>&1 &

    PID=$!
    echo "$PID" > "$PID_FILE"
    echo -e "${GREEN}Node $i started with PID $PID${NC}"

    # Wait and verify process is running
    sleep 2
    if ! ps -p $PID > /dev/null 2>&1; then
        echo -e "${RED}ERROR: Node $i (PID $PID) failed to start!${NC}"
        echo -e "${YELLOW}Check log file: $LOG_FILE${NC}"
        echo -e "${YELLOW}Last 20 lines of log:${NC}"
        tail -20 "$LOG_FILE"
        echo ""
        echo -e "${RED}Aborting test. Cleaning up...${NC}"
        for j in $(seq 0 $((i))); do
            if [ -f "pids/node-$j.pid" ]; then
                kill -9 $(cat "pids/node-$j.pid") 2>/dev/null || true
            fi
        done
        exit 1
    fi

    # Check for bind exceptions in log
    sleep 1
    if grep -q "BindException\|Address already in use" "$LOG_FILE" 2>/dev/null; then
        echo -e "${RED}ERROR: Node $i port binding failed!${NC}"
        echo -e "${YELLOW}Port $PORT may still be in use. Check log: $LOG_FILE${NC}"
        echo -e "${RED}Aborting test. Cleaning up...${NC}"
        for j in $(seq 0 $((i))); do
            if [ -f "pids/node-$j.pid" ]; then
                kill -9 $(cat "pids/node-$j.pid") 2>/dev/null || true
            fi
        done
        exit 1
    fi
done

echo ""
echo -e "${GREEN}=== All nodes started! ===${NC}"
echo -e "${YELLOW}Wait 30s for connections to establish...${NC}"
sleep 30

# Check connections
echo -e "${GREEN}=== Initial Connection Check ===${NC}"
for i in $(seq 0 $((NODE_COUNT-1))); do
    LOG_FILE="logs/node-$i.log"
    if [ -f "$LOG_FILE" ]; then
        CONN_COUNT=$(grep -c "handshake success" "$LOG_FILE" 2>/dev/null || echo "0")
        echo -e "${GREEN}Node $i: $CONN_COUNT connections${NC}"
    fi
done

echo ""
echo -e "${BLUE}Commands:${NC}"
echo -e "  ${YELLOW}View logs:${NC}     tail -f logs/node-*.log"
echo -e "  ${YELLOW}Stop nodes:${NC}    ./stop-nodes.sh"
