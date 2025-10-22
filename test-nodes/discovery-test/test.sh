#!/bin/bash
# Universal Node Discovery Testing Script
#
# Supports both Kademlia DHT and DNS discovery modes
#
# Usage:
#   ./test.sh [mode] [node_count] [duration_seconds]
#
# Modes:
#   dht  - Kademlia DHT discovery (default)
#   dns  - DNS discovery with Mock DNS
#
# Examples:
#   ./test.sh dht 10 300      # Kademlia DHT: 10 nodes, 5 minutes
#   ./test.sh dns 5 180       # DNS discovery: 5 nodes, 3 minutes
#   ./test.sh 10 300          # Default to DHT mode
#

set -e

# Source shared library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

# Convenience aliases for colors
GREEN=$COLOR_GREEN
YELLOW=$COLOR_YELLOW
RED=$COLOR_RED
BLUE=$COLOR_BLUE
NC=$COLOR_NC

# Parse arguments
if [[ "$1" == "dht" || "$1" == "dns" ]]; then
    MODE="$1"
    NODE_COUNT=${2:-10}
    TEST_DURATION=${3:-300}
else
    MODE="dht"  # Default mode
    NODE_COUNT=${1:-10}
    TEST_DURATION=${2:-300}
fi

BASE_PORT=$DEFAULT_BASE_PORT

# DNS configuration (for DNS mode)
# Using nodes.xdag.io domain (mainnet subdomain for production-like testing)
DNS_TREE_DOMAIN="mainnet.nodes.xdag.io"
DNS_TREE_PUBKEY="ABCD1234"
DNS_TREE_URL="enrtree://${DNS_TREE_PUBKEY}@${DNS_TREE_DOMAIN}"

# Validate mode
if [[ "$MODE" != "dht" && "$MODE" != "dns" ]]; then
    echo "Error: Invalid mode '$MODE'"
    echo ""
    echo "Usage: $0 [mode] [node_count] [duration_seconds]"
    echo ""
    echo "Modes:"
    echo "  dht  - Kademlia DHT discovery (default)"
    echo "  dns  - DNS discovery with Mock DNS"
    echo ""
    echo "Examples:"
    echo "  $0 dht 10 300      # DHT: 10 nodes, 5 minutes"
    echo "  $0 dns 5 180       # DNS: 5 nodes, 3 minutes"
    echo "  $0 10 300          # Default to DHT mode"
    exit 1
fi

# Validate node count
if ! validate_node_count "$NODE_COUNT" 1 20; then
    echo "Error: Invalid node count"
    echo "Node count must be between 1 and 20"
    exit 1
fi

# Display test configuration
if [ "$MODE" == "dns" ]; then
    log_section "EIP-1459 DNS Discovery Test"
    log_info "Mode: DNS Discovery with Mock DNS"
    log_info "DNS Tree URL: $DNS_TREE_URL"
else
    log_section "Kademlia DHT Node Discovery Test"
    log_info "Mode: Kademlia DHT"
fi

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
mkdir -p logs pids results
log_info "Using JAR: $(basename "$JAR_FILE")"

