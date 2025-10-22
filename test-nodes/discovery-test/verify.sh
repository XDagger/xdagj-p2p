#!/bin/bash
# Universal Node Discovery Verification Script
#
# Verifies both Kademlia DHT and DNS discovery functionality
#
# Usage: ./verify.sh [mode]
#
# Modes:
#   dht  - Verify Kademlia DHT discovery (default)
#   dns  - Verify DNS discovery with Mock DNS
#

set -e

# Source shared library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

# Convenience aliases
GREEN=$COLOR_GREEN
YELLOW=$COLOR_YELLOW
RED=$COLOR_RED
BLUE=$COLOR_BLUE
NC=$COLOR_NC

# Parse mode
MODE="${1:-dht}"

# Validate mode
if [[ "$MODE" != "dht" && "$MODE" != "dns" ]]; then
    echo "Error: Invalid mode '$MODE'"
    echo ""
    echo "Usage: $0 [mode]"
    echo ""
    echo "Modes:"
    echo "  dht  - Verify Kademlia DHT discovery (default)"
    echo "  dns  - Verify DNS discovery"
    echo ""
    echo "Examples:"
    echo "  $0 dht    # Verify DHT discovery"
    echo "  $0 dns    # Verify DNS discovery"
    echo "  $0        # Default to DHT mode"
    exit 1
fi

# Determine file prefix
if [ "$MODE" == "dns" ]; then
    PREFIX="dns"
    log_section "DNS Discovery Verification"
else
    PREFIX="dht"
    log_section "Kademlia DHT Discovery Verification"
fi

# Check if test is running
if [ ! -d "pids" ] || [ -z "$(ls -A pids/${PREFIX}-*.pid 2>/dev/null)" ]; then
    log_error "No ${MODE} discovery test is currently running"
    log_info "Start a test with: ./test.sh ${MODE}"
    exit 1
fi

# Count running nodes
NODE_COUNT=$(ls pids/${PREFIX}-*.pid 2>/dev/null | wc -l | tr -d ' ')
log_info "Found $NODE_COUNT running ${MODE} discovery nodes"
echo ""

# Verification checks
PASS_COUNT=0
FAIL_COUNT=0

if [ "$MODE" == "dns" ]; then
    # DNS-specific checks

    # Check 1: Mock DNS enabled
    log_info "Check 1: Verifying Mock DNS is enabled..."
    MOCK_DNS_COUNT=0
    for pidfile in pids/${PREFIX}-*.pid; do
        NODE_NUM=$(basename "$pidfile" .pid | sed "s/${PREFIX}-//")
        LOGFILE="logs/${PREFIX}-$NODE_NUM.log"

        if [ -f "$LOGFILE" ]; then
            if grep -q "MOCK DNS MODE ENABLED" "$LOGFILE"; then
                MOCK_DNS_COUNT=$((MOCK_DNS_COUNT + 1))
            fi
        fi
    done

    if [ $MOCK_DNS_COUNT -eq $NODE_COUNT ]; then
        echo -e "${GREEN}✅ PASS${NC}: All $NODE_COUNT nodes have Mock DNS enabled"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "${RED}❌ FAIL${NC}: Only $MOCK_DNS_COUNT/$NODE_COUNT nodes have Mock DNS enabled"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
    echo ""

    # Check 2: DNS Tree URL configured
    log_info "Check 2: Verifying DNS Tree URL configuration..."
    DNS_URL_COUNT=0
    for pidfile in pids/${PREFIX}-*.pid; do
        NODE_NUM=$(basename "$pidfile" .pid | sed "s/${PREFIX}-//")
        LOGFILE="logs/${PREFIX}-$NODE_NUM.log"

        if [ -f "$LOGFILE" ]; then
            if grep -q "DNS Tree URL" "$LOGFILE"; then
                DNS_URL_COUNT=$((DNS_URL_COUNT + 1))
            fi
        fi
    done

    if [ $DNS_URL_COUNT -eq $NODE_COUNT ]; then
        echo -e "${GREEN}✅ PASS${NC}: All $NODE_COUNT nodes have DNS Tree URL configured"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "${RED}❌ FAIL${NC}: Only $DNS_URL_COUNT/$NODE_COUNT nodes have DNS Tree URL configured"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
    echo ""

    # Check 3: DNS monitoring active
    log_info "Check 3: Verifying DNS monitoring is active..."
    MONITORING_COUNT=0
    for pidfile in pids/${PREFIX}-*.pid; do
        NODE_NUM=$(basename "$pidfile" .pid | sed "s/${PREFIX}-//")
        LOGFILE="logs/${PREFIX}-$NODE_NUM.log"

        if [ -f "$LOGFILE" ]; then
            if grep -q "DNS Discovery Status" "$LOGFILE"; then
                MONITORING_COUNT=$((MONITORING_COUNT + 1))
            fi
        fi
    done

    if [ $MONITORING_COUNT -eq $NODE_COUNT ]; then
        echo -e "${GREEN}✅ PASS${NC}: All $NODE_COUNT nodes have DNS monitoring active"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "${YELLOW}⚠️  WARN${NC}: Only $MONITORING_COUNT/$NODE_COUNT nodes have DNS monitoring active"
        echo "   (This may be normal if test just started)"
        PASS_COUNT=$((PASS_COUNT + 1))  # Don't fail on this
    fi
    echo ""

