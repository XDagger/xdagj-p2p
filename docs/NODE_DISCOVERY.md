# Node Discovery Guide

Complete guide to node discovery mechanisms in XDAGJ-P2P.

## Table of Contents
- [Current Implementation](#current-implementation)
- [Technical Details](#technical-details)
- [Configuration Guide](#configuration-guide)
- [Best Practices](#best-practices)
- [Future Enhancements](#future-enhancements)
- [Troubleshooting](#troubleshooting)

---

## Current Implementation

### Kademlia DHT Discovery (Production-Ready)

XDAGJ-P2P v0.1.5 uses **Kademlia DHT** for fully decentralized peer-to-peer discovery via UDP protocol.

#### Basic Usage

```bash
# Start node with seed nodes (recommended for production)
java -jar xdagj-p2p-0.1.5-jar-with-dependencies.jar \
  -p 16783 \
  -s <SEED_NODE_IP_1>:16783,<SEED_NODE_IP_2>:16783
```

#### How It Works

1. **TCP Connection**: Connect to seed nodes for initial communication
2. **Liveness Detection**: Send UDP PING/PONG messages (every 30 seconds)
3. **Recursive Discovery**: Use FIND_NODE/NEIGHBORS to discover peers
4. **Topology Building**: Build complete network topology via Kademlia DHT (160-bit Node ID)
5. **Routing Table Maintenance**: Continuously maintain routing table with active nodes

#### Advantages

✅ **Fully Decentralized** - No central authority required
✅ **Self-Organizing Network** - Automatic topology optimization
✅ **No Single Point of Failure** - Resilient to node failures
✅ **Real-Time Node Discovery** - Live network updates
✅ **Production-Ready** - Battle-tested in XDAG network

#### Performance Characteristics

- **Discovery Latency**: 30-60s for initial bootstrap
- **Network Overhead**: Medium (UDP heartbeat + routing table maintenance)
- **Scalability**: Tested with 30+ node networks
- **Memory**: ~20 bytes per node (160-bit Node ID)

---

## Technical Details

### Node ID Format

- **160-bit XDAG address** (20 bytes / 40 hex chars)
- Derived from node's public key
- Same standard as BitTorrent DHT (BEP-0005)
- Perfect for XOR-based distance calculation

**Example:**
```
Node ID: a1b2c3d4e5f6789012345678901234567890abcd (40 hex chars)
Binary:  10100001101100101100001111010100... (160 bits)
```

### Routing Table Structure

**K-Bucket Organization:**
- **K-value**: 16 nodes per bucket
- **Bucket Count**: 160 buckets (one per bit position)
- **Replacement Policy**: LRU (Least Recently Used)
- **Stale Detection**: Automatic via PING/PONG timeout

**Bucket Distance:**
```
Bucket 0:   Distance 2^0 to 2^1   (closest nodes)
Bucket 1:   Distance 2^1 to 2^2
...
Bucket 159: Distance 2^159 to 2^160 (farthest nodes)
```

### Discovery Protocol Messages

#### PING/PONG (Liveness Check)
```
PING:  { type: KAD_PING, nodeId: <160-bit>, timestamp: <long> }
PONG:  { type: KAD_PONG, nodeId: <160-bit>, timestamp: <long> }
```
- **Purpose**: Verify node is alive
- **Frequency**: Every 30 seconds
- **Timeout**: 5 seconds
- **Transport**: UDP

#### FIND_NODE/NEIGHBORS (Peer Discovery)
```
FIND_NODE:   { type: KAD_FIND_NODE, target: <160-bit> }
NEIGHBORS:   { type: KAD_NEIGHBORS, nodes: [<Node>, <Node>, ...] }
```
- **Purpose**: Recursively discover peers
- **Response**: Up to 16 closest nodes to target
- **Algorithm**: Iterative deepening
- **Transport**: UDP

### Network Topology

**Small-World Network Properties:**
- **Lookup Complexity**: O(log N) where N = total nodes
- **Average Path Length**: ~log₂(N) hops
- **Self-Healing**: Automatic recovery from node failures
- **Load Balancing**: Even distribution across routing table

**Example (20 nodes):**
```
Network Diameter:    5 hops maximum
Average Path Length: 2.38 hops
Clustering:          High (>0.6)
```

### XOR Distance Metric

**Distance Calculation:**
```java
// XOR distance between two 160-bit Node IDs
BigInteger distance = new BigInteger(nodeId1).xor(new BigInteger(nodeId2));
int leadingZeros = 160 - distance.bitLength();
// leadingZeros determines bucket number
```

**Properties:**
- Symmetric: d(A,B) = d(B,A)
- Triangle inequality: d(A,C) ≤ d(A,B) + d(B,C)
- Unidirectional: For any N, exactly one path from A to B

---

## Configuration Guide

### Production Deployments

#### Full Nodes (24/7 Operation)

```bash
# Multiple seed nodes for redundancy
java -jar xdagj-p2p-0.1.5-jar-with-dependencies.jar \
  -p 16783 \
  -s <SEED_NODE_IP_1>:16783,<SEED_NODE_IP_2>:16783,<SEED_NODE_IP_3>:16783 \
  -d 1
```

**Recommended:**
- 3-5 seed nodes
- Static IPs with high uptime
- Same network ID (default: 0x01)

#### Programmatic Configuration

```java
P2pConfig config = new P2pConfig();
config.setPort(16783);
config.setDiscoverEnable(true);
config.setMaxConnections(30);
// Note: Connection timeout is set via P2pConstant.NODE_CONNECTION_TIMEOUT (default: 2000ms)
// It cannot be configured through P2pConfig

// Add multiple seed nodes
List<InetSocketAddress> seeds = Arrays.asList(
    new InetSocketAddress("<SEED_NODE_IP_1>", 16783),
    new InetSocketAddress("<SEED_NODE_IP_2>", 16783),
    new InetSocketAddress("<SEED_NODE_IP_3>", 16783)
);
config.setSeedNodes(seeds);

// Register event handler (if needed)
// config.addP2pEventHandle(new MyEventHandler());

// Start service
P2pService service = new P2pService(config);
service.start();
```

### Development & Testing

#### Local Development

```bash
# Single seed node for local testing
java -jar xdagj-p2p-0.1.5-jar-with-dependencies.jar \
  -p 10000 \
  -s 127.0.0.1:10001 \
  -d 1
```

#### Multi-Node Local Testing

```bash
# Node 1
java -jar xdagj-p2p-0.1.5-jar-with-dependencies.jar -p 10000 -s 127.0.0.1:10001,127.0.0.1:10002 &

# Node 2
java -jar xdagj-p2p-0.1.5-jar-with-dependencies.jar -p 10001 -s 127.0.0.1:10000,127.0.0.1:10002 &

# Node 3
java -jar xdagj-p2p-0.1.5-jar-with-dependencies.jar -p 10002 -s 127.0.0.1:10000,127.0.0.1:10001 &
```

### Private Networks

#### Enterprise Deployment

```bash
# Private seed nodes with internal IPs
java -jar xdagj-p2p-0.1.5-jar-with-dependencies.jar \
  -p 16783 \
  -s 10.0.1.10:16783,10.0.1.11:16783,10.0.1.12:16783 \
  -d 1
```

**Security Considerations:**
- Use firewall rules to restrict access
- Consider VPN for inter-region connectivity
- Whitelist trusted nodes in production

---

## Best Practices

### 1. Seed Node Selection

✅ **DO:**
- Use 3-5 geographically distributed seed nodes
- Choose nodes with static IPs and >99% uptime
- Monitor seed node health regularly
- Have backup seed nodes ready

❌ **DON'T:**
- Rely on a single seed node
- Use nodes with dynamic IPs
- Use unreliable or low-bandwidth nodes

### 2. Network Configuration

✅ **DO:**
- Use standard port 16783 for XDAG network
- Allow UDP and TCP traffic on configured port
- Set appropriate connection limits (recommended: 30)
- Note: Timeouts are set via constants (connection: 2s default, discovery: 15s default)

❌ **DON'T:**
- Block UDP traffic (required for discovery)
- Set connection limits too low (<10)
- Use non-standard ports without coordination

### 3. Firewall Rules

**Required Ports:**
```bash
# TCP (for connections)
sudo ufw allow 16783/tcp

# UDP (for discovery)
sudo ufw allow 16783/udp
```

**NAT Configuration:**
```bash
# Port forwarding for nodes behind NAT
iptables -t nat -A PREROUTING -p tcp --dport 16783 -j DNAT --to 192.168.1.100:16783
iptables -t nat -A PREROUTING -p udp --dport 16783 -j DNAT --to 192.168.1.100:16783
```

### 4. Monitoring

**Key Metrics to Track:**
- Active connections count
- Routing table size
- Discovery success rate
- Average ping latency
- Failed connection attempts

**Example Monitoring:**
```java
// Get connectable nodes count
int connectableNodes = p2pService.getConnectableNodes().size();

// Note: Direct access to ChannelManager and KadService internal components
// is not part of the public API. Use the provided public methods instead.
log.info("Connectable nodes: {}", connectableNodes);
```

### 5. Performance Tuning

**Optimal Configuration:**
```java
P2pConfig config = new P2pConfig();
config.setMaxConnections(30);           // Balance between connectivity and resources
// Note: Connection timeout, discovery cycle, and Kademlia K-value are set via
// P2pConstant and KademliaOptions constants, not through P2pConfig:
// - Connection timeout: P2pConstant.NODE_CONNECTION_TIMEOUT (default: 2000ms)
// - Discovery cycle: KademliaOptions.DISCOVER_CYCLE (default: 15000ms)
// - Kademlia K-value: KademliaOptions.BUCKET_SIZE (default: 16)
```

**Resource Considerations:**
- Memory: ~20 bytes per node × routing table size
- Bandwidth: ~1 KB/s per active connection (UDP heartbeat)
- CPU: Minimal (<1% for 30 connections)

---

## Future Enhancements

### DNS Discovery (Planned Mid-term)

**Status:** Code implementation complete, integration planned for Q2-Q3 2025

#### Overview

Quick node discovery via DNS TXT records (EIP-1459 compliant).

#### Planned Features

- **Ultra-fast cold start**: 2-5 seconds (vs 30-60s for pure DHT)
- **EIP-1459 compliance**: Standard DNS-based discovery
- **DNS TXT records**: Store node lists in TXT records
- **Automatic publishing**: Authority nodes can publish to DNS

#### Why Not Now?

- Current Kademlia DHT works excellently for all use cases
- DNS integration requires production DNS infrastructure setup
- Mid-term deployment allows for proper testing and gradual rollout

#### Planned Timeline

- **Q2 2025**: DNS Client integration into NodeManager
- **Q3 2025**: Production DNS infrastructure deployment
- **Q4 2025**: Hybrid mode (DNS + DHT) general availability

### Discovery Method Comparison

| Feature | Kademlia DHT (Current) | DNS Discovery (Planned) | Hybrid Mode (Future) |
|---------|------------------------|-------------------------|----------------------|
| **Status** | ✅ Production | 🚧 Mid-term | 🔮 Long-term |
| **Startup Speed** | Medium (30-60s) | Fast (2-5s) | Fast (2-5s) |
| **Decentralization** | ✅ Fully | ⚠️ Semi | ✅ Fully |
| **Infrastructure** | ✅ None required | ⚠️ DNS service needed | ⚠️ DNS service needed |
| **Network Overhead** | Medium | Low | Medium |
| **Single Point Failure** | ✅ No | ⚠️ Yes (DNS) | ✅ No (DHT backup) |
| **Auto IP Updates** | ✅ Real-time | ⏱️ Hourly | ✅ Real-time |
| **Cold Start** | Requires seeds | Fast | Fastest |
| **Long-term Operation** | ✅ Excellent | ⚠️ Depends on DNS | ✅ Excellent |

### Upcoming Features (Roadmap)

**Mid-term (Q2-Q3 2025):**
- DNS Discovery integration
- DNS PublishService for authority nodes
- Multi-source bootstrap (DNS + DHT)

**Long-term (Q4 2025+):**
- Hybrid discovery mode (DNS + DHT)
- Intelligent bootstrap strategy
- Enhanced network monitoring
- IPv6 support

---

## Troubleshooting

### Common Issues

#### 1. No Peers Discovered

**Symptoms:**
```
Routing table size: 0
Active connections: 0
```

**Causes & Solutions:**
- ❌ **Seed nodes unreachable** → Check seed node addresses and network connectivity
- ❌ **Firewall blocking UDP** → Allow UDP traffic on discovery port
- ❌ **Wrong network ID** → Ensure all nodes use same network ID

**Debug:**
```bash
# Check UDP connectivity to seed node
nc -u <SEED_NODE_IP> 16783

# View discovery logs
tail -f logs/xdagj-p2p.log | grep "KAD_"
```

#### 2. Slow Discovery

**Symptoms:**
```
Discovery takes >2 minutes
Few peers found
```

**Causes & Solutions:**
- ❌ **Network latency** → Check ping to seed nodes
- ❌ **Too few seed nodes** → Add more seed nodes (3-5 recommended)
- ❌ **NAT/firewall issues** → Configure port forwarding

#### 3. Connection Failures

**Symptoms:**
```
Connection timeout errors
High failed connection rate
```

**Causes & Solutions:**
- ❌ **TCP port blocked** → Allow TCP traffic on configured port
- ❌ **Connection limit reached** → Increase maxConnections
- ❌ **Seed node down** → Use multiple seed nodes for redundancy

### Diagnostic Commands

```bash
# Check active connections
lsof -i :16783

# Monitor UDP traffic
sudo tcpdump -i any udp port 16783

# Check routing table
# (Requires JMX or custom endpoint)
curl http://localhost:8080/p2p/routing-table
```

### Enable Debug Logging

```xml
<!-- logback.xml -->
<logger name="io.xdag.p2p.discover" level="DEBUG"/>
<logger name="io.xdag.p2p.discover.kad" level="DEBUG"/>
```

---

## References

- [Kademlia Paper](https://pdos.csail.mit.edu/~petar/papers/maymounkov-kademlia-lncs.pdf) - Original Kademlia DHT paper
- [BitTorrent DHT (BEP-0005)](http://www.bittorrent.org/beps/bep_0005.html) - DHT standard
- [EIP-1459](https://eips.ethereum.org/EIPS/eip-1459) - Node Discovery via DNS
- [Node ID Migration Plan](../NODE_ID_MIGRATION_PLAN.md) - 520-bit to 160-bit migration

---

**Current Recommendation:** Use **Kademlia DHT with multiple IP seeds** for all production deployments. This provides excellent decentralization, reliability, and performance without requiring additional infrastructure.
