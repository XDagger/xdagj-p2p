#!/bin/bash
# Simple P2P Network Test Script
# Usage: ./start-nodes.sh [node_count]

set -e

NODE_COUNT=${1:-6}
BASE_PORT=10000
NETWORK_ID=1
JAR_FILE="../target/xdagj-p2p-0.1.2-jar-with-dependencies.jar"

# Check if ENABLE_DETAILED_LOGGING environment variable is set
# Default: true (detailed logging enabled)
# Set to "false" for maximum TPS performance mode
if [ -z "$ENABLE_DETAILED_LOGGING" ]; then
    export ENABLE_DETAILED_LOGGING="true"
fi

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== Starting P2P Network ($NODE_COUNT nodes) ===${NC}"

# Display logging mode
if [ "$ENABLE_DETAILED_LOGGING" = "false" ]; then
    echo -e "${YELLOW}⚡ Performance Mode: TURBO (Detailed logging DISABLED)${NC}"
    echo -e "${YELLOW}   TPS will be maximized, minimal I/O overhead${NC}"
else
    echo -e "${GREEN}📊 Performance Mode: STANDARD (Detailed logging enabled)${NC}"
    echo -e "${GREEN}   Full MSG_RECEIVED/MSG_FORWARDED logs for analysis${NC}"
fi
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
    # Strategy: Each node connects to 3 peers (or all available if < 3)
    # 1. Previous node (if exists)
    # 2. First node (if not self)
    # 3. One or more strategic nodes for balanced distribution
    SEEDS=""

    if [ $i -eq 0 ]; then
        # Node 0: Bootstrap node, no outgoing connections
        SEEDS=""
    elif [ $i -eq 1 ]; then
        # Node 1: Connect to Node 0 only (just 1 available)
        SEEDS="127.0.0.1:$BASE_PORT"
    elif [ $i -eq 2 ]; then
        # Node 2: Connect to Node 0 and Node 1 (2 available)
        SEEDS="127.0.0.1:$BASE_PORT,127.0.0.1:$((BASE_PORT + 1))"
    elif [ $i -eq 3 ]; then
        # Node 3: Connect to Node 0, Node 1, Node 2 (3 available)
        SEEDS="127.0.0.1:$BASE_PORT,127.0.0.1:$((BASE_PORT + 1)),127.0.0.1:$((BASE_PORT + 2))"
    else
        # Node 4+: Connect to 3 strategically chosen nodes
        # Strategy: Distribute connections to avoid centralization

        # 1. Always connect to previous node (for chain connectivity)
        PREV_NODE=$((i - 1))
        SEEDS="127.0.0.1:$((BASE_PORT + PREV_NODE))"

        # 2. Connect to a "low-numbered" node (avoid overloading node 0)
        # Use modulo to distribute: node 4->1, node 5->2, node 6->0, etc.
        LOW_NODE=$(( (i - 1) % 3 ))
        SEEDS="$SEEDS,127.0.0.1:$((BASE_PORT + LOW_NODE))"

        # 3. Connect to a "middle" node for better mesh
        # Pick a node roughly in the middle of available nodes
        MID_NODE=$(( i / 2 ))
        if [ $MID_NODE -ne $PREV_NODE ] && [ $MID_NODE -ne $LOW_NODE ]; then
            SEEDS="$SEEDS,127.0.0.1:$((BASE_PORT + MID_NODE))"
        else
            # If middle node conflicts, pick i-2 instead
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

    nohup java -Xms256m -Xmx512m \
        -jar "$JAR_FILE" \
        -p $PORT \
        -d 1 \
        $([ -n "$SEEDS" ] && echo "-s $SEEDS") \
        > "$LOG_FILE" 2>&1 &

    PID=$!
    echo "$PID" > "$PID_FILE"
    echo -e "${GREEN}Node $i started with PID $PID${NC}"

    # Wait a moment and verify the process is still running
    sleep 2
    if ! ps -p $PID > /dev/null 2>&1; then
        echo -e "${RED}ERROR: Node $i (PID $PID) failed to start!${NC}"
        echo -e "${YELLOW}Check log file: $LOG_FILE${NC}"
        echo -e "${YELLOW}Last 20 lines of log:${NC}"
        tail -20 "$LOG_FILE"
        echo ""
        echo -e "${RED}Aborting test. Cleaning up...${NC}"
        # Stop all started nodes
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
        # Stop all started nodes
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
echo -e "  ${YELLOW}Analyze:${NC}       python3 analyze-network-performance.py"
echo -e "  ${YELLOW}Stop nodes:${NC}    ./stop-nodes.sh"
