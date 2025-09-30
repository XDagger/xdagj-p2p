#!/bin/bash

# True P2P Network Test Script
# Creates a distributed P2P network with diverse connections
# Creates real P2P network avoiding centralized structure

set -e

# Default values
DEFAULT_NODES=20
DEFAULT_BASE_PORT=10000
DEFAULT_NETWORK_ID=1

# Parse arguments
NODES=${1:-$DEFAULT_NODES}
BASE_PORT=${2:-$DEFAULT_BASE_PORT}
NETWORK_ID=${3:-$DEFAULT_NETWORK_ID}

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOGS_DIR="$SCRIPT_DIR/logs"
PIDS_DIR="$SCRIPT_DIR/pids"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Initial wait seconds for connections (override with env INITIAL_WAIT)
INITIAL_WAIT=${INITIAL_WAIT:-30}

# Preflight: gracefully stop known nodes, then free occupied TCP/UDP ports in range
preflight_cleanup() {
  echo -e "${YELLOW}Preflight: stopping previously started nodes (if any)...${NC}"
  if [ -x "$SCRIPT_DIR/stop-nodes.sh" ]; then
    "$SCRIPT_DIR/stop-nodes.sh" >/dev/null 2>&1 || true
  fi

  echo -e "${YELLOW}Preflight: checking occupied ports ${BASE_PORT}..$((BASE_PORT + NODES - 1)) (TCP/UDP)${NC}"
  # Collect unique PIDs listening on TCP or UDP in the range
  PIDS=$(for p in $(seq $BASE_PORT $((BASE_PORT + NODES - 1))); do \
    (lsof -tiTCP:$p -sTCP:LISTEN 2>/dev/null; lsof -tiUDP:$p 2>/dev/null) || true; \
  done | sort -u)

  if [ -n "$PIDS" ]; then
    echo -e "${YELLOW}Preflight: found processes on target ports ->${NC} $(echo $PIDS | tr '\n' ' ')"
    echo -e "${YELLOW}Preflight: sending TERM...${NC}"
    for pid in $PIDS; do kill "$pid" 2>/dev/null || true; done
    sleep 1
    # Recheck and KILL if still alive
    REMAIN=$(for pid in $PIDS; do kill -0 "$pid" 2>/dev/null && echo "$pid" || true; done | sort -u)
    if [ -n "$REMAIN" ]; then
      echo -e "${YELLOW}Preflight: forcing KILL for ->${NC} $(echo $REMAIN | tr '\n' ' ')"
      for pid in $REMAIN; do kill -9 "$pid" 2>/dev/null || true; done
      sleep 1
    fi
  else
    echo -e "${GREEN}Preflight: no occupied ports detected in range.${NC}"
  fi
}

echo -e "${BLUE}=== Starting True P2P Network ($NODES nodes) ===${NC}"
echo -e "${BLUE}Base Port: $BASE_PORT${NC}"
echo -e "${BLUE}Network ID: $NETWORK_ID${NC}"

# Create directories (initial), then run preflight, then ensure directories exist again
mkdir -p "$LOGS_DIR" "$PIDS_DIR"

# Run preflight cleanup before any bind attempts
preflight_cleanup

# Ensure directories still exist after preflight (stop script may remove empty dirs)
mkdir -p "$LOGS_DIR" "$PIDS_DIR"

# Check JAR file (pick latest assembled jar automatically)
JAR_FILE=$(ls -t "$PROJECT_ROOT/target"/xdagj-p2p-*-jar-with-dependencies.jar 2>/dev/null | head -n 1)
if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    echo -e "${YELLOW}Building project...${NC}"
    cd "$PROJECT_ROOT"
    mvn -q -DskipTests -Dmaven.test.skip package
    JAR_FILE=$(ls -t "$PROJECT_ROOT/target"/xdagj-p2p-*-jar-with-dependencies.jar 2>/dev/null | head -n 1)
    if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
        echo -e "${RED}Failed to build JAR file${NC}"
        exit 1
    fi
fi

# Function to generate diverse seed nodes for each node
generate_seed_nodes() {
    local node_id=$1
    local seeds=""
    local seed_count=0
    local max_seeds=3
    
    # Create diverse seed connections to avoid centralization
    case $((node_id % 4)) in
        0) # Group 0: Connect to nodes 1, 5, 9
            local targets=(1 5 9)
            ;;
        1) # Group 1: Connect to nodes 0, 6, 10
            local targets=(0 6 10)
            ;;
        2) # Group 2: Connect to nodes 3, 7, 11
            local targets=(3 7 11)
            ;;
        3) # Group 3: Connect to nodes 2, 8, 12
            local targets=(2 8 12)
            ;;
    esac
    
    # Add some additional connections for better connectivity
    if [ $node_id -ge 10 ]; then
        # Higher numbered nodes also connect to some lower numbered ones
        targets+=($(($node_id - 10)) $(($node_id - 5)))
    fi
    
    # Build seed list
    for target in "${targets[@]}"; do
        if [ $target -lt $NODES ] && [ $target -ne $node_id ] && [ $seed_count -lt $max_seeds ]; then
            local port=$((BASE_PORT + target))
            if [ -z "$seeds" ]; then
                seeds="127.0.0.1:$port"
            else
                seeds="$seeds,127.0.0.1:$port"
            fi
            seed_count=$((seed_count + 1))
        fi
    done
    
    # If no seeds generated, use a fallback
    if [ -z "$seeds" ]; then
        local fallback_target=$(( (node_id + 1) % NODES ))
        if [ $fallback_target -eq $node_id ]; then
            fallback_target=$(( (node_id + 2) % NODES ))
        fi
        seeds="127.0.0.1:$((BASE_PORT + fallback_target))"
    fi
    
    echo "$seeds"
}

