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
package io.xdag.p2p;

import io.netty.channel.ChannelFuture;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.NodeManager;
import io.xdag.p2p.metrics.P2pMetrics;
import io.xdag.p2p.stats.P2pStats;
import io.xdag.p2p.stats.P2pStatsManager;
import java.net.InetSocketAddress;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j(topic = "p2p")
public class P2pService {

    private final P2pConfig config;
    private final NodeManager nodeManager;
    private final ChannelManager channelManager;
    private final P2pStatsManager p2pStatsManager;
    private final P2pMetrics metrics;

    private PeerServer peerServer;
    private PeerClient peerClient;

    private volatile boolean isShutdown = false;

    public P2pService(final P2pConfig config) {
        this.config = config;
        this.metrics = new P2pMetrics();
        this.nodeManager = new NodeManager(config, metrics);
        this.channelManager = new ChannelManager(config, nodeManager, metrics);
        this.p2pStatsManager = new P2pStatsManager();
    }

    public void start() {
        if (isShutdown) {
            log.warn("P2P service is already shut down.");
            return;
        }

        // Ensure node key is available for handshake; generate ephemeral if missing
        if (config.getNodeKey() == null) {
            try {
                config.setNodeKey(io.xdag.crypto.keys.ECKeyPair.generate());
                log.info("Generated ephemeral node key for handshake");
            } catch (Exception e) {
                log.warn("Failed to generate node key: {}", e.getMessage());
            }
        }

        // Initialize metrics if enabled
        if (config.isMetricsEnabled()) {
            try {
                metrics.enable(config.getMetricsPort());
                log.info("Prometheus metrics enabled on port {}", config.getMetricsPort());
            } catch (Exception e) {
                log.error("Failed to enable metrics: {}", e.getMessage());
            }
        }

        nodeManager.init();

        peerServer = new PeerServer(config, channelManager);
        peerServer.start();

        peerClient = new PeerClient(config, channelManager);
        peerClient.start();

        channelManager.start(peerClient);
        // Trigger an immediate connect attempt to seeds (don't wait for scheduler)
        channelManager.triggerImmediateConnect();

        log.info("P2P service started successfully.");
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "p2p-shutdown"));
    }

    public void stop() {
        if (isShutdown) {
            return;
        }
        isShutdown = true;

        log.info("Stopping P2P service...");

        // Disable metrics first
        if (metrics != null && metrics.isMetricsEnabled()) {
            metrics.disable();
        }

        channelManager.stop();
        if (peerClient != null) {
            peerClient.stop();
        }
        if (peerServer != null) {
            peerServer.stop();
        }
        nodeManager.close();
        log.info("P2P service stopped.");
    }

    public ChannelFuture connect(InetSocketAddress remoteAddress) {
        Node node = new Node(null, remoteAddress);
        return channelManager.connectAsync(node, false);
    }

    public P2pStats getP2pStats() {
        return p2pStatsManager.getP2pStats(channelManager);
    }

    public List<Node> getConnectableNodes() {
        return nodeManager.getConnectableNodes();
    }
}
