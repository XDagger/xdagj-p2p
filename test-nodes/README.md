# XDAG P2P Network Testing Suite

A comprehensive testing and analysis suite for XDAG P2P network implementation, providing tools to create, monitor, and analyze distributed peer-to-peer networks.

## 🚀 Quick Start

### Prerequisites
- Java 21 or higher
- Maven 3.6+
- Python 3.7+ (for analysis tools)
- Required Python packages: `matplotlib`, `networkx`, `pandas`

### Installation
```bash
# Install Python dependencies
pip3 install matplotlib networkx pandas

# Build the project (run from project root)
cd ..
mvn clean package -DskipTests
cd test-nodes
```

### Basic Usage
```bash
# 1. Start P2P network
./start-p2p-network.sh

# 2. Monitor network status
./status.sh

# 3. Analyze network performance
python3 analyze-network-performance.py

# 4. Stop the network
./stop-nodes.sh
```

## 📁 Directory Structure

```
test-nodes/
├── start-p2p-network.sh           # Start distributed P2P network
├── stop-nodes.sh                  # Stop all nodes
├── status.sh                      # Quick status check
├── monitor-nodes.sh               # Real-time network monitoring
├── cleanup.sh                     # Clean up test artifacts
├── analyze-network-performance.py # Network analysis tool
├── README.md                      # This documentation
└── analysis_results_latest/       # Latest analysis results
```

## 🛠️ Core Tools

### 1. Network Management Scripts

#### `start-p2p-network.sh` - Start Distributed P2P Network
Creates a true peer-to-peer network with distributed connections avoiding centralized structures.

**Features:**
- **Distributed seed configuration**: Each node connects to different seed combinations
- **Phase-based startup**: Gradual node deployment for natural network evolution
- **P2P-optimized parameters**: Configured for real peer-to-peer behavior
- **Load balancing**: No single points of failure

**Usage:**
```bash
./start-p2p-network.sh [NODES] [BASE_PORT] [NETWORK_ID]

# Examples:
./start-p2p-network.sh              # Start 20 nodes (default)
./start-p2p-network.sh 10           # Start 10 nodes
./start-p2p-network.sh 15 11000     # Start 15 nodes on port 11000+
```

**Network Architecture:**
- **Group 0**: Connects to nodes 1, 5, 9
- **Group 1**: Connects to nodes 0, 6, 10
- **Group 2**: Connects to nodes 3, 7, 11
- **Group 3**: Connects to nodes 2, 8, 12
- **Dynamic discovery**: Enables organic network growth

#### `stop-nodes.sh` - Stop All Nodes
Gracefully stops all running P2P nodes and cleans up PID files.

```bash
./stop-nodes.sh
```

#### `status.sh` - Quick Status Check
Displays current status of all nodes and network statistics.

```bash
./status.sh
```

**Output:**
- Node status with PID information
- Running/stopped node counts
- Active port listeners

#### `monitor-nodes.sh` - Real-time Network Monitor
Advanced monitoring tool providing real-time network statistics and performance metrics.

**Features:**
- Live connection monitoring
- Performance metrics tracking
- Error detection and reporting
- Network topology analysis
- Resource utilization monitoring

```bash
./monitor-nodes.sh [INTERVAL] [DURATION]

# Examples:
./monitor-nodes.sh              # Monitor with default settings
./monitor-nodes.sh 5            # Update every 5 seconds
./monitor-nodes.sh 10 300       # Monitor for 5 minutes, update every 10s
```

#### `cleanup.sh` - Environment Cleanup
Comprehensive cleanup tool for test environments.

**Cleanup operations:**
- Stop all running nodes
- Remove log files
- Clean PID files
- Clean Maven target directory (optional)

```bash
./cleanup.sh
```

### 2. Network Analysis Tool

#### `analyze-network-performance.py` - Professional Network Analyzer
Comprehensive P2P network performance analysis tool with advanced visualization capabilities.

**Key Features:**
- **Multi-format analysis**: Detailed reports, CSV exports, visualizations
- **Real-time log parsing**: Processes live node logs
- **Network topology mapping**: Clear network structure visualization
- **Performance metrics**: Latency, throughput, connection analysis
- **Error analysis**: Comprehensive error detection and reporting

**Usage:**
```bash
python3 analyze-network-performance.py [OPTIONS]

# Options:
-l, --logs-dir DIR      Log directory (default: logs)
-o, --output-dir DIR    Output directory (default: analysis_results)
-f, --format FORMAT     Output format: report,csv,json,all (default: all)

# Examples:
python3 analyze-network-performance.py
python3 analyze-network-performance.py -l logs -o results
python3 analyze-network-performance.py --format csv
```

**Generated Files:**
- `network_analysis_report.txt` - Comprehensive analysis report
- `clean_network_topology.png` - Clear network topology visualization
- `connection_statistics.png` - Connection analysis charts
- `latency_distribution.png` - Message latency distribution
- `node_performance.png` - Node performance comparison
- `message_flows.png` - Message flow analysis
- `node_summary.csv` - Node performance data
- `message_flows.csv` - Detailed message flow data

