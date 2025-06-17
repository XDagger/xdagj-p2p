# XDAGJ-P2P

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.java.net/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-417%20Total-brightgreen.svg)](#testing)

> **🚀 Powering the Future of XDAG Network**  
> *Next-generation P2P networking library designed to make XDAG blockchain faster, stronger, and more decentralized*

**High-performance Java P2P networking library for the XDAG blockchain ecosystem - actively developed and continuously optimized**

XDAGJ-P2P is an innovative peer-to-peer networking library designed specifically for the XDAG blockchain network. Built with modern Java technologies and comprehensive testing, it provides robust node discovery, efficient connection management, and scalable network topology. As an actively developed project, it aims to make XDAG stronger and more resilient through continuous optimization and feature enhancement.

## ⚡ Performance at a Glance

### 🚀 **Speed Metrics** (Actual Performance)
```
Message Creation:    1.3M-8M ops/sec
Network Processing:  0.8M-2M ops/sec  
Serialization:       4M-22M ops/sec
Data Access:         98M-206M ops/sec
Concurrent Scale:    19M ops/sec (4 threads)
Status:             v0.1.0 - Production Ready
```

### 🔧 **Tech Stack**
```
Core:        Java 21 + Netty 4.2.1
Protocol:    Kademlia DHT + EIP-1459 DNS
Serialization: Protocol Buffers 4.31.1
Crypto:      Hyperledger Besu + BouncyCastle 1.80
Testing:     JUnit 5.12 + Mockito 5.12 + JMH 1.37
Build:       Maven + JaCoCo + Protobuf Plugin
```

## 🎯 Why XDAGJ-P2P?

### 🔥 **Core Features**
```
Kademlia DHT:       Distributed hash table
Netty Powered:      Async I/O + Zero-copy
EIP-1459 DNS:       Reliable fallback protocol
Quality Focus:      417 comprehensive tests
```

### 💎 **XDAG Network Impact**
```
Network Speed:      1K+ messages/sec
Global Reach:       90%+ discovery rate
Connection Time:    Sub-500ms setup
Development:        v0.1.0 Active evolution
```

## 🎯 Technology Stack

### 🚀 **Core Technologies**
```
Java Runtime:       Java 21 LTS
Network Engine:     Netty 4.2.1
Message Protocol:   Protocol Buffers 4.31.1
Packet Processing:  ConsenSys Tuweni 2.7.0
```

### 🔐 **Security & Infrastructure**
```
Cryptography:       Hyperledger Besu 25.5.0
Crypto Provider:    BouncyCastle 1.80
Cloud DNS:          AWS Route53 2.31.52
Testing Framework:  JUnit 5.12 + 417 tests
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
Performance Tests:  JMH benchmarks + Monitoring
```

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
High Performance:   Sub-500ms establishment
```

### 📨 **Message Router**
```
Protocol Buffers:   Custom extensible schemas
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
    <version>0.1.0</version>
</dependency>
```

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
// 1. Define your message in proto file
message CustomBlockMessage {
    bytes blockHash = 1;
    int64 blockNumber = 2;
    repeated bytes transactions = 3;
    int64 timestamp = 4;
}

// 2. Use generated Java classes
CustomBlockMessage blockMsg = CustomBlockMessage.newBuilder()
    .setBlockHash(ByteString.copyFrom(hash))
    .setBlockNumber(12345)
    .addTransactions(ByteString.copyFrom(tx1))
    .addTransactions(ByteString.copyFrom(tx2))
    .setTimestamp(System.currentTimeMillis())
    .build();

// 3. Send via P2P channel
channel.send(Bytes.wrap(blockMsg.toByteArray()));

// 4. Receive and parse
@Override
public void onMessage(Channel channel, Bytes data) {
    try {
        CustomBlockMessage received = CustomBlockMessage.parseFrom(data.toArray());
        System.out.println("Received block: " + received.getBlockNumber());
    } catch (InvalidProtocolBufferException e) {
        log.error("Failed to parse custom message", e);
    }
}
```

### Standalone Execution
```bash
# Build the project
mvn clean package -DskipTests

# Run P2P node
java -jar target/xdagj-p2p-0.1.0-jar-with-dependencies.jar \
  -p 16783 \
  -s bootstrap.xdag.io:16783 \
  -d 1
```

## 🧪 Testing & Performance

### 🚀 **Performance Benchmarks**

XDAGJ-P2P delivers **production-ready performance** with comprehensive benchmarking:

```
🎯 REAL PERFORMANCE RESULTS:
• Message Creation:    1.3M-8M ops/sec (HelloMessage, PingMessage, PongMessage)
• Network Processing:  0.8M-2M ops/sec (EmbeddedChannel pipeline)
• Serialization:       4M-22M ops/sec (Protocol Buffers)
• Data Access:         98M-206M ops/sec (Pre-created messages)
• Concurrent Scale:    19M ops/sec (4 threads optimal)
• Memory Efficiency:   Sub-microsecond message operations
```

### 🧪 **Test Suite Overview**

- **417 Unit Tests**: Complete coverage of all components
- **Integration Tests**: End-to-end network scenarios  
- **Performance Tests**: Real-world benchmarks with million+ ops/sec
- **Stress Tests**: High-load and failure scenarios

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
# Run all tests
mvn test

# Run performance benchmarks
mvn test -Dtest=P2pPerformanceTest

# Run specific test categories
mvn test -Dtest="*HandlerTest"
mvn test -Dtest="*IntegrationTest"
```

### 📊 **Performance Reports**

After running performance tests, you can view the detailed output:

- **📈 Console Output**: Real-time performance data with TPS metrics
- **📄 Surefire Reports**: Detailed test logs in `target/surefire-reports/`

```bash
# View detailed test output
cat target/surefire-reports/io.xdag.p2p.performance.P2pPerformanceTest-output.txt
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

We welcome contributions! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

## 📞 Support

- **GitHub Issues**: [Report bugs or request features](https://github.com/XDagger/xdagj-p2p/issues)
- **Documentation**: [Complete user guide](docs/USER_GUIDE.md)
- **Examples**: [Sample implementations](docs/EXAMPLES.md)