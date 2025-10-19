#!/bin/bash
# Stop all P2P test nodes
#
# Usage: ./stop-nodes.sh
# Can be run from any test directory

set -e

# Try to find common.sh for nice logging
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/common.sh" ]; then
    source "$SCRIPT_DIR/common.sh"
else
    # Fallback logging if common.sh not available
    log_info() { echo "[INFO] $*"; }
    log_warning() { echo "[WARN] $*"; }
    cleanup_all_nodes() {
        local pids_dir="${1:-pids}"
        if [ -d "$pids_dir" ]; then
            for pid_file in "$pids_dir"/*.pid; do
                [ -f "$pid_file" ] || continue
                local pid=$(cat "$pid_file" 2>/dev/null || echo "")
                [ -n "$pid" ] && kill $pid 2>/dev/null && log_info "Stopped PID $pid" || true
                rm -f "$pid_file"
            done
        fi
        pkill -f "io.xdag.p2p.example" 2>/dev/null && log_info "Killed remaining processes" || true
    }
fi

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

