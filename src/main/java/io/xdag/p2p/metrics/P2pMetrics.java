/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.p2p.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.exporter.HTTPServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Prometheus metrics collector for P2P network.
 *
 * <p>Collects and exposes metrics including:
 * <ul>
 *   <li>Connection metrics (active, passive, total)</li>
 *   <li>Message metrics (sent, received, errors)</li>
 *   <li>Node metrics (discovered, banned, reputation)</li>
 *   <li>Performance metrics (latency, throughput)</li>
 *   <li>Ban metrics (total bans, by reason)</li>
 * </ul>
 */
@Slf4j
public class P2pMetrics {

    private static final String NAMESPACE = "xdagj_p2p";

    // Connection metrics
    @Getter
    private final Gauge connectionsActive = Gauge.build()
            .namespace(NAMESPACE)
            .name("connections_active")
            .help("Number of active P2P connections")
            .labelNames("type") // "active", "passive"
            .register();

    @Getter
    private final Counter connectionsTotal = Counter.build()
            .namespace(NAMESPACE)
            .name("connections_total")
            .help("Total number of connection attempts")
            .labelNames("result") // "success", "failure"
            .register();

    @Getter
    private final Histogram connectionDuration = Histogram.build()
            .namespace(NAMESPACE)
            .name("connection_duration_seconds")
            .help("Connection duration in seconds")
            .buckets(60, 300, 600, 1800, 3600, 7200, 14400) // 1m, 5m, 10m, 30m, 1h, 2h, 4h
            .register();

    // Message metrics
    @Getter
    private final Counter messagesSent = Counter.build()
            .namespace(NAMESPACE)
            .name("messages_sent_total")
            .help("Total number of messages sent")
            .labelNames("type") // message type
            .register();

    @Getter
    private final Counter messagesReceived = Counter.build()
            .namespace(NAMESPACE)
            .name("messages_received_total")
            .help("Total number of messages received")
            .labelNames("type") // message type
            .register();

    @Getter
    private final Counter messagesErrors = Counter.build()
            .namespace(NAMESPACE)
            .name("messages_errors_total")
            .help("Total number of message errors")
            .labelNames("type", "error") // message type, error type
            .register();

    @Getter
    private final Histogram messageSizeBytes = Histogram.build()
            .namespace(NAMESPACE)
            .name("message_size_bytes")
            .help("Message size in bytes")
            .labelNames("direction") // "sent", "received"
            .buckets(64, 256, 1024, 4096, 16384, 65536, 262144) // 64B to 256KB
            .register();

    // Node metrics
    @Getter
    private final Gauge nodesDiscovered = Gauge.build()
            .namespace(NAMESPACE)
            .name("nodes_discovered")
            .help("Number of discovered nodes")
            .register();

    @Getter
    private final Gauge nodesBanned = Gauge.build()
            .namespace(NAMESPACE)
            .name("nodes_banned")
            .help("Number of currently banned nodes")
            .register();

    @Getter
    private final Counter banTotal = Counter.build()
            .namespace(NAMESPACE)
            .name("ban_total")
            .help("Total number of bans")
            .labelNames("reason") // ban reason
            .register();

    @Getter
    private final Histogram nodeReputation = Histogram.build()
            .namespace(NAMESPACE)
            .name("node_reputation")
            .help("Node reputation score distribution")
            .buckets(0, 20, 50, 80, 100, 120, 150, 180, 200)
            .register();

    // DHT metrics
    @Getter
    private final Gauge dhtNodes = Gauge.build()
            .namespace(NAMESPACE)
            .name("dht_nodes")
            .help("Number of nodes in DHT table")
            .register();

    @Getter
    private final Counter dhtLookups = Counter.build()
            .namespace(NAMESPACE)
            .name("dht_lookups_total")
            .help("Total number of DHT lookups")
            .labelNames("result") // "success", "failure"
            .register();

    // Performance metrics
    @Getter
    private final Histogram messageLatency = Histogram.build()
            .namespace(NAMESPACE)
            .name("message_latency_seconds")
            .help("Message processing latency")
            .labelNames("type")
            .buckets(0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0, 5.0) // 1ms to 5s
            .register();

    @Getter
    private final Gauge throughputBytesPerSecond = Gauge.build()
            .namespace(NAMESPACE)
            .name("throughput_bytes_per_second")
            .help("Network throughput in bytes per second")
            .labelNames("direction") // "sent", "received"
            .register();

