#!/bin/bash
# 节点发现快速验证脚本
# 分析现有日志，检查发现功能是否正常

echo "=== 节点发现功能验证 ==="
echo ""

# 检查是否有日志
if [ ! -d "logs" ] || [ -z "$(ls -A logs/*.log 2>/dev/null)" ]; then
    echo "❌ 没有找到日志文件"
    echo "请先运行: ./start-nodes.sh 或 ./test-discovery.sh"
    exit 1
fi

echo "📊 1. 检查节点发现功能是否启用"
echo "----------------------------------------"

for log in logs/*.log; do
    node_name=$(basename "$log" .log)

    # 检查discovery是否启用
    if grep -q "Discovery enabled: true" "$log" 2>/dev/null; then
        echo "✅ $node_name: Discovery已启用"
    else
        echo "❌ $node_name: Discovery未启用或未找到配置日志"
    fi
done

echo ""
echo "📡 2. Kademlia DHT消息统计"
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
        echo "  PING发送: $ping_sent | PONG接收: $pong_recv"
        echo "  FIND_NODE: $find_node | NEIGHBORS: $neighbors"
        echo "  总计: $total 条消息"
    else
        echo "⚠️  $node_name: 无Kademlia消息（可能功能未启用或日志级别不足）"
    fi
    echo ""
done

echo "🔍 3. 节点表 (DHT) 统计"
echo "----------------------------------------"

for log in logs/*.log; do
    node_name=$(basename "$log" .log)

    # 查找DHT节点数
    dht_info=$(grep "DHT nodes\|nodes in table\|table nodes" "$log" 2>/dev/null | tail -1)

    if [ -n "$dht_info" ]; then
        dht_count=$(echo "$dht_info" | grep -o "[0-9]\+" | head -1)
        echo "$node_name: DHT节点数 = $dht_count"
    else
        echo "⚠️  $node_name: 未找到DHT统计"
    fi
done

echo ""
echo "🌐 4. 节点发现进度"
echo "----------------------------------------"

for log in logs/*.log; do
    node_name=$(basename "$log" .log)

    # 查找已发现节点数
    discovered=$(grep "nodes discovered\|Discovered.*nodes" "$log" 2>/dev/null | tail -1)

    if [ -n "$discovered" ]; then
        count=$(echo "$discovered" | grep -o "[0-9]\+" | head -1)
        echo "$node_name: 已发现 $count 个节点"
    else
        # 尝试其他模式
        node_added=$(grep -c "Node added\|NodeHandler created" "$log" 2>/dev/null || echo "0")
        if [ $node_added -gt 0 ]; then
            echo "$node_name: 节点表有 $node_added 条记录"
        else
            echo "⚠️  $node_name: 未找到发现统计"
        fi
    fi
done

echo ""
echo "🔗 5. TCP连接状态"
echo "----------------------------------------"

for log in logs/*.log; do
    node_name=$(basename "$log" .log)

    # 查找连接数
    connections=$(grep "Total channels\|Active channels\|Connected.*total" "$log" 2>/dev/null | tail -1)

    if [ -n "$connections" ]; then
        count=$(echo "$connections" | grep -o "[0-9]\+" | tail -1)
        echo "$node_name: TCP连接数 = $count"
    else
        # 统计handshake成功次数
        handshakes=$(grep -c "Handshake successful\|handshake success" "$log" 2>/dev/null || echo "0")
        if [ $handshakes -gt 0 ]; then
            echo "$node_name: 握手成功 $handshakes 次"
        else
            echo "⚠️  $node_name: 未找到连接统计"
        fi
    fi
done

echo ""
echo "⚙️  6. DNS Discovery检查"
echo "----------------------------------------"

dns_found=false
for log in logs/*.log; do
    node_name=$(basename "$log" .log)

    # 检查DNS同步
    if grep -q "SyncTree\|DNS sync" "$log" 2>/dev/null; then
        dns_found=true
        sync_info=$(grep "SyncTree" "$log" 2>/dev/null | tail -1)
        echo "✅ $node_name: DNS Discovery已启用"
        if [ -n "$sync_info" ]; then
            echo "   $sync_info"
        fi
    fi
done

if ! $dns_found; then
    echo "ℹ️  未检测到DNS Discovery活动（可能未配置DNS URL）"
fi

echo ""
echo "📋 7. 诊断建议"
echo "----------------------------------------"

# 分析问题
has_kad_messages=false
for log in logs/*.log; do
    if grep -q "PING\|PONG\|FIND_NODE\|NEIGHBORS" "$log" 2>/dev/null; then
        has_kad_messages=true
        break
    fi
done

if ! $has_kad_messages; then
    echo "❌ 问题: 未检测到Kademlia消息"
    echo ""
    echo "可能原因："
    echo "  1. 日志级别过高（DEBUG消息被过滤）"
    echo "  2. Discovery功能未正确启动"
    echo "  3. UDP端口被防火墙阻止"
    echo ""
    echo "解决方案："
    echo "  1. 检查日志配置: cat ../src/main/resources/logback.xml"
    echo "  2. 确认-d参数: grep '\\-d' start-nodes.sh"
    echo "  3. 检查UDP端口: lsof -i UDP:10000-10009"
    echo ""
else
    echo "✅ Kademlia消息检测正常"
fi

# 检查连接
connection_count=0
for log in logs/*.log; do
    conn=$(grep "Total channels" "$log" 2>/dev/null | tail -1 | grep -o "[0-9]\+" | tail -1 || echo "0")
    connection_count=$((connection_count + conn))
done

if [ $connection_count -eq 0 ]; then
    echo "⚠️  警告: 所有节点TCP连接数为0"
    echo ""
    echo "可能原因："
    echo "  1. 节点刚启动，还未建立连接"
    echo "  2. 种子节点配置错误"
    echo "  3. 端口被占用或防火墙阻止"
    echo ""
    echo "解决方案："
    echo "  1. 等待30秒后重新检查"
    echo "  2. 检查端口: lsof -i TCP:10000-10009"
    echo "  3. 查看错误日志: grep -i error logs/*.log"
fi

echo ""
echo "📈 8. 性能指标建议"
echo "----------------------------------------"
echo ""
echo "节点发现正常指标（10个节点）："
echo "  ✅ 30秒: 每节点发现 2-3 个节点"
echo "  ✅ 1分钟: 每节点发现 4-6 个节点"
echo "  ✅ 2分钟: 每节点发现 7-9 个节点"
echo "  ✅ 5分钟: 每节点发现 >90% 节点"
echo ""
echo "Kademlia消息速率："
echo "  ✅ 正常: <100 msg/sec/node"
echo "  ⚠️  偏高: 100-500 msg/sec/node"
echo "  ❌ 异常: >500 msg/sec/node"
echo ""

echo "💡 9. 推荐操作"
echo "----------------------------------------"
echo ""
echo "查看详细Kademlia日志:"
echo "  grep 'KadService\|DiscoverTask\|NodeHandler' logs/node-0.log | head -50"
echo ""
echo "监控实时发现:"
echo "  tail -f logs/node-0.log | grep 'discover\|FIND_NODE\|DHT'"
echo ""
echo "运行完整发现测试:"
echo "  ./test-discovery.sh 10 300"
echo ""
echo "分析网络拓扑:"
echo "  grep 'Handshake successful' logs/*.log | wc -l  # 总连接数"
echo ""
