# Node Discovery Testing

Testing Kademlia DHT + EIP-1459 DNS Discovery functionality

---

## 🎯 Test Objectives

Verify P2P network node discovery functionality:
- **Kademlia DHT**: Recursive node discovery via UDP
- **EIP-1459 DNS**: Retrieve initial node list from DNS
- **Network Topology**: Can nodes self-organize into a complete network
- **DHT Health**: K-bucket fill rate, node liveness rate

**Note**: This is a **node discovery functionality test**, focusing on network topology rather than message throughput.

---

## 📊 Performance Metrics

### Current Targets

```
Test Configuration: 10 nodes, local environment
Discovery Coverage: > 70% (within 5 minutes)
DHT Fill Rate: > 60%
UDP Message Rate: < 100 msg/sec/node
```

### Real Blockchain Standards

| Metric | Bitcoin | Ethereum | XDAG Target |
|------|---------|----------|----------|
| Block Propagation (50% nodes) | 6.5s | 2-5s | < 5s |
| Block Propagation (95% nodes) | 12.6s | < 10s | < 10s |
| Typical Connections | 8-125 | 25-50 | 8-50 |
| DHT Nodes | Thousands | Thousands | Scalable |

---

## 🚀 Quick Start

### 1. Basic Discovery Test (Quick Start)

```bash
# Run discovery test with 10 nodes for 5 minutes
./test-discovery.sh 10 300

# Monitor progress in real-time
tail -f logs/discovery-*.log | grep "Discovery Status"

# Expected output:
# [node-10001] Discovery Status at 30s  | DHT: 1/2 | ...
# [node-10001] Discovery Status at 60s  | DHT: 3/5 | ...
# [node-10001] Discovery Status at 120s | DHT: 7/9 | ...
```

**DHT Metrics Interpretation**:
```
DHT: 5/8
     ↑ ↑
     │ └─ Total known nodes
     └─── Verified nodes in K-bucket
```

**Runtime**: The script will run for the specified duration (300s = 5 minutes)

---

### 2. Complete Discovery Test (Recommended)

```bash
# Run complete test (10 nodes, 5 minutes)
./test-discovery.sh 10 300

# Auto-generate report
cat discovery-results/report-*.txt

# Example report:
# ========================================
# Time: 300 seconds
# ========================================
# Average DHT nodes: 7
# Average discovered: 8
# Discovery coverage: 89%
# ✅ Excellent: Discovery coverage 89% >= 70%
```

---

### 3. Quick Verification

```bash
# Run diagnostic script
./verify-discovery.sh

# Output:
# ✅ Discovery enabled
# ✅ Kademlia messages detected normally
# ⚠️  DHT statistics not found (may be log level issue)
```

---

## 📁 File Description

### Script Files

| File | Function | Usage |
|------|------|------|
| `test-discovery.sh` | Complete discovery test | `./test-discovery.sh [node_count] [seconds]` |
| `verify-discovery.sh` | Quick verification script | `./verify-discovery.sh` |
| `stop-nodes.sh` | Stop all nodes | `./stop-nodes.sh` |

### Log Files

- `logs/discovery-*.log` - Node discovery logs
- `pids/discovery-*.pid` - Node process IDs
- `discovery-results/report-*.txt` - Test reports

### Documentation Files

- `docs/EIP-1459-detailed-guide.md` - EIP-1459 detailed explanation
- `docs/node-discovery-test-plan.md` - Test plan
- `docs/discovery-test-summary.md` - Test summary
- `docs/IMPLEMENTATION_SUMMARY.md` - Implementation summary

---

## 🔧 Configuration Parameters

### test-discovery.sh Configuration

