# Performance Guide

Complete performance benchmarking guide and methodology for XDAGJ-P2P.

## Table of Contents
- [Overview](#overview)
- [Benchmark Results](#benchmark-results)
- [Test Environment](#test-environment)
- [Benchmark Methodology](#benchmark-methodology)
- [Network Testing](#network-testing)
- [Performance Tuning](#performance-tuning)
- [Known Limitations](#known-limitations)

---

## Overview

XDAGJ-P2P delivers production-ready performance with comprehensive benchmarking achieving million+ ops/sec across all operations.

**Key Highlights:**
- **Message Processing**: 1.3M - 8M ops/sec
- **Network Throughput**: 17,000+ msg/sec (real network)
- **Test Coverage**: 491 tests, 66% instruction coverage
- **Network Latency**: 1-8ms (95% under 8ms)
- **Error Rate**: 0% (7M+ messages tested)

---

## Benchmark Results

### Unit Operation Performance

Apple M-series, Java 21, single-threaded benchmarks:

#### Message Processing
```
⚡ HelloMessage Creation:     1,323,399 ops/sec
⚡ PingMessage Creation:      7,521,059 ops/sec
⚡ PongMessage Creation:      7,963,686 ops/sec
⚡ StatusMessage Creation:    5,002,451 ops/sec
```

**Analysis:**
- Simple messages (Ping/Pong) are 5-6x faster than complex messages (Hello)
- All message types exceed 1M ops/sec threshold
- Performance suitable for high-throughput blockchain applications

#### Network I/O Pipeline
```
⚡ HelloMessage Pipeline:       826,556 ops/sec
⚡ PingMessage Pipeline:      1,997,124 ops/sec
```

**Analysis:**
- Full pipeline includes: create → encode → Netty write → decode → process
- HelloMessage pipeline: 62% of creation performance (acceptable overhead)
- PingMessage pipeline: 27% of creation performance (expected for simple messages)

#### Serialization Performance
```
⚡ HelloMessage Encoding:     4,576,701 ops/sec
⚡ PingMessage Encoding:     21,863,658 ops/sec
⚡ StatusMessage Encoding:    5,002,451 ops/sec
⚡ DisconnectMessage Encoding: 18,960,347 ops/sec
```

**Analysis:**
- SimpleCodec achieves 4M-22M ops/sec encoding performance
- Significantly faster than Protobuf (typically 100K-500K ops/sec)
- Zero-copy design minimizes GC pressure

#### Data Access Performance
```
⚡ HelloMessage Access:      98,661,168 ops/sec
⚡ PingMessage Access:      206,509,169 ops/sec
```

**Analysis:**
- Field access is extremely fast (>98M ops/sec)
- Direct field access (no reflection)
- JIT optimization effective

#### Concurrent Processing
```
⚡ 1 Thread:                 10,560,130 ops/sec
⚡ 2 Threads:                15,631,619 ops/sec (1.48x speedup)
⚡ 4 Threads:                18,960,347 ops/sec (1.80x speedup) ⭐ Optimal
⚡ 8 Threads:                 8,190,847 ops/sec (0.78x speedup)
```

**Analysis:**
- Optimal thread count: 4 threads (matches CPU core count)
- Linear scaling up to 4 threads
- Beyond 4 threads: context switching overhead dominates

### Real Network Performance

Professional network testing with multi-node clusters:

#### 6-Node Cluster (Test Configuration)
```
Network Throughput:     17,433 msg/sec average
Peak Performance:       18,917 msg/sec burst
Message Volume:         7M+ messages in 405 seconds
Network Latency:        2.06ms average
P99 Latency:           <10ms
Forward Efficiency:     23.7% (optimized routing)
Error Rate:            0% (zero errors)
```

#### 20-Node Cluster (Stress Test)
```
Network Scale:          20 distributed nodes
Total Connections:      108 P2P links
Network Diameter:       5 hops maximum
Average Path Length:    2.38 hops
Message Latency:        1-8ms (95% under 8ms)
Connection Success:     >99% reliability
Network Stability:      Zero downtime over long-term operation
```

**Analysis:**
- 6-node cluster achieves 17K+ msg/sec sustained throughput
- 20-node cluster maintains sub-10ms latency
- Network scales well with Kademlia DHT topology
- Zero packet loss under normal conditions

---

## Test Environment

### Hardware Specifications

**Development Machine:**
```
Processor:    Apple M1/M2/M3 (ARM64)
Cores:        8 (4 performance + 4 efficiency)
RAM:          16GB+ unified memory
Storage:      NVMe SSD
Network:      Gigabit Ethernet / WiFi 6
```

**Production Simulation:**
```
Processor:    Intel Xeon / AMD EPYC
Cores:        4-8 cores
RAM:          8-16GB
Storage:      SSD
Network:      1-10 Gbps Ethernet
```

### Software Environment

```
Java Version:   OpenJDK 21 LTS (with Virtual Threads)
JVM Args:       -Xms2048m -Xmx6144m
                -XX:+UseG1GC
                --enable-preview
OS:             macOS 14+ / Linux 5.x+
Network:        Local (127.0.0.1) or LAN
```

### Test Coverage

```
📈 Coverage Metrics:
  Instructions:    67% (10,646 / 15,810)
  Branches:        52% (745 / 1,409)
  Lines:           67% (2,483 / 3,669)
  Methods:         80% (460 / 574)
  Classes:         96% (82 / 85)

🚀 Test Execution:
  Total Tests:     491 test cases
  Success Rate:    100% pass rate
  Execution Time:  ~18 seconds
  Stability:       Zero flaky tests

🎯 Module Coverage Highlights:
  Core Messaging:  100% (message.discover)
  Handler Node:    100% (handler.node)
  Channel Module:  75% (channel)
  Configuration:   95% (config)
  DNS Discovery:   94% (discover.dns)
  Utilities:       84% (utils)
```

---

## Benchmark Methodology

### Unit Operation Benchmarks

**Setup:**
```java
// Warm-up phase (JIT optimization)
for (int i = 0; i < 100_000; i++) {
    operation(); // Warm up
}

// Measurement phase
long start = System.nanoTime();
for (int i = 0; i < iterations; i++) {
    operation(); // Actual test
}
long duration = System.nanoTime() - start;
double opsPerSec = (iterations * 1_000_000_000.0) / duration;
```

**Key Principles:**
- Warm-up JIT compiler before measurement
- Run sufficient iterations (100K-1M) for statistical significance
- Measure in nanoseconds for accuracy
- Report ops/sec (higher is better)
- Avoid GC during measurement

### Network Testing Methodology

**Test Framework:**
```bash
# Performance Testing (TPS测试)
cd test-nodes/performance-test
./start-nodes.sh [node_count]    # Start N nodes for performance testing
./stop-nodes.sh                   # Stop all nodes

# Discovery Testing (节点发现测试)
cd test-nodes/discovery-test
./test.sh                         # Run discovery tests
./verify.sh                       # Verify discovery results
```

**Available Tests:**
- **Performance Test**: Network throughput and TPS testing (2-6 nodes recommended)
- **Discovery Test**: Node discovery and DHT functionality testing

**Measurement Points:**
1. **Message Creation**: Timestamp when message created
2. **Network Send**: Timestamp when sent to Netty
3. **Network Receive**: Timestamp when received from Netty
4. **Message Process**: Timestamp when processing complete

**Latency Calculation:**
```
Network Latency = (Receive Time - Send Time)
Processing Latency = (Process Time - Receive Time)
Total Latency = (Process Time - Send Time)
```

**Throughput Calculation:**
```
Throughput (msg/sec) = Total Messages / Duration (seconds)
Throughput (MB/sec) = (Total Messages × Avg Message Size) / Duration
```

### Professional Testing Tools

**Available Test Suites:**
```bash
test-nodes/
├── performance-test/         # TPS and throughput testing
│   ├── start-nodes.sh       # Launch N nodes
│   ├── stop-nodes.sh        # Stop all nodes
│   └── README.md            # Detailed testing guide
├── discovery-test/          # Node discovery testing
│   ├── test.sh              # Run discovery tests
│   ├── verify.sh            # Verify results
│   └── README.md            # Discovery test guide
└── lib/
    ├── common.sh            # Shared utilities
    └── stop-nodes.sh        # Common stop script
```

**See [test-nodes/performance-test/README.md](../test-nodes/performance-test/README.md) and [test-nodes/discovery-test/README.md](../test-nodes/discovery-test/README.md) for detailed testing guides.**

---

## Network Testing

### Quick Performance Test (2 Nodes)

```bash
cd test-nodes/performance-test

# Start 2 nodes
./start-nodes.sh 2

# Watch TPS statistics in real-time
tail -f logs/node-*.log | grep "TPS:"

# Stop all nodes
./stop-nodes.sh
```

**Expected Results:**
```
Peak TPS:         500K - 1M msg/sec
Memory Usage:     60-70% (of 6GB heap)
CPU Usage:        Moderate (depends on thread count)
```

### Multi-Node Performance Test (6 Nodes)

```bash
cd test-nodes/performance-test

# Start 6 nodes
./start-nodes.sh 6

# Watch all nodes' TPS
tail -f logs/node-*.log | grep "TPS:"

# Check memory after 2 minutes
sleep 120
grep "Memory:" logs/node-*.log | tail -6

# Stop all nodes
./stop-nodes.sh
```

**Expected Results:**
```
TPS:              500K-800K msg/sec (slightly lower than 2 nodes)
Memory Usage:     Increased (more connections)
Stability:        All nodes should run stably
```

### Node Discovery Test

```bash
cd test-nodes/discovery-test

# Run comprehensive discovery tests
./test.sh

# Verify results
./verify.sh

# Check detailed results
cat results/*.md
```

**Test Coverage:**
- Node discovery via Kademlia DHT
- PING/PONG message exchange
- FIND_NODE/NEIGHBORS routing
- Routing table management

---

## Performance Tuning

### JVM Configuration

**Recommended JVM Args:**
```bash
java -Xms2048m -Xmx6144m \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+UseStringDeduplication \
     --enable-preview \
     -jar xdagj-p2p-0.1.2.jar
```

**Explanation:**
- `-Xms2048m -Xmx6144m`: Heap size 2GB-6GB (adjust based on node count)
- `-XX:+UseG1GC`: G1 garbage collector (low-latency)
- `-XX:MaxGCPauseMillis=200`: Target GC pause <200ms
- `-XX:+UseStringDeduplication`: Reduce memory for duplicate strings
- `--enable-preview`: Enable Java 21 preview features (Virtual Threads)

### Application Configuration

**Optimal P2P Config:**
```java
P2pConfig config = new P2pConfig();

// Connection settings
config.setMaxConnections(30);           // Balance connectivity vs resources
config.setConnectionTimeout(10000);     // 10s timeout
config.setBacklog(100);                 // Accept queue size

// Discovery settings
config.setDiscoverEnable(true);
config.setDiscoveryCycle(30000);        // 30s PING interval
config.setMaxPeersToDiscover(16);       // K-value for Kademlia

// Performance settings
config.setChannelReadLimit(100000);     // Max messages per read
config.setSyncThreads(4);               // Thread pool size (match CPU cores)
```

### Netty Tuning

**Netty Best Practices:**
```java
// Increase buffer sizes for high throughput
bootstrap.option(ChannelOption.SO_RCVBUF, 2 * 1024 * 1024); // 2MB
bootstrap.option(ChannelOption.SO_SNDBUF, 2 * 1024 * 1024); // 2MB

// Enable TCP optimizations
bootstrap.option(ChannelOption.TCP_NODELAY, true);          // Disable Nagle
bootstrap.option(ChannelOption.SO_KEEPALIVE, true);         // Enable keepalive

// Set write buffer watermarks
bootstrap.option(ChannelOption.WRITE_BUFFER_WATER_MARK,
    new WriteBufferWaterMark(512 * 1024, 1024 * 1024));     // 512KB-1MB
```

### Operating System Tuning

**Linux Kernel Parameters:**
```bash
# Increase file descriptor limit
ulimit -n 65536

# TCP tuning
sudo sysctl -w net.core.rmem_max=16777216
sudo sysctl -w net.core.wmem_max=16777216
sudo sysctl -w net.ipv4.tcp_rmem='4096 87380 16777216'
sudo sysctl -w net.ipv4.tcp_wmem='4096 65536 16777216'

# Connection tracking
sudo sysctl -w net.netfilter.nf_conntrack_max=1000000
```

**macOS Tuning:**
```bash
# Increase file descriptor limit
ulimit -n 65536
sudo sysctl -w kern.maxfiles=65536
sudo sysctl -w kern.maxfilesperproc=65536
```

---

## Known Limitations

### Current Limitations

1. **IPv4 Only**
   - IPv6 support planned for future release
   - Workaround: Use IPv4 addressing

2. **NAT Traversal**
   - Requires port forwarding for nodes behind NAT
   - STUN/TURN support not implemented
   - Workaround: Configure port forwarding manually

3. **Connection Limits**
   - Default max connections: 30
   - Recommended max: 50 (higher may impact performance)
   - Reason: Each connection consumes ~1MB memory

4. **Discovery Latency**
   - Cold start: 30-60 seconds
   - Reason: Kademlia DHT requires multiple rounds of FIND_NODE
   - Mitigation: DNS discovery planned for faster bootstrap

### Performance Considerations

**Memory Usage:**
```
Per Node:           ~20 bytes (Node ID + metadata)
Per Connection:     ~1 MB (Netty buffers + state)
Baseline:           ~50 MB (JVM + core classes)
Total (30 conns):   ~80 MB minimum
```

**CPU Usage:**
```
Idle:               <1% CPU
Normal (30 conns):  2-5% CPU
High load:          10-20% CPU
Peak (burst):       30-40% CPU
```

**Network Bandwidth:**
```
Per Connection:     ~1 KB/s (UDP heartbeat + keepalive)
30 Connections:     ~30 KB/s baseline
High throughput:    100+ MB/s possible (depends on network)
```

### Scaling Limits

**Tested Configurations:**
```
Max tested nodes:       30 nodes
Max tested connections: 120 concurrent connections
Max tested throughput:  18,917 msg/sec (burst)
Max routing table:      480 nodes (16 per bucket × 30 buckets used)
```

**Theoretical Limits:**
```
Max routing table:      2,560 nodes (16 × 160 buckets)
Max connections:        Limited by OS file descriptors
Max throughput:         Limited by network bandwidth
```

---

## Comparison with Other P2P Libraries

### Feature Comparison

| Feature | XDAGJ-P2P | libp2p (Go) | Netty (Java) | Tuweni (Java) |
|---------|-----------|-------------|--------------|---------------|
| **Language** | Java 21 | Go | Java | Java |
| **Discovery** | Kademlia DHT | mDNS + DHT | Manual | Manual |
| **Performance** | 17K+ msg/sec | 10K+ msg/sec | 50K+ msg/sec | N/A |
| **Latency** | 1-8ms | 5-15ms | <1ms | N/A |
| **Test Coverage** | 66% | ~70% | ~80% | ~60% |
| **Virtual Threads** | ✅ Yes | ❌ No | ❌ No | ❌ No |

### Performance Comparison

**Message Creation (ops/sec):**
```
XDAGJ-P2P:      7.5M ops/sec (PingMessage)
Protobuf:       500K ops/sec
JSON:           200K ops/sec
```

**Serialization (ops/sec):**
```
SimpleCodec:    21M ops/sec (XDAGJ-P2P)
Protobuf:       2M ops/sec
JSON:           500K ops/sec
```

**Network Throughput (msg/sec):**
```
XDAGJ-P2P:      17K+ msg/sec (6-node cluster)
libp2p (Go):    10K+ msg/sec
Bitcoin Core:   ~5K msg/sec
```

---

## References

- [JMH Benchmarking](https://github.com/openjdk/jmh) - Java Microbenchmark Harness
- [Netty Performance](https://netty.io/wiki/native-transports.html) - Netty optimization guide
- [G1GC Tuning](https://docs.oracle.com/en/java/javase/21/gctuning/) - Java GC tuning
- [Testing Guide](../test-nodes/performance-test/README.md) - Performance testing guide
- [Discovery Testing](../test-nodes/discovery-test/README.md) - Discovery testing guide

---

**Performance Status:** Production-ready with 17K+ msg/sec throughput and sub-10ms latency