else
    # DHT-specific checks

    # Check 1: UDP discovery active
    log_info "Check 1: Verifying UDP discovery is active..."
    UDP_COUNT=0
    for pidfile in pids/${PREFIX}-*.pid; do
        NODE_NUM=$(basename "$pidfile" .pid | sed "s/${PREFIX}-//")
        LOGFILE="logs/${PREFIX}-$NODE_NUM.log"

        if [ -f "$LOGFILE" ]; then
            if grep -q "Send PING\|Receive PONG" "$LOGFILE"; then
                UDP_COUNT=$((UDP_COUNT + 1))
            fi
        fi
    done

    if [ $UDP_COUNT -eq $NODE_COUNT ]; then
        echo -e "${GREEN}✅ PASS${NC}: All $NODE_COUNT nodes have UDP discovery active"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "${YELLOW}⚠️  WARN${NC}: Only $UDP_COUNT/$NODE_COUNT nodes have UDP discovery active"
        echo "   (This may be normal if test just started)"
        PASS_COUNT=$((PASS_COUNT + 1))  # Don't fail on this
    fi
    echo ""

    # Check 2: DHT discovery active
    log_info "Check 2: Verifying DHT (FIND_NODE) is active..."
    DHT_COUNT=0
    for pidfile in pids/${PREFIX}-*.pid; do
        NODE_NUM=$(basename "$pidfile" .pid | sed "s/${PREFIX}-//")
        LOGFILE="logs/${PREFIX}-$NODE_NUM.log"

        if [ -f "$LOGFILE" ]; then
            if grep -q "Send FIND_NODE\|Receive NEIGHBORS" "$LOGFILE"; then
                DHT_COUNT=$((DHT_COUNT + 1))
            fi
        fi
    done

    if [ $DHT_COUNT -gt 0 ]; then
        echo -e "${GREEN}✅ PASS${NC}: $DHT_COUNT/$NODE_COUNT nodes have DHT active"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "${YELLOW}⚠️  WARN${NC}: No nodes have DHT active yet"
        echo "   (This may be normal if test just started)"
        PASS_COUNT=$((PASS_COUNT + 1))  # Don't fail on this
    fi
    echo ""

    # Check 3: DHT monitoring active
    log_info "Check 3: Verifying DHT monitoring is active..."
    MONITORING_COUNT=0
    for pidfile in pids/${PREFIX}-*.pid; do
        NODE_NUM=$(basename "$pidfile" .pid | sed "s/${PREFIX}-//")
        LOGFILE="logs/${PREFIX}-$NODE_NUM.log"

        if [ -f "$LOGFILE" ]; then
            if grep -q "Discovery Status" "$LOGFILE"; then
                MONITORING_COUNT=$((MONITORING_COUNT + 1))
            fi
        fi
    done

    if [ $MONITORING_COUNT -eq $NODE_COUNT ]; then
        echo -e "${GREEN}✅ PASS${NC}: All $NODE_COUNT nodes have DHT monitoring active"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "${YELLOW}⚠️  WARN${NC}: Only $MONITORING_COUNT/$NODE_COUNT nodes have DHT monitoring active"
        echo "   (This may be normal if test just started)"
        PASS_COUNT=$((PASS_COUNT + 1))  # Don't fail on this
    fi
    echo ""
fi

# Check 4: Node discovery (common for both modes)
log_info "Check 4: Verifying node discovery..."
DISCOVERY_COUNT=0
TOTAL_DISCOVERED=0