    // HTTP server for metrics export
    private HTTPServer metricsServer;
    @Getter
    private boolean metricsEnabled = false;

    /**
     * Enable metrics collection and export.
     *
     * @param port the port to expose metrics HTTP endpoint
     * @throws IOException if failed to start HTTP server
     */
    public void enable(int port) throws IOException {
        if (metricsEnabled) {
            log.warn("Metrics already enabled");
            return;
        }

        // Register JVM metrics (memory, GC, threads, etc.)
        DefaultExports.initialize();

        // Start HTTP server (binds to all interfaces)
        metricsServer = new HTTPServer(port, true);
        metricsEnabled = true;

        log.info("Prometheus metrics enabled on port {}", port);
        log.info("Metrics available at http://localhost:{}/metrics", port);
    }

    /**
     * Disable metrics collection and stop HTTP server.
     */
    public void disable() {
        if (!metricsEnabled) {
            return;
        }

        if (metricsServer != null) {
            metricsServer.close();
            metricsServer = null;
        }

        metricsEnabled = false;
        log.info("Prometheus metrics disabled");
    }

    /**
     * Record a successful connection.
     *
     * @param isActive whether it's an active (outbound) connection
     */
    public void recordConnection(boolean isActive) {
        connectionsActive.labels(isActive ? "active" : "passive").inc();
        connectionsTotal.labels("success").inc();
    }

    /**
     * Record a failed connection attempt.
     */
    public void recordConnectionFailure() {
        connectionsTotal.labels("failure").inc();
    }

    /**
     * Record connection closure.
     *
     * @param isActive whether it was an active connection
     * @param durationSeconds connection duration in seconds
     */
    public void recordDisconnection(boolean isActive, double durationSeconds) {
        connectionsActive.labels(isActive ? "active" : "passive").dec();
        connectionDuration.observe(durationSeconds);
    }

    /**
     * Record a sent message.
     *
     * @param messageType the message type
     * @param sizeBytes message size in bytes
     */
    public void recordMessageSent(String messageType, int sizeBytes) {
        messagesSent.labels(messageType).inc();
        messageSizeBytes.labels("sent").observe(sizeBytes);
    }

    /**
     * Record a received message.
     *
     * @param messageType the message type
     * @param sizeBytes message size in bytes
     */
    public void recordMessageReceived(String messageType, int sizeBytes) {
        messagesReceived.labels(messageType).inc();
        messageSizeBytes.labels("received").observe(sizeBytes);
    }

    /**
     * Record a message error.
     *
     * @param messageType the message type
     * @param errorType the error type
     */
    public void recordMessageError(String messageType, String errorType) {
        messagesErrors.labels(messageType, errorType).inc();
    }

    /**
     * Update discovered nodes count.
     *
     * @param count number of discovered nodes
     */
    public void setNodesDiscovered(int count) {
        nodesDiscovered.set(count);
    }

    /**
     * Update banned nodes count.
     *
     * @param count number of banned nodes
     */
    public void setNodesBanned(int count) {
        nodesBanned.set(count);
    }

    /**
     * Record a node ban.
     *
     * @param reason the ban reason
     */
    public void recordBan(String reason) {
        banTotal.labels(reason).inc();
    }

    /**
     * Record node reputation score.
     *
     * @param score reputation score (0-200)
     */
    public void recordNodeReputation(double score) {
        nodeReputation.observe(score);
    }

    /**
     * Update DHT nodes count.
     *
     * @param count number of nodes in DHT
     */
    public void setDhtNodes(int count) {
        dhtNodes.set(count);
    }

    /**
     * Record DHT lookup result.
     *
     * @param success whether the lookup was successful
     */
    public void recordDhtLookup(boolean success) {
        dhtLookups.labels(success ? "success" : "failure").inc();
    }

    /**
     * Record message processing latency.
     *
     * @param messageType the message type
     * @param latencySeconds latency in seconds
     */
    public void recordMessageLatency(String messageType, double latencySeconds) {
        messageLatency.labels(messageType).observe(latencySeconds);
    }

    /**
     * Update network throughput.
     *
     * @param direction "sent" or "received"
     * @param bytesPerSecond bytes per second
     */
    public void setThroughput(String direction, double bytesPerSecond) {
        throughputBytesPerSecond.labels(direction).set(bytesPerSecond);
    }
}
