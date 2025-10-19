#!/bin/bash
# Node Discovery Quick Verification Script
# Analyze existing logs to check if discovery is working correctly

echo "=== Node Discovery Verification ==="
echo ""

# Check if logs exist
if [ ! -d "logs" ] || [ -z "$(ls -A logs/*.log 2>/dev/null)" ]; then
    echo "❌ No log files found"
    echo "Please run first: ./start-nodes.sh or ./test-discovery.sh"
    exit 1
fi

echo "📊 1. Check Discovery Feature Status"
echo "----------------------------------------"

for log in logs/*.log; do
    node_name=$(basename "$log" .log)

    # Check if discovery is enabled
    if grep -q "Discovery enabled: true" "$log" 2>/dev/null; then
        echo "✅ $node_name: Discovery enabled"
    else
        echo "❌ $node_name: Discovery not enabled or config log not found"
    fi
done

echo ""
echo "📡 2. Kademlia DHT Message Statistics"
echo "----------------------------------------"

for log in logs/*.log; do
    node_name=$(basename "$log" .log)

    ping_sent=$(grep -c "Send.*PING\|Sending PING" "$log" 2>/dev/null || echo "0")
    pong_recv=$(grep -c "Receive.*PONG\|Received PONG" "$log" 2>/dev/null || echo "0")
    find_node=$(grep -c "Send.*FIND_NODE\|Sending FIND_NODE" "$log" 2>/dev/null || echo "0")
    neighbors=$(grep -c "Receive.*NEIGHBORS\|Received NEIGHBORS" "$log" 2>/dev/null || echo "0")

    total=$((ping_sent + pong_recv + find_node + neighbors))

    if [ $total -gt 0 ]; then
        echo "$node_name:"
        echo "  PING sent: $ping_sent | PONG received: $pong_recv"
        echo "  FIND_NODE: $find_node | NEIGHBORS: $neighbors"
        echo "  Total: $total messages"
    else
        echo "⚠️  $node_name: No Kademlia messages (feature may be disabled or insufficient log level)"
    fi
    echo ""
done

echo "🔍 3. Node Table (DHT) Statistics"
echo "----------------------------------------"

for log in logs/*.log; do
    node_name=$(basename "$log" .log)

    # Find DHT node count
    dht_info=$(grep "DHT nodes\|nodes in table\|table nodes" "$log" 2>/dev/null | tail -1)

    if [ -n "$dht_info" ]; then
        dht_count=$(echo "$dht_info" | grep -o "[0-9]\+" | head -1)
        echo "$node_name: DHT nodes = $dht_count"
    else
        echo "⚠️  $node_name: DHT statistics not found"
    fi
done

echo ""
echo "🌐 4. Node Discovery Progress"
echo "----------------------------------------"

for log in logs/*.log; do
    node_name=$(basename "$log" .log)

    # Find discovered node count
    discovered=$(grep "nodes discovered\|Discovered.*nodes" "$log" 2>/dev/null | tail -1)

    if [ -n "$discovered" ]; then
        count=$(echo "$discovered" | grep -o "[0-9]\+" | head -1)
        echo "$node_name: Discovered $count nodes"
    else
        # Try alternative patterns
        node_added=$(grep -c "Node added\|NodeHandler created" "$log" 2>/dev/null || echo "0")
        if [ $node_added -gt 0 ]; then
            echo "$node_name: Node table has $node_added entries"
        else
            echo "⚠️  $node_name: Discovery statistics not found"
        fi
    fi
done

echo ""
echo "🔗 5. TCP Connection Status"
echo "----------------------------------------"

for log in logs/*.log; do
    node_name=$(basename "$log" .log)

    # Find connection count
    connections=$(grep "Total channels\|Active channels\|Connected.*total" "$log" 2>/dev/null | tail -1)

    if [ -n "$connections" ]; then
        count=$(echo "$connections" | grep -o "[0-9]\+" | tail -1)
        echo "$node_name: TCP connections = $count"
    else
        # Count handshake successes
        handshakes=$(grep -c "Handshake successful\|handshake success" "$log" 2>/dev/null || echo "0")
        if [ $handshakes -gt 0 ]; then
            echo "$node_name: Handshake successful $handshakes times"
        else
            echo "⚠️  $node_name: Connection statistics not found"
        fi
    fi
done

echo ""
echo "⚙️  6. DNS Discovery Check"
echo "----------------------------------------"

dns_found=false
for log in logs/*.log; do
    node_name=$(basename "$log" .log)

    # Check DNS sync
    if grep -q "SyncTree\|DNS sync" "$log" 2>/dev/null; then
        dns_found=true
        sync_info=$(grep "SyncTree" "$log" 2>/dev/null | tail -1)
        echo "✅ $node_name: DNS Discovery enabled"
        if [ -n "$sync_info" ]; then
            echo "   $sync_info"
        fi
    fi
done

if ! $dns_found; then
    echo "ℹ️  No DNS Discovery activity detected (DNS URL may not be configured)"
fi

echo ""
echo "📋 7. Diagnostic Recommendations"
echo "----------------------------------------"

# Analyze issues
has_kad_messages=false
for log in logs/*.log; do
    if grep -q "PING\|PONG\|FIND_NODE\|NEIGHBORS" "$log" 2>/dev/null; then
        has_kad_messages=true
        break
    fi
done

if ! $has_kad_messages; then
    echo "❌ Issue: No Kademlia messages detected"
    echo ""
    echo "Possible causes:"
    echo "  1. Log level too high (DEBUG messages filtered)"
    echo "  2. Discovery feature not properly started"
    echo "  3. UDP ports blocked by firewall"
    echo ""
    echo "Solutions:"
    echo "  1. Check log config: cat ../src/main/resources/logback.xml"
    echo "  2. Verify -d parameter: grep '\\-d' start-nodes.sh"
    echo "  3. Check UDP ports: lsof -i UDP:10000-10009"
    echo ""
else
    echo "✅ Kademlia messages detected normally"
fi

# Check connections
connection_count=0
for log in logs/*.log; do
    conn=$(grep "Total channels" "$log" 2>/dev/null | tail -1 | grep -o "[0-9]\+" | tail -1 || echo "0")
    connection_count=$((connection_count + conn))
done

if [ $connection_count -eq 0 ]; then
    echo "⚠️  Warning: All nodes have 0 TCP connections"
    echo ""
    echo "Possible causes:"
    echo "  1. Nodes just started, connections not established yet"
    echo "  2. Seed node configuration error"
    echo "  3. Ports occupied or blocked by firewall"
    echo ""
    echo "Solutions:"
    echo "  1. Wait 30 seconds and check again"
    echo "  2. Check ports: lsof -i TCP:10000-10009"
    echo "  3. View error logs: grep -i error logs/*.log"
fi

echo ""
echo "📈 8. Performance Metric Guidelines"
echo "----------------------------------------"
echo ""
echo "Normal discovery metrics (10 nodes):"
echo "  ✅ 30 seconds: 2-3 nodes discovered per node"
echo "  ✅ 1 minute: 4-6 nodes discovered per node"
echo "  ✅ 2 minutes: 7-9 nodes discovered per node"
echo "  ✅ 5 minutes: >90% nodes discovered per node"
echo ""
echo "Kademlia message rate:"
echo "  ✅ Normal: <100 msg/sec/node"
echo "  ⚠️  High: 100-500 msg/sec/node"
echo "  ❌ Abnormal: >500 msg/sec/node"
echo ""

echo "💡 9. Recommended Actions"
echo "----------------------------------------"
echo ""
echo "View detailed Kademlia logs:"
echo "  grep 'KadService\|DiscoverTask\|NodeHandler' logs/node-0.log | head -50"
echo ""
echo "Monitor real-time discovery:"
echo "  tail -f logs/node-0.log | grep 'discover\|FIND_NODE\|DHT'"
echo ""
echo "Run complete discovery test:"
echo "  ./test-discovery.sh 10 300"
echo ""
echo "Analyze network topology:"
echo "  grep 'Handshake successful' logs/*.log | wc -l  # Total connections"
echo ""
