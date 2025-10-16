# P2P Network Performance Testing

Test P2P network message transmission performance (TPS - Transactions Per Second)

---

## 🎯 Test Objectives

Verify the xdagj-p2p network module's:
- **Throughput**: How many messages/sec the network can handle
- **Stability**: Whether it runs stably for long periods
- **Resource Efficiency**: CPU and memory usage
- **Concurrency**: Multi-threaded sending performance

**Note**: This is **network-level performance testing**, excluding block validation, consensus, and other business logic.

---

## 📊 Performance Metrics

### Current Results

```
Test Configuration: 2 nodes, 8 sender threads, 6GB heap
Peak TPS: 500K - 1M msg/sec
Memory Usage: 60-70%
Duplicate Rate: 97% (caused by message forwarding)
```

### Real Blockchain Comparison

| Network | Network TPS | Per-Node Message Rate |
|---------|------------|----------------------|
| Bitcoin | 7 tx/sec | < 10 msg/sec |
| Ethereum | 15-30 tx/sec | < 100 msg/sec |
| **Current Test** | **800K msg/sec** | **800K msg/sec** |

**Difference Reasons**:
- ✅ Tests pure network transmission, no business logic
- ❌ 97% duplicate rate (caused by message forwarding)
- ❌ Small messages (tens of bytes vs real blocks 1MB)
- ❌ Local environment (no network latency)

**Conclusion**: Current test proves underlying network capability, but doesn't represent real blockchain performance.

See: [docs/blockchain-reality-check.md](docs/blockchain-reality-check.md)

---

## 🚀 Quick Start

### 1. Basic Performance Test (2 Nodes)

```bash
# Start 2 nodes
./start-nodes.sh 2

# Watch TPS statistics (updates every 5 seconds)
tail -f logs/node-*.log | grep "TPS:"

# Expected output:
# [node-10000] Uptime: 20s | TPS: 819073 | Messages: 21,464,754 | ...
# [node-10001] Uptime: 20s | TPS: 794315 | Messages: 19,441,214 | ...
```

**Duration**: Recommended 1-5 minutes

---

### 2. Multi-Node Performance Test (6 Nodes)

```bash
# Start 6 nodes
./start-nodes.sh 6

# Watch all nodes' TPS
tail -f logs/node-*.log | grep "TPS:"

# Check memory after 2 minutes
sleep 120
grep "Memory:" logs/node-*.log | tail -6
```

**Expected**:
- TPS may decrease slightly (500K-800K)
- Memory usage increases
- All nodes should run stably

---

### 3. Verify Send/Receive Functionality

```bash
# Run verification script
./verify-send-receive.sh

# Example output:
# ✅ Sender: All messages sent via TCP (no loss)
# ✅ Receiver: messageCounter counts all network receives (including duplicates)
# ✅ Bloom Filter: Filters at application layer, doesn't affect network stats
```

---

## 📁 Files Description

### Scripts

| File | Function | Usage |
|------|----------|-------|
| `start-nodes.sh` | Start nodes | `./start-nodes.sh [node_count]` |
| `stop-nodes.sh` | Stop all nodes | `./stop-nodes.sh` |
| `verify-send-receive.sh` | Verify send/receive | `./verify-send-receive.sh` |
| `analyze-performance.py` | Performance analysis (Python) | `python3 analyze-performance.py` |
| `compare-with-reality.sh` | Compare with real blockchains | `./compare-with-reality.sh` |

### Log Files

- `logs/node-*.log` - Node runtime logs
- `pids/node-*.pid` - Node process IDs

### Documentation

- `docs/blockchain-reality-check.md` - Reality check comparison
- `docs/detailed-analysis.md` - Detailed analysis

---

## 🔧 Configuration Parameters

### start-nodes.sh Configuration

```bash
NODE_COUNT=6          # Number of nodes
BASE_PORT=10000       # Starting port
NETWORK_ID=1          # Network ID

# JVM parameters
-Xms2048m             # Initial heap 2GB
-Xmx6144m             # Max heap 6GB

# Application parameters
-p $PORT              # Listening port
-d 1                  # Enable Discovery (but performance test doesn't rely on it)
-s $SEEDS             # TCP seed nodes
```

### Performance Parameters (in code)

```java
// StartApp.java
int senderThreads = 8;        // Number of sender threads
int batchSize = 100;          // Batch send size
int sleepMs = 1;              // Batch interval (milliseconds)
int monitorInterval = 5;      // TPS statistics interval (seconds)
```

---

## 📈 Performance Tuning

### 1. Increase Throughput

**Method A: Increase sender threads**
```java
// Modify StartApp.java:147
scheduler = Executors.newScheduledThreadPool(17); // 16 sender threads + 1 monitor
for (int i = 0; i < 16; i++) {
    scheduler.submit(this::tpsSender);
}
```

**Warning**: Too many threads may cause contention and performance degradation!

**Method B: Increase batch size**
```java
// Modify StartApp.java:176
for (int i = 0; i < 200; i++) {  // Change from 100 to 200
    eventHandler.sendNetworkTestMessage(...);
}
```

**Method C: Reduce sleep time**
```java
// Modify StartApp.java:182
Thread.sleep(0);  // Change from 1ms to 0 (Risk: CPU 100%)
```

---

### 2. Reduce Memory Usage

**Method A: Reduce Bloom Filter capacity**
```java
// Modify ExampleEventHandler.java:67
private static final int EXPECTED_INSERTIONS = 100_000;  // Change from 200K to 100K
```

**Method B: Reduce Guava Cache size**
```java
// Modify ExampleEventHandler.java:118
.maximumSize(25_000)  // Change from 50K to 25K
```