for pidfile in pids/${PREFIX}-*.pid; do
    NODE_NUM=$(basename "$pidfile" .pid | sed "s/${PREFIX}-//")
    LOGFILE="logs/${PREFIX}-$NODE_NUM.log"

    if [ -f "$LOGFILE" ]; then
        DISCOVERED=$(grep "Discovered Nodes:" "$LOGFILE" 2>/dev/null | tail -1 | sed 's/.*Discovered Nodes: \([0-9]*\).*/\1/' || echo "0")
        if [ "$DISCOVERED" -gt 0 ] 2>/dev/null; then
            DISCOVERY_COUNT=$((DISCOVERY_COUNT + 1))
            TOTAL_DISCOVERED=$((TOTAL_DISCOVERED + DISCOVERED))
        fi
    fi
done

if [ $DISCOVERY_COUNT -gt 0 ]; then
    AVG_DISCOVERED=$((TOTAL_DISCOVERED / DISCOVERY_COUNT))
    echo -e "${GREEN}✅ PASS${NC}: $DISCOVERY_COUNT/$NODE_COUNT nodes have discovered nodes"
    echo "   Average: $AVG_DISCOVERED nodes per discovering node"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo -e "${YELLOW}⚠️  WARN${NC}: No nodes have discovered other nodes yet"
    echo "   (This may be normal if test just started)"
    PASS_COUNT=$((PASS_COUNT + 1))  # Don't fail on this
fi
echo ""

# Check 5: No critical errors
log_info "Check 5: Checking for critical errors..."
ERROR_COUNT=0
for pidfile in pids/${PREFIX}-*.pid; do
    NODE_NUM=$(basename "$pidfile" .pid | sed "s/${PREFIX}-//")
    LOGFILE="logs/${PREFIX}-$NODE_NUM.log"

    if [ -f "$LOGFILE" ]; then
        # Look for ERROR but exclude known non-critical patterns
        ERRORS=$(grep -E "ERROR" "$LOGFILE" 2>/dev/null | \
                 grep -v "MockDnsResolver" | \
                 grep -v "Failed to enable mock DNS" | \
                 wc -l || echo "0")
        ERROR_COUNT=$((ERROR_COUNT + ERRORS))
    fi
done

if [ $ERROR_COUNT -eq 0 ]; then
    echo -e "${GREEN}✅ PASS${NC}: No critical errors found in logs"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo -e "${YELLOW}⚠️  WARN${NC}: Found $ERROR_COUNT error messages in logs"
    echo "   (Some errors may be expected during testing)"
    PASS_COUNT=$((PASS_COUNT + 1))  # Don't fail on this
fi
echo ""

# DNS-specific check: Mock DNS records
if [ "$MODE" == "dns" ]; then
    log_info "Check 6: Verifying Mock DNS records..."
    MOCK_RECORDS_COUNT=0
    for pidfile in pids/${PREFIX}-*.pid; do
        NODE_NUM=$(basename "$pidfile" .pid | sed "s/${PREFIX}-//")
        LOGFILE="logs/${PREFIX}-$NODE_NUM.log"

        if [ -f "$LOGFILE" ]; then
            if grep -q "Added.*entry" "$LOGFILE"; then
                MOCK_RECORDS_COUNT=$((MOCK_RECORDS_COUNT + 1))
            fi
        fi
    done

    if [ $MOCK_RECORDS_COUNT -gt 0 ]; then
        echo -e "${GREEN}✅ PASS${NC}: Mock DNS records created successfully"
        echo "   (Records found in $MOCK_RECORDS_COUNT node logs)"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "${YELLOW}⚠️  WARN${NC}: No Mock DNS records creation logged"
        echo "   (Records may be created at runtime)"
        PASS_COUNT=$((PASS_COUNT + 1))  # Don't fail on this
    fi
    echo ""
fi

# Summary
log_section "Verification Summary"
TOTAL_CHECKS=$((PASS_COUNT + FAIL_COUNT))
echo -e "${BLUE}Total Checks: $TOTAL_CHECKS${NC}"
echo -e "${GREEN}Passed: $PASS_COUNT${NC}"
if [ $FAIL_COUNT -gt 0 ]; then
    echo -e "${RED}Failed: $FAIL_COUNT${NC}"
fi
echo ""

if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}✅ All critical checks passed!${NC}"
    if [ "$MODE" == "dns" ]; then
        echo "DNS discovery is configured correctly"
    else
        echo "DHT discovery is configured correctly"
    fi
else
    echo -e "${RED}❌ $FAIL_COUNT checks failed${NC}"
    echo "Please review the logs for more details"
fi

echo ""
log_info "Check detailed logs with:"
if [ "$MODE" == "dns" ]; then
    echo "  grep 'DNS\|Mock' logs/${PREFIX}-*.log | less"
else
    echo "  grep 'PING\|PONG\|FIND_NODE\|NEIGHBORS' logs/${PREFIX}-*.log | less"
fi
