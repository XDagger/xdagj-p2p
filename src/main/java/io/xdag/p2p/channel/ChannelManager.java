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
package io.xdag.p2p.channel;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFutureListener;
import io.xdag.p2p.PeerClient;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.NodeManager;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

@Slf4j
public class ChannelManager {

    private final P2pConfig config;
    private final NodeManager nodeManager;
    private PeerClient peerClient;

    @Getter
    private final Map<InetSocketAddress, Channel> channels = new ConcurrentHashMap<>();
    private final List<Channel> activePeers = Collections.synchronizedList(new ArrayList<>());
    private final Cache<InetSocketAddress, Long> recentConnections =
            CacheBuilder.newBuilder().maximumSize(2000).expireAfterWrite(30, TimeUnit.SECONDS).build();

    @Getter
    private final AtomicInteger passivePeersCount = new AtomicInteger(0);
    @Getter
    private final AtomicInteger activePeersCount = new AtomicInteger(0);
    @Getter
    private final AtomicInteger connectingPeersCount = new AtomicInteger(0);

    private final ScheduledExecutorService poolLoopExecutor =
            Executors.newSingleThreadScheduledExecutor(new BasicThreadFactory.Builder().namingPattern("p2p-pool-%d").build());
    private final ScheduledExecutorService disconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(new BasicThreadFactory.Builder().namingPattern("p2p-disconnect-%d").build());


    public ChannelManager(P2pConfig config, NodeManager nodeManager) {
        this.config = config;
        this.nodeManager = nodeManager;
    }

    public void start(PeerClient peerClient) {
        this.peerClient = peerClient;
        poolLoopExecutor.scheduleWithFixedDelay(this::connectLoop, 3, 5, TimeUnit.SECONDS);

        if (config.isDisconnectionPolicyEnable()) {
            disconnectExecutor.scheduleWithFixedDelay(this::checkConnections, 30, 30, TimeUnit.SECONDS);
        }
    }

    /**
     * Trigger an immediate connection attempt without waiting for the next scheduled run.
     */
    public void triggerImmediateConnect() {
        try {
            poolLoopExecutor.execute(this::connectLoop);
        } catch (Exception ignored) {
        }
    }

    public void stop() {
        poolLoopExecutor.shutdownNow();
        disconnectExecutor.shutdownNow();
        activePeers.forEach(channel -> channel.close(0)); // No ban time for graceful shutdown
    }

    /**
     * Check if the channel manager is shutdown.
     *
     * @return true if shutdown, false otherwise
     */
    public boolean isShutdown() {
        return poolLoopExecutor.isShutdown() && disconnectExecutor.isShutdown();
    }

    /**
     * Ban a node's IP address for the specified duration.
     *
     * @param inetAddress the IP address to ban
     * @param banTime the ban duration in milliseconds
     */
    public void banNode(InetAddress inetAddress, long banTime) {
        if (inetAddress != null && banTime > 0) {
            log.info("Banning node {} for {} ms", inetAddress, banTime);
            // TODO: Implement actual banning logic if needed
            // For now, just log the ban action
        }
    }


    private void connectLoop() {
        if (activePeers.size() >= config.getMaxConnections()) {
            return;
        }

        int desiredConnections = config.getMinConnections() - activePeers.size();
        if (desiredConnections <= 0) {
            return;
        }
        log.debug(
                "Pool before-connect: active={}, min={}, desired={}",
                activePeers.size(),
                config.getMinConnections(),
                desiredConnections);

        List<Node> connectableNodes = new ArrayList<>(nodeManager.getConnectableNodes());
        if (connectableNodes.isEmpty()) {
            // Fallback: directly try boot seeds to bootstrap TCP handshake (independent of KAD)
            log.debug("No discovered nodes yet; will try boot seeds via TCP");
            try {
                connectableNodes.addAll(nodeManager.getBootNodes());
            } catch (Throwable ignore) {
                // ignore if not available
            }
        }
        Collections.shuffle(connectableNodes);

        int connectCount = 0;
        for (Node node : connectableNodes) {
            if (connectCount >= desiredConnections) {
                break;
            }

            InetSocketAddress address = node.getPreferInetSocketAddress();
            if (address != null && !isConnected(address) && recentConnections.getIfPresent(address) == null) {
                log.debug("Attempting to connect to {}", address);
                connectAsync(node, false);
                connectCount++;
            }
        }

    }