# Clean up old processes
log_warning "Stopping old nodes..."
cleanup_all_nodes "pids"
rm -f logs/*.log
sleep 2

# Determine file prefix and result directory
if [ "$MODE" == "dns" ]; then
    PREFIX="dns"
    EXTRA_JAVA_OPTS="-Dmock.dns.enabled=true"
    EXTRA_CLASSPATH=":$JAR_FILE/../test-classes"
else
    PREFIX="dht"
    EXTRA_JAVA_OPTS=""
    EXTRA_CLASSPATH=""
fi

# Start nodes
log_section "Starting ${MODE} discovery nodes"
for i in $(seq 0 $((NODE_COUNT-1))); do
    PORT=$((BASE_PORT + i))
    LOG_FILE="logs/${PREFIX}-$i.log"
    PID_FILE="pids/${PREFIX}-$i.pid"

    # Calculate seed nodes
    if [ "$MODE" == "dns" ]; then
        SEEDS=$(calculate_tcp_seeds $i $NODE_COUNT $BASE_PORT)
        log_info "Starting Node $i on port $PORT"
        [ -n "$SEEDS" ] && log_debug "  TCP Seeds (fallback): $SEEDS"
        log_debug "  DNS Tree URL: $DNS_TREE_URL"

        # Start node in DNS mode
        nohup java -Xms256m -Xmx512m \
            $EXTRA_JAVA_OPTS \
            -cp "$JAR_FILE$EXTRA_CLASSPATH" \
            io.xdag.p2p.example.DiscoveryApp \
            -p $PORT \
            -d 1 \
            --url-schemes "$DNS_TREE_URL" \
            $([ -n "$SEEDS" ] && echo "-s $SEEDS") \
            > "$LOG_FILE" 2>&1 &
    else
        SEEDS=$(calculate_tcp_seeds $i $NODE_COUNT $BASE_PORT)
        ACTIVE_NODES=$(calculate_udp_active_nodes $i $NODE_COUNT $BASE_PORT)

        log_info "Starting Node $i on port $PORT"
        [ -n "$SEEDS" ] && log_debug "  TCP Seeds: $SEEDS"
        [ -n "$ACTIVE_NODES" ] && log_debug "  UDP Active: $ACTIVE_NODES"

        # Start node in DHT mode
        nohup java -Xms256m -Xmx512m \
            -cp "$JAR_FILE" \
            io.xdag.p2p.example.DiscoveryApp \
            -p $PORT \
            -d 1 \
            $([ -n "$SEEDS" ] && echo "-s $SEEDS") \
            $([ -n "$ACTIVE_NODES" ] && echo "-a $ACTIVE_NODES") \
            > "$LOG_FILE" 2>&1 &
    fi

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
REPORT_FILE="results/${MODE}-report-$(date +%Y%m%d-%H%M%S).txt"

echo "Node Discovery Test Report" > "$REPORT_FILE"
echo "=========================" >> "$REPORT_FILE"
echo "Mode: ${MODE}" >> "$REPORT_FILE"
echo "Node count: $NODE_COUNT" >> "$REPORT_FILE"
echo "Test duration: ${TEST_DURATION} seconds" >> "$REPORT_FILE"
if [ "$MODE" == "dns" ]; then
    echo "DNS Tree URL: $DNS_TREE_URL" >> "$REPORT_FILE"
    echo "Mock DNS: ENABLED" >> "$REPORT_FILE"
fi
echo "Start time: $(date)" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

# Monitoring loop
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
        if [ $i -ge $NODE_COUNT ]; then
            continue
        fi

        LOG="logs/${PREFIX}-$i.log"

        if [ ! -f "$LOG" ]; then
            continue
        fi

        if [ "$MODE" == "dns" ]; then
            # DNS-specific metrics
            MOCK_DNS=$(grep -c "Mock DNS" "$LOG" 2>/dev/null || echo "0")
            DNS_SYNC=$(grep -c "DNS Discovery Status" "$LOG" 2>/dev/null || echo "0")
            DISCOVERED=$(grep "Discovered Nodes:" "$LOG" 2>/dev/null | tail -1 | sed 's/.*Discovered Nodes: \([0-9]*\).*/\1/' || echo "0")
            PING_SENT=$(grep -c "Sending PING to node:" "$LOG" 2>/dev/null || echo "0")
            PONG_RECV=$(grep -c "Received PONG from node:" "$LOG" 2>/dev/null || echo "0")

            echo "" | tee -a "$REPORT_FILE"
            echo "Node $i:" | tee -a "$REPORT_FILE"
            echo "  - Mock DNS enabled: $([ $MOCK_DNS -gt 0 ] && echo 'YES' || echo 'NO')" | tee -a "$REPORT_FILE"
            echo "  - DNS sync checks: $DNS_SYNC" | tee -a "$REPORT_FILE"
            echo "  - Discovered nodes: $DISCOVERED" | tee -a "$REPORT_FILE"
            echo "  - PING sent: $PING_SENT" | tee -a "$REPORT_FILE"
            echo "  - PONG received: $PONG_RECV" | tee -a "$REPORT_FILE"
        else
            # DHT-specific metrics
            PING_SENT=$(grep -c "Sending PING to node:" "$LOG" 2>/dev/null || echo "0")
            PONG_RECV=$(grep -c "Received PONG from node:" "$LOG" 2>/dev/null || echo "0")
            FIND_NODE=$(grep -c "Sending FIND_NODE to node:" "$LOG" 2>/dev/null || echo "0")
            NEIGHBORS=$(grep -c "Received NEIGHBORS from node:" "$LOG" 2>/dev/null || echo "0")
            DISCOVERED=$(grep "Discovered Nodes:" "$LOG" 2>/dev/null | tail -1 | sed 's/.*Discovered Nodes: \([0-9]*\).*/\1/' || echo "0")

            echo "" | tee -a "$REPORT_FILE"
            echo "Node $i:" | tee -a "$REPORT_FILE"
            echo "  - PING sent: $PING_SENT" | tee -a "$REPORT_FILE"
            echo "  - PONG received: $PONG_RECV" | tee -a "$REPORT_FILE"
            echo "  - FIND_NODE: $FIND_NODE" | tee -a "$REPORT_FILE"
            echo "  - NEIGHBORS: $NEIGHBORS" | tee -a "$REPORT_FILE"
            echo "  - Discovered nodes: $DISCOVERED" | tee -a "$REPORT_FILE"
        fi
    done

    echo "" | tee -a "$REPORT_FILE"
