#!/usr/bin/env python3
"""
XDAG P2P Network Performance Analysis Tool
Professional P2P network performance analysis tool
"""

import json
import re
import argparse
import os
import sys
from datetime import datetime
from collections import defaultdict, Counter
import statistics
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import pandas as pd
import networkx as nx
from typing import Dict, List, Tuple, Any

class P2PNetworkAnalyzer:
    """P2P Network Performance Analyzer"""
    
    def __init__(self, logs_dir: str, output_dir: str = "analysis_results"):
        self.logs_dir = logs_dir
        self.output_dir = output_dir
        self.nodes_data = {}
        self.network_graph = nx.Graph()
        self.message_flows = []
        self.connection_events = []
        # Add clean topology data structures
        self.node_connections = defaultdict(set)  # Which nodes each node connects to
        self.node_connection_info = {}  # Node connection information
        
        # Create output directory
        os.makedirs(output_dir, exist_ok=True)
    
    def parse_logs(self):
        """Parse all node log files"""
        log_files = [f for f in os.listdir(self.logs_dir) if f.endswith('.log')]
        
        for log_file in log_files:
            node_id = log_file.replace('node-', '').replace('.log', '')
            log_path = os.path.join(self.logs_dir, log_file)
            
            print(f"Parsing log for node {node_id}...")
            self.nodes_data[node_id] = self._parse_node_log(log_path, node_id)
    
    def _parse_node_log(self, log_path: str, node_id: str) -> Dict:
        """Parse single node log file"""
        node_data = {
            'connections': [],
            'messages_sent': 0,
            'messages_received': 0,
            'messages_forwarded': 0,
            'duplicates': 0,
            'latencies': [],
            'test_types': Counter(),
            'errors': [],
            'uptime_start': None,
            'statistics_timeline': []
        }
        
        # Initialize clean topology data for this node
        int_node_id = int(node_id)
        self.node_connection_info[int_node_id] = {
            'connections': 0,
            'status': 'active'
        }
        
        try:
            with open(log_path, 'r', encoding='utf-8') as f:
                for line in f:
                    self._parse_log_line(line, node_data, node_id)
        except Exception as e:
            print(f"Error parsing log file {log_path}: {e}")
        
        return node_data
    
    def _parse_log_line(self, line: str, node_data: Dict, node_id: str):
        """Parse single log line"""
        # Extract timestamp
        timestamp_match = re.search(r'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})', line)
        if not timestamp_match:
            return
        
        timestamp = timestamp_match.group(1)
        int_node_id = int(node_id)
        
        # Parse clean topology connections using "Add peer" logs
        if "Add peer" in line and "total channels:" in line:
            # Example: Add peer /127.0.0.1:10002, total channels: 3
            peer_match = re.search(r'Add peer /127\.0\.0\.1:(\d+)', line)
            if peer_match:
                peer_port = int(peer_match.group(1))
                peer_node = peer_port - 10000  # Calculate peer node ID
                if 0 <= peer_node <= 19:  # Ensure valid node ID
                    self.node_connections[int_node_id].add(peer_node)
                    
                    # Also add to old format for compatibility
                    node_data['connections'].append({
                        'timestamp': timestamp,
                        'peer': f"127.0.0.1:{peer_port}",
                        'type': 'connected'
                    })
        
        # Get final connection count from "total channels" logs
        if "total channels:" in line:
            channels_match = re.search(r'total channels:\s*(\d+)', line)
            if channels_match:
                self.node_connection_info[int_node_id]['connections'] = int(channels_match.group(1))

        # Parse connection events (old format for compatibility)
        if "New peer connected:" in line:
            peer_match = re.search(r'New peer connected: /([0-9.]+):(\d+)', line)
            if peer_match:
                peer_ip, peer_port = peer_match.groups()
                node_data['connections'].append({
                    'timestamp': timestamp,
                    'peer': f"{peer_ip}:{peer_port}",
                    'type': 'connected'
                })
                self.network_graph.add_edge(node_id, peer_port)
        
        # Parse disconnection events
        elif "Disconnected from peer:" in line:
            peer_match = re.search(r'Disconnected from peer: /([0-9.]+):(\d+)', line)
            if peer_match:
                peer_ip, peer_port = peer_match.groups()
                node_data['connections'].append({
                    'timestamp': timestamp,
                    'peer': f"{peer_ip}:{peer_port}",
                    'type': 'disconnected'
                })
        
        # Parse test messages
        elif "Received network test message:" in line:
            msg_match = re.search(r'message: (\w+).*hops: (\d+).*latency: (\d+)ms.*type: (\w+)', line)
            if msg_match:
                msg_id, hops, latency, test_type = msg_match.groups()
                node_data['messages_received'] += 1
                node_data['latencies'].append(int(latency))
                node_data['test_types'][test_type] += 1
                
                self.message_flows.append({
                    'timestamp': timestamp,
                    'node': node_id,
                    'message_id': msg_id,
                    'hops': int(hops),
                    'latency': int(latency),
                    'test_type': test_type,
                    'event': 'received'
                })
        
        # Parse forwarded messages
        elif "Forwarding network test message:" in line:
            node_data['messages_forwarded'] += 1
        
        # Parse network statistics
        elif "Network test statistics:" in line:
            stats_match = re.search(r'Received: (\d+), Forwarded: (\d+), Duplicates: (\d+), AvgLatency: ([\d.]+)ms', line)
            if stats_match:
                received, forwarded, duplicates, avg_latency = stats_match.groups()
                node_data['statistics_timeline'].append({
                    'timestamp': timestamp,
                    'received': int(received),
                    'forwarded': int(forwarded),
                    'duplicates': int(duplicates),
                    'avg_latency': float(avg_latency)
                })
        
        # Parse detailed statistics (JSON format)
        elif "DETAILED_STATS:" in line:
            try:
                json_match = re.search(r'DETAILED_STATS: ({.*})', line)
                if json_match:
                    stats_data = json.loads(json_match.group(1))
                    node_data['statistics_timeline'].append(stats_data)
            except json.JSONDecodeError:
                pass
        
        # Parse errors
        elif "ERROR" in line or "Exception" in line:
            node_data['errors'].append({
                'timestamp': timestamp,
                'message': line.strip()
            })
    
    def create_clean_network_graph(self):
        """Create clean network topology graph using the reliable parsing method"""
        G = nx.Graph()
        
        # Add all nodes (0-19)
        for i in range(20):
            connections_count = self.node_connection_info.get(i, {}).get('connections', 0)
            G.add_node(i, connections=connections_count)
        
        # Add connection edges based on parsed data
        edges_added = set()
        for node_id, connected_nodes in self.node_connections.items():
            for connected_node in connected_nodes:
                # Avoid duplicate edges
                edge = tuple(sorted([node_id, connected_node]))
                if edge not in edges_added:
                    G.add_edge(node_id, connected_node)
                    edges_added.add(edge)
        
        return G

    def generate_network_report(self) -> str:
        """Generate network performance report"""
        report = []
        report.append("=" * 80)
        report.append("XDAG P2P Network Performance Analysis Report")
        report.append("=" * 80)
        report.append(f"Analysis Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        report.append(f"Total Nodes Analyzed: {len(self.nodes_data)}")
        report.append("")
        
        # Network Overview
        report.append("🌐 Network Overview")
        report.append("-" * 40)
        total_messages = sum(data['messages_received'] for data in self.nodes_data.values())
        total_forwarded = sum(data['messages_forwarded'] for data in self.nodes_data.values())
        
        # Use clean topology for connection count
        clean_graph = self.create_clean_network_graph()
        total_connections = len(clean_graph.edges())
        
        report.append(f"Total Messages Received: {total_messages:,}")
        report.append(f"Total Messages Forwarded: {total_forwarded:,}")
        report.append(f"Total Network Connections: {total_connections}")
        report.append(f"Network Density: {nx.density(clean_graph):.3f}")
        
        if total_messages > 0:
            forward_ratio = total_forwarded / total_messages
            report.append(f"Message Forward Ratio: {forward_ratio:.2%}")
        
        report.append("")
        
        # Node Performance Ranking
        report.append("🏆 Node Performance Ranking")
        report.append("-" * 40)
        
        node_performance = []
        for node_id, data in self.nodes_data.items():
            avg_latency = statistics.mean(data['latencies']) if data['latencies'] else 0
            performance_score = (
                data['messages_received'] * 0.4 +
                data['messages_forwarded'] * 0.3 +
                (1000 / max(avg_latency, 1)) * 0.2 +
                len(data['connections']) * 0.1
            )
            
            node_performance.append({
                'node': node_id,
                'score': performance_score,
                'received': data['messages_received'],
                'forwarded': data['messages_forwarded'],
                'avg_latency': avg_latency,
                'connections': len(data['connections'])
            })
        
        node_performance.sort(key=lambda x: x['score'], reverse=True)
        
        for i, node in enumerate(node_performance[:10], 1):
            report.append(f"{i:2d}. Node-{node['node']:>2} | "
                         f"Score: {node['score']:6.1f} | "
                         f"Recv: {node['received']:4d} | "
                         f"Fwd: {node['forwarded']:4d} | "
                         f"Latency: {node['avg_latency']:5.1f}ms")
        
        report.append("")
        
        # Message Type Analysis
        report.append("📊 Message Type Analysis")
        report.append("-" * 40)
        
        all_test_types = Counter()
        for data in self.nodes_data.values():
            all_test_types.update(data['test_types'])
        
        for test_type, count in all_test_types.most_common():
            percentage = (count / total_messages) * 100 if total_messages > 0 else 0
            report.append(f"{test_type:>15}: {count:6,} messages ({percentage:5.1f}%)")
        
        report.append("")
        
        # Network Health Metrics
        report.append("💚 Network Health Metrics")
        report.append("-" * 40)
        
        all_latencies = []
        for data in self.nodes_data.values():
            all_latencies.extend(data['latencies'])
        
        if all_latencies:
            report.append(f"Average Latency: {statistics.mean(all_latencies):.2f}ms")
            report.append(f"Median Latency: {statistics.median(all_latencies):.2f}ms")
            report.append(f"Latency Std Dev: {statistics.stdev(all_latencies):.2f}ms")
            report.append(f"Min Latency: {min(all_latencies)}ms")
            report.append(f"Max Latency: {max(all_latencies)}ms")
        
        # Error Analysis
        total_errors = sum(len(data['errors']) for data in self.nodes_data.values())
        report.append(f"Total Errors: {total_errors}")
        
        if total_errors > 0:
            error_rate = (total_errors / total_messages) * 100 if total_messages > 0 else 0
            report.append(f"Error Rate: {error_rate:.3f}%")
        
        report.append("")
        
        # Add clean topology connection statistics
        self._add_clean_topology_stats(report, clean_graph)
        
        report.append("=" * 80)
        
        return "\n".join(report)
    
    def _add_clean_topology_stats(self, report, G):
        """Add clean topology connection statistics to report"""
        report.append("🔗 Clean Topology Connection Analysis")
        report.append("-" * 40)
        
        # Group nodes by connection pattern
        bootstrap_nodes = []  # Nodes 0, 1, 2 (bootstrap)
        connected_nodes = []  # Nodes with connections
        isolated_nodes = []   # Nodes without connections
        
        for node in sorted(G.nodes()):
            neighbors = list(G.neighbors(node))
            connections = self.node_connection_info.get(node, {}).get('connections', 0)
            
            if node in [0, 1, 2]:
                bootstrap_nodes.append((node, connections, neighbors))
            elif neighbors:
                connected_nodes.append((node, connections, neighbors))
            else:
                isolated_nodes.append((node, connections))
        
        # Print bootstrap nodes (usually high-connectivity)
        if bootstrap_nodes:
            report.append(f"Bootstrap Nodes (Seed Nodes): {len(bootstrap_nodes)}")
            for node, connections, neighbors in bootstrap_nodes:
                report.append(f"  Node-{node:2d}: {connections} connections -> {len(neighbors)} peers")
        
        # Print regular connected nodes
        if connected_nodes:
            report.append(f"Regular Connected Nodes: {len(connected_nodes)}")
            for node, connections, neighbors in connected_nodes[:5]:  # Show top 5
                neighbor_str = ", ".join([f"Node-{n}" for n in sorted(neighbors)[:3]])
                if len(neighbors) > 3:
                    neighbor_str += f" ... (+{len(neighbors)-3} more)"
                report.append(f"  Node-{node:2d}: {connections} connections -> [{neighbor_str}]")
        
        # Print isolated nodes
        if isolated_nodes:
            report.append(f"Isolated Nodes: {len(isolated_nodes)}")
            isolated_list = [f"Node-{node}" for node, _ in isolated_nodes[:10]]
            if len(isolated_nodes) > 10:
                isolated_list.append(f"... (+{len(isolated_nodes)-10} more)")
            report.append(f"  {', '.join(isolated_list)}")
        
        # Network connectivity analysis
        if nx.is_connected(G):
            report.append("✅ Network is fully connected")
            if len(G.nodes()) > 1:
                diameter = nx.diameter(G)
                avg_path_length = nx.average_shortest_path_length(G)
                report.append(f"   Network diameter: {diameter} hops")
                report.append(f"   Average path length: {avg_path_length:.2f} hops")
        else:
            components = list(nx.connected_components(G))
            report.append(f"❌ Network is fragmented into {len(components)} components")
            for i, component in enumerate(components, 1):
                nodes = sorted(list(component))
                if len(nodes) <= 5:
                    report.append(f"   Component {i}: {[f'Node-{n}' for n in nodes]}")
                else:
                    report.append(f"   Component {i}: {len(nodes)} nodes (Node-{min(nodes)} to Node-{max(nodes)})")
    
    def generate_visualizations(self):
        """Generate visualization charts"""
        print("Generating visualization charts...")
        
        # 1. Clean Network Topology - Primary visualization
        self._generate_clean_topology_visualization()
        
        # 2. Latency Distribution
        self._generate_latency_distribution()
        
        # 3. Node Performance Comparison  
        self._generate_node_performance_comparison()
        
        # 4. Connection Statistics Chart
        self._generate_connection_statistics_chart()
        
        # 5. Message Flow Analysis
        self._generate_message_flow_analysis()
    
    def _generate_clean_topology_visualization(self):
        """Generate clean and clear network topology visualization"""
        G = self.create_clean_network_graph()
        
        plt.figure(figsize=(16, 12))
        
        # Use spring layout to cluster connected nodes
        pos = nx.spring_layout(G, k=2, iterations=50, seed=42)
        
        # Prepare node colors and sizes
        node_colors = []
        node_sizes = []
        node_labels = {}
        
        for node in G.nodes():
            connections = self.node_connection_info.get(node, {}).get('connections', 0)
            node_labels[node] = f"Node-{node}\n({connections})"
            
            if connections > 0:
                # Connected nodes - blue, size based on connection count
                node_colors.append('lightblue')
                node_sizes.append(800 + connections * 100)
            else:
                # Unconnected nodes - gray, smaller size
                node_colors.append('lightgray')
                node_sizes.append(400)
        
        # Draw nodes
        nx.draw_networkx_nodes(G, pos,
                              node_color=node_colors,
                              node_size=node_sizes,
                              alpha=0.8)
        
        # Draw connection lines
        nx.draw_networkx_edges(G, pos,
                              edge_color='darkblue',
                              width=2,
                              alpha=0.6)
        
        # Add node labels
        nx.draw_networkx_labels(G, pos, node_labels,
                               font_size=9,
                               font_weight='bold')
        
        # Statistics
        total_nodes = len(G.nodes())
        connected_nodes = len([n for n in G.nodes() if len(list(G.neighbors(n))) > 0])
        total_edges = len(G.edges())
        
        # Create legend
        legend_elements = [
            mpatches.Patch(color='lightblue', label=f'Connected Nodes ({connected_nodes})'),
            mpatches.Patch(color='lightgray', label=f'Isolated Nodes ({total_nodes - connected_nodes})'),
            plt.Line2D([0], [0], color='darkblue', linewidth=2, label=f'P2P Connections ({total_edges})')
        ]
        plt.legend(handles=legend_elements, loc='upper right', bbox_to_anchor=(1, 1))
        
        plt.title(f"XDAG P2P Network Node Topology\n" +
                 f"Total Nodes: {total_nodes} | Connected Nodes: {connected_nodes} | Total Connections: {total_edges}",
                 fontsize=16, pad=20)
        plt.axis('off')
        plt.tight_layout()
        plt.savefig(os.path.join(self.output_dir, "clean_network_topology.png"), dpi=300, bbox_inches='tight')
        plt.close()
    
    def _generate_latency_distribution(self):
        """Generate latency distribution chart"""
        all_latencies = []
        for data in self.nodes_data.values():
            all_latencies.extend(data['latencies'])
        
        if all_latencies:
            plt.figure(figsize=(10, 6))
            plt.hist(all_latencies, bins=50, alpha=0.7, edgecolor='black', color='skyblue')
            plt.xlabel("Latency (ms)")
            plt.ylabel("Frequency")
            plt.title("Message Latency Distribution")
            plt.grid(True, alpha=0.3)
            
            # Add statistics text
            mean_lat = statistics.mean(all_latencies)
            median_lat = statistics.median(all_latencies)
            plt.axvline(mean_lat, color='red', linestyle='--', label=f'Mean: {mean_lat:.1f}ms')
            plt.axvline(median_lat, color='green', linestyle='--', label=f'Median: {median_lat:.1f}ms')
            plt.legend()
            
            plt.tight_layout()
            plt.savefig(os.path.join(self.output_dir, "latency_distribution.png"), dpi=300, bbox_inches='tight')
            plt.close()
    
    def _generate_node_performance_comparison(self):
        """Generate node performance comparison chart"""
        nodes = list(self.nodes_data.keys())
        received_counts = [self.nodes_data[node]['messages_received'] for node in nodes]
        forwarded_counts = [self.nodes_data[node]['messages_forwarded'] for node in nodes]
        
        x = range(len(nodes))
        width = 0.35
        
        plt.figure(figsize=(14, 6))
        bars1 = plt.bar([i - width/2 for i in x], received_counts, width, 
                       label='Received', alpha=0.8, color='lightblue')
        bars2 = plt.bar([i + width/2 for i in x], forwarded_counts, width, 
                       label='Forwarded', alpha=0.8, color='lightcoral')
        
        plt.xlabel("Node ID")
        plt.ylabel("Message Count")
        plt.title("Node Performance Comparison - Message Handling")
        plt.xticks(x, [f"Node-{node}" for node in nodes], rotation=45)
        plt.legend()
        plt.grid(True, alpha=0.3, axis='y')
        
        # Add value labels on bars
        for bar in bars1:
            height = bar.get_height()
            if height > 0:
                plt.text(bar.get_x() + bar.get_width()/2., height,
                        f'{int(height)}', ha='center', va='bottom', fontsize=8)
        
        for bar in bars2:
            height = bar.get_height()
            if height > 0:
                plt.text(bar.get_x() + bar.get_width()/2., height,
                        f'{int(height)}', ha='center', va='bottom', fontsize=8)
        
        plt.tight_layout()
        plt.savefig(os.path.join(self.output_dir, "node_performance.png"), dpi=300, bbox_inches='tight')
        plt.close()
    
    def _generate_connection_statistics_chart(self):
        """Generate connection statistics chart"""
        G = self.create_clean_network_graph()
        
        plt.figure(figsize=(12, 8))
        
        # Prepare data
        nodes = sorted(G.nodes())
        connection_counts = [self.node_connection_info.get(node, {}).get('connections', 0) for node in nodes]
        neighbor_counts = [len(list(G.neighbors(node))) for node in nodes]
        
        x = range(len(nodes))
        width = 0.35
        
        # Create bar chart
        bars1 = plt.bar([i - width/2 for i in x], connection_counts, width, 
                       label='Total Channels', alpha=0.8, color='lightgreen')
        bars2 = plt.bar([i + width/2 for i in x], neighbor_counts, width, 
                       label='Active P2P Connections', alpha=0.8, color='orange')
        
        plt.xlabel("Node ID")
        plt.ylabel("Connection Count")
        plt.title("P2P Node Connection Statistics\n(Total Channels vs Active P2P Connections)")
        plt.xticks(x, [f"Node-{node}" for node in nodes], rotation=45)
        plt.legend()
        plt.grid(True, alpha=0.3, axis='y')
        
        # Add value labels on bars
        for bar in bars1:
            height = bar.get_height()
            if height > 0:
                plt.text(bar.get_x() + bar.get_width()/2., height,
                        f'{int(height)}', ha='center', va='bottom', fontsize=8)
        
        plt.tight_layout()
        plt.savefig(os.path.join(self.output_dir, "connection_statistics.png"), dpi=300, bbox_inches='tight')
        plt.close()
    
    def _generate_message_flow_analysis(self):
        """Generate message flow analysis chart"""
        if not self.message_flows:
            return
            
        # Analyze message types
        type_counts = Counter()
        for flow in self.message_flows:
            type_counts[flow['test_type']] += 1
        
        if type_counts:
            plt.figure(figsize=(10, 6))
            
            types = list(type_counts.keys())
            counts = list(type_counts.values())
            colors = plt.cm.Set3(range(len(types)))
            
            bars = plt.bar(types, counts, color=colors, alpha=0.8)
            plt.xlabel("Message Type")
            plt.ylabel("Message Count")
            plt.title("Message Flow Analysis by Type")
            plt.xticks(rotation=45)
            plt.grid(True, alpha=0.3, axis='y')
            
            # Add value labels on bars
            for bar in bars:
                height = bar.get_height()
                plt.text(bar.get_x() + bar.get_width()/2., height,
                        f'{int(height)}', ha='center', va='bottom')
            
            plt.tight_layout()
            plt.savefig(os.path.join(self.output_dir, "message_flows.png"), dpi=300, bbox_inches='tight')
            plt.close()
    
    def export_csv_data(self):
        """Export detailed data in CSV format"""
        print("Exporting CSV data...")
        
        # Node summary data
        node_summary = []
        for node_id, data in self.nodes_data.items():
            avg_latency = statistics.mean(data['latencies']) if data['latencies'] else 0
            int_node_id = int(node_id)
            clean_connections = self.node_connection_info.get(int_node_id, {}).get('connections', 0)
            
            node_summary.append({
                'node_id': node_id,
                'messages_received': data['messages_received'],
                'messages_forwarded': data['messages_forwarded'],
                'duplicates': data['duplicates'],
                'avg_latency_ms': avg_latency,
                'connection_count': len(data['connections']),
                'clean_connections': clean_connections,
                'error_count': len(data['errors'])
            })
        
        df_nodes = pd.DataFrame(node_summary)
        df_nodes.to_csv(os.path.join(self.output_dir, "node_summary.csv"), index=False)
        
        # Message flows data
        if self.message_flows:
            df_flows = pd.DataFrame(self.message_flows)
            df_flows.to_csv(os.path.join(self.output_dir, "message_flows.csv"), index=False)
        
        print(f"CSV data exported to {self.output_dir} directory")
    
    def run_analysis(self):
        """Run complete network analysis"""
        print("Starting P2P network performance analysis...")
        
        # Parse all log files
        self.parse_logs()
        
        if not self.nodes_data:
            print("Error: No parseable log data found")
            return
        
        # Generate report
        report = self.generate_network_report()
        
        # Save report to file
        report_file = os.path.join(self.output_dir, "network_analysis_report.txt")
        with open(report_file, 'w', encoding='utf-8') as f:
            f.write(report)
        
        print(f"Analysis report saved to: {report_file}")
        print("\n" + report)
        
        # Generate visualizations
        try:
            self.generate_visualizations()
            print(f"Visualization charts saved to {self.output_dir} directory")
        except ImportError:
            print("Warning: Missing matplotlib or networkx library, skipping visualization generation")
        
        # Export CSV data
        try:
            self.export_csv_data()
        except ImportError:
            print("Warning: Missing pandas library, skipping CSV export")


def main():
    parser = argparse.ArgumentParser(description="XDAG P2P Network Performance Analyzer")
    parser.add_argument("--logs-dir", "-l", 
                       default="logs", 
                       help="Directory containing node log files")
    parser.add_argument("--output-dir", "-o", 
                       default="analysis_results", 
                       help="Output directory for analysis results")
    parser.add_argument("--format", "-f", 
                       choices=["report", "csv", "json", "all"], 
                       default="all",
                       help="Output format")
    
    args = parser.parse_args()
    
    if not os.path.exists(args.logs_dir):
        print(f"Error: Log directory {args.logs_dir} does not exist")
        sys.exit(1)
    
    analyzer = P2PNetworkAnalyzer(args.logs_dir, args.output_dir)
    analyzer.run_analysis()


if __name__ == "__main__":
    main() 