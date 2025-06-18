#!/bin/bash

# Enhanced P2P Network Performance Monitor
# Provides real-time network statistics, performance metrics, and error monitoring

set -e

# Script directory and configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOGS_DIR="$SCRIPT_DIR/logs"
PIDS_DIR="$SCRIPT_DIR/pids"
RESULTS_DIR="$SCRIPT_DIR/results"

# Colors and formatting
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
BOLD='\033[1m'
NC='\033[0m'

# Monitoring configuration
REFRESH_INTERVAL=3
STATS_HISTORY=10
ERROR_THRESHOLD=5

# Create results directory
mkdir -p "$RESULTS_DIR"

# Utility functions
is_running() {
    local pid=$1
    [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

format_number() {
    local num=$1
    if [ "$num" -gt 1000000 ]; then
        echo "$(( num / 1000000 ))M"
    elif [ "$num" -gt 1000 ]; then
        echo "$(( num / 1000 ))K"
    else
        echo "$num"
    fi
}

# Enhanced node statistics extraction
get_enhanced_stats() {
    local log_file=$1
    local node_id=$2
    
    if [ ! -f "$log_file" ]; then
        echo "N/A|N/A|N/A|N/A|N/A|N/A|N/A"
        return
    fi
    
    # Get recent statistics (last 100 lines for better accuracy)
    local recent_logs=$(tail -100 "$log_file")
    
    # Extract network test statistics
    local net_stats=$(echo "$recent_logs" | grep "Network test statistics" | tail -1)
    local received=0
    local forwarded=0
    local duplicates=0
    local unique=0
    local avg_latency="N/A"
    
    if [ -n "$net_stats" ]; then
        received=$(echo "$net_stats" | sed -n 's/.*received: \([0-9]*\).*/\1/p')
        forwarded=$(echo "$net_stats" | sed -n 's/.*forwarded: \([0-9]*\).*/\1/p')
        duplicates=$(echo "$net_stats" | sed -n 's/.*duplicates: \([0-9]*\).*/\1/p')
        unique=$(echo "$net_stats" | sed -n 's/.*unique: \([0-9]*\).*/\1/p')
        avg_latency=$(echo "$net_stats" | sed -n 's/.*average latency: \([0-9.]*\)ms.*/\1/p')
    fi
    
    # Count errors in recent logs
    local errors=$(echo "$recent_logs" | grep -c "ERROR\|Exception" || echo "0")
    
    # Count active connections
    local connections=$(echo "$recent_logs" | grep "Channel active" | wc -l | tr -d ' ')
    
    echo "$received|$forwarded|$duplicates|$unique|$avg_latency|$errors|$connections"
}

# Get test message type distribution
get_message_distribution() {
    local log_file=$1
    
    if [ ! -f "$log_file" ]; then
        echo "N/A"
        return
    fi
    
    # Count different test message types from recent activity
    local types=$(tail -200 "$log_file" | grep -o 'type: [^)]*' | sed 's/type: //' | sort | uniq -c | sort -nr | head -3)
    
    if [ -n "$types" ]; then
        echo "$types" | awk '{printf "%s:%s ", $2, $1}' | sed 's/ $//'
    else
        echo "No data"
    fi
}

# Network topology analysis
analyze_topology() {
    echo -e "\n${BOLD}${CYAN}=== Network Topology Analysis ===${NC}"
    
    local total_connections=0
    local total_messages=0
    local total_errors=0
    
    for pid_file in "$PIDS_DIR"/node-*.pid; do
        if [ -f "$pid_file" ]; then
            local node_name=$(basename "$pid_file" .pid)
            local log_file="$LOGS_DIR/$node_name.log"
            
            if [ -f "$log_file" ]; then
                local stats=$(get_enhanced_stats "$log_file" "${node_name#node-}")
                IFS='|' read -r received forwarded duplicates unique latency errors connections <<< "$stats"
                
                total_connections=$((total_connections + connections))
                total_messages=$((total_messages + received))
                total_errors=$((total_errors + errors))
            fi
        fi
    done
    
    echo -e "${YELLOW}Total Network Connections: ${GREEN}$total_connections${NC}"
    echo -e "${YELLOW}Total Messages Processed: ${GREEN}$(format_number $total_messages)${NC}"
    echo -e "${YELLOW}Total Network Errors: ${RED}$total_errors${NC}"
    
    # Network health assessment
    local health="Unknown"
    local health_color=$YELLOW
    
    if [ "$total_errors" -lt "$ERROR_THRESHOLD" ] && [ "$total_connections" -gt 0 ]; then
        health="Healthy"
        health_color=$GREEN
    elif [ "$total_errors" -ge "$ERROR_THRESHOLD" ]; then
        health="Issues Detected"
        health_color=$RED
    elif [ "$total_connections" -eq 0 ]; then
        health="No Connections"
        health_color=$YELLOW
    fi
    
    echo -e "${YELLOW}Network Health: ${health_color}$health${NC}"
}

# Performance benchmark
show_performance_metrics() {
    echo -e "\n${BOLD}${CYAN}=== Performance Metrics ===${NC}"
    
    printf "%-8s %-10s %-10s %-10s %-8s %-12s\n" "Node" "Msg/s" "Latency" "CPU%" "Memory" "Throughput"
    echo -e "${BLUE}$(printf '%.0s-' {1..70})${NC}"
    
    for pid_file in "$PIDS_DIR"/node-*.pid; do
        if [ -f "$pid_file" ]; then
            local node_name=$(basename "$pid_file" .pid)
            local node_id=${node_name#node-}
            local pid=$(cat "$pid_file" 2>/dev/null || echo "")
            local log_file="$LOGS_DIR/$node_name.log"
            
            if [ -n "$pid" ] && is_running "$pid"; then
                # Get process metrics
                local cpu_mem=$(ps -o %cpu,%mem -p "$pid" 2>/dev/null | tail -1 | awk '{print $1"|"$2}')
                IFS='|' read -r cpu memory <<< "$cpu_mem"
                
                # Get network stats
                local stats=$(get_enhanced_stats "$log_file" "$node_id")
                IFS='|' read -r received forwarded duplicates unique latency errors connections <<< "$stats"
                
                # Calculate messages per second (rough estimate)
                local msg_per_sec="N/A"
                if [ "$received" -gt 0 ]; then
                    # Estimate based on recent activity
                    local recent_count=$(tail -50 "$log_file" | grep -c "Received network test message" || echo "0")
                    msg_per_sec=$((recent_count * 60 / REFRESH_INTERVAL))
                fi
                
                # Calculate throughput (KB/s estimate)
                local throughput="N/A"
                if [ "$received" -gt 0 ]; then
                    throughput="$(( (received * 512) / 1024 ))KB/s"
                fi
                
                printf "%-8s %-10s %-10s %-8s %-12s %-12s\n" \
                    "$node_name" "$msg_per_sec" "${latency}ms" "${cpu}%" "${memory}%" "$throughput"
            else
                printf "%-8s %-10s %-10s %-8s %-12s %-12s\n" \
                    "$node_name" "OFFLINE" "OFFLINE" "N/A" "N/A" "N/A"
            fi
        fi
    done
}

# Real-time network monitoring
monitor_network() {
    while true; do
        clear
        echo -e "${BOLD}${BLUE}===============================================================================${NC}"
        echo -e "${BOLD}${BLUE}                    XDAG P2P Network Performance Monitor${NC}"
        echo -e "${BOLD}${BLUE}===============================================================================${NC}"
        echo -e "${CYAN}$(date '+%Y-%m-%d %H:%M:%S') | Refresh: ${REFRESH_INTERVAL}s | Press Ctrl+C to exit${NC}"
        
        if [ ! -d "$PIDS_DIR" ]; then
            echo -e "${YELLOW}No nodes found. Run ./start-multi-nodes.sh first.${NC}"
            sleep $REFRESH_INTERVAL
            continue
        fi
        
        # Main status table
        echo -e "\n${BOLD}${CYAN}=== Node Status & Network Statistics ===${NC}"
        printf "%-8s %-8s %-8s %-8s %-8s %-8s %-8s %-6s\n" \
            "Node" "Status" "Recv" "Fwd" "Dup" "Unique" "Latency" "Errors"
        echo -e "${BLUE}$(printf '%.0s-' {1..70})${NC}"
        
        local running_count=0
        local total_count=0
        
        for pid_file in "$PIDS_DIR"/node-*.pid; do
            if [ -f "$pid_file" ]; then
                local node_name=$(basename "$pid_file" .pid)
                local node_id=${node_name#node-}
                local pid=$(cat "$pid_file" 2>/dev/null || echo "")
                local log_file="$LOGS_DIR/$node_name.log"
                
                ((total_count++))
                
                local status_color=$RED
                local status="DOWN"
                if [ -n "$pid" ] && is_running "$pid"; then
                    status_color=$GREEN
                    status="UP"
                    ((running_count++))
                fi
                
                local stats=$(get_enhanced_stats "$log_file" "$node_id")
                IFS='|' read -r received forwarded duplicates unique latency errors connections <<< "$stats"
                
                # Color code latency
                local latency_color=$GREEN
                if [ "$latency" != "N/A" ] && [ "${latency%.*}" -gt 2 ]; then
                    latency_color=$YELLOW
                fi
                if [ "$latency" != "N/A" ] && [ "${latency%.*}" -gt 5 ]; then
                    latency_color=$RED
                fi
                
                # Color code errors
                local error_color=$GREEN
                if [ "$errors" -gt 0 ]; then
                    error_color=$YELLOW
                fi
                if [ "$errors" -gt $ERROR_THRESHOLD ]; then
                    error_color=$RED
                fi
                
                printf "%-8s ${status_color}%-8s${NC} %-8s %-8s %-8s %-8s ${latency_color}%-8s${NC} ${error_color}%-6s${NC}\n" \
                    "$node_name" "$status" "$(format_number $received)" "$(format_number $forwarded)" \
                    "$(format_number $duplicates)" "$(format_number $unique)" "${latency}ms" "$errors"
            fi
        done
        
        echo -e "${BLUE}$(printf '%.0s-' {1..70})${NC}"
        echo -e "${YELLOW}Nodes: $total_count | Running: ${GREEN}$running_count${NC} | Offline: ${RED}$((total_count - running_count))${NC}"
        
        # Show topology analysis
        analyze_topology
        
        # Show performance metrics
        show_performance_metrics
        
        # Show recent test message distribution for first node
        local first_log="$LOGS_DIR/node-0.log"
        if [ -f "$first_log" ]; then
            echo -e "\n${BOLD}${CYAN}=== Test Message Distribution (Node-0) ===${NC}"
            local distribution=$(get_message_distribution "$first_log")
            echo -e "${YELLOW}$distribution${NC}"
        fi
        
        # Warning for high error rates
        local total_errors=$(find "$LOGS_DIR" -name "*.log" -exec grep -c "ERROR\|Exception" {} + 2>/dev/null | awk '{sum+=$1} END {print sum+0}')
        if [ "$total_errors" -gt $((ERROR_THRESHOLD * total_count)) ]; then
            echo -e "\n${RED}${BOLD}⚠️  WARNING: High error rate detected! Check logs for details.${NC}"
        fi
        
        sleep $REFRESH_INTERVAL
    done
}

# Export statistics to file
export_stats() {
    local output_file="$RESULTS_DIR/network_stats_$(date +%Y%m%d_%H%M%S).json"
    echo -e "${YELLOW}Exporting network statistics to: $output_file${NC}"
    
    echo '{' > "$output_file"
    echo '  "timestamp": "'$(date -Iseconds)'",' >> "$output_file"
    echo '  "nodes": [' >> "$output_file"
    
    local first=true
    for pid_file in "$PIDS_DIR"/node-*.pid; do
        if [ -f "$pid_file" ]; then
            local node_name=$(basename "$pid_file" .pid)
            local node_id=${node_name#node-}
            local pid=$(cat "$pid_file" 2>/dev/null || echo "")
            local log_file="$LOGS_DIR/$node_name.log"
            
            [ "$first" = false ] && echo '    ,' >> "$output_file"
            first=false
            
            echo '    {' >> "$output_file"
            echo "      \"node_id\": \"$node_id\"," >> "$output_file"
            echo "      \"status\": \"$([ -n "$pid" ] && is_running "$pid" && echo "running" || echo "stopped")\"," >> "$output_file"
            
            if [ -f "$log_file" ]; then
                local stats=$(get_enhanced_stats "$log_file" "$node_id")
                IFS='|' read -r received forwarded duplicates unique latency errors connections <<< "$stats"
                
                echo "      \"received_messages\": $received," >> "$output_file"
                echo "      \"forwarded_messages\": $forwarded," >> "$output_file"
                echo "      \"duplicate_messages\": $duplicates," >> "$output_file"
                echo "      \"unique_messages\": $unique," >> "$output_file"
                echo "      \"average_latency_ms\": \"$latency\"," >> "$output_file"
                echo "      \"error_count\": $errors," >> "$output_file"
                echo "      \"active_connections\": $connections" >> "$output_file"
            fi
            
            echo '    }' >> "$output_file"
        fi
    done
    
    echo '  ]' >> "$output_file"
    echo '}' >> "$output_file"
    
    echo -e "${GREEN}Statistics exported successfully!${NC}"
}

# Show detailed logs with filtering
show_filtered_logs() {
    local node_id=$1
    local filter=${2:-""}
    local log_file="$LOGS_DIR/node-$node_id.log"
    
    if [ ! -f "$log_file" ]; then
        echo -e "${RED}Log file not found: $log_file${NC}"
        exit 1
    fi
    
    echo -e "${BLUE}=== Node $node_id Logs ===${NC}"
    
    if [ -n "$filter" ]; then
        echo -e "${YELLOW}Filter: $filter${NC}"
        case "$filter" in
            "errors")
                tail -f "$log_file" | grep --color=always "ERROR\|Exception"
                ;;
            "stats")
                tail -f "$log_file" | grep --color=always "statistics\|test message"
                ;;
            "network")
                tail -f "$log_file" | grep --color=always "Channel\|Connect\|Disconnect"
                ;;
            *)
                tail -f "$log_file" | grep --color=always "$filter"
                ;;
        esac
    else
        tail -f "$log_file"
    fi
}

# Help function
show_help() {
    echo -e "${BOLD}${BLUE}Enhanced P2P Network Monitor${NC}"
    echo ""
    echo -e "${YELLOW}Usage:${NC}"
    echo -e "  $0                          # Monitor all nodes with enhanced metrics"
    echo -e "  $0 -l <node_id> [filter]    # Show filtered logs for specific node"
    echo -e "  $0 -e                       # Export current statistics to JSON"
    echo -e "  $0 -p                       # Show performance metrics only"
    echo -e "  $0 -h                       # Show this help"
    echo ""
    echo -e "${YELLOW}Log Filters:${NC}"
    echo -e "  errors                      # Show only errors and exceptions"
    echo -e "  stats                       # Show only statistics and test messages"
    echo -e "  network                     # Show only network connections"
    echo -e "  <custom>                    # Show lines containing custom text"
    echo ""
    echo -e "${YELLOW}Examples:${NC}"
    echo -e "  $0                          # Full network monitoring dashboard"
    echo -e "  $0 -l 0 errors             # Show only errors from node-0"
    echo -e "  $0 -l 1 stats              # Show only statistics from node-1"
    echo -e "  $0 -e                       # Export current network stats"
}

# Parse command line arguments
while getopts "l:eph" opt; do
    case $opt in
        l)
            shift $((OPTIND-2))
            show_filtered_logs "$OPTARG" "$1"
            exit 0
            ;;
        e)
            export_stats
            exit 0
            ;;
        p)
            show_performance_metrics
            exit 0
            ;;
        h)
            show_help
            exit 0
            ;;
        \?)
            echo -e "${RED}Invalid option: -$OPTARG${NC}" >&2
            show_help
            exit 1
            ;;
    esac
done

# Default action: enhanced network monitoring
monitor_network 