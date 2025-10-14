#!/usr/bin/env python3
"""
XDAG P2P Network Performance Analyzer V2
Complete rewrite with accurate parsing and comprehensive metrics
"""

import re
import os
import sys
from datetime import datetime
from collections import defaultdict, Counter
from typing import Dict, List, Set, Tuple
import statistics

# Optional imports for visualization
try:
    import matplotlib.pyplot as plt
    import matplotlib.patches as mpatches
    import networkx as nx
    HAS_MATPLOTLIB = True
except ImportError:
    HAS_MATPLOTLIB = False
    print("Warning: matplotlib/networkx not available, skipping visualizations")

try:
    import pandas as pd
    HAS_PANDAS = True
except ImportError:
    HAS_PANDAS = False
    print("Warning: pandas not available, skipping CSV export")


class P2PNetworkAnalyzer:
    """Advanced P2P Network Performance Analyzer"""

    def __init__(self, logs_dir: str = "logs", output_dir: str = "analysis_results"):
        self.logs_dir = logs_dir
        self.output_dir = output_dir

        # Core data structures
        self.node_info = {}  # node_id -> {connections, messages, etc}
        self.connections = defaultdict(set)  # node_id -> set of connected node_ids
        self.message_tracking = {}  # message_id -> {origin, hops, latencies, path}
        self.forwarding_stats = defaultdict(lambda: {'sent': 0, 'received': 0, 'forwarded': 0, 'latencies': []})

        # Timeline data
        self.connection_events = []  # List of connection/disconnection events
        self.message_events = []  # List of all message events

        # NEW: Time-series data for throughput analysis
        self.message_timestamps = []  # All message timestamps for throughput calculation
        self.message_sizes = []  # All message sizes in bytes

        # NEW: Connection stability tracking
        self.connection_lifetimes = {}  # connection_key -> {start_time, end_time, disconnections}
        self.active_connections = {}  # Track currently active connections

        os.makedirs(output_dir, exist_ok=True)

    def parse_all_logs(self):
        """Parse all node log files"""
        log_files = sorted([f for f in os.listdir(self.logs_dir) if f.endswith('.log')])

        if not log_files:
            print(f"ERROR: No log files found in {self.logs_dir}")
            return False

        print(f"Found {len(log_files)} log files")

        for log_file in log_files:
            # Extract node ID from filename (node-0.log -> node-10000)
            match = re.search(r'node-(\d+)\.log', log_file)
            if match:
                node_number = int(match.group(1))
                node_id = f"node-{10000 + node_number}"  # Map to port-based ID
                log_path = os.path.join(self.logs_dir, log_file)
                print(f"Parsing {log_file} as {node_id}...")
                self.parse_node_log(log_path, node_id, node_number)

        print(f"Parsed {len(self.node_info)} nodes")
        return True

    def parse_node_log(self, log_path: str, node_id: str, node_number: int):
        """Parse a single node's log file"""
        if node_id not in self.node_info:
            self.node_info[node_id] = {
                'node_number': node_number,
                'messages_received': 0,
                'messages_forwarded': 0,
                'messages_sent': 0,
                'connection_count': 0,
                'errors': 0,
                'latencies': [],
                'test_types': Counter()
            }

        try:
            with open(log_path, 'r', encoding='utf-8') as f:
                for line in f:
                    self._parse_log_line(line, node_id)
        except Exception as e:
            print(f"ERROR parsing {log_path}: {e}")

    def _parse_log_line(self, line: str, node_id: str):
        """Parse a single log line"""
        # Extract timestamp
        ts_match = re.search(r'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})', line)
        if not ts_match:
            return
        timestamp = ts_match.group(1)

        # Parse connection established events
        # NEW Format: CONN_ESTABLISHED|timestamp|node-10000|/127.0.0.1:63863|1
        if 'CONN_ESTABLISHED|' in line:
            parts = line.split('CONN_ESTABLISHED|')[1].split('|')
            if len(parts) >= 4:
                event_timestamp = int(parts[0].strip())
                source_node = parts[1].strip()
                peer_addr = parts[2].strip()
                conn_count = int(parts[3].strip())

                # Extract peer port from address
                port_match = re.search(r':(\d+)', peer_addr)
                if port_match:
                    peer_port = int(port_match.group(1))
                    # Map port to node ID (10000-10999 range)
                    if 10000 <= peer_port <= 10999:
                        peer_node = f"node-{peer_port}"
                        self.connections[source_node].add(peer_node)
                        self.connections[peer_node].add(source_node)  # Bidirectional
                        # Connection count will be calculated from self.connections, not from log

                        # NEW: Track connection lifetime
                        conn_key = tuple(sorted([source_node, peer_node]))
                        if conn_key not in self.connection_lifetimes:
                            self.connection_lifetimes[conn_key] = {
                                'start_time': event_timestamp,
                                'end_time': None,
                                'disconnections': 0
                            }
                        else:
                            # Reconnection
                            self.connection_lifetimes[conn_key]['disconnections'] += 1
                            self.connection_lifetimes[conn_key]['start_time'] = event_timestamp
                            self.connection_lifetimes[conn_key]['end_time'] = None

                        self.active_connections[conn_key] = event_timestamp

                        self.connection_events.append({
                            'timestamp': timestamp,
                            'event_timestamp': event_timestamp,
                            'type': 'established',
                            'node': source_node,
                            'peer': peer_node
                        })

        # Parse connection closed events
        # NEW Format: CONN_CLOSED|timestamp|node-10000|/127.0.0.1:63863|0
        elif 'CONN_CLOSED|' in line:
            parts = line.split('CONN_CLOSED|')[1].split('|')
            if len(parts) >= 4:
                event_timestamp = int(parts[0].strip())
                source_node = parts[1].strip()
                peer_addr = parts[2].strip()

                # Extract peer port
                port_match = re.search(r':(\d+)', peer_addr)
                if port_match:
                    peer_port = int(port_match.group(1))
                    if 10000 <= peer_port <= 10999:
                        peer_node = f"node-{peer_port}"

                        # NEW: Update connection lifetime
                        conn_key = tuple(sorted([source_node, peer_node]))
                        if conn_key in self.connection_lifetimes:
                            self.connection_lifetimes[conn_key]['end_time'] = event_timestamp

                        if conn_key in self.active_connections:
                            del self.active_connections[conn_key]

                self.connection_events.append({
                    'timestamp': timestamp,
                    'event_timestamp': event_timestamp,
                    'type': 'closed',
                    'node': source_node,
                    'peer': peer_addr
                })

        # Parse message received events
        # NEW Format: MSG_RECEIVED|timestamp|node-10000|a457ff4f|node-10000|1|6|2|connection_test|150
        elif 'MSG_RECEIVED|' in line:
            parts = line.split('MSG_RECEIVED|')[1].split('|')
            if len(parts) >= 9:
                event_timestamp = int(parts[0].strip())
                receiver = parts[1].strip()
                msg_id = parts[2].strip()
                origin = parts[3].strip()
                hops = int(parts[4].strip())
                max_hops = int(parts[5].strip())
                latency = int(parts[6].strip())
                test_type = parts[7].strip()
                message_size = int(parts[8].strip())

                self.node_info[receiver]['messages_received'] += 1
                self.node_info[receiver]['latencies'].append(latency)
                self.node_info[receiver]['test_types'][test_type] += 1

                # NEW: Track timestamps and sizes for throughput calculation
                self.message_timestamps.append(event_timestamp)
                self.message_sizes.append(message_size)

                # Track message path
                if msg_id not in self.message_tracking:
                    self.message_tracking[msg_id] = {
                        'origin': origin,
                        'receivers': [],
                        'forwarders': [],
                        'max_hops': max_hops,
                        'test_type': test_type,
                        'latencies': [],
                        'size': message_size
                    }

                self.message_tracking[msg_id]['receivers'].append((receiver, hops))
                self.message_tracking[msg_id]['latencies'].append(latency)

                self.message_events.append({
                    'timestamp': timestamp,
                    'event_timestamp': event_timestamp,
                    'type': 'received',
                    'node': receiver,
                    'message_id': msg_id,
                    'origin': origin,
                    'hops': hops,
                    'latency': latency,
                    'test_type': test_type,
                    'size': message_size
                })

        # Parse message forwarded events
        # NEW Format: MSG_FORWARDED|timestamp|node-10001|a457ff4f|node-10000|2|1|150
        elif 'MSG_FORWARDED|' in line:
            parts = line.split('MSG_FORWARDED|')[1].split('|')
            if len(parts) >= 7:
                event_timestamp = int(parts[0].strip())
                forwarder = parts[1].strip()
                msg_id = parts[2].strip()
                origin = parts[3].strip()
                new_hops = int(parts[4].strip())
                channel_count = int(parts[5].strip())
                message_size = int(parts[6].strip())

                self.node_info[forwarder]['messages_forwarded'] += 1

                if msg_id in self.message_tracking:
                    self.message_tracking[msg_id]['forwarders'].append((forwarder, new_hops))

                self.message_events.append({
                    'timestamp': timestamp,
                    'event_timestamp': event_timestamp,
                    'type': 'forwarded',
                    'node': forwarder,
                    'message_id': msg_id,
                    'origin': origin,
                    'hops': new_hops,
                    'size': message_size
                })

        # Parse statistics output
        elif 'STATS|' in line:
            # Extract statistics data
            pass  # Already captured from individual events

        # Parse errors
        elif 'ERROR' in line or 'Exception' in line:
            self.node_info[node_id]['errors'] += 1

    def calculate_metrics(self) -> Dict:
        """Calculate comprehensive network metrics"""
        metrics = {}

        # Network topology metrics
        total_nodes = len(self.node_info)
        total_connections = sum(len(conns) for conns in self.connections.values()) // 2  # Bidirectional
        metrics['topology'] = {
            'total_nodes': total_nodes,
            'total_connections': total_connections,
            'avg_connections_per_node': total_connections * 2 / total_nodes if total_nodes > 0 else 0,
            'connection_distribution': {node: len(conns) for node, conns in self.connections.items()}
        }

        # Message metrics
        total_received = sum(info['messages_received'] for info in self.node_info.values())
        total_forwarded = sum(info['messages_forwarded'] for info in self.node_info.values())
        total_unique_messages = len(self.message_tracking)

        metrics['messages'] = {
            'total_received': total_received,
            'total_forwarded': total_forwarded,
            'unique_messages': total_unique_messages,
            'forward_ratio': total_forwarded / total_received if total_received > 0 else 0,
            'avg_receives_per_message': total_received / total_unique_messages if total_unique_messages > 0 else 0
        }

        # Latency metrics
        all_latencies = []
        for info in self.node_info.values():
            all_latencies.extend(info['latencies'])

        if all_latencies:
            metrics['latency'] = {
                'mean': statistics.mean(all_latencies),
                'median': statistics.median(all_latencies),
                'stdev': statistics.stdev(all_latencies) if len(all_latencies) > 1 else 0,
                'min': min(all_latencies),
                'max': max(all_latencies),
                'p95': statistics.quantiles(all_latencies, n=20)[18] if len(all_latencies) >= 20 else max(all_latencies),
                'p99': statistics.quantiles(all_latencies, n=100)[98] if len(all_latencies) >= 100 else max(all_latencies)
            }
        else:
            metrics['latency'] = {'mean': 0, 'median': 0, 'stdev': 0, 'min': 0, 'max': 0, 'p95': 0, 'p99': 0}

        # Routing efficiency metrics
        hop_counts = []
        for msg_data in self.message_tracking.values():
            if msg_data['receivers']:
                max_hops_reached = max(hop for _, hop in msg_data['receivers'])
                hop_counts.append(max_hops_reached)

        if hop_counts:
            metrics['routing'] = {
                'avg_hops': statistics.mean(hop_counts),
                'max_hops_observed': max(hop_counts),
                'messages_with_multihop': sum(1 for h in hop_counts if h > 0),
                'multihop_ratio': sum(1 for h in hop_counts if h > 0) / len(hop_counts)
            }
        else:
            metrics['routing'] = {'avg_hops': 0, 'max_hops_observed': 0, 'messages_with_multihop': 0, 'multihop_ratio': 0}

        # Node performance ranking
        node_scores = []
        for node_id, info in self.node_info.items():
            score = (
                info['messages_received'] * 0.3 +
                info['messages_forwarded'] * 0.4 +
                (1000 / (statistics.mean(info['latencies']) if info['latencies'] else 1)) * 0.2 +
                len(self.connections.get(node_id, [])) * 0.1
            )
            node_scores.append((node_id, score, info))

        node_scores.sort(key=lambda x: x[1], reverse=True)
        metrics['node_ranking'] = node_scores

        # Test type distribution
        all_test_types = Counter()
        for info in self.node_info.values():
            all_test_types.update(info['test_types'])
        metrics['test_types'] = all_test_types

        # Error metrics
        total_errors = sum(info['errors'] for info in self.node_info.values())
        metrics['errors'] = {
            'total': total_errors,
            'error_rate': total_errors / total_received if total_received > 0 else 0
        }

        # NEW: Throughput metrics
        metrics['throughput'] = self._calculate_throughput_metrics()

        # NEW: Connection stability metrics
        metrics['connection_stability'] = self._calculate_connection_stability()

        # NEW: Load balance analysis
        metrics['load_balance'] = self._calculate_load_balance()

        return metrics

    def _calculate_throughput_metrics(self) -> Dict:
        """Calculate throughput metrics"""
        if not self.message_timestamps:
            return {
                'messages_per_second': 0,
                'bytes_per_second': 0,
                'avg_message_size': 0,
                'total_bytes': 0,
                'duration_seconds': 0,
                'peak_messages_per_second': 0
            }

        # Sort timestamps
        sorted_timestamps = sorted(self.message_timestamps)
        start_time = sorted_timestamps[0]
        end_time = sorted_timestamps[-1]
        duration_ms = end_time - start_time
        duration_sec = duration_ms / 1000.0

        # Calculate overall throughput
        total_messages = len(self.message_timestamps)
        total_bytes = sum(self.message_sizes)
        messages_per_second = total_messages / duration_sec if duration_sec > 0 else 0
        bytes_per_second = total_bytes / duration_sec if duration_sec > 0 else 0
        avg_message_size = total_bytes / total_messages if total_messages > 0 else 0

        # Calculate peak throughput (1-second windows)
        peak_mps = 0
        if duration_sec > 1:
            window_size = 1000  # 1 second in milliseconds
            for i in range(0, int(duration_ms), window_size):
                window_start = start_time + i
                window_end = window_start + window_size
                messages_in_window = sum(1 for ts in sorted_timestamps if window_start <= ts < window_end)
                peak_mps = max(peak_mps, messages_in_window)

        return {
            'messages_per_second': messages_per_second,
            'bytes_per_second': bytes_per_second,
            'avg_message_size': avg_message_size,
            'total_bytes': total_bytes,
            'duration_seconds': duration_sec,
            'peak_messages_per_second': peak_mps
        }

    def _calculate_connection_stability(self) -> Dict:
        """Calculate connection stability metrics"""
        if not self.connection_lifetimes:
            return {
                'total_connections': 0,
                'active_connections': 0,
                'disconnections': 0,
                'reconnections': 0,
                'avg_connection_duration_seconds': 0,
                'connection_uptime_ratio': 0,
                'most_stable_connections': []
            }

        total_connections = len(self.connection_lifetimes)
        active_connections = len(self.active_connections)
        total_disconnections = sum(conn['disconnections'] for conn in self.connection_lifetimes.values())
        reconnections = sum(1 for conn in self.connection_lifetimes.values() if conn['disconnections'] > 0)

        # Calculate connection durations
        durations = []
        for conn_key, conn_data in self.connection_lifetimes.items():
            start = conn_data['start_time']
            end = conn_data['end_time'] if conn_data['end_time'] else max(self.message_timestamps) if self.message_timestamps else start
            duration_ms = end - start
            durations.append(duration_ms)

        avg_duration_ms = statistics.mean(durations) if durations else 0
        avg_duration_sec = avg_duration_ms / 1000.0

        # Calculate uptime ratio
        if self.message_timestamps:
            total_time_ms = max(self.message_timestamps) - min(self.message_timestamps)
            total_uptime_ms = sum(durations)
            uptime_ratio = (total_uptime_ms / (total_time_ms * total_connections)) if total_time_ms > 0 else 0
        else:
            uptime_ratio = 0

        # Find most stable connections (longest duration, fewest disconnections)
        stable_connections = []
        for conn_key, conn_data in self.connection_lifetimes.items():
            start = conn_data['start_time']
            end = conn_data['end_time'] if conn_data['end_time'] else max(self.message_timestamps) if self.message_timestamps else start
            duration_ms = end - start
            disconnections = conn_data['disconnections']
            stable_connections.append({
                'nodes': f"{conn_key[0]} <-> {conn_key[1]}",
                'duration_seconds': duration_ms / 1000.0,
                'disconnections': disconnections
            })

        stable_connections.sort(key=lambda x: (x['disconnections'], -x['duration_seconds']))

        return {
            'total_connections': total_connections,
            'active_connections': active_connections,
            'disconnections': total_disconnections,
            'reconnections': reconnections,
            'avg_connection_duration_seconds': avg_duration_sec,
            'connection_uptime_ratio': uptime_ratio,
            'most_stable_connections': stable_connections[:5]
        }

    def _calculate_load_balance(self) -> Dict:
        """Calculate load balance metrics"""
        message_counts = [info['messages_received'] for info in self.node_info.values()]

        if not message_counts:
            return {
                'min_messages': 0,
                'max_messages': 0,
                'avg_messages': 0,
                'std_dev': 0,
                'imbalance_ratio': 0,
                'coefficient_of_variation': 0
            }

        min_messages = min(message_counts)
        max_messages = max(message_counts)
        avg_messages = statistics.mean(message_counts)
        std_dev = statistics.stdev(message_counts) if len(message_counts) > 1 else 0

        # Imbalance ratio: max / min
        imbalance_ratio = max_messages / min_messages if min_messages > 0 else float('inf')

        # Coefficient of variation: std_dev / mean (lower is better)
        coefficient_of_variation = std_dev / avg_messages if avg_messages > 0 else 0

        return {
            'min_messages': min_messages,
            'max_messages': max_messages,
            'avg_messages': avg_messages,
            'std_dev': std_dev,
            'imbalance_ratio': imbalance_ratio,
            'coefficient_of_variation': coefficient_of_variation
        }

    def generate_report(self, metrics: Dict) -> str:
        """Generate comprehensive text report"""
        lines = []
        lines.append("=" * 100)
        lines.append("XDAG P2P Network Performance Analysis Report V2")
        lines.append("=" * 100)
        lines.append(f"Analysis Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        lines.append(f"Log Directory: {self.logs_dir}")
        lines.append("")

        # Network Topology
        lines.append("🌐 Network Topology")
        lines.append("-" * 60)
        topo = metrics['topology']
        lines.append(f"Total Nodes: {topo['total_nodes']}")
        lines.append(f"Total Connections: {topo['total_connections']}")
        lines.append(f"Average Connections per Node: {topo['avg_connections_per_node']:.2f}")
        lines.append(f"Network Density: {2 * topo['total_connections'] / (topo['total_nodes'] * (topo['total_nodes'] - 1)):.3f}")
        lines.append("")

        # Connection Distribution
        lines.append("Connection Distribution:")
        for node_id, conn_count in sorted(topo['connection_distribution'].items(), key=lambda x: x[1], reverse=True):
            node_num = self.node_info[node_id]['node_number']
            lines.append(f"  {node_id} (Node-{node_num}): {conn_count} connections")
        lines.append("")

        # Message Statistics
        lines.append("📊 Message Statistics")
        lines.append("-" * 60)
        msg = metrics['messages']
        lines.append(f"Total Messages Received: {msg['total_received']:,}")
        lines.append(f"Total Messages Forwarded: {msg['total_forwarded']:,}")
        lines.append(f"Unique Messages: {msg['unique_messages']:,}")
        lines.append(f"Forward Ratio: {msg['forward_ratio']:.2%}")
        lines.append(f"Average Receives per Message: {msg['avg_receives_per_message']:.2f}")
        lines.append("")

        # Latency Metrics
        lines.append("⚡ Latency Metrics")
        lines.append("-" * 60)
        lat = metrics['latency']
        lines.append(f"Mean Latency: {lat['mean']:.2f}ms")
        lines.append(f"Median Latency: {lat['median']:.2f}ms")
        lines.append(f"Std Deviation: {lat['stdev']:.2f}ms")
        lines.append(f"Min Latency: {lat['min']}ms")
        lines.append(f"Max Latency: {lat['max']}ms")
        lines.append(f"P95 Latency: {lat['p95']:.2f}ms")
        lines.append(f"P99 Latency: {lat['p99']:.2f}ms")
        lines.append("")

        # Routing Efficiency
        lines.append("🔀 Routing Efficiency")
        lines.append("-" * 60)
        route = metrics['routing']
        lines.append(f"Average Hops: {route['avg_hops']:.2f}")
        lines.append(f"Max Hops Observed: {route['max_hops_observed']}")
        lines.append(f"Messages with Multi-hop: {route['messages_with_multihop']:,} ({route['multihop_ratio']:.1%})")
        lines.append("")

        # Node Performance Ranking
        lines.append("🏆 Node Performance Ranking")
        lines.append("-" * 60)
        for i, (node_id, score, info) in enumerate(metrics['node_ranking'][:10], 1):
            node_num = info['node_number']
            avg_lat = statistics.mean(info['latencies']) if info['latencies'] else 0
            lines.append(f"{i:2d}. {node_id} (Node-{node_num}) | "
                        f"Score: {score:7.1f} | "
                        f"Recv: {info['messages_received']:5d} | "
                        f"Fwd: {info['messages_forwarded']:5d} | "
                        f"Lat: {avg_lat:5.1f}ms")
        lines.append("")

        # Test Type Distribution
        lines.append("📈 Test Type Distribution")
        lines.append("-" * 60)
        test_types = metrics['test_types']
        total_test_msgs = sum(test_types.values())
        for test_type, count in test_types.most_common():
            pct = count / total_test_msgs * 100 if total_test_msgs > 0 else 0
            lines.append(f"{test_type:>20}: {count:6,} messages ({pct:5.1f}%)")
        lines.append("")

        # Error Statistics
        lines.append("❌ Error Statistics")
        lines.append("-" * 60)
        err = metrics['errors']
        lines.append(f"Total Errors: {err['total']}")
        lines.append(f"Error Rate: {err['error_rate']:.4%}")
        lines.append("")

        # NEW: Throughput Metrics
        lines.append("🚀 Throughput Metrics")
        lines.append("-" * 60)
        tput = metrics['throughput']
        lines.append(f"Test Duration: {tput['duration_seconds']:.1f} seconds")
        lines.append(f"Messages per Second: {tput['messages_per_second']:.1f} msg/s")
        lines.append(f"Bytes per Second: {tput['bytes_per_second']:.1f} bytes/s ({tput['bytes_per_second']/1024:.1f} KB/s)")
        lines.append(f"Average Message Size: {tput['avg_message_size']:.1f} bytes")
        lines.append(f"Total Data Transferred: {tput['total_bytes']:,} bytes ({tput['total_bytes']/1024/1024:.2f} MB)")
        lines.append(f"Peak Messages per Second: {tput['peak_messages_per_second']} msg/s")
        lines.append("")

        # NEW: Connection Stability
        lines.append("🔗 Connection Stability")
        lines.append("-" * 60)
        conn_stab = metrics['connection_stability']
        lines.append(f"Total Connections Established: {conn_stab['total_connections']}")
        lines.append(f"Active Connections: {conn_stab['active_connections']}")
        lines.append(f"Disconnections: {conn_stab['disconnections']}")
        lines.append(f"Reconnections: {conn_stab['reconnections']}")
        lines.append(f"Average Connection Duration: {conn_stab['avg_connection_duration_seconds']:.1f} seconds")
        lines.append(f"Connection Uptime Ratio: {conn_stab['connection_uptime_ratio']:.1%}")
        lines.append("")

        if conn_stab['most_stable_connections']:
            lines.append("Most Stable Connections:")
            for i, conn in enumerate(conn_stab['most_stable_connections'], 1):
                lines.append(f"  {i}. {conn['nodes']}: {conn['duration_seconds']:.1f}s, {conn['disconnections']} disconnections")
            lines.append("")

        # NEW: Load Balance Analysis
        lines.append("⚖️  Load Balance Analysis")
        lines.append("-" * 60)
        lb = metrics['load_balance']
        lines.append(f"Min Messages per Node: {lb['min_messages']:,}")
        lines.append(f"Max Messages per Node: {lb['max_messages']:,}")
        lines.append(f"Average Messages per Node: {lb['avg_messages']:.1f}")
        lines.append(f"Standard Deviation: {lb['std_dev']:.1f}")
        lines.append(f"Imbalance Ratio (max/min): {lb['imbalance_ratio']:.2f}x")
        lines.append(f"Coefficient of Variation: {lb['coefficient_of_variation']:.3f}")
        lines.append("")

        if lb['imbalance_ratio'] > 2:
            lines.append("⚠️  Warning: Significant load imbalance detected (>2x difference)")
            lines.append("")

        lines.append("=" * 100)

        return "\n".join(lines)

    def create_network_graph(self) -> 'nx.Graph':
        """Create NetworkX graph from connections"""
        if not HAS_MATPLOTLIB:
            return None

        G = nx.Graph()

        # Add all nodes
        for node_id in self.node_info.keys():
            node_num = self.node_info[node_id]['node_number']
            G.add_node(node_num,
                      label=node_id,
                      connections=len(self.connections.get(node_id, [])),
                      messages=self.node_info[node_id]['messages_received'])

        # Add edges
        added_edges = set()
        for source_node, peers in self.connections.items():
            source_num = self.node_info[source_node]['node_number']
            for peer_node in peers:
                if peer_node in self.node_info:
                    peer_num = self.node_info[peer_node]['node_number']
                    edge = tuple(sorted([source_num, peer_num]))
                    if edge not in added_edges:
                        G.add_edge(source_num, peer_num)
                        added_edges.add(edge)

        return G

    def generate_visualizations(self, metrics: Dict):
        """Generate all visualization charts"""
        if not HAS_MATPLOTLIB:
            print("Skipping visualizations (matplotlib not available)")
            return

        print("Generating visualizations...")

        # 1. Network Topology Graph
        self._plot_network_topology()

        # 2. Node Performance Chart
        self._plot_node_performance(metrics)

        # 3. Latency Distribution
        self._plot_latency_distribution(metrics)

        # 4. Message Flow Heatmap
        self._plot_message_heatmap()

        # 5. Test Type Distribution
        self._plot_test_type_distribution(metrics)

        # NEW: 6. Time Series - Throughput Over Time
        self._plot_throughput_timeline(metrics)

        # NEW: 7. Time Series - Latency Over Time
        self._plot_latency_timeline(metrics)

        # NEW: 8. Connection Stability Chart
        self._plot_connection_stability(metrics)

        print(f"Visualizations saved to {self.output_dir}/")

    def _plot_network_topology(self):
        """Plot network topology graph"""
        G = self.create_network_graph()
        if G is None or len(G.nodes()) == 0:
            return

        plt.figure(figsize=(14, 10))

        # Use spring layout
        pos = nx.spring_layout(G, k=2.5, iterations=50, seed=42)

        # Node colors and sizes based on connections
        node_colors = []
        node_sizes = []
        for node in G.nodes():
            connections = G.nodes[node]['connections']
            node_colors.append(connections)
            node_sizes.append(500 + connections * 200)

        # Draw network
        nx.draw_networkx_nodes(G, pos,
                              node_color=node_colors,
                              node_size=node_sizes,
                              cmap=plt.cm.YlOrRd,
                              alpha=0.8)

        nx.draw_networkx_edges(G, pos,
                              edge_color='gray',
                              width=2,
                              alpha=0.5)

        # Labels
        labels = {node: f"Node-{node}" for node in G.nodes()}
        nx.draw_networkx_labels(G, pos, labels,
                               font_size=10,
                               font_weight='bold')

        plt.title(f"P2P Network Topology\n{len(G.nodes())} Nodes, {len(G.edges())} Connections",
                 fontsize=16, pad=20)
        plt.axis('off')
        plt.tight_layout()
        plt.savefig(os.path.join(self.output_dir, "network_topology.png"),
                   dpi=300, bbox_inches='tight')
        plt.close()

    def _plot_node_performance(self, metrics: Dict):
        """Plot node performance comparison"""
        node_ranking = metrics['node_ranking'][:15]  # Top 15 nodes

        node_labels = [f"Node-{info['node_number']}" for _, _, info in node_ranking]
        received = [info['messages_received'] for _, _, info in node_ranking]
        forwarded = [info['messages_forwarded'] for _, _, info in node_ranking]

        x = range(len(node_labels))
        width = 0.35

        plt.figure(figsize=(14, 6))
        plt.bar([i - width/2 for i in x], received, width,
               label='Received', alpha=0.8, color='skyblue')
        plt.bar([i + width/2 for i in x], forwarded, width,
               label='Forwarded', alpha=0.8, color='lightcoral')

        plt.xlabel('Node ID', fontsize=12)
        plt.ylabel('Message Count', fontsize=12)
        plt.title('Node Performance - Message Handling', fontsize=14)
        plt.xticks(x, node_labels, rotation=45, ha='right')
        plt.legend()
        plt.grid(True, alpha=0.3, axis='y')
        plt.tight_layout()
        plt.savefig(os.path.join(self.output_dir, "node_performance.png"),
                   dpi=300, bbox_inches='tight')
        plt.close()

    def _plot_latency_distribution(self, metrics: Dict):
        """Plot latency distribution histogram"""
        all_latencies = []
        for info in self.node_info.values():
            all_latencies.extend(info['latencies'])

        if not all_latencies:
            return

        plt.figure(figsize=(12, 6))
        plt.hist(all_latencies, bins=50, alpha=0.7, edgecolor='black', color='skyblue')

        mean_lat = metrics['latency']['mean']
        median_lat = metrics['latency']['median']

        plt.axvline(mean_lat, color='red', linestyle='--', linewidth=2, label=f'Mean: {mean_lat:.1f}ms')
        plt.axvline(median_lat, color='green', linestyle='--', linewidth=2, label=f'Median: {median_lat:.1f}ms')

        plt.xlabel('Latency (ms)', fontsize=12)
        plt.ylabel('Frequency', fontsize=12)
        plt.title('Message Latency Distribution', fontsize=14)
        plt.legend()
        plt.grid(True, alpha=0.3)
        plt.tight_layout()
        plt.savefig(os.path.join(self.output_dir, "latency_distribution.png"),
                   dpi=300, bbox_inches='tight')
        plt.close()

    def _plot_message_heatmap(self):
        """Plot message flow heatmap"""
        # Create matrix of message flows
        node_ids = sorted(self.node_info.keys(), key=lambda x: self.node_info[x]['node_number'])
        matrix_size = len(node_ids)

        if matrix_size == 0:
            return

        # Count messages from origin to receiver
        flow_matrix = [[0] * matrix_size for _ in range(matrix_size)]

        for msg_data in self.message_tracking.values():
            origin = msg_data['origin']
            if origin not in node_ids:
                continue
            origin_idx = node_ids.index(origin)

            for receiver, _ in msg_data['receivers']:
                if receiver not in node_ids:
                    continue
                receiver_idx = node_ids.index(receiver)
                flow_matrix[origin_idx][receiver_idx] += 1

        plt.figure(figsize=(12, 10))
        plt.imshow(flow_matrix, cmap='YlOrRd', interpolation='nearest')
        plt.colorbar(label='Message Count')

        node_labels = [f"Node-{self.node_info[nid]['node_number']}" for nid in node_ids]
        plt.xticks(range(matrix_size), node_labels, rotation=45, ha='right')
        plt.yticks(range(matrix_size), node_labels)

        plt.xlabel('Receiver', fontsize=12)
        plt.ylabel('Origin', fontsize=12)
        plt.title('Message Flow Heatmap (Origin -> Receiver)', fontsize=14)
        plt.tight_layout()
        plt.savefig(os.path.join(self.output_dir, "message_flow_heatmap.png"),
                   dpi=300, bbox_inches='tight')
        plt.close()

    def _plot_test_type_distribution(self, metrics: Dict):
        """Plot test type distribution pie chart"""
        test_types = metrics['test_types']
        if not test_types:
            return

        # Get top 10 test types
        top_types = test_types.most_common(10)
        labels = [t[0] for t in top_types]
        sizes = [t[1] for t in top_types]

        plt.figure(figsize=(10, 8))
        plt.pie(sizes, labels=labels, autopct='%1.1f%%', startangle=90)
        plt.title('Test Type Distribution (Top 10)', fontsize=14)
        plt.axis('equal')
        plt.tight_layout()
        plt.savefig(os.path.join(self.output_dir, "test_type_distribution.png"),
                   dpi=300, bbox_inches='tight')
        plt.close()

    def _plot_throughput_timeline(self, metrics: Dict):
        """Plot throughput over time"""
        if not self.message_timestamps:
            return

        sorted_timestamps = sorted(self.message_timestamps)
        start_time = sorted_timestamps[0]

        # Create 1-second buckets
        window_size = 1000  # 1 second in ms
        duration_ms = sorted_timestamps[-1] - start_time
        num_buckets = int(duration_ms / window_size) + 1

        time_buckets = []
        message_counts = []

        for i in range(num_buckets):
            bucket_start = start_time + (i * window_size)
            bucket_end = bucket_start + window_size
            count = sum(1 for ts in sorted_timestamps if bucket_start <= ts < bucket_end)
            time_buckets.append(i)  # Time in seconds
            message_counts.append(count)

        plt.figure(figsize=(14, 6))
        plt.plot(time_buckets, message_counts, linewidth=2, color='steelblue')
        plt.fill_between(time_buckets, message_counts, alpha=0.3, color='lightblue')

        # Add average line
        avg_throughput = metrics['throughput']['messages_per_second']
        plt.axhline(y=avg_throughput, color='red', linestyle='--', linewidth=2,
                   label=f'Average: {avg_throughput:.1f} msg/s')

        plt.xlabel('Time (seconds)', fontsize=12)
        plt.ylabel('Messages per Second', fontsize=12)
        plt.title('Network Throughput Over Time', fontsize=14)
        plt.legend()
        plt.grid(True, alpha=0.3)
        plt.tight_layout()
        plt.savefig(os.path.join(self.output_dir, "throughput_timeline.png"),
                   dpi=300, bbox_inches='tight')
        plt.close()

    def _plot_latency_timeline(self, metrics: Dict):
        """Plot latency over time"""
        if not self.message_events:
            return

        # Sort events by timestamp
        sorted_events = sorted([e for e in self.message_events if e['type'] == 'received'],
                              key=lambda x: x.get('event_timestamp', 0))

        if not sorted_events:
            return

        start_time = sorted_events[0]['event_timestamp']
        times = [(e['event_timestamp'] - start_time) / 1000.0 for e in sorted_events]  # Convert to seconds
        latencies = [e['latency'] for e in sorted_events]

        # Calculate moving average (window of 100 messages)
        window = 100
        moving_avg = []
        for i in range(len(latencies)):
            start_idx = max(0, i - window + 1)
            moving_avg.append(statistics.mean(latencies[start_idx:i+1]))

        plt.figure(figsize=(14, 6))

        # Plot actual latencies (with alpha for visibility)
        plt.scatter(times, latencies, alpha=0.1, s=5, color='lightblue', label='Individual Messages')

        # Plot moving average
        plt.plot(times, moving_avg, linewidth=2, color='steelblue', label=f'Moving Average ({window} messages)')

        # Add P95 line
        p95_latency = metrics['latency']['p95']
        plt.axhline(y=p95_latency, color='orange', linestyle='--', linewidth=2,
                   label=f'P95: {p95_latency:.1f}ms')

        # Add mean line
        mean_latency = metrics['latency']['mean']
        plt.axhline(y=mean_latency, color='red', linestyle='--', linewidth=2,
                   label=f'Mean: {mean_latency:.1f}ms')

        plt.xlabel('Time (seconds)', fontsize=12)
        plt.ylabel('Latency (ms)', fontsize=12)
        plt.title('Message Latency Over Time', fontsize=14)
        plt.legend()
        plt.grid(True, alpha=0.3)
        plt.tight_layout()
        plt.savefig(os.path.join(self.output_dir, "latency_timeline.png"),
                   dpi=300, bbox_inches='tight')
        plt.close()

    def _plot_connection_stability(self, metrics: Dict):
        """Plot connection stability metrics"""
        conn_stab = metrics['connection_stability']

        if conn_stab['total_connections'] == 0:
            return

        # Prepare data for bar chart
        categories = ['Active', 'Disconnections', 'Reconnections']
        values = [
            conn_stab['active_connections'],
            conn_stab['disconnections'],
            conn_stab['reconnections']
        ]
        colors = ['green', 'red', 'orange']

        plt.figure(figsize=(10, 6))
        bars = plt.bar(categories, values, color=colors, alpha=0.7, edgecolor='black')

        # Add value labels on bars
        for bar in bars:
            height = bar.get_height()
            plt.text(bar.get_x() + bar.get_width()/2., height,
                    f'{int(height)}', ha='center', va='bottom', fontsize=12, fontweight='bold')

        plt.ylabel('Count', fontsize=12)
        plt.title(f'Connection Stability Metrics\n'
                 f'Total Connections: {conn_stab["total_connections"]} | '
                 f'Avg Duration: {conn_stab["avg_connection_duration_seconds"]:.1f}s | '
                 f'Uptime: {conn_stab["connection_uptime_ratio"]:.1%}',
                 fontsize=14, pad=20)
        plt.grid(True, alpha=0.3, axis='y')
        plt.tight_layout()
        plt.savefig(os.path.join(self.output_dir, "connection_stability.png"),
                   dpi=300, bbox_inches='tight')
        plt.close()

    def export_csv(self, metrics: Dict):
        """Export data to CSV files"""
        if not HAS_PANDAS:
            print("Skipping CSV export (pandas not available)")
            return

        print("Exporting CSV files...")

        # Node summary
        node_data = []
        for node_id, info in self.node_info.items():
            avg_lat = statistics.mean(info['latencies']) if info['latencies'] else 0
            node_data.append({
                'node_id': node_id,
                'node_number': info['node_number'],
                'connections': len(self.connections.get(node_id, [])),
                'messages_received': info['messages_received'],
                'messages_forwarded': info['messages_forwarded'],
                'avg_latency_ms': avg_lat,
                'errors': info['errors']
            })

        df_nodes = pd.DataFrame(node_data)
        df_nodes.to_csv(os.path.join(self.output_dir, "node_summary.csv"), index=False)

        # Message tracking
        msg_data = []
        for msg_id, data in self.message_tracking.items():
            msg_data.append({
                'message_id': msg_id,
                'origin': data['origin'],
                'test_type': data['test_type'],
                'max_hops': data['max_hops'],
                'receivers_count': len(data['receivers']),
                'forwarders_count': len(data['forwarders']),
                'avg_latency_ms': statistics.mean(data['latencies']) if data['latencies'] else 0
            })

        df_messages = pd.DataFrame(msg_data)
        df_messages.to_csv(os.path.join(self.output_dir, "message_summary.csv"), index=False)

        print(f"CSV files exported to {self.output_dir}/")

    def run(self):
        """Run complete analysis"""
        print("=" * 80)
        print("XDAG P2P Network Performance Analyzer V2")
        print("=" * 80)
        print()

        # Parse logs
        if not self.parse_all_logs():
            return

        # Calculate metrics
        print("\nCalculating metrics...")
        metrics = self.calculate_metrics()

        # Generate report
        print("\nGenerating report...")
        report = self.generate_report(metrics)

        # Save report
        report_file = os.path.join(self.output_dir, "performance_report.txt")
        with open(report_file, 'w', encoding='utf-8') as f:
            f.write(report)

        print(f"\n✅ Report saved to: {report_file}")
        print("\n" + report)

        # Generate visualizations
        self.generate_visualizations(metrics)

        # Export CSV
        self.export_csv(metrics)

        print("\n✅ Analysis complete!")
        print(f"Results saved to: {self.output_dir}/")


def main():
    analyzer = P2PNetworkAnalyzer()
    analyzer.run()


if __name__ == "__main__":
    main()
