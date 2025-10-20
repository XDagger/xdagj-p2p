#!/bin/bash
# Kademlia DHT Node Discovery Testing
#
# Usage: ./test-discovery.sh [node_count] [duration_seconds]
#

set -e

# Source shared library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

# Convenience aliases for report output colors
GREEN=$COLOR_GREEN
YELLOW=$COLOR_YELLOW
RED=$COLOR_RED
BLUE=$COLOR_BLUE
NC=$COLOR_NC

# Configuration
NODE_COUNT=${1:-10}
TEST_DURATION=${2:-300}  # Default: 5 minutes
BASE_PORT=$DEFAULT_BASE_PORT

# Validate input
if ! validate_node_count "$NODE_COUNT" 1 20; then
    echo "Usage: $0 [node_count] [duration_seconds]"
    echo "  node_count: Number of nodes (1-20, default: 10)"
    echo "  duration: Test duration in seconds (default: 300)"
    exit 1
fi

log_section "Kademlia DHT Node Discovery Test"
log_info "Node count: $NODE_COUNT"
log_info "Test duration: ${TEST_DURATION} seconds"
echo ""

# Initialize
PROJECT_ROOT=$(get_project_root)
JAR_FILE=$(ensure_jar_exists "$PROJECT_ROOT")
if [ $? -ne 0 ]; then
    exit 1
fi

# Create directories
mkdir -p logs pids discovery-results
log_info "Using JAR: $(basename "$JAR_FILE")"

# Clean up old processes
log_warning "Stopping old nodes..."
cleanup_all_nodes "pids"
rm -f logs/discovery-*.log
sleep 2

# Start nodes
log_section "Starting discovery nodes"
for i in $(seq 0 $((NODE_COUNT-1))); do
    PORT=$((BASE_PORT + i))
    LOG_FILE="logs/discovery-$i.log"
    PID_FILE="pids/discovery-$i.pid"

    # Calculate seed nodes using shared library functions
    SEEDS=$(calculate_tcp_seeds $i $NODE_COUNT $BASE_PORT)
    ACTIVE_NODES=$(calculate_udp_active_nodes $i $NODE_COUNT $BASE_PORT)

    # Display node info
    log_info "Starting Node $i on port $PORT"
    [ -n "$SEEDS" ] && log_debug "  TCP Seeds: $SEEDS"
    [ -n "$ACTIVE_NODES" ] && log_debug "  UDP Active: $ACTIVE_NODES"

    # Start node (-d 1 enables discovery)
    nohup java -Xms512m -Xmx1024m \
        -jar "$JAR_FILE" \
        -p $PORT \
        -d 1 \
        $([ -n "$SEEDS" ] && echo "-s $SEEDS") \
        $([ -n "$ACTIVE_NODES" ] && echo "-a $ACTIVE_NODES") \
        > "$LOG_FILE" 2>&1 &

    PID=$!
    echo "$PID" > "$PID_FILE"

    # Verify startup
    sleep $PROCESS_START_DELAY_SEC
    if ! wait_for_process_start $PID 5; then
        log_error "Node $i (PID $PID) failed to start!"
        log_warning "Last 20 lines of log:"
        tail -20 "$LOG_FILE"
        cleanup_all_nodes "pids"
        exit 1
    fi

    # Check for errors
    ERROR=$(check_log_for_errors "$LOG_FILE")
    if [ $? -ne 0 ]; then
        log_error "Node $i encountered error: $ERROR"
        log_warning "Check log file: $LOG_FILE"
        cleanup_all_nodes "pids"
        exit 1
    fi

    log_info "Node $i started successfully (PID $PID)"
done

# Wait for connections to establish
echo ""
log_section "All discovery nodes started"
log_info "Waiting ${CONNECTION_ESTABLISH_DELAY_SEC}s for node initialization..."
sleep $CONNECTION_ESTABLISH_DELAY_SEC

# Monitor node discovery
echo ""
log_section "Begin Monitoring Node Discovery"
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