```bash
NODE_COUNT=10         # Node count (default: 3)
TEST_DURATION=300     # Test duration in seconds (default: 60)
BASE_PORT=10000       # Starting port (TCP+UDP)
NETWORK_ID=1          # Network ID

# JVM parameters
-Xms256m              # Initial heap 256MB (smaller than performance test)
-Xmx512m              # Max heap 512MB

# Application parameters
-p $PORT              # Listen port
-d 1                  # Enable Discovery ✅ Required
-s $SEEDS             # TCP seed nodes
-a $ACTIVE_NODES      # UDP active nodes ✅ Critical configuration
```

### Key Difference: active-nodes Parameter

```bash
# Node 0 (Bootstrap): No configuration needed
-a ""

# Node 1-2: Connect to multiple UDP nodes
-a "127.0.0.1:10000,127.0.0.1:10001"

# Node 3+: Only connect to Node 0 (force recursive discovery via DHT)
-a "127.0.0.1:10000"
```

**Purpose**: Force Node 3+ to discover other nodes through Kademlia DHT, testing DHT functionality.

---

## 📈 Test Scenarios

### Scenario 1: Kademlia DHT Basic Test

**Objective**: Verify UDP message exchange and node discovery

**Configuration**:
- 10 nodes, no DNS configured
- Rely solely on Kademlia DHT

**Expected Results**:
```
Time  | Node0 Discovered | Node5 Discovered | Node9 Discovered
------|-----------------|-----------------|------------------
30s   | 2-3             | 2-3             | 2-3              # Direct neighbors
60s   | 4-6             | 4-6             | 4-6              # Recursive discovery
120s  | 7-9             | 7-9             | 7-9              # Most nodes
300s  | 9-10            | 9-10            | 9-10             # All nodes
```

**Verification Metrics**:
- ✅ PING/PONG success rate > 95%
- ✅ Node discovery coverage > 90% (within 5 minutes)
- ✅ K-bucket fill rate > 80%
- ✅ No node leaks (stable memory)

**Execution**:
```bash
./test-discovery.sh 10 300
# The script will:
# 1. Start nodes with DiscoveryApp
# 2. Monitor at intervals (30s, 60s, 120s, 180s, 300s)
# 3. Generate detailed report
# Check DHT statistics in the report
cat discovery-results/report-*.txt
```

---

### Scenario 2: DNS Discovery Test

**Objective**: Verify EIP-1459 DNS sync functionality

**Prerequisites**: Requires Mock DNS server or real DNS

**Steps**:

1. **Start Mock DNS Server**:
```java
// Use MockDnsServer.java
MockDnsServer server = new MockDnsServer();
for (int i = 0; i < 50; i++) {
    server.addMockNode("127.0.0.1", 20000 + i);
}
server.start("test.nodes.local", 5353);
System.out.println("Tree URL: " + server.getTreeUrl());
```

2. **Start Nodes**:
```bash
# Modify test-discovery.sh to add DNS URL parameter
--dns-url "enrtree://PUBKEY@test.nodes.local"
```

3. **Verify Sync**:
```bash
grep "SyncTree\|DNS sync" logs/discovery-*.log

# Expected:
# [INFO] SyncTree complete: Links=0, Nodes=50, Total=50
# [INFO] Added 50 nodes from DNS to DHT
```

---

### Scenario 3: Hybrid Discovery Mode (Most Realistic)

**Objective**: DNS + Kademlia working together

**Architecture**:
```
Bootstrap Node (Node 0):
  - Retrieve 50 nodes from DNS
  - Act as UDP seed for other nodes

Regular Nodes (Node 1-9):
  - PING/PONG with Bootstrap node
  - Recursive discovery via FIND_NODE
  - Eventually form complete network
```

**Expected Flow**:
```
Phase 1 (0-30s): Bootstrap
  - Node 0 retrieves 50 nodes from DNS
  - Node 0 DHT table: 50 nodes

Phase 2 (30-120s): Discovery Propagation
  - Node 1-9 PING Node 0
  - Node 1-9 FIND_NODE queries
  - Receive NEIGHBORS responses
  - Recursively discover more nodes

Phase 3 (120s+): Network Stabilization
  - All nodes have complete DHT tables
  - TCP connections established
  - Complete P2P topology formed
```

