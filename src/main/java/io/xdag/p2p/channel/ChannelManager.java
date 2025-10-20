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
    // Track connected Node IDs to prevent duplicate connections to the same peer
    // This works in both local testing (same IP, different ports) and production (different IPs)
    private final Map<String, Channel> connectedNodeIds = new ConcurrentHashMap<>();
    private final List<Channel> activePeers = Collections.synchronizedList(new ArrayList<>());
    private final Cache<InetSocketAddress, Long> recentConnections =
            CacheBuilder.newBuilder().maximumSize(2000).expireAfterWrite(30, TimeUnit.SECONDS).build();

    // Enhanced ban system with reason codes and statistics
    private final Map<InetAddress, BanInfo> bannedNodes = new ConcurrentHashMap<>();
    private final Map<InetAddress, AtomicInteger> banCounts = new ConcurrentHashMap<>();

    @Getter
    private final BanStatistics banStatistics = new BanStatistics();
    private final Set<InetAddress> whitelist = ConcurrentHashMap.newKeySet();

    private final AtomicInteger passivePeersCount = new AtomicInteger(0);
    private final AtomicInteger activePeersCount = new AtomicInteger(0);
    private final AtomicInteger connectingPeersCount = new AtomicInteger(0);

    private final ScheduledExecutorService poolLoopExecutor =
            Executors.newSingleThreadScheduledExecutor(BasicThreadFactory.builder().namingPattern("p2p-pool-%d").build());
    private final ScheduledExecutorService disconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(BasicThreadFactory.builder().namingPattern("p2p-disconnect-%d").build());


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
        } catch (Exception e) {
            log.warn("Failed to schedule connect loop: {}", e.getMessage());
        }
    }

    public void stop() {
        poolLoopExecutor.shutdownNow();
        disconnectExecutor.shutdownNow();
        activePeers.forEach(Channel::closeWithoutBan); // Graceful shutdown without ban
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
     * Ban a node's IP address with a specific reason.
     *
     * @param inetAddress the IP address to ban
     * @param reason the ban reason (determines duration)
     */
    public void banNode(InetAddress inetAddress, BanReason reason) {
        banNode(inetAddress, reason, reason.getDefaultDurationMs());
    }

    /**
     * Ban a node's IP address with custom duration.
     *
     * @param inetAddress the IP address to ban
     * @param reason the ban reason
     * @param banTimeMs the ban duration in milliseconds (overrides default)
     */
    public void banNode(InetAddress inetAddress, BanReason reason, long banTimeMs) {
        if (inetAddress == null || banTimeMs <= 0) {
            return;
        }

        // Check whitelist
        if (whitelist.contains(inetAddress)) {
            log.info("Attempted to ban whitelisted node {}, ignoring", inetAddress);
            return;
        }

        long now = System.currentTimeMillis();
        long banExpiry = now + banTimeMs;

        // Track ban count for this IP
        AtomicInteger count = banCounts.computeIfAbsent(inetAddress, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();

        // Apply graduated ban duration for repeat offenders
        long adjustedBanTime = banTimeMs;
        if (currentCount > 1) {
            // Double ban time for each repeat offense, up to 30 days max
            adjustedBanTime = Math.min(banTimeMs * (long) Math.pow(2, currentCount - 1),
                                       30L * 24 * 60 * 60 * 1000);
            banExpiry = now + adjustedBanTime;
            log.info("Repeat offender {} (count: {}), increasing ban duration to {}ms",
                     inetAddress, currentCount, adjustedBanTime);
        }

        BanInfo banInfo = new BanInfo(inetAddress, reason, now, banExpiry, currentCount);
        bannedNodes.put(inetAddress, banInfo);
        banStatistics.recordBan(reason, adjustedBanTime);

        log.info("Banned node {} for {} ({}) - count: {}, expires: {}",
                 inetAddress, reason.getDescription(), formatDuration(adjustedBanTime),
                 currentCount, banExpiry);

        // Close any existing connections from this IP - optimized to avoid stream
        for (Channel ch : channels.values()) {
            if (ch.getInetAddress() != null && ch.getInetAddress().equals(inetAddress)) {
                log.debug("Closing existing connection from banned node: {}", ch.getRemoteAddress());
                ch.closeWithoutBan(); // Use closeWithoutBan() to prevent infinite recursion
            }
        }
    }

    /**
     * Check if a node is currently banned.
     *
     * @param inetAddress the IP address to check
     * @return true if the node is banned, false otherwise
     */
    public boolean isBanned(InetAddress inetAddress) {
        if (inetAddress == null) {
            return false;
        }

        // Whitelisted nodes are never banned
        if (whitelist.contains(inetAddress)) {
            return false;
        }

        BanInfo banInfo = bannedNodes.get(inetAddress);
        if (banInfo == null) {
            return false;
        }

        // Check if ban has expired
        if (!banInfo.isActive()) {
            bannedNodes.remove(inetAddress);
            banStatistics.recordUnban();

            log.debug("Ban expired for {}", inetAddress);
            return false;
        }

        return true;
    }

    /**
     * Get ban information for a node.
     *
     * @param inetAddress the IP address to check
     * @return BanInfo or null if not banned
     */
    public BanInfo getBanInfo(InetAddress inetAddress) {
        if (inetAddress == null) {
            return null;
        }
        BanInfo info = bannedNodes.get(inetAddress);
        return (info != null && info.isActive()) ? info : null;
    }

    /**
     * Manually unban a node.
     *
     * @param inetAddress the IP address to unban
     */
    public void unbanNode(InetAddress inetAddress) {
        if (inetAddress != null) {
            BanInfo removed = bannedNodes.remove(inetAddress);
            if (removed != null) {
                banStatistics.recordUnban();

                log.info("Unbanned node {}", inetAddress);
            }
        }
    }

    /**
     * Add a node to the whitelist.
     *
     * @param inetAddress the IP address to whitelist
     */
    public void addToWhitelist(InetAddress inetAddress) {
        if (inetAddress != null) {
            whitelist.add(inetAddress);
            // Remove from ban list if currently banned
            if (bannedNodes.containsKey(inetAddress)) {
                unbanNode(inetAddress);
            }
            log.info("Added {} to whitelist", inetAddress);
        }
    }

    /**
     * Remove a node from the whitelist.
     *
     * @param inetAddress the IP address to remove from whitelist
     */
    public void removeFromWhitelist(InetAddress inetAddress) {
        if (inetAddress != null && whitelist.remove(inetAddress)) {
            log.info("Removed {} from whitelist", inetAddress);
        }
    }

    /**
     * Check if a node is whitelisted.
     *
     * @param inetAddress the IP address to check
     * @return true if whitelisted
     */
    public boolean isWhitelisted(InetAddress inetAddress) {
        return inetAddress != null && whitelist.contains(inetAddress);
    }

    /**
     * Get all currently banned nodes.
     *
     * @return collection of BanInfo for all active bans
     */
    public Collection<BanInfo> getAllBannedNodes() {
        List<BanInfo> activeBans = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (BanInfo info : bannedNodes.values()) {
            if (info.isActive()) {
                activeBans.add(info);
            }
        }
        return activeBans;
    }

    /**
     * Get count of currently banned nodes.
     *
     * @return number of active bans
     */
    public int getBannedNodeCount() {
        int count = 0;
        for (BanInfo info : bannedNodes.values()) {
            if (info.isActive()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Format duration in human-readable form.
     *
     * @param durationMs duration in milliseconds
     * @return formatted string (e.g., "5m", "2h", "3d")
     */
    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h";
        }
        long days = hours / 24;
        return days + "d";
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
                // Skip banned nodes
                if (isBanned(address.getAddress())) {
                    log.debug("Skipping banned node: {}", address);
                    continue;
                }

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
        String nodeId = channel.getNodeId();

        // Check if we already have a connection to this Node ID (prevents duplicate connections)
        // This works in both local testing (same IP) and production (different IPs)
        if (nodeId != null && !nodeId.isEmpty()) {
            Channel existingChannel = connectedNodeIds.get(nodeId);
            if (existingChannel != null) {
                // Check if the Netty channel is actually active (not the isActive field)
                boolean nettyChannelActive = existingChannel.getCtx() != null
                    && existingChannel.getCtx().channel() != null
                    && existingChannel.getCtx().channel().isActive();
                if (nettyChannelActive) {
                    log.warn("Duplicate connection detected to Node ID {}. Existing: {}, New: {}. Closing new connection.",
                             nodeId, existingChannel.getRemoteAddress(), channel.getRemoteAddress());
                    channel.closeWithoutBan();
                    return;
                }
            }
        }

        // Proceed with normal connection logic
        if (channels.putIfAbsent(channel.getRemoteAddress(), channel) == null) {
            // Track by Node ID (if available)
            if (nodeId != null && !nodeId.isEmpty()) {
                connectedNodeIds.put(nodeId, channel);
            }
            activePeers.add(channel);
            boolean isActive = channel.isActive();
            if (isActive) {
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
     *
     * @param remote the remote address
     * @param ctx the channel handler context
     * @param nodeId the peer's node ID (for duplicate connection detection)
     */
    public void markHandshakeSuccess(java.net.InetSocketAddress remote, ChannelHandlerContext ctx, String nodeId) {
        try {
            Channel ch = new Channel(this);
            ch.setP2pConfig(config);
            ch.setChannelHandlerContext(ctx);
            // Set nodeId BEFORE calling onChannelActive() so duplicate detection works
            ch.setNodeId(nodeId);
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
            // Also remove from Node ID tracking map
            String nodeId = channel.getNodeId();
            if (nodeId != null && !nodeId.isEmpty()) {
                connectedNodeIds.remove(nodeId);
            }
            activePeers.remove(channel);
            boolean isActive = channel.isActive();
            if (isActive) {
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

    public AtomicInteger getConnectingPeersCount() {
        return connectingPeersCount;
    }
}
