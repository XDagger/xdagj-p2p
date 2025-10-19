#!/bin/bash
# Common functions for P2P test scripts
#
# Usage: source "$(dirname "$0")/../lib/common.sh"

# Exit on error
set -e

# ============================================================================
# Configuration Constants
# ============================================================================

readonly DEFAULT_NODE_COUNT=6
readonly DEFAULT_BASE_PORT=10000
readonly DEFAULT_NETWORK_ID=1

# JVM Memory (per node)
readonly MIN_HEAP_MB=2048
readonly MAX_HEAP_MB=6144

# Timing
readonly PROCESS_START_DELAY_SEC=2
readonly CONNECTION_ESTABLISH_DELAY_SEC=30

# Colors
readonly COLOR_RED='\033[0;31m'
readonly COLOR_GREEN='\033[0;32m'
readonly COLOR_YELLOW='\033[1;33m'
readonly COLOR_BLUE='\033[0;34m'
readonly COLOR_NC='\033[0m'

# ============================================================================
# Path Resolution
# ============================================================================

# Get project root directory (2 levels up from lib/)
get_project_root() {
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    echo "$(cd "$script_dir/../.." && pwd)"
}

# Find JAR file using wildcard (no hardcoded version)
# Returns: Path to JAR file or empty string if not found
find_jar_file() {
    local project_root="$1"
    local jar_file=$(find "$project_root/target" -name "xdagj-p2p-*-jar-with-dependencies.jar" 2>/dev/null | head -1)
    echo "$jar_file"
}

# Ensure JAR exists, build if necessary
# Returns: Path to JAR file or exits on failure
ensure_jar_exists() {
    local project_root="$1"
    local jar_file=$(find_jar_file "$project_root")
    
    if [ -z "$jar_file" ]; then
        log_warning "JAR file not found, building..."
        cd "$project_root" && mvn clean package -DskipTests -q
        jar_file=$(find_jar_file "$project_root")
        
        if [ -z "$jar_file" ]; then
            log_error "Failed to build JAR file"
            exit 1
        fi
    fi
    
    echo "$jar_file"
}

# ============================================================================
# Port Management
# ============================================================================

# Check if a port is available
# Returns: 0 if available, 1 if in use
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        return 1  # Port in use
    else
        return 0  # Port available
    fi
}

