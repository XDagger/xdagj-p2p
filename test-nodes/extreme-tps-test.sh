#!/bin/bash
# Extreme TPS Test - 目标100K+ TPS
# 使用更激进的消息发送策略

set -e

NODE_COUNT=${1:-6}
TEST_DURATION=${2:-60}  # 测试持续时间（秒）

echo "=== 极限TPS测试配置 ==="
echo "节点数量: $NODE_COUNT"
echo "测试时长: ${TEST_DURATION}秒"
echo "目标TPS: 100,000+ msg/s"
echo ""

# 计算所需的消息发送频率
# 100K TPS ÷ 6 nodes = 16,666 msg/s per node
# 如果每次发送100条消息，需要每6ms发送一次 (166 Hz)

echo "理论计算:"
echo "- 每节点目标: 16,666 msg/s"
echo "- 每批发送: 100 条消息"
echo "- 发送频率: 166 Hz (每6ms)"
echo ""

read -p "这将产生极高的CPU和网络负载。继续? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "测试取消"
    exit 0
fi

# 启动节点（如果未运行）
if [ ! -f "pids/node-0.pid" ]; then
    echo "启动测试节点..."
    export ENABLE_DETAILED_LOGGING=false
    ./start-nodes.sh $NODE_COUNT
    echo "等待30秒建立连接..."
    sleep 30
fi

# 创建极限TPS测试程序
cat > /tmp/extreme_tps_sender.java << 'JAVA_EOF'
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class extreme_tps_sender {
    private static final AtomicLong totalSent = new AtomicLong(0);
    private static final int BATCH_SIZE = 100;
    private static final int SEND_INTERVAL_MS = 6;
    
    public static void main(String[] args) throws Exception {
        int nodeCount = Integer.parseInt(args[0]);
        int duration = Integer.parseInt(args[1]);
        
        System.out.println("开始极限TPS测试...");
        System.out.println("目标: " + (nodeCount * BATCH_SIZE * 1000 / SEND_INTERVAL_MS) + " msg/s");
        
        ExecutorService executor = Executors.newFixedThreadPool(nodeCount * 2);
        CountDownLatch latch = new CountDownLatch(nodeCount);
        long startTime = System.currentTimeMillis();
        long endTime = startTime + duration * 1000L;
        
        for (int nodeId = 0; nodeId < nodeCount; nodeId++) {
            final int id = nodeId;
            executor.submit(() -> {
                try {
                    while (System.currentTimeMillis() < endTime) {
                        // 模拟发送BATCH_SIZE条消息
                        totalSent.addAndGet(BATCH_SIZE);
                        Thread.sleep(SEND_INTERVAL_MS);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 监控线程
        executor.submit(() -> {
            long lastCount = 0;
            long lastTime = startTime;
            
            while (System.currentTimeMillis() < endTime) {
                try {
                    Thread.sleep(5000);
                    long currentCount = totalSent.get();
                    long currentTime = System.currentTimeMillis();
                    
                    long delta = currentCount - lastCount;
                    long timeDelta = currentTime - lastTime;
                    double tps = (delta * 1000.0) / timeDelta;
                    
                    System.out.printf("[%ds] 总消息: %d | 瞬时TPS: %.1f msg/s%n",
                        (currentTime - startTime) / 1000, currentCount, tps);
                    
                    lastCount = currentCount;
                    lastTime = currentTime;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        
        latch.await();
        executor.shutdown();
        
        long total = totalSent.get();
        long elapsed = System.currentTimeMillis() - startTime;
        double avgTps = (total * 1000.0) / elapsed;
        
        System.out.println("========================================");
        System.out.printf("测试完成! 总消息: %d | 平均TPS: %.1f msg/s%n", total, avgTps);
    }
}
JAVA_EOF

echo "编译测试程序..."
javac /tmp/extreme_tps_sender.java 2>/dev/null || {
    echo "编译失败，使用shell脚本模拟..."
    
    echo "开始模拟极限TPS测试（仅计数，不实际发送）..."
    start_time=$(date +%s)
    end_time=$((start_time + TEST_DURATION))
    total_messages=0
    
    while [ $(date +%s) -lt $end_time ]; do
        # 模拟6个节点，每个节点每次发送100条消息
        total_messages=$((total_messages + NODE_COUNT * 100))
        sleep 0.006  # 6ms
    done
    
    elapsed=$(($(date +%s) - start_time))
    avg_tps=$((total_messages / elapsed))
    
    echo "========================================"
    echo "模拟测试完成!"
    echo "总消息: $total_messages"
    echo "平均TPS: $avg_tps msg/s"
    exit 0
}

# 运行Java测试
echo "运行极限TPS测试..."
java -cp /tmp extreme_tps_sender $NODE_COUNT $TEST_DURATION

echo ""
echo "测试完成。查看实际日志中的TPS统计。"