**Method C: Reduce heap memory**
```bash
# Modify start-nodes.sh:126
-Xmx3072m  # Change from 6GB to 3GB
```

---

### 3. Reduce Duplicate Rate (More Realistic)

**Current Issue**: 97% duplicate rate due to message forwarding (maxHops=3)

**Solution**: Implement Inv-GetData pattern

```java
// Don't send full message, send hash first
sendInv(messageHash);

// Peer checks if they have it
if (!hasMessage(hash)) {
    requestGetData(hash);
}

// Only send full message when needed
sendFullMessage(message);
```

See: [docs/blockchain-reality-check.md](docs/blockchain-reality-check.md)

---

## 📊 Key Metrics Interpretation

### TPS (Transactions Per Second)

```
[node-10000] TPS: 819073 | Messages: 21,464,754
                   ↑                    ↑
            Current 5s rate     Total received messages
```

**Calculation**:
```
TPS = (Current total - Previous total) / 5 seconds * 1000
```

**Meaning**:
- Messages received per second at network layer
- **Includes duplicate messages** (due to forwarding)
- Measures network throughput capability

---

### Memory Usage

```
Memory: 4107/6144MB (66.8%)
         ↑     ↑      ↑
      Used   Max   Usage %
```

**Health Standards**:
- ✅ < 70%: Healthy
- ⚠️  70-90%: High
- ❌ > 90%: Dangerous (possible OOM)

**Memory Components**:
- Bloom Filter: ~120KB (negligible)
- Guava Cache: ~2-5MB (50K entries)
- Netty buffers: Most memory
- Message objects: Depends on TPS

---

### Connections

```
Connections: 2
```

**In performance testing**:
- 2 nodes → Max 2 connections
- 6 nodes → 3-5 connections per node

**Note**: Performance testing uses TCP direct connect, doesn't rely on node discovery

---

## ⚠️ Important Warnings

### 1. This is NOT Real Blockchain Performance

Current test limitations:

❌ **No Business Logic**
- No block validation
- No signature verification
- No state updates
- No consensus algorithm

❌ **97% Duplicate Rate**
- Normal blockchain should be < 5%
- Need to implement Inv-GetData

❌ **Small Messages**
- Test messages: tens of bytes
- Real blocks: 1-2MB

❌ **Local Network**
- No network latency
- No packet loss
- No bandwidth limits

### 2. What Real Scenarios Should Test

✅ **Block Propagation Test**
- Time for 1MB block to reach 50% of nodes
- Target: < 5 seconds

✅ **Transaction Broadcast Test**
- Time for transaction to reach 95% of nodes
- Target: < 3 seconds

✅ **Bandwidth Efficiency**
- Effective data / Total traffic
- Target: > 90%

See: [docs/blockchain-reality-check.md](docs/blockchain-reality-check.md)

---

## 🐛 Troubleshooting

### Issue 1: Very Low TPS (< 10K)

**Possible Causes**:
- Thread configuration error
- Insufficient memory
- Connection not established

**Check Steps**:
```bash
# 1. Check connections
grep "Total channels\|Connected" logs/node-*.log

# 2. Check threads
grep "sender threads" start-nodes.sh

# 3. Check memory
grep "Memory:" logs/node-*.log | tail -5

# 4. View errors
grep -i error logs/node-*.log
```

---

### Issue 2: Memory Keeps Growing

**Possible Causes**:
- Memory leak
- Bloom Filter not rotating
- Cache not cleaning

**Check Steps**:
```bash
# Run verification script
./verify-send-receive.sh

# Check Bloom Filter rotation
grep "Bloom Filter rotated" logs/node-*.log

# View cache size
grep "Cache:" logs/node-*.log
```

---

### Issue 3: Node Startup Failed

**Possible Causes**:
- Port already in use
- JAR file doesn't exist
- Insufficient memory

**Check Steps**:
```bash
# Check port
lsof -i TCP:10000-10010

# Check JAR
ls -lh ../target/xdagj-p2p-*.jar

# View startup log
tail -50 logs/node-0.log
```

---

## 📝 Test Checklist

Before running performance test, ensure:

- [ ] Built latest JAR: `mvn clean package -DskipTests`
- [ ] Ports not occupied: `lsof -i TCP:10000-10010`
- [ ] Sufficient memory: At least 2GB per node
- [ ] Stopped old nodes: `./stop-nodes.sh`
- [ ] Cleaned old logs: `rm -rf logs/* pids/*`

During testing, monitor:

- [ ] Is TPS stable
- [ ] Is memory continuously growing
- [ ] Are there error logs
- [ ] CPU usage

After testing, analyze:

- [ ] What is peak TPS
- [ ] What is average TPS
- [ ] Is memory usage stable
- [ ] Are there performance bottlenecks

---

## 🎓 Further Reading

1. **Reality Check**: [docs/blockchain-reality-check.md](docs/blockchain-reality-check.md)
   - Gap between current test and real blockchains
   - Areas needing improvement
   - Real scenario test plans

2. **Detailed Analysis**: [docs/detailed-analysis.md](docs/detailed-analysis.md)
   - Send/receive flow
   - Bloom Filter principles
   - Network TPS real meaning

---

## 🎯 Summary

### What This Test Proves:

✅ Underlying Netty framework is stable
✅ MessageQueue batch processing is effective
✅ Bloom Filter deduplication works
✅ System can handle high concurrency

### What This Test Does NOT Prove:

❌ Real blockchain performance
❌ Block validation speed
❌ Consensus algorithm efficiency
❌ Network performance in production environment

**Remember**: This is **network layer stress testing**, NOT **blockchain performance testing**!