    private void checkConnections() {
        if (activePeers.size() < config.getMaxConnections()) {
            return;
        }

        List<Channel> peersToDisconnect = new ArrayList<>(activePeers);
        peersToDisconnect.removeIf(p -> !p.isActive() || p.isTrustPeer());

        if (!peersToDisconnect.isEmpty()) {
            Channel peerToDisconnect = peersToDisconnect.get(new Random().nextInt(peersToDisconnect.size()));
            log.info("Max connection limit reached. Disconnecting a random peer: {}", peerToDisconnect.getRemoteAddress());
            peerToDisconnect.close(); // Use default ban time for random disconnection
        }
    }


    public ChannelFuture connectAsync(Node node, boolean isDiscovery) {
        InetSocketAddress address = node.getPreferInetSocketAddress();
        if (address != null) {
            recentConnections.put(address, System.currentTimeMillis());
        }
        return peerClient.connect(node, (ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                log.warn("Connect to peer {} fail, cause:{}", node.getPreferInetSocketAddress(),
                        future.cause() != null ? future.cause().getMessage() : "unknown");
            }
        });
    }

    public void onChannelActive(Channel channel) {
        if (channels.putIfAbsent(channel.getRemoteAddress(), channel) == null) {
            activePeers.add(channel);
            if (channel.isActive()) {
                activePeersCount.incrementAndGet();
            } else {
                passivePeersCount.incrementAndGet();
            }
            log.info("New channel connected: {}. Total channels: {}", channel.getRemoteAddress(), channels.size());
            // Notify application handlers
            try {
                for (var h : config.getHandlerList()) {
                    h.onConnect(channel);
                }
            } catch (Exception e) {
                log.warn("Handler onConnect error: {}", e.getMessage());
            }
        }
    }

    /**
     * Helper to record handshake success for a Netty channel that doesn't yet have a wrapped Channel instance.
     */
    public void markHandshakeSuccess(java.net.InetSocketAddress remote, ChannelHandlerContext ctx) {
        try {
            Channel ch = new Channel(this);
            ch.setP2pConfig(config);
            ch.setChannelHandlerContext(ctx);
            onChannelActive(ch);
            int nowActive = activePeers.size();
            int min = config.getMinConnections();
            int nowDesired = Math.max(0, min - nowActive);
            log.debug("Pool after-connect: active={}, min={}, desired={}", nowActive, min, nowDesired);
        } catch (Exception e) {
            log.warn("Failed to mark handshake success for {}: {}", remote, e.getMessage());
        }
    }

    public void onChannelInactive(Channel channel) {
        if (channels.remove(channel.getRemoteAddress()) != null) {
            activePeers.remove(channel);
            if (channel.isActive()) {
                activePeersCount.decrementAndGet();
            } else {
                passivePeersCount.decrementAndGet();
            }
            log.info("Channel disconnected: {}. Total channels: {}", channel.getRemoteAddress(), channels.size());
            // Notify application handlers
            try {
                for (var h : config.getHandlerList()) {
                    h.onDisconnect(channel);
                }
            } catch (Exception e) {
                log.warn("Handler onDisconnect error: {}", e.getMessage());
            }
        }
    }

    public boolean isConnected(InetSocketAddress address) {
        return channels.containsKey(address);
    }

    public int getActivePeersCount() {
        return activePeersCount.get();
    }

    public int getPassivePeersCount() {
        return passivePeersCount.get();
    }
}
