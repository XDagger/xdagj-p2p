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

    private PeerServer peerServer;
    private PeerClient peerClient;

    private volatile boolean started = false;
    private volatile boolean isShutdown = false;

    public P2pService(final P2pConfig config) {
        this.config = config;
        this.nodeManager = new NodeManager(config);
        this.channelManager = new ChannelManager(config, nodeManager);
    }

    /**
     * Starts discovery, the listener and the dialling of peers. When this returns the node can be reached: the
     * TCP listener and the discovery socket are bound (or could not be, which is logged - see
     * {@link PeerServer#isListening()}).
     */
    public synchronized void start() {
        if (isShutdown) {
            log.warn("P2P service is already shut down.");
            return;
        }
        if (started) {
            log.warn("P2P service is already running.");
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

        nodeManager.init();

        peerServer = new PeerServer(config, channelManager);
        peerServer.start();

        peerClient = new PeerClient(config, channelManager);
        peerClient.start();

        channelManager.start(peerClient);
        started = true;
        // Trigger an immediate connect attempt to seeds (don't wait for scheduler)
        channelManager.triggerImmediateConnect();

        if (config.getPort() > 0 && !peerServer.isListening()) {
            log.warn("P2P service started without a TCP listener (port {} could not be bound): "
                    + "no peer can connect to this node", config.getPort());
        } else {
            log.info("P2P service started successfully.");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "p2p-shutdown"));
    }

    public synchronized void stop() {
        if (isShutdown) {
            return;
        }
        isShutdown = true;

        log.info("Stopping P2P service...");

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

    /**
     * Opens or closes the network. Closed: only the configured seed / active / trust nodes are talked to
     * (existing connections with anybody else are dropped, discovery ignores everybody else). Open: anybody may
     * connect and discovered nodes are dialled.
     *
     * <p>May be called at any time. On a service that is not running (not started yet, or stopped) only the
     * setting changes: there are no connections to drop and nothing to dial with, and {@link #start()} begins
     * in the mode that is set by then.
     */
    public void setPermissionless(boolean permissionless) {
        boolean was = config.isPermissionless();
        config.setPermissionless(permissionless);
        if (!started || isShutdown) {
            return;
        }
        if (was && !permissionless) {
            log.info("P2P network closed: only configured peers from now on");
            channelManager.closeUnconfiguredPeers();
        } else if (!was && permissionless) {
            log.info("P2P network open: accepting and discovering peers");
            channelManager.triggerImmediateConnect();
        }
    }

    public boolean isPermissionless() {
        return config.isPermissionless();
    }

    public ChannelFuture connect(InetSocketAddress remoteAddress) {
        Node node = new Node(null, remoteAddress);
        return channelManager.connectAsync(node, false);
    }

    public List<Node> getConnectableNodes() {
        return nodeManager.getConnectableNodes();
    }
}