---

## 🔍 Key Metrics Interpretation

### DHT Statistics: `DHT: X/Y`

```
[node-10001] ... | DHT: 5/8 | ...
```

- **X (dhtNodes)**: Verified nodes in K-bucket
  - PING sent and PONG received
  - Available for queries and connections
  - These nodes are reliable

- **Y (allNodes)**: Total known nodes
  - Includes K-bucket nodes
  - Includes nodes discovered via FIND_NODE but not verified
  - Includes nodes retrieved from DNS

**Health Indicators**:
```
Excellent: X/Y >= 80%  (Most nodes verified)
Good:      X/Y >= 60%
Fair:      X/Y >= 40%
Poor:      X/Y < 40%   (Many nodes unverified or offline)
```

---

### UDP Message Statistics

**Normal Range** (10-node network):
- PING: 10-50 times/minute
- PONG: 10-50 times/minute
- FIND_NODE: 5-20 times/minute
- NEIGHBORS: 5-20 times/minute

**Abnormal Conditions**:
- Message count = 0: Discovery not started or UDP port blocked
- Message count too high (>500/min): May have query loops or configuration errors

---

### Discovery Coverage

```
Discovery Coverage = (Nodes discovered by this node) / (Total network nodes - 1)
```

**Targets**:
- 1 minute: > 30%
- 5 minutes: > 90%
- 10 minutes: > 95%

---

## 🛠️ Troubleshooting

### Issue 1: DHT Statistics Always 0/0

**Possible Causes**:
- Discovery not enabled
- UDP port blocked
- active-nodes not configured

**Verification Steps**:
```bash
# 1. Confirm Discovery is enabled
grep "Discovery enabled: true" logs/discovery-*.log

# 2. Confirm UDP port is listening
lsof -i UDP:10000-10010

# 3. Confirm active-nodes is configured
grep "UDP Active Nodes" logs/discovery-*.log

# 4. Check KadService logs
grep "KadService\|NodeHandler" logs/discovery-*.log
```

**Solution**:
```bash
# Check -a parameter in test-discovery.sh
grep '\-a' test-discovery.sh

# Ensure there's configuration like:
# -a "127.0.0.1:10000"
```

---

### Issue 2: DHT Growth is Slow

**Possible Causes**:
- DiscoverTask period too long
- Insufficient active-nodes configuration
- High network latency

**Verification Steps**:
```bash
# Check if DiscoverTask is running
grep "DiscoverTask\|discover-task" logs/discovery-*.log

# Check FIND_NODE messages
grep "FIND_NODE\|FindNode" logs/discovery-*.log
```

**Solution**:
```bash
# Increase active-nodes
ACTIVE_NODES="127.0.0.1:10000,127.0.0.1:10001,127.0.0.1:10002"
```

---

### Issue 3: DNS Sync Failure

**Possible Causes**:
- DNS URL not configured
- Mock DNS server not started
- Domain resolution failed

**Verification Steps**:
```bash
# Confirm DNS URL configuration
grep "treeUrls\|dns-url" logs/discovery-*.log

# Check DNS resolution logs
grep "SyncTree\|DNS sync\|resolveRoot" logs/discovery-*.log
```

---

## 📊 EIP-1459 DNS Discovery

### What is EIP-1459?

**Objective**: Publish signed, verifiable, updatable node lists via DNS

**URL Format**:
```
enrtree://PUBKEY@DOMAIN

Example:
enrtree://AOFTICU2...@nodes.xdag.org
         └─────┬────┘  └─────┬─────┘
         Public Key(base32)  DNS domain
```

### Merkle Tree Structure

```
                ROOT (signed)
               /     |      \
          BRANCH   BRANCH   LINK
          /    \      |       |
       ENR   ENR   BRANCH  Other trees
```