# Function to start a node with P2P configuration
start_p2p_node() {
    local node_id=$1
    local port=$((BASE_PORT + node_id))
    local log_file="$LOGS_DIR/node-$node_id.log"
    local pid_file="$PIDS_DIR/node-$node_id.pid"
    
    # Generate diverse seed nodes for this node
    local seed_nodes=$(generate_seed_nodes $node_id)
    
    echo -e "${GREEN}Starting Node $node_id on port $port${NC}"
    echo -e "${BLUE}  Seeds: $seed_nodes${NC}"
    
    # Enhanced Java opts for P2P performance and prefer IPv4 stack to match 127.0.0.1 seeds
    JAVA_OPTS="-server -Xmx512m -Xms256m -XX:+UseG1GC -XX:+UseStringDeduplication -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"
    
    # P2P-optimized parameters
    cd "$PROJECT_ROOT"
    nohup java $JAVA_OPTS -cp "$JAR_FILE" io.xdag.p2p.example.StartApp \
        -p $port \
        -v $NETWORK_ID \
        -d 1 \
        -s "$seed_nodes" \
        -M 15 \
        -m 3 \
        -ma 2 \
        -msi 25 \
        > "$log_file" 2>&1 &
    
    echo $! > "$pid_file"
    echo -e "${GREEN}Node $node_id started with PID $(cat $pid_file)${NC}"
}

# Start nodes in phases to allow natural discovery
if [ $NODES -le 5 ]; then
    echo -e "${YELLOW}Phase 1: Starting nodes (0-$((NODES-1)))...${NC}"
    for i in $(seq 0 $((NODES - 1))); do
        start_p2p_node $i
        sleep 0.8
    done
else
    echo -e "${YELLOW}Phase 1: Starting initial nodes (0-4)...${NC}"
    for i in $(seq 0 4); do
        start_p2p_node $i
        sleep 1
    done

    if [ $NODES -gt 5 ]; then
        mid_end=$(( NODES - 1 ))
        if [ $mid_end -gt 14 ]; then mid_end=14; fi
        if [ $mid_end -ge 5 ]; then
            echo -e "${YELLOW}Phase 2: Starting middle nodes (5-$mid_end)...${NC}"
            for i in $(seq 5 $mid_end); do
                start_p2p_node $i
                sleep 0.5
            done
        fi
    fi

    if [ $NODES -gt 15 ]; then
        echo -e "${YELLOW}Phase 3: Starting final nodes (15-$((NODES-1)))...${NC}"
        for i in $(seq 15 $((NODES - 1))); do
            start_p2p_node $i
            sleep 0.3
        done
    fi
fi

echo ""
echo -e "${GREEN}=== True P2P Network Started Successfully! ===${NC}"
echo -e "${BLUE}Network Structure: Distributed connections (no central nodes)${NC}"
echo -e "${BLUE}Discovery: Enabled for organic network growth${NC}"
echo -e "${BLUE}Connections: 3-15 per node, encouraging P2P discovery${NC}"
echo ""
echo -e "${YELLOW}Network Analysis Commands:${NC}"
echo -e "${YELLOW}  Monitor topology: python3 clean-node-topology.py${NC}"
echo -e "${YELLOW}  View all logs:    tail -f $LOGS_DIR/node-*.log${NC}"
echo -e "${YELLOW}  Stop network:     ./stop-nodes.sh${NC}"
echo ""
echo -e "${BLUE}Ports: $BASE_PORT - $((BASE_PORT + NODES - 1))${NC}"

# Wait a bit and show initial connection status
echo -e "${YELLOW}Waiting ${INITIAL_WAIT}s for initial connections...${NC}"
sleep ${INITIAL_WAIT}

echo -e "${GREEN}=== Initial Connection Check ===${NC}"
total_connections=0
for i in $(seq 0 $((NODES - 1))); do
    if [ -f "$LOGS_DIR/node-$i.log" ]; then
        # Count either handshake success or channel activation logs as established connections
        connections=$(grep -E -c "Handshake successful|New channel connected" "$LOGS_DIR/node-$i.log" 2>/dev/null || true)
        connections=${connections:-0}
        total_connections=$(( total_connections + connections ))
        if [ $connections -gt 0 ]; then
            echo -e "${GREEN}Node $i: $connections connections${NC}"
        else
            echo -e "${YELLOW}Node $i: No connections yet${NC}"
        fi
    fi
done

echo -e "${BLUE}Total network connections (handshake successes): $total_connections${NC}"
echo -e "${YELLOW}Network will continue to evolve through P2P discovery...${NC}" 