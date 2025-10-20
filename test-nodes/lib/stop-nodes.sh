#!/bin/bash
# Stop all P2P test nodes
#
# Usage: ./stop-nodes.sh
# Can be run from any test directory

set -e

# Source common library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# Determine PID directory
# If run from test dir, use local pids/, otherwise look for ../pids/
if [ -d "pids" ]; then
    PIDS_DIR="pids"
elif [ -d "../pids" ]; then
    PIDS_DIR="../pids"
else
    log_warning "No pids directory found, will only kill by process name"
    PIDS_DIR=""
fi

log_warning "Stopping all P2P nodes..."
cleanup_all_nodes "$PIDS_DIR"
log_info "All nodes stopped"

