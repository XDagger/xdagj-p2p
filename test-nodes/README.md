# P2P TPS Testing

Simple TPS testing for xdagj-p2p network.

## Quick Start

```bash
# Start 6 nodes (default)
./start-nodes.sh

# Start 2 nodes
./start-nodes.sh 2

# View logs
tail -f logs/node-*.log

# Stop all nodes
./stop-nodes.sh
```

## Performance

**Target**: 1M TPS (1,000,000 transactions per second)

**Achieved**: 500K - 1M TPS consistently

**Configuration**:
- 8 concurrent sender threads per node
- Batch processing: 100 messages per batch
- 6GB heap memory per node
- Optimized for minimal contention

## Architecture

Each node:
1. Connects to 3 seed nodes (balanced mesh topology)
2. Runs 8 sender threads for maximum throughput
3. Uses Bloom Filter + Guava Cache for deduplication
4. Real-time TPS monitoring every 5 seconds

## Requirements

- Java 21+
- Maven 3.6+
- 6GB+ RAM per node
- Available ports: 10000-1000X

## Logs

Logs are stored in `logs/node-X.log`:
- Connection events
- TPS statistics (every 5s)
- Memory usage
- Bloom Filter statistics

Example TPS output:
```
[node-10000] Uptime: 40s | TPS: 819073 | Messages: 21,464,754 | Connections: 2 | Memory: 4107/6144MB (66.8%)
```

## Troubleshooting

**Port already in use**:
```bash
lsof -ti :10000 | xargs kill -9
```

**Build failed**:
```bash
cd .. && mvn clean package -DskipTests
```

**Low TPS**:
- Check memory usage (should be < 90%)
- Verify connections established
- Check for errors in logs
