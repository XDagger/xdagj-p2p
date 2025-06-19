# Release Notes - XDAGJ-P2P v0.1.0

## Overview

First stable release of XDAGJ-P2P, a high-performance Java P2P networking library for XDAG blockchain ecosystem.

## New Features

### Core Components
- **Node Discovery**: Kademlia DHT implementation with EIP-1459 DNS fallback
- **Connection Management**: Netty-based async I/O with connection pooling
- **Message Handling**: Protocol Buffers serialization with type-safe routing
- **Cryptography**: Integrated Hyperledger Besu and BouncyCastle for secure communication

### Network Protocols
- **Kademlia DHT**: Distributed hash table for peer discovery
- **EIP-1459 DNS**: DNS-based node discovery with AWS Route53/Aliyun support
- **TCP/UDP Transport**: Dual protocol support for different message types
- **Protocol Buffers**: Efficient binary serialization format

### API Features
- **P2pService**: Main service interface for P2P operations
- **ChannelManager**: Connection lifecycle management
- **NodeManager**: Peer discovery and maintenance
- **EventHandler**: Pluggable event processing system

## Technical Specifications

### Dependencies
- Java 21+ (LTS)
- Netty 4.2.1.Final
- Protocol Buffers 4.31.1
- Hyperledger Besu 25.5.0
- BouncyCastle 1.80

### Performance Metrics
- Message processing: 1.3M-8M ops/sec
- Network I/O: 0.8M-2M ops/sec
- Serialization: 4M-22M ops/sec
- Test coverage: 71% (518 test cases)

### Network Testing
- Scale: 20 nodes distributed testing
- Latency: 1-8ms average response time
- Throughput: 1500-2000 messages/minute per node
- Reliability: >99% connection success rate

## Installation

### Maven
```xml
<dependency>
    <groupId>io.xdag</groupId>
    <artifactId>xdagj-p2p</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Standalone
```bash
java -jar xdagj-p2p-0.1.0-jar-with-dependencies.jar [options]
```

## Breaking Changes
- Initial release, no previous versions

## Bug Fixes
- N/A (initial release)

## Known Issues
- IPv6 support requires additional testing
- DNS publishing needs valid cloud credentials
- Large topologies (>100 nodes) may need parameter tuning

## Migration Guide
- New projects: Add Maven dependency
- XDAG integration: Replace existing P2P components
- Configuration: Use new `P2pConfig` class

## Documentation
- [User Guide](docs/USER_GUIDE.md)
- [API Examples](docs/EXAMPLES.md)
- [JavaDoc](https://javadoc.io/doc/io.xdag/xdagj-p2p)

## Release Assets
- `xdagj-p2p-0.1.0.jar` - Library JAR for Maven dependency
- `xdagj-p2p-0.1.0-jar-with-dependencies.jar` - Standalone executable
- `xdagj-p2p-0.1.0-sources.jar` - Source code
- `xdagj-p2p-0.1.0-javadoc.jar` - API documentation

---
**Release Date**: December 2024  
**License**: MIT  
**Minimum Java Version**: 21 