done

# Final statistics
echo "" | tee -a "$REPORT_FILE"
echo "========================================" | tee -a "$REPORT_FILE"
echo "Final Statistics (after ${TEST_DURATION} seconds)" | tee -a "$REPORT_FILE"
echo "========================================" | tee -a "$REPORT_FILE"

TOTAL_DISCOVERED=0
NODES_WITH_DNS=0

for i in $(seq 0 $((NODE_COUNT-1))); do
    LOG="logs/${PREFIX}-$i.log"

    if [ ! -f "$LOG" ]; then
        continue
    fi

    DISCOVERED=$(grep "Discovered Nodes:" "$LOG" 2>/dev/null | tail -1 | sed 's/.*Discovered Nodes: \([0-9]*\).*/\1/' || echo "0")
    TOTAL_DISCOVERED=$((TOTAL_DISCOVERED + DISCOVERED))

    if [ "$MODE" == "dns" ]; then
        MOCK_DNS=$(grep -c "Mock DNS" "$LOG" 2>/dev/null || echo "0")
        if [ $MOCK_DNS -gt 0 ]; then
            NODES_WITH_DNS=$((NODES_WITH_DNS + 1))
        fi
        echo "Node $i: DNS=$([ $MOCK_DNS -gt 0 ] && echo 'YES' || echo 'NO'), Discovered=$DISCOVERED" | tee -a "$REPORT_FILE"
    else
        echo "Node $i: Discovered=$DISCOVERED" | tee -a "$REPORT_FILE"
    fi
done

AVG_DISCOVERED=$((TOTAL_DISCOVERED / NODE_COUNT))

echo "" | tee -a "$REPORT_FILE"
echo "Averages:" | tee -a "$REPORT_FILE"
if [ "$MODE" == "dns" ]; then
    echo "  - Nodes with Mock DNS: $NODES_WITH_DNS / $NODE_COUNT" | tee -a "$REPORT_FILE"
fi
echo "  - Average discovered: $AVG_DISCOVERED" | tee -a "$REPORT_FILE"

# Calculate effectiveness
if [ "$MODE" == "dns" ]; then
    # For DNS: 6 mock nodes expected
    if [ $AVG_DISCOVERED -gt 0 ]; then
        DNS_EFFECTIVENESS=$((AVG_DISCOVERED * 100 / 6))
    else
        DNS_EFFECTIVENESS=0
    fi
    echo "  - DNS effectiveness: ${DNS_EFFECTIVENESS}%" | tee -a "$REPORT_FILE"
else
    # For DHT: N-1 nodes expected
    EXPECTED=$((NODE_COUNT - 1))
    if [ $EXPECTED -gt 0 ]; then
        COVERAGE=$((AVG_DISCOVERED * 100 / EXPECTED))
    else
        COVERAGE=0
    fi
    echo "  - Discovery coverage: ${COVERAGE}%" | tee -a "$REPORT_FILE"
fi

