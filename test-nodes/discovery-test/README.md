# Node Discovery Testing Framework

Universal testing framework for both **Kademlia DHT** and **EIP-1459 DNS** node discovery mechanisms.

## Quick Start

```bash
# Kademlia DHT mode (10 nodes, 5 minutes)
./test.sh dht 10 300

# DNS discovery mode (5 nodes, 3 minutes)
./test.sh dns 5 180

# Verify test results
./verify.sh dht    # or ./verify.sh dns

# Stop all nodes
./stop-nodes.sh
```

## Features

### Kademlia DHT Discovery
- ✅ UDP-based peer discovery (PING/PONG)
- ✅ Recursive node lookup (FIND_NODE/NEIGHBORS)
- ✅ Self-organizing routing table
- ✅ Automatic peer connection

### DNS Discovery (EIP-1459)
- ✅ DNS TXT record-based discovery
- ✅ Mock DNS for local testing (no external DNS needed)
- ✅ Auto-generated DNS tree structure
- ✅ EIP-1459 compliant format

## Test Scripts

### 1. test.sh - Universal Test Script

Supports both DHT and DNS modes with a single script.

**Usage**:
```bash
./test.sh [mode] [node_count] [duration_seconds]

Modes:
  dht  - Kademlia DHT discovery (default)
  dns  - DNS discovery with Mock DNS

Examples:
  ./test.sh dht 10 300      # DHT: 10 nodes, 5 minutes
  ./test.sh dns 5 180       # DNS: 5 nodes, 3 minutes
  ./test.sh 10 300          # Default to DHT mode
```

**How it works**:
- Automatically starts nodes with appropriate configuration
- DHT mode: Configures TCP seeds + UDP active nodes
- DNS mode: Enables Mock DNS + configures DNS tree URL
- Monitors discovery progress at intervals
- Generates comprehensive test report in `results/`

### 2. verify.sh - Universal Verification Script

Verifies discovery functionality for both modes.

**Usage**:
```bash
./verify.sh [mode]

Modes:
  dht  - Verify Kademlia DHT discovery (default)
  dns  - Verify DNS discovery

Examples:
  ./verify.sh dht    # Verify DHT discovery
  ./verify.sh dns    # Verify DNS discovery
  ./verify.sh        # Default to DHT mode
```

**Checks performed**:
- DHT mode: UDP discovery, DHT active, node discovery
- DNS mode: Mock DNS enabled, DNS Tree URL, DNS sync, node discovery
- Common: No critical errors, proper monitoring

### 3. stop-nodes.sh - Stop All Nodes

**Usage**:
```bash
./stop-nodes.sh
```

Gracefully stops all running discovery test nodes.

## Directory Structure

```
discovery-test/
├── test.sh                  # Universal test script (DHT + DNS)
├── verify.sh                # Universal verification script
├── stop-nodes.sh           # Stop all nodes
├── README.md                # This file
├── logs/                    # Node logs (dht-*.log, dns-*.log)
├── pids/                    # Process IDs
└── results/                 # Test reports
```

## Discovery Modes

### Mode 1: Kademlia DHT

**Pure UDP-based distributed hash table discovery**

```bash
./test.sh dht 10 300
```

**How it works**:
1. Each node starts with UDP discovery enabled
2. Nodes PING each other to establish connectivity
3. FIND_NODE queries recursively discover all peers
4. TCP connections established based on discovery
5. Self-organizing routing table maintained

**Best for**:
- Peer-to-peer networks
- Decentralized systems
- Dynamic node membership

### Mode 2: DNS Discovery (Mock DNS)

**EIP-1459 DNS-based discovery with local Mock DNS**

```bash
./test.sh dns 5 180
```

**How it works**:
1. Mock DNS resolver created in-memory
2. DNS tree structure auto-generated (root + branch + nodes)
3. Nodes query Mock DNS for peer list
4. Nodes connect to discovered peers
5. No external DNS infrastructure needed

