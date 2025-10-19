#!/bin/bash
# P2P Network Performance Testing Script
#
# Usage: ./start-nodes.sh [node_count]
#
# Optimized for TPS testing with balanced memory usage
#
set -e

# Source shared library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

# Configuration
NODE_COUNT=${1:-$DEFAULT_NODE_COUNT}
BASE_PORT=$DEFAULT_BASE_PORT
TEST_TYPE="performance"

# Validate input
if ! validate_node_count "$NODE_COUNT" 1 20; then
    echo "Usage: $0 [node_count]"
    echo "  node_count: Number of nodes (1-20, default: $DEFAULT_NODE_COUNT)"
    exit 1
fi

# Display banner
log_section "Starting P2P Performance Test ($NODE_COUNT nodes)"
log_info "Test type: TPS Testing"
log_info "Heap: ${MIN_HEAP_MB}MB - ${MAX_HEAP_MB}MB"
log_info "Features: 4 sender threads + batching"
echo ""

# Initialize
PROJECT_ROOT=$(get_project_root)
JAR_FILE=$(ensure_jar_exists "$PROJECT_ROOT")
if [ $? -ne 0 ]; then
    exit 1
fi

mkdir -p logs pids
log_info "Using JAR: $(basename "$JAR_FILE")"

# Clean up old processes
log_warning "Stopping any existing nodes..."
cleanup_all_nodes "pids"
sleep 2

# Check ports
log_section "Checking port availability"
if ! check_port_range $BASE_PORT $NODE_COUNT; then
    exit 1
fi
log_info "All $NODE_COUNT ports available ($BASE_PORT-$((BASE_PORT + NODE_COUNT - 1)))"
echo ""

# Start nodes
log_section "Starting nodes"
for i in $(seq 0 $((NODE_COUNT-1))); do
    PORT=$((BASE_PORT + i))
    LOG_FILE="logs/${TEST_TYPE}-node-${i}.log"
    PID_FILE="pids/${TEST_TYPE}-node-${i}.pid"
    
    # Calculate seed nodes using shared library
    SEEDS=$(calculate_tcp_seeds $i $NODE_COUNT $BASE_PORT)
    ACTIVE_NODES=$(calculate_udp_active_nodes $i $NODE_COUNT $BASE_PORT)
    
    # Display node info
    log_info "Starting Node $i on port $PORT"
    [ -n "$SEEDS" ] && log_debug "  TCP Seeds: $SEEDS"
    [ -n "$ACTIVE_NODES" ] && log_debug "  UDP Active: $ACTIVE_NODES"
    
    # Start node with optimized heap for TPS testing
    nohup java -Xms${MIN_HEAP_MB}m -Xmx${MAX_HEAP_MB}m \
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
log_section "All nodes started"
log_info "Waiting ${CONNECTION_ESTABLISH_DELAY_SEC}s for connections to establish..."
sleep $CONNECTION_ESTABLISH_DELAY_SEC

# Check initial connections
log_section "Initial Connection Check"
for i in $(seq 0 $((NODE_COUNT-1))); do
    LOG_FILE="logs/${TEST_TYPE}-node-${i}.log"
    if [ -f "$LOG_FILE" ]; then
        CONN_COUNT=$(grep -c "handshake success" "$LOG_FILE" 2>/dev/null || echo "0")
        log_info "Node $i: $CONN_COUNT connections"
    fi
done

# Display usage instructions
echo ""
log_section "Performance Test Running"
log_info "View logs:     tail -f logs/${TEST_TYPE}-node-*.log"
log_info "Monitor TPS:   tail -f logs/${TEST_TYPE}-node-*.log | grep 'TPS:'"
log_info "Stop nodes:    ./stop-nodes.sh"
log_info ""
log_info "Test will run until manually stopped with ./stop-nodes.sh"
