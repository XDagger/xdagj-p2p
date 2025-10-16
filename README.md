# XDAGJ-P2P

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.java.net/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-503%20Total-brightgreen.svg)](#testing)
[![Coverage](https://img.shields.io/badge/Coverage-67%25-yellow.svg)](#testing)

> **🚀 Powering the Future of XDAG Network**
> *Next-generation P2P networking library designed to make XDAG blockchain faster, stronger, and more decentralized*

**High-performance Java P2P networking library for the XDAG blockchain ecosystem - actively developed and continuously optimized**

XDAGJ-P2P is an innovative peer-to-peer networking library designed specifically for the XDAG blockchain network. Built with modern Java technologies and comprehensive testing, it provides robust node discovery, efficient connection management, and scalable network topology. As an actively developed project, it aims to make XDAG stronger and more resilient through continuous optimization and feature enhancement.

## 🎉 What's New in v0.1.2

### 🚨 Breaking Change: Node ID Migration
- **Migrated from 520-bit to 160-bit Node ID format** (XDAG address-based)
- Perfect Kademlia DHT compliance (same as BitTorrent DHT standard)
- **69% storage reduction** per node (65 bytes → 20 bytes)
- Improved XOR distance calculation performance
- ⚠️ Not compatible with v0.1.0/0.1.1 nodes

### ✨ New Features
- **Reputation System Persistence**: Automatic disk-based saves with time-based decay
- **Enhanced Ban Management**: 13 graduated ban reasons with whitelist support
- **Prometheus Metrics Export**: 40+ metrics via HTTP endpoint (configurable port)
- **Test Coverage Boost**: 88.9% coverage for XdagMessageHandler, 57.2% for P2pPacketDecoder

### 🧹 Code Quality Improvements
- Removed 1,159 lines of dead code (XdagPayloadCodec, DnsManager, NodeStats)
- Added 34 new comprehensive tests (503 total, all passing)
- Overall coverage improved: 62.9% → 66.7%

See [CHANGELOG.md](CHANGELOG.md) for complete release notes.

## ⚡ Performance at a Glance

### 🚀 **Speed Metrics** (Actual Performance)
```
Message Creation:    1.3M-8M ops/sec
Network Processing:  0.8M-2M ops/sec
Serialization:       4M-22M ops/sec
Data Access:         98M-206M ops/sec
Concurrent Scale:    19M ops/sec (4 threads)
P2P Network Tests:   1500-2000 messages/min (20 nodes)
Network Latency:     1-8ms (Professional stress tests)
Status:             v0.1.2 - Production Ready
```

### 🔧 **Tech Stack**
```
Core:        Java 21 + Netty 4.2.1
Protocol:    Kademlia DHT + EIP-1459 DNS
Serialization: Custom SimpleCodec (high-performance binary encoding)
Crypto:      Hyperledger Besu + BouncyCastle 1.80
Testing:     JUnit 5.12.2 + Mockito 5.12.0 + 503 tests
Build:       Maven + JaCoCo
```

## 🎯 Why XDAGJ-P2P?

### 🔥 **Core Features**
```
Kademlia DHT:       160-bit Node ID (XDAG address)
Netty Powered:      Async I/O + Zero-copy
EIP-1459 DNS:       Reliable fallback protocol
Quality Focus:      503 comprehensive tests
Node Reputation:    Persistent scoring system
Ban Management:     Graduated ban durations
Prometheus Metrics: 40+ metrics exported
```

### 💎 **XDAG Network Impact**
```
Network TPS:        17,000+ msg/sec (Production Network Performance)
Processing Power:   206M+ ops/sec (Industry Leading Unit Operations)
P2P Stress Test:    Enterprise-grade 30-node stress testing
Real-World Tests:   7M+ messages in 405s, 1-8ms latency, 0% error rate
Professional Tools: Automated benchmarks + Analysis suite
Status:            v0.1.2 Production Ready - Powering Next-Gen XDAG
```

## 🎯 Technology Stack

### 🚀 **Core Technologies**
```
Java Runtime:       Java 21 LTS
Network Engine:     Netty 4.2.1
Message Protocol:   Custom XDAG message encoding
Packet Processing:  ConsenSys Tuweni 2.7.0
```

### 🔐 **Security & Infrastructure**
```
Cryptography:       Hyperledger Besu 25.5.0
Crypto Provider:    BouncyCastle 1.80
Cloud DNS:          AWS Route53 2.31.52
Testing Framework:  JUnit 5.12.2 + 503 tests
```

## ⚡ Performance Features

### 🚀 **High-Performance Processing**
```
Zero-Copy Buffers:  Direct memory access
Smart Messaging:    Custom extensible schemas
Virtual Threads:    Lightweight concurrency
Event-Driven I/O:   Non-blocking operations
```

### 🎯 **Network Optimization**
```
Discovery Protocol: Kademlia DHT + DNS fallback
Connection Pool:    Adaptive limits + Auto-recovery
Message Routing:    Type-safe + Backward compatible
Performance Tests:  Real-world benchmarks + Monitoring
```

## 📊 Network Performance Visualization

### 🎯 **Live Testing Results**
Real-world P2P network testing with 20 nodes demonstrating production-level performance and reliability.

<div align="center">

#### 🌐 Network Topology Analysis
![Network Topology](docs/images/clean_network_topology.png)
*Professional P2P network visualization showing distributed node connections and network diameter of 5 hops*

#### ⚡ Node Performance Comparison
![Node Performance](docs/images/node_performance.png)
*Real-time performance metrics across all nodes with consistent sub-10ms latency*

#### 📈 Connection Statistics
![Connection Statistics](docs/images/connection_statistics.png)
*Comprehensive connection analysis showing network health and load distribution*

#### 🚀 Message Latency Distribution
![Latency Distribution](docs/images/latency_distribution.png)
*Message latency distribution demonstrating 95% of messages under 8ms response time*

</div>

### 🏆 **Key Testing Achievements**
```
Network Scale:       20 distributed nodes
Total Connections:   108 successful P2P links
Network Diameter:    5 hops maximum
Average Path:        2.38 hops
Message Latency:     1-8ms (95% under 8ms)
Connection Success:  >99% reliability
Network Health:      Fully connected mesh
Load Balancing:      Even distribution across nodes
```

> **🔬 Testing Environment**: MacOS with Java 21, professional stress testing suite with comprehensive network analysis tools

## 🏗️ Architecture Overview

```mermaid
graph TB
    subgraph "🌐 Application Layer"
        A["XDAG Blockchain Nodes"]
    end

    subgraph "🔗 P2P Network Layer"
        B["XDAGJ-P2P Library"]
    end

    subgraph "🛠️ Core Components"
        C["Discovery"]
        D["Connection"]
        E["Messaging"]
    end

    A --> B
    B --> C
    B --> D
    B --> E

    style A fill:#e3f2fd,stroke:#1976d2,stroke-width:2px
    style B fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    style C fill:#e8f5e8,stroke:#388e3c,stroke-width:2px
    style D fill:#e8f5e8,stroke:#388e3c,stroke-width:2px
    style E fill:#e8f5e8,stroke:#388e3c,stroke-width:2px
```

### 🎯 Component Details

### 🔍 **Discovery Engine**
```
Kademlia DHT:       Distributed hash table protocol
EIP-1459 DNS:       Reliable DNS discovery fallback
Smart Peer Finding: Lightning-fast node discovery
Network Coverage:   Multi-region optimization
```

### ⚡ **Connection Hub**
```
Netty Engine:       Async I/O + Event loops
Zero-Copy Buffers:  Direct memory access
Connection Pool:    Adaptive limits + Auto-recovery
High Performance:   Sub-millisecond processing
```

### 📨 **Message Router**
```
Custom Encoding:     Extensible, backward-compatible
Type Safety:        Strongly-typed definitions
Smart Routing:      Efficient message delivery
Backward Compatible: Schema evolution support
```

## 🎯 Use Cases

### 🏆 XDAG Blockchain Network
- **🔍 Node Discovery**: Lightning-fast peer finding for XDAG blockchain nodes
- **📡 Block Propagation**: Instant block and transaction broadcasting across XDAG network
- **🤝 Consensus Support**: Rock-solid communication for XDAG consensus mechanisms
- **💪 Network Strengthening**: Enhanced connectivity makes XDAG more robust and decentralized

### 🌍 Beyond XDAG
- **⛓️ Blockchain Networks**: Universal P2P solution for any blockchain project
- **🏗️ Distributed Systems**: Service discovery and data replication at scale
- **🌐 IoT Networks**: Self-organizing mesh networks for IoT devices

## 🚀 Quick Start

### Prerequisites
- **Java 21+** (Latest LTS with Virtual Threads support)
- **Maven 3.6+** for dependency management

### Maven Dependency
```xml
<dependency>
    <groupId>io.xdag</groupId>
    <artifactId>xdagj-p2p</artifactId>
    <version>0.1.2</version>
</dependency>
```

> **⚠️ Breaking Change in v0.1.2**: Node ID format changed from 520-bit to 160-bit XDAG address. This version is not compatible with v0.1.0/0.1.1 nodes.

> **💡 Note**: This library leverages Java 21 features including Virtual Threads and Preview APIs for optimal performance.

### Basic Usage
```java
// Configure P2P service
P2pConfig config = new P2pConfig();
config.setPort(16783);
config.setDiscoverEnable(true);
config.setSeedNodes(Arrays.asList(
    new InetSocketAddress("bootstrap.xdag.io", 16783)
));

// Implement event handler
public class MyEventHandler extends P2pEventHandler {
    @Override
    public void onConnect(Channel channel) {
        System.out.println("Connected to: " + channel.getRemoteAddress());
    }

    @Override
    public void onMessage(Channel channel, Bytes data) {
        // Process incoming messages
    }
}

// Start P2P service
P2pService p2pService = new P2pService();
p2pService.register(new MyEventHandler());
p2pService.start(config);
```

### Custom Message Example
```java
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.utils.SimpleEncoder;
import io.xdag.p2p.utils.SimpleDecoder;
import org.apache.tuweni.bytes.Bytes;

// 1. Define your custom message class
public class CustomBlockMessage extends Message {
    private byte[] blockHash;
    private long blockNumber;
    private List<byte[]> transactions;
    private long timestamp;

    public CustomBlockMessage(byte[] blockHash, long blockNumber,
                             List<byte[]> transactions, long timestamp) {
        super(MessageCode.APP_TEST, null);
        this.blockHash = blockHash;
        this.blockNumber = blockNumber;
        this.transactions = transactions;
        this.timestamp = timestamp;
    }

    // 2. Implement encoding using SimpleEncoder
    @Override
    public void encode(SimpleEncoder enc) {
        enc.writeBytes(blockHash);
        enc.writeLong(blockNumber);
        enc.writeInt(transactions.size());
        for (byte[] tx : transactions) {
            enc.writeBytes(tx);
        }
        enc.writeLong(timestamp);
    }

    // 3. Implement decoding using SimpleDecoder
    public static CustomBlockMessage decode(byte[] encoded) {
        SimpleDecoder dec = new SimpleDecoder(encoded);
        byte[] blockHash = dec.readBytes();
        long blockNumber = dec.readLong();
        int txCount = dec.readInt();
        List<byte[]> transactions = new ArrayList<>();
        for (int i = 0; i < txCount; i++) {
            transactions.add(dec.readBytes());
        }
        long timestamp = dec.readLong();
        return new CustomBlockMessage(blockHash, blockNumber, transactions, timestamp);
    }
}

// 4. Send message via P2P channel
CustomBlockMessage blockMsg = new CustomBlockMessage(
    hash, 12345L, Arrays.asList(tx1, tx2), System.currentTimeMillis());
channel.send(blockMsg.getSendData());

// 5. Receive and parse message
@Override
public void onMessage(Channel channel, Bytes data) {
    try {
        CustomBlockMessage received = CustomBlockMessage.decode(data.toArray());
        System.out.println("Received block: " + received.getBlockNumber());
    } catch (Exception e) {
        log.error("Failed to parse custom message", e);
    }
}
```

### Standalone Execution
```bash
# Build the project
mvn clean package -DskipTests

# Run single P2P node
java -jar target/xdagj-p2p-0.1.2-jar-with-dependencies.jar \
  -p 16783 \
  -s bootstrap.xdag.io:16783 \
  -d 1
```

### Professional Network Testing
```bash
# Multi-node network testing
cd test-nodes
chmod +x *.sh

# Quick test: 6 nodes with real-time monitoring
./start-p2p-network.sh 6
./monitor-nodes.sh

# View network status
./status.sh

# Advanced analysis with Python tools
python3 analyze-network-performance.py --logs-dir logs

# Clean shutdown
./stop-nodes.sh
```

## 🧪 Testing & Performance

XDAGJ-P2P delivers **production-ready performance** with comprehensive benchmarking achieving million+ ops/sec across all operations.

### 🧪 **Test Suite Overview**

- **503 Unit Tests**: Comprehensive coverage with 66% code coverage
- **Integration Tests**: End-to-end network scenarios
- **Performance Tests**: Real-world benchmarks with million+ ops/sec
- **Stress Tests**: High-load and failure scenarios
- **🎯 Professional P2P Testing Suite**: Enterprise-level network testing tools

### 📊 **Test Coverage Statistics**

```
📈 Coverage Metrics (Latest Report):
  Instructions:    67% (11,161 / 16,770)
  Branches:        53% (755 / 1,425)
  Lines:           68% (2,599 / 3,861)
  Methods:         74% (463 / 623)
  Classes:         93% (85 / 91)

🚀 Test Execution:
  Total Tests:     503 test cases
  Success Rate:    100% pass rate
  Execution Time:  ~20 seconds
  Stability:       Zero flaky tests

🎯 Module Coverage Highlights:
  Core Messaging:  100% (message.discover)
  Handler Node:    89% (handler.node)
  Channel Module:  88% (channel.XdagMessageHandler)
  Configuration:   95% (config)
  DNS Discovery:   94% (discover.dns)
  Utilities:       89% (utils)
```

### 🎯 **Professional Network Testing**

XDAGJ-P2P includes a comprehensive **professional testing suite** for enterprise-grade P2P network evaluation:

```bash
# Quick functional testing (6 nodes)
cd test-nodes
./start-p2p-network.sh 6
./monitor-nodes.sh

# Check network status
./status.sh

# Stress testing (20 nodes, 5 minutes)
./start-p2p-network.sh 20
sleep 300

# Deep performance analysis
python3 analyze-network-performance.py --logs-dir logs

# Clean shutdown
./stop-nodes.sh
```

**🚀 Professional Test Capabilities:**
- **17 Message Types**: Comprehensive test coverage (latency, throughput, stability, topology analysis)
- **High-Performance TPS**: 17,000+ messages/sec network throughput (6-node cluster)
- **Multi-Scale Benchmarks**: Automated 5-30 node scaling tests
- **Real-Time Monitoring**: Live performance metrics and network topology
- **Professional Reports**: Automated analysis with visualizations and CSV export

**📊 Stress Test Results (Latest):**
```
Network Throughput:    17,433 msg/sec average
Peak Performance:      18,917 msg/sec burst
Message Volume:        7M+ messages in 405 seconds
Network Latency:       2.06ms average, <10ms P99
Forward Efficiency:    23.7% (optimized routing)
Error Rate:           0% (zero errors across all tests)
Network Stability:     Long-term stable operation with zero downtime
Scalability:          Tested up to 30 nodes
```

### 📊 **Performance Benchmark Results**

Latest performance test results (Apple M-series, Java 21):

```
📨 P2P Message Processing:
⚡ HelloMessage Creation:     1,323,399 ops/sec
⚡ PingMessage Creation:      7,521,059 ops/sec
⚡ PongMessage Creation:      7,963,686 ops/sec

🔗 Network I/O Performance:
⚡ HelloMessage Pipeline:       826,556 ops/sec
⚡ PingMessage Pipeline:      1,997,124 ops/sec

📦 Serialization Performance:
⚡ HelloMessage Encoding:     4,576,701 ops/sec
⚡ PingMessage Encoding:     21,863,658 ops/sec
⚡ StatusMessage Encoding:    5,002,451 ops/sec

🚀 Data Access Performance:
⚡ HelloMessage Access:      98,661,168 ops/sec
⚡ PingMessage Access:      206,509,169 ops/sec

🔄 Concurrent Processing:
⚡ 1 Thread:                 10,560,130 ops/sec
⚡ 2 Threads:                15,631,619 ops/sec
⚡ 4 Threads:                18,960,347 ops/sec (optimal)
⚡ 8 Threads:                 8,190,847 ops/sec
```

### 🏃 **Running Tests**

```bash
# Unit and integration tests (503 test cases)
mvn test

# Generate coverage report
mvn clean test jacoco:report

# Performance benchmarks
mvn test -Dtest=P2pPerformanceTest

# Professional P2P network testing
cd test-nodes
chmod +x *.sh

# Basic network test (recommended)
./start-p2p-network.sh 6        # Start 6 nodes
./monitor-nodes.sh              # Monitor performance
./status.sh                     # Check status
./stop-nodes.sh                 # Clean shutdown

# Larger network test
./start-p2p-network.sh 20       # Start 20 nodes
./analyze-network-performance.py # Analyze performance

# Network analysis (requires Python 3.7+)
pip3 install matplotlib pandas networkx
python3 analyze-network-performance.py --logs-dir logs
```

### 📊 **Testing Tools Overview**

#### 🔧 **Core Testing Scripts**
- `start-p2p-network.sh`: Launch multiple P2P nodes (configurable count)
- `stop-nodes.sh`: Gracefully stop all running nodes
- `status.sh`: Quick status check of all nodes
- `monitor-nodes.sh`: Real-time network monitoring and statistics
- `cleanup.sh`: Clean up logs and temporary files
- `analyze-network-performance.py`: Advanced Python data analysis tool

#### 🎯 **Test Message Types**
```
Basic Tests:        latency_test, throughput_test, coverage_test
Pressure Tests:     burst_test, pressure_test, size_test
Stability Tests:    stability_test, reliability_test, resilience_test
Analysis Tests:     topology_scan, benchmark_test, route_efficiency
Advanced Tests:     route_discovery, congestion_test, endurance_test
```

#### 📈 **Analysis Outputs**
- **Network Topology Graphs**: Visual network structure analysis
- **Performance Reports**: Comprehensive Markdown reports with metrics
- **CSV Data Export**: Raw data for external analysis tools
- **Real-time Dashboards**: Live monitoring with connection stats

### 📊 **Performance Reports**

After running tests, you can view detailed results:

- **📈 Console Output**: Real-time performance data with TPS metrics
- **📄 Test Reports**: Detailed logs in `target/surefire-reports/`
- **🎯 Professional Reports**: Network analysis in `benchmark_results/`
- **📊 Visualizations**: Network topology and performance charts
- **🔍 Coverage Reports**: JaCoCo HTML reports in `target/site/jacoco/`

```bash
# View unit test output (503 tests)
mvn test

# Generate and view coverage report
mvn clean test jacoco:report
open target/site/jacoco/index.html

# Run specific optimized tests
cat target/surefire-reports/io.xdag.p2p.performance.P2pPerformanceTest-output.txt

# View network analysis results
ls -la test-nodes/analysis_results*/
cat test-nodes/analysis_results*/network_analysis_report.txt
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

We welcome contributions! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

## 📞 Support

- **GitHub Issues**: [Report bugs or request features](https://github.com/XDagger/xdagj-p2p/issues)
- **Documentation**: [Complete user guide](docs/USER_GUIDE.md)
- **Examples**: [Sample implementations](docs/EXAMPLES.md)
