# XDAG P2P Network Testing Framework V2

Professional multi-node P2P network performance testing tool for XDAG blockchain.

## ✨ Key Features

- ✅ **Real P2P Routing Test** - 27.9% message forwarding rate
- ✅ **Accurate Network Topology Analysis** - Auto-detect node connections
- ✅ **Comprehensive Performance Metrics** - Latency, throughput, forwarding efficiency
- ✅ **Smart Network Topology Generation** - Mesh network structure, decentralized
- ✅ **Rich Visualizations** - 5 professional charts
- ✅ **Detailed CSV Data Export** - For further analysis

## 🚀 Quick Start

### 1. Start Test Network

```bash
# Start 6 nodes (default)
./start-nodes.sh

# Or specify node count
./start-nodes.sh 10
```

### 2. View Real-time Logs

```bash
# View all node logs
tail -f logs/node-*.log

# View specific node
tail -f logs/node-0.log
```

### 3. Performance Analysis

```bash
# Run analysis after nodes have been running for 2-5 minutes
python3 analyze-p2p-performance.py

# Analysis results saved to:
# - analysis_results/performance_report.txt  (text report)
# - analysis_results/*.png                   (visualization charts)
# - analysis_results/*.csv                   (raw data)
```

### 4. Stop Nodes

```bash
./stop-nodes.sh
```

## 📊 Sample Test Results (6 Nodes, 2 Minutes)

### Network Topology
- **Total Nodes**: 6
- **Total Connections**: 11
- **Average Connections**: 3.67 per node
- **Network Density**: 0.733

### Message Statistics
- **Total Messages Received**: 73,055
- **Total Messages Forwarded**: 20,386
- **Unique Messages**: 3,999
- **Forward Ratio**: 27.91% ✅
- **Average Receives per Message**: 18.27

### Latency Metrics
- **Mean Latency**: 3.23ms
- **Median Latency**: 2.00ms
- **P95 Latency**: 10.00ms
- **P99 Latency**: 18.00ms

### Routing Efficiency
- **Average Hops**: 2.54
- **Max Hops**: 4
- **Multi-hop Message Ratio**: 100% ✅

## 📁 Generated Files

```
test-nodes/
├── logs/                         # Node log files
│   ├── node-0.log
│   ├── node-1.log
│   └── ...
├── pids/                         # Process ID files
│   ├── node-0.pid
│   └── ...
└── analysis_results/             # Performance analysis results
    ├── performance_report.txt    # Text report
    ├── network_topology.png      # Network topology graph
    ├── node_performance.png      # Node performance comparison
    ├── latency_distribution.png  # Latency distribution
    ├── message_flow_heatmap.png  # Message flow heatmap
    ├── test_type_distribution.png # Test type distribution
    ├── node_summary.csv          # Node summary data
    └── message_summary.csv       # Message summary data
```

## 🔧 Scripts

| Script | Purpose |
|--------|---------|
| `start-nodes.sh` | Start P2P test network |
| `stop-nodes.sh` | Stop all nodes |
| `analyze-p2p-performance.py` | Performance analysis and report generation |

## ⚙️ Requirements

- Java 21+
- Python 3.7+ (matplotlib, pandas, networkx required)
- Built JAR file: `../target/xdagj-p2p-0.1.2-jar-with-dependencies.jar`

Install Python dependencies:
```bash
pip3 install matplotlib pandas networkx
```

## 📈 Performance Metrics Explained

The analyzer provides the following metrics:

### Network Topology Metrics
- **Total Nodes** - Number of nodes participating in the test
- **Total Connections** - Number of successful P2P connections
- **Network Density** - Actual connections / maximum possible connections
- **Connection Distribution** - Connection count per node

### Message Routing Metrics
- **Total Messages Received** - Total messages received by all nodes
- **Total Messages Forwarded** - Messages forwarded by nodes (tests real routing)
- **Forward Ratio** - Forwarded messages / received messages
- **Average Hops** - Average number of hops from source to destination

### Performance Metrics
- **Latency Statistics** - Mean, median, P95, P99 latency
- **Throughput** - Messages processed per unit time
- **Error Rate** - Error count / total messages

### Node Ranking
- Comprehensive score considers:
  - Message receive count (30% weight)
  - Message forward count (40% weight)
  - Latency performance (20% weight)
  - Connection count (10% weight)

## 💡 Example Workflow

```bash
# 1. Build the project
cd ..
mvn clean package -DskipTests
cd test-nodes

# 2. Start test network (6 nodes)
./start-nodes.sh 6

# 3. Let nodes run for 2-5 minutes for performance testing

# 4. Run performance analysis
python3 analyze-p2p-performance.py

# 5. View results
cat analysis_results/performance_report.txt
open analysis_results/*.png  # View charts on macOS

# 6. Stop testing
./stop-nodes.sh
```

## 🔍 Troubleshooting

### Nodes Won't Start
- Check JAR file: `ls -lh ../target/*.jar`
- Rebuild if needed: `cd .. && mvn clean package -DskipTests`

### Port Conflicts
- Stop existing processes: `pkill -f "io.xdag.p2p.example.StartApp"`
- Check port usage: `lsof -i :10000-10010`

### Analysis Fails
- Install dependencies: `pip3 install matplotlib pandas networkx`
- Check logs: `ls -lh logs/`

## 🚀 Advanced Usage

### Custom Base Port
Edit `start-nodes.sh` and change `BASE_PORT=10000`

### Long-duration Testing
Let nodes run longer to collect more data for better analysis accuracy.

### Large-scale Network Testing
```bash
./start-nodes.sh 20  # 20 nodes
./start-nodes.sh 50  # 50 nodes
```

## 💪 Performance Tips

- **Memory**: Each node uses ~256-512MB RAM
- **CPU**: Minimal CPU usage when idle
- **Disk**: Log files grow ~1-2MB per node per minute
- **Network**: All traffic is localhost, no external bandwidth used

## 🎯 Testing Framework V2 Improvements

Compared to the old version, V2 has resolved the following critical issues:

### ✅ Fixed Issues
1. **Message forwarding rate improved from 0% to 27.9%** - Implemented real multi-hop routing
2. **Accurate connection parsing** - Correctly identifies connections between nodes
3. **Network topology optimization** - Changed from centralized to mesh structure (density 0.733)
4. **Complete performance metrics** - Added forwarding rate, hop count, routing efficiency, etc.
5. **Enhanced log output** - Structured logs for easy parsing

### 📊 Test Data Comparison

| Metric | Old Version | V2 Version | Improvement |
|--------|-------------|------------|-------------|
| Message Forward Ratio | 0% | 27.91% | ✅ Real routing implemented |
| Network Connection Detection | Failed | 11 connections | ✅ Correctly parsed |
| Network Density | 0.000 | 0.733 | ✅ Good mesh structure |
| Multi-hop Message Ratio | Unknown | 100% | ✅ All messages test routing |
| Average Hops | 0 | 2.54 | ✅ Multi-hop forwarding verified |

---

For more details, see the main project README: `../README.md`