# Check range of ports
# Usage: check_port_range START_PORT COUNT
# Returns: 0 if all available, 1 if any in use
check_port_range() {
    local start_port=$1
    local count=$2
    local failed_ports=()
    
    for i in $(seq 0 $((count-1))); do
        local port=$((start_port + i))
        if ! check_port $port; then
            failed_ports+=($port)
        fi
    done
    
    if [ ${#failed_ports[@]} -gt 0 ]; then
        log_error "The following ports are already in use:"
        for port in "${failed_ports[@]}"; do
            local pid=$(lsof -ti :$port 2>/dev/null || echo "unknown")
            log_error "  Port $port (PID: $pid)"
        done
        log_warning "Kill processes with: lsof -ti :PORT | xargs kill -9"
        return 1
    fi
    
    return 0
}

# ============================================================================
# Logging Functions
# ============================================================================

log_info() {
    echo -e "${COLOR_GREEN}[INFO]${COLOR_NC} $*"
}

log_warning() {
    echo -e "${COLOR_YELLOW}[WARN]${COLOR_NC} $*"
}

log_error() {
    echo -e "${COLOR_RED}[ERROR]${COLOR_NC} $*"
}

log_debug() {
    if [ "${DEBUG:-0}" = "1" ]; then
        echo -e "${COLOR_BLUE}[DEBUG]${COLOR_NC} $*"
    fi
}

log_section() {
    echo ""
    echo -e "${COLOR_BLUE}=== $* ===${COLOR_NC}"
}

# ============================================================================
# Seed Node Calculation
# ============================================================================

# Calculate TCP seed nodes for a given node index
# Usage: calculate_tcp_seeds NODE_INDEX NODE_COUNT BASE_PORT
calculate_tcp_seeds() {
    local node_index=$1
    local node_count=$2
    local base_port=$3
    local seeds=""
    
    if [ $node_index -eq 0 ]; then
        # Bootstrap node: no seeds
        seeds=""
    elif [ $node_index -eq 1 ]; then
        # Node 1: Connect to Node 0
        seeds="127.0.0.1:$base_port"
    elif [ $node_index -eq 2 ]; then
        # Node 2: Connect to Node 0 and Node 1
        seeds="127.0.0.1:$base_port,127.0.0.1:$((base_port + 1))"
    else
        # Node 3+: Connect to strategically chosen nodes
        local prev_node=$((node_index - 1))
        seeds="127.0.0.1:$((base_port + prev_node))"
        
        local low_node=$(( (node_index - 1) % 3 ))
        seeds="$seeds,127.0.0.1:$((base_port + low_node))"
        
        local mid_node=$(( node_index / 2 ))
        if [ $mid_node -ne $prev_node ] && [ $mid_node -ne $low_node ]; then
            seeds="$seeds,127.0.0.1:$((base_port + mid_node))"
        else
            local alt_node=$((node_index - 2))
            if [ $alt_node -ge 0 ] && [ $alt_node -ne $low_node ]; then
                seeds="$seeds,127.0.0.1:$((base_port + alt_node))"
            fi
        fi
    fi
    
    echo "$seeds"
}

# Calculate UDP active nodes for Kademlia DHT
# Usage: calculate_udp_active_nodes NODE_INDEX NODE_COUNT BASE_PORT
calculate_udp_active_nodes() {
    local node_index=$1
    local node_count=$2
    local base_port=$3
    local active_nodes=""
    
    if [ $node_index -eq 0 ]; then
        # Bootstrap node: no active nodes
        active_nodes=""
    elif [ $node_index -eq 1 ]; then
        # Node 1: Connect to Node 0
        active_nodes="127.0.0.1:$base_port"
    elif [ $node_index -eq 2 ]; then
        # Node 2: Connect to Node 0 and Node 1
        active_nodes="127.0.0.1:$base_port,127.0.0.1:$((base_port + 1))"
    else
        # Node 3+: Only connect to Node 0 (force DHT discovery)
        active_nodes="127.0.0.1:$base_port"
    fi
    
    echo "$active_nodes"
}

# ============================================================================
# Node Management
# ============================================================================

# Wait for a process to start
# Returns: 0 if started successfully, 1 if failed
wait_for_process_start() {
    local pid=$1
    local timeout=${2:-5}
    local elapsed=0
    
    while [ $elapsed -lt $timeout ]; do
        if ps -p $pid > /dev/null 2>&1; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    
    return 1
}

# Check for critical errors in log file
# Returns: 0 if no errors, 1 if error found (and prints error type)
check_log_for_errors() {
    local log_file=$1
    local critical_errors=(
        "BindException"
        "Address already in use"
        "OutOfMemoryError"
        "Failed to start"
    )
    
    for error in "${critical_errors[@]}"; do
        if grep -q "$error" "$log_file" 2>/dev/null; then
            echo "$error"
            return 1
        fi
    done
    
    return 0
}

# ============================================================================
# Cleanup Functions
# ============================================================================

# Kill all test nodes
cleanup_all_nodes() {
    local pids_dir="${1:-pids}"
    
    if [ -d "$pids_dir" ]; then
        for pid_file in "$pids_dir"/*.pid; do
            if [ -f "$pid_file" ]; then
                local pid=$(cat "$pid_file" 2>/dev/null || echo "")
                if [ -n "$pid" ]; then
                    kill $pid 2>/dev/null && log_info "Stopped node with PID $pid" || true
                fi
                rm -f "$pid_file"
            fi
        done
    fi
    
    # Backup: Kill by process name
    pkill -f "io.xdag.p2p.example.StartApp" 2>/dev/null && log_info "Killed remaining processes" || true
    pkill -f "io.xdag.p2p.example.DiscoveryTestApp" 2>/dev/null || true
}

# ============================================================================
# Validation Functions
# ============================================================================

# Validate node count
validate_node_count() {
    local count=$1
    local min=${2:-1}
    local max=${3:-50}
    
    if [ $count -lt $min ] || [ $count -gt $max ]; then
        log_error "Invalid node count: $count (must be between $min and $max)"
        return 1
    fi
    
    return 0
}

# ============================================================================
# Initialization
# ============================================================================

# Log that library is loaded (for debugging)
log_debug "Common library loaded from: ${BASH_SOURCE[0]}"

