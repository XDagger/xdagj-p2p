#!/bin/bash
# Stop all P2P nodes

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}Stopping all P2P nodes...${NC}"

# Method 1: Kill by PID files
if [ -d "pids" ]; then
    for pid_file in pids/*.pid; do
        if [ -f "$pid_file" ]; then
            PID=$(cat "$pid_file" 2>/dev/null || echo "")
            if [ -n "$PID" ]; then
                kill $PID 2>/dev/null && echo -e "${GREEN}Stopped node with PID $PID${NC}" || true
            fi
            rm -f "$pid_file"
        fi
    done
fi

# Method 2: Kill by process name (backup)
pkill -f "io.xdag.p2p.example.StartApp" 2>/dev/null && echo -e "${GREEN}Killed remaining processes${NC}" || true

echo -e "${GREEN}All nodes stopped${NC}"