**Best for**:
- Fast bootstrap
- Testing without real DNS
- CI/CD pipelines
- Offline development

**DNS Tree Structure** (auto-generated):
```
mainnet.nodes.xdag.io (Root)
  → enrtree-root:v1 e=MOCKBRANCH001 ...
      │
      └─→ MOCKBRANCH001.mainnet.nodes.xdag.io (Branch)
          → enrtree-branch:MOCKLEAF001,MOCKLEAF002
              │
              ├─→ MOCKLEAF001.mainnet.nodes.xdag.io (3 nodes)
              └─→ MOCKLEAF002.mainnet.nodes.xdag.io (3 nodes)
```

**Domain Structure**:
- `nodes.xdag.io` - Base domain (XDAG project)
- `mainnet.nodes.xdag.io` - Mainnet nodes (used in tests)
- `testnet.nodes.xdag.io` - Testnet nodes
- `dev.nodes.xdag.io` - Development nodes

## Test Results

### Result Files

Test reports are saved in `results/`:
- `dht-report-YYYYMMDD-HHMMSS.txt` - DHT test results
- `dns-report-YYYYMMDD-HHMMSS.txt` - DNS test results

### Success Criteria

**DHT Mode**:
- ✅ Discovery coverage > 90% (excellent)
- ✅ Discovery coverage > 70% (good)
- ✅ Average > 3 TCP connections per node
- ✅ PING/PONG exchanges active
- ✅ FIND_NODE/NEIGHBORS working

**DNS Mode**:
- ✅ All nodes have Mock DNS enabled
- ✅ DNS effectiveness > 80% (excellent)
- ✅ DNS effectiveness > 50% (good)
- ✅ Average > 3 discovered nodes
- ✅ DNS sync active

## Examples

### Example 1: Quick DHT Test (3 nodes, 1 minute)

```bash
./test.sh dht 3 60
./verify.sh dht
./stop-nodes.sh
```

### Example 2: DNS Discovery Test (5 nodes, 3 minutes)

```bash
./test.sh dns 5 180
./verify.sh dns
./stop-nodes.sh
```

### Example 3: Production-like DHT Test (20 nodes, 10 minutes)

```bash
./test.sh dht 20 600
./verify.sh dht

# View detailed logs
grep "FIND_NODE" logs/dht-*.log | less

# View statistics
grep "Discovered Nodes" logs/dht-*.log
```

## Viewing Logs

### View discovery activity
```bash
# DHT mode
grep "PING\|PONG\|FIND_NODE\|NEIGHBORS" logs/dht-*.log | less

# DNS mode
grep "DNS\|Mock" logs/dns-*.log | less
```

### View discovery statistics
```bash
# Any mode
grep "Discovered Nodes" logs/*.log

# View monitoring status
grep "Discovery Status" logs/*.log
```

### View specific node log
```bash
# Node 0 in DHT mode
cat logs/dht-0.log

# Node 0 in DNS mode
cat logs/dns-0.log
```

## Troubleshooting

### Issue: "No discovery test is currently running"

**Check**: No test has been started or all nodes have stopped

**Fix**:
```bash
./test.sh dht 5 180    # Start a test
```

### Issue: Low discovery coverage in DHT mode

**Possible causes**:
- Network connectivity issues
- Firewall blocking UDP ports
- Test duration too short

**Fix**:
```bash
# Increase test duration
./test.sh dht 10 600   # 10 minutes

# Check logs for errors
grep -i "error\|exception" logs/dht-*.log
```

### Issue: "Mock DNS not enabled" in DNS mode

**Check**: Mock DNS initialization failed

**Fix**:
```bash
# Rebuild with tests
mvn clean package

# Ensure test-classes directory exists
ls target/test-classes/io/xdag/p2p/discover/dns/mock/
```

### Issue: Nodes fail to start

**Check**: Port conflicts or JAR missing

**Fix**:
```bash
# Stop all Java processes
./stop-nodes.sh

# Rebuild JAR
mvn clean package -DskipTests

# Check JAR exists
ls -lh ../../target/xdagj-p2p-*.jar
```