## 📊 Analysis Reports

### Network Performance Metrics
- **Latency Analysis**: Average, median, standard deviation
- **Throughput Metrics**: Messages per second, data volume
- **Connection Statistics**: Network density, node degrees
- **Error Rates**: Connection failures, timeout analysis

### Network Topology Analysis
- **Connectivity**: Network diameter, average path length
- **Node Roles**: Bootstrap nodes, high-connectivity nodes
- **Load Distribution**: Connection load balancing
- **Network Health**: Overall network status assessment

### Message Flow Analysis
- **Message Types**: Distribution by test type
- **Flow Patterns**: Message routing efficiency
- **Performance Ranking**: Node performance comparison
- **Error Analysis**: Failure pattern detection

## 🎯 Testing Scenarios

### 1. Basic P2P Network Test
```bash
# Start network
./start-p2p-network.sh

# Monitor for 5 minutes
./monitor-nodes.sh 10 300

# Analyze results
python3 analyze-network-performance.py

# Stop network
./stop-nodes.sh
```

### 2. Extended Performance Test
```bash
# Start larger network
./start-p2p-network.sh 30

# Extended monitoring
./monitor-nodes.sh 30 1800  # 30 minutes

# Comprehensive analysis
python3 analyze-network-performance.py --format all

# Cleanup
./cleanup.sh
```

### 3. Custom Configuration Test
```bash
# Custom port range
./start-p2p-network.sh 15 12000

# Custom analysis
python3 analyze-network-performance.py -o custom_results

# Status check
./status.sh
```

## ⚙️ Configuration Options

### Network Parameters
- **Node Count**: 1-50 nodes (recommended: 10-30)
- **Base Port**: Starting port number (default: 10000)
- **Network ID**: Network identifier (default: 1)
- **Connection Limits**: Max connections per node
- **Discovery Settings**: Peer discovery configuration

### Java Runtime Options
- **Memory Settings**: Heap size optimization
- **GC Configuration**: Garbage collection tuning
- **Performance Flags**: JVM optimization flags

### Analysis Options
- **Output Formats**: Report, CSV, JSON, visualizations
- **Time Ranges**: Configurable analysis periods
- **Metric Filters**: Selective metric analysis

## 🔧 Troubleshooting

### Common Issues

#### 1. Nodes Fail to Start
```bash
# Check Java installation (requires Java 21+)
java -version

# Verify JAR file exists
ls -la ../target/xdagj-p2p-*-jar-with-dependencies.jar

# Check port availability
netstat -an | grep 10000
```

#### 2. Connection Issues
```bash
# Check node status
./status.sh

# View logs
tail -f logs/node-0.log

# Monitor connections
./monitor-nodes.sh 5
```

#### 3. Analysis Errors
```bash
# Verify Python dependencies
pip3 list | grep -E "(matplotlib|networkx|pandas)"

# Check log directory
ls -la logs/

# Test with minimal analysis
python3 analyze-network-performance.py --format report
```

### Performance Optimization

#### For Large Networks (30+ nodes)
- Increase JVM heap size: `-Xmx1024m`
- Use G1 garbage collector: `-XX:+UseG1GC`
- Adjust connection limits: `-M 20 -m 5`

#### For High-Performance Testing
- Enable performance flags: `-XX:+AggressiveOpts`
- Optimize discovery: Reduce discovery intervals
- Monitor system resources: CPU, memory, network

## 📈 Performance Benchmarks

### Typical Performance (20 nodes)
- **Startup Time**: 30-60 seconds
- **Average Latency**: 5-10ms
- **Connection Success Rate**: >99%
- **Memory Usage**: ~500MB per node
- **Network Diameter**: 3-5 hops

### Scalability Limits
- **Maximum Tested**: 50 nodes
- **Recommended**: 10-30 nodes
- **Port Range**: 10000-10999 (1000 ports)
- **Memory Requirements**: 256MB-1GB per node

## 🔬 Advanced Features

### Custom Network Topologies
- Modify seed node configurations in `start-p2p-network.sh`
- Adjust connection parameters for different topologies
- Implement custom discovery patterns

### Extended Analysis
- Custom metric collection
- Time-series analysis
- Comparative network studies
- Performance regression testing

### Integration Testing
- CI/CD pipeline integration
- Automated test suites
- Performance regression detection
- Network stress testing

## 📝 Contributing

### Development Guidelines
- Follow Java coding standards
- Use English for all comments and documentation
- Implement comprehensive logging
- Include unit tests for new features

### Testing Protocol
- Test with multiple node counts
- Verify cross-platform compatibility
- Performance baseline validation
- Documentation updates

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](../LICENSE) file for details.

## 🤝 Support

For issues, questions, or contributions:
- Create GitHub issues for bugs or feature requests
- Submit pull requests for improvements
- Follow the coding standards and testing protocols

---

**Note**: This testing suite is designed for development and testing purposes. For production deployments, additional security and performance considerations may be required. 