# Evaluate results
echo "" | tee -a "$REPORT_FILE"
echo "========================================" | tee -a "$REPORT_FILE"
echo "Test Evaluation" | tee -a "$REPORT_FILE"
echo "========================================" | tee -a "$REPORT_FILE"

if [ "$MODE" == "dns" ]; then
    if [ $NODES_WITH_DNS -eq $NODE_COUNT ]; then
        echo -e "${GREEN}✅ All nodes enabled Mock DNS${NC}" | tee -a "$REPORT_FILE"
    else
        echo -e "${RED}❌ Only $NODES_WITH_DNS/$NODE_COUNT nodes enabled Mock DNS${NC}" | tee -a "$REPORT_FILE"
    fi

    if [ $DNS_EFFECTIVENESS -ge 80 ]; then
        echo -e "${GREEN}✅ Excellent: DNS discovery ${DNS_EFFECTIVENESS}% effective${NC}" | tee -a "$REPORT_FILE"
    elif [ $DNS_EFFECTIVENESS -ge 50 ]; then
        echo -e "${YELLOW}⚠️  Good: DNS discovery ${DNS_EFFECTIVENESS}% effective${NC}" | tee -a "$REPORT_FILE"
    else
        echo -e "${RED}❌ Poor: DNS discovery only ${DNS_EFFECTIVENESS}% effective${NC}" | tee -a "$REPORT_FILE"
    fi

    if [ $AVG_DISCOVERED -ge 3 ]; then
        echo -e "${GREEN}✅ Discovery working: Average ${AVG_DISCOVERED} nodes${NC}" | tee -a "$REPORT_FILE"
    else
        echo -e "${YELLOW}⚠️  Low discovery: Only ${AVG_DISCOVERED} nodes average${NC}" | tee -a "$REPORT_FILE"
    fi
else
    if [ $COVERAGE -ge 90 ]; then
        echo -e "${GREEN}✅ Excellent: Discovery coverage ${COVERAGE}% >= 90%${NC}" | tee -a "$REPORT_FILE"
    elif [ $COVERAGE -ge 70 ]; then
        echo -e "${YELLOW}⚠️  Good: Discovery coverage ${COVERAGE}% >= 70%${NC}" | tee -a "$REPORT_FILE"
    elif [ $COVERAGE -ge 50 ]; then
        echo -e "${YELLOW}⚠️  Fair: Discovery coverage ${COVERAGE}% >= 50%${NC}" | tee -a "$REPORT_FILE"
    else
        echo -e "${RED}❌ Poor: Discovery coverage ${COVERAGE}% < 50%${NC}" | tee -a "$REPORT_FILE"
    fi

    if [ $AVG_DISCOVERED -ge 3 ]; then
        echo -e "${GREEN}✅ Discovery working: Average ${AVG_DISCOVERED} nodes${NC}" | tee -a "$REPORT_FILE"
    else
        echo -e "${RED}❌ Insufficient discovery: Only ${AVG_DISCOVERED} average${NC}" | tee -a "$REPORT_FILE"
    fi
fi

echo "" | tee -a "$REPORT_FILE"
echo "End time: $(date)" >> "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

echo -e "${BLUE}Complete report saved to: $REPORT_FILE${NC}"
echo ""

# Command hints
echo -e "${YELLOW}Command hints:${NC}"
if [ "$MODE" == "dns" ]; then
    echo -e "  ${GREEN}View DNS logs:${NC} grep 'DNS\|Mock' logs/${PREFIX}-*.log"
    echo -e "  ${GREEN}View discovery stats:${NC} grep 'Discovered Nodes' logs/${PREFIX}-*.log"
else
    echo -e "  ${GREEN}View discovery logs:${NC} grep 'PING\|PONG\|FIND_NODE\|NEIGHBORS' logs/${PREFIX}-*.log"
    echo -e "  ${GREEN}View discovery stats:${NC} grep 'Discovered Nodes' logs/${PREFIX}-*.log"
fi
echo -e "  ${GREEN}Stop all nodes:${NC} ./stop-nodes.sh"
echo -e "  ${GREEN}Verify results:${NC} ./verify.sh ${MODE}"
echo -e "  ${GREEN}View detailed report:${NC} cat $REPORT_FILE"