**Record Types**:
1. **enrtree-root**: Root record (with signature)
2. **enrtree-branch**: Branch record
3. **enr**: Leaf node (actual node info)
4. **enrtree-link**: Link to other trees

### Why Do We Need It?

**Scenario 1 - New Node Bootstrap**:
```
Traditional: Hardcode 5 seed IPs → If all offline = Cannot join network ❌
EIP-1459: Query DNS → Get 100 latest nodes → Success rate >90% ✅
```

**Scenario 2 - Network Upgrade**:
```
Traditional: Modify code → Release new version → Users update (slow) ❌
EIP-1459: Update DNS record (1 minute) → All clients auto-retrieve ✅
```

**Scenario 3 - Prevent DNS Hijacking**:
```
Problem: ISP or attackers tamper with DNS records
EIP-1459: Hash protection + Signature verification + Hardcoded public key → Cannot forge ✅
```

See: [docs/EIP-1459-detailed-guide.md](docs/EIP-1459-detailed-guide.md)

---

## 📝 Test Checklist

Before running node discovery tests, ensure:

- [ ] Latest JAR built: `mvn clean package -DskipTests`
- [ ] TCP+UDP ports not occupied: `lsof -i :10000-10010`
- [ ] Old nodes stopped: `./stop-nodes.sh`
- [ ] Old logs cleaned: `rm -rf logs/* pids/*`
- [ ] Understand DHT metrics meaning

During testing, monitor:

- [ ] DHT node count increasing
- [ ] UDP messages exchanging normally
- [ ] Any error logs
- [ ] K-bucket fill rate

After testing, analyze:

- [ ] Final discovery coverage percentage
- [ ] DHT health (X/Y ratio)
- [ ] Can all nodes discover each other
- [ ] UDP message rate normal

---

## 🎓 Further Reading

1. **EIP-1459 Detailed Guide**: [docs/EIP-1459-detailed-guide.md](docs/EIP-1459-detailed-guide.md)
   - EIP-1459 principles
   - Merkle tree structure
   - Signature verification mechanism

2. **Test Plan**: [docs/node-discovery-test-plan.md](docs/node-discovery-test-plan.md)
   - Detailed test scenarios
   - Performance metrics definition
   - Troubleshooting guide

3. **Implementation Summary**: [docs/IMPLEMENTATION_SUMMARY.md](docs/IMPLEMENTATION_SUMMARY.md)
   - Completed work
   - Test tool inventory
   - Ready-to-use solutions

4. **Test Summary**: [docs/discovery-test-summary.md](docs/discovery-test-summary.md)
   - Current status analysis
   - Root cause issues
   - Correct test approach

---

## 🎯 Summary

### What This Test Validates:

✅ Kademlia DHT working properly
✅ UDP messages exchanging normally
✅ Nodes can discover each other
✅ DHT table maintained correctly
✅ Network can self-organize

### Value of Kademlia DHT:

✅ **Auto-discovery**: No central server needed
✅ **Network Self-organization**: Nodes automatically form topology
✅ **Fault Tolerance**: Node downtime doesn't affect network
✅ **Scalability**: Supports thousands of nodes

### Value of EIP-1459 DNS:

✅ **Cold Start**: New nodes quickly retrieve initial nodes
✅ **Dynamic Updates**: DNS records can be updated anytime
✅ **Security Verification**: Merkle tree + signature prevents tampering
✅ **Decentralization**: DNS is distributed

### Synergy Between Both:

```
Complete Bootstrap Flow:
  1. DNS Discovery: Retrieve initial nodes (50-200)
  2. TCP Connections: Connect to some nodes
  3. Kademlia DHT: Recursively discover more from these nodes
  4. Complete Network: Form complete P2P topology
```

**Remember**: Node discovery is the foundation of P2P networks! Without it, networks cannot self-organize.
