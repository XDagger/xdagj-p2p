#!/bin/bash

# Script to clean up all test artifacts

set -e

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOGS_DIR="$SCRIPT_DIR/logs"
PIDS_DIR="$SCRIPT_DIR/pids"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Cleaning up P2P test artifacts ===${NC}"

# Stop nodes first
echo -e "${YELLOW}Stopping all running nodes...${NC}"
"$SCRIPT_DIR/stop-nodes.sh"

# Remove logs directory
if [ -d "$LOGS_DIR" ]; then
    echo -e "${YELLOW}Removing logs directory...${NC}"
    rm -rf "$LOGS_DIR"
    echo -e "${GREEN}Logs directory removed${NC}"
fi

# Remove PIDs directory (should be empty after stopping nodes)
if [ -d "$PIDS_DIR" ]; then
    echo -e "${YELLOW}Removing PIDs directory...${NC}"
    rm -rf "$PIDS_DIR"
    echo -e "${GREEN}PIDs directory removed${NC}"
fi

# Optional: Clean Maven target directory
read -p "$(echo -e ${YELLOW}Do you want to clean Maven target directory? [y/N]: ${NC})" -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    cd "$SCRIPT_DIR/.."
    echo -e "${YELLOW}Cleaning Maven target...${NC}"
    mvn clean
    echo -e "${GREEN}Maven target cleaned${NC}"
fi

echo -e "${GREEN}=== Cleanup completed ===${NC}" 