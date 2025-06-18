#!/bin/bash

# Script to stop all running P2P nodes

set -e

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PIDS_DIR="$SCRIPT_DIR/pids"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Stopping P2P Nodes ===${NC}"

if [ ! -d "$PIDS_DIR" ]; then
    echo -e "${YELLOW}No PID directory found. No nodes to stop.${NC}"
    exit 0
fi

stopped_count=0
failed_count=0

# Stop all nodes
for pid_file in "$PIDS_DIR"/node-*.pid; do
    if [ -f "$pid_file" ]; then
        node_name=$(basename "$pid_file" .pid)
        pid=$(cat "$pid_file")
        
        if kill -0 "$pid" 2>/dev/null; then
            echo -e "${YELLOW}Stopping $node_name (PID: $pid)...${NC}"
            if kill "$pid" 2>/dev/null; then
                # Wait for process to terminate
                for i in {1..10}; do
                    if ! kill -0 "$pid" 2>/dev/null; then
                        break
                    fi
                    sleep 0.5
                done
                
                # Force kill if still running
                if kill -0 "$pid" 2>/dev/null; then
                    echo -e "${RED}Force killing $node_name...${NC}"
                    kill -9 "$pid" 2>/dev/null || true
                fi
                
                echo -e "${GREEN}$node_name stopped${NC}"
                ((stopped_count++))
            else
                echo -e "${RED}Failed to stop $node_name${NC}"
                ((failed_count++))
            fi
        else
            echo -e "${YELLOW}$node_name was not running${NC}"
        fi
        
        # Remove PID file
        rm -f "$pid_file"
    fi
done

echo ""
if [ $stopped_count -gt 0 ]; then
    echo -e "${GREEN}Successfully stopped $stopped_count nodes${NC}"
fi

if [ $failed_count -gt 0 ]; then
    echo -e "${RED}Failed to stop $failed_count nodes${NC}"
fi

# Clean up empty directories
if [ -d "$PIDS_DIR" ] && [ -z "$(ls -A "$PIDS_DIR")" ]; then
    rmdir "$PIDS_DIR"
fi

echo -e "${BLUE}=== Stop operation completed ===${NC}" 