## Architecture

### Unified DiscoveryApp

The discovery test framework uses a single **DiscoveryApp** that supports both modes:

```
DiscoveryApp (Auto-detects mode)
  │
  ├─ Kademlia DHT Mode (no --url-schemes)
  │  ├─ UDP discovery (PING/PONG)
  │  ├─ DHT routing (FIND_NODE/NEIGHBORS)
  │  └─ TCP connections
  │
  └─ DNS Discovery Mode (--url-schemes provided)
     ├─ Mock DNS (if -Dmock.dns.enabled=true)
     │  ├─ MockDnsResolver (in-memory)
     │  └─ Auto-generated DNS tree
     └─ DNS sync → Node discovery → TCP connections
```

### Mock DNS Framework

For DNS testing without external infrastructure:

```
MockDnsResolver (Singleton)
  ├─ TXT record storage (in-memory)
  ├─ Thread-safe operations
  └─ Simple CRUD API

MockableLookUpTxt
  ├─ Mock mode switching
  └─ Compatible with production code
```

## Advanced Usage

### Running specific node count ranges

```bash
# Small network (3-5 nodes)
./test.sh dht 5 180

# Medium network (10-15 nodes)
./test.sh dht 15 300

# Large network (20 nodes, max)
./test.sh dht 20 600
```

### Analyzing discovery patterns

```bash
# Count PING messages per node
for i in {0..4}; do
  echo "Node $i: $(grep -c 'Sending PING to node:' logs/dht-$i.log) PINGs sent"
done

# Check discovery progression
for t in 30 60 120; do
  echo "At ${t}s:"
  grep "Discovered Nodes:" logs/dht-0.log | grep "$t"
done
```

### Testing both modes sequentially

```bash
# Test DHT, then DNS
./test.sh dht 10 300
./verify.sh dht
./stop-nodes.sh

sleep 5

./test.sh dns 5 180
./verify.sh dns
./stop-nodes.sh
```

## Production Considerations

### DHT Mode
- **Pros**: Self-organizing, resilient, no central dependency
- **Cons**: Slower bootstrap, requires active nodes
- **Use when**: Building decentralized P2P network

### DNS Mode
- **Pros**: Fast bootstrap, reliable, well-known mechanism
- **Cons**: Requires DNS infrastructure (or Mock DNS for testing)
- **Use when**: Need fast initial node list, have DNS available

### Hybrid Approach (Recommended)
1. Use DNS for fast initial bootstrap
2. Switch to DHT for ongoing peer discovery
3. Maintain both mechanisms for redundancy

## Technical Details

### Node ID Derivation
- Derived from XDAG address (20 bytes, 160 bits)
- Used for Kademlia distance calculation
- Consistent across restarts

### Port Allocation
- Base port: 10000 (configurable)
- Node i uses port: BASE_PORT + i
- UDP and TCP on same port

### Discovery Timing
- Initial delay: 10s (monitoring start)
- Monitoring interval: 30s
- Connection establishment: 10s

### Resource Usage
- Memory: 256MB-512MB per node
- CPU: Minimal (discovery only, no mining)
- Disk: Logs only (~1-5MB per node)

## References

- **EIP-1459**: https://eips.ethereum.org/EIPS/eip-1459
- **Kademlia Paper**: Maymounkov & Mazières, 2002
- **Mock DNS Guide**: `docs/DNS_MOCK_TESTING_GUIDE.md`
- **Source Code**: `src/main/java/io/xdag/p2p/example/DiscoveryApp.java`

## Summary

This testing framework provides:
1. **Unified Scripts**: Single scripts for both DHT and DNS modes
2. **Zero Dependencies**: Mock DNS works offline
3. **Comprehensive Testing**: Automated verification
4. **Production Ready**: EIP-1459 compliant

**Get Started**:
```bash
# Quick test
./test.sh dns 5 60
./verify.sh dns
./stop-nodes.sh
```

Happy testing! 🚀
