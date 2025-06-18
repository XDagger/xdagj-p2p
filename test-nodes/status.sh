#!/bin/bash

# Quick status check script for P2P nodes

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PIDS_DIR="$SCRIPT_DIR/pids"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== P2P Nodes Quick Status ===${NC}"

if [ ! -d "$PIDS_DIR" ]; then
    echo -e "${RED}No nodes found${NC}"
    exit 0
fi

running=0
stopped=0

for pid_file in "$PIDS_DIR"/node-*.pid; do
    if [ -f "$pid_file" ]; then
        node_name=$(basename "$pid_file" .pid)
        pid=$(cat "$pid_file" 2>/dev/null || echo "")
        
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            echo -e "${GREEN}✓${NC} $node_name (PID: $pid)"
            ((running++))
        else
            echo -e "${RED}✗${NC} $node_name (not running)"
            ((stopped++))
        fi
    fi
done

echo -e "${BLUE}==========================${NC}"
echo -e "Running: ${GREEN}$running${NC} | Stopped: ${RED}$stopped${NC}"

# Show listening ports
echo -e "${BLUE}=== Listening Ports ===${NC}"
lsof -i :10000-10019 | grep java | wc -l | xargs echo "Active UDP listeners:" 