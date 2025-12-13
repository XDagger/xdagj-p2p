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
import io.xdag.crypto.encoding.Base58;
import io.xdag.crypto.keys.AddressUtils;
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

    // Ban system with graduated durations
    private final Map<InetAddress, BanInfo> bannedNodes = new ConcurrentHashMap<>();
    private final Map<InetAddress, AtomicInteger> banCounts = new ConcurrentHashMap<>();
    private final Set<InetAddress> whitelist = ConcurrentHashMap.newKeySet();

    private final AtomicInteger passivePeersCount = new AtomicInteger(0);
    private final AtomicInteger activePeersCount = new AtomicInteger(0);
    private final AtomicInteger connectingPeersCount = new AtomicInteger(0);

    // Cached local NodeId for deterministic duplicate connection resolution
    private volatile String localNodeId;

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

        // Initialize local NodeId for deterministic duplicate connection resolution
        if (config.getNodeKey() != null) {
            this.localNodeId = Base58.encodeCheck(AddressUtils.toBytesAddress(config.getNodeKey().getPublicKey()));
            log.info("ChannelManager started with localNodeId: {}", localNodeId);
        } else {
            log.warn("No nodeKey configured - duplicate connection resolution may not work correctly");
        }

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
     * Ban a node's IP address with custom duration.
     *
     * @param inetAddress the IP address to ban
     * @param banTimeMs the ban duration in milliseconds
     */
    public void banNode(InetAddress inetAddress, long banTimeMs) {
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

        BanInfo banInfo = new BanInfo(inetAddress, banExpiry, currentCount);
        bannedNodes.put(inetAddress, banInfo);

        log.info("Banned node {} - count: {}, duration: {}, expires: {}",
                 inetAddress, currentCount, formatDuration(adjustedBanTime), banExpiry);

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

        // Get home node's port to avoid self-connection
        int homePort = config.getPort();

        int connectCount = 0;
        for (Node node : connectableNodes) {
            if (connectCount >= desiredConnections) {
                break;
            }

            InetSocketAddress address = node.getPreferInetSocketAddress();
            if (address == null) {
                continue;
            }

            // Skip self-connection attempts (comparing port since in local testing all nodes use 127.0.0.1)
            if (address.getPort() == homePort &&
                (address.getAddress().isLoopbackAddress() || address.getAddress().isAnyLocalAddress())) {
                log.debug("Skipping self-connection to {}", address);
                continue;
            }

            // Skip if already connected to this Node ID (prevents duplicate connections in local testing)
            if (node.getId() != null && connectedNodeIds.containsKey(node.getId())) {
                log.debug("Skipping connection to {} - already connected to Node ID {}", address, node.getId());
                continue;
            }

            // CRITICAL CHECK: Skip if we already have an active connection to this address
            // This check MUST come before recentConnections check to prevent duplicate connection attempts
            // when both nodes try to connect to each other simultaneously
            if (hasActiveConnectionTo(address)) {
                log.debug("Skipping connection to {} - already have an active connection to this node", address);
                continue;
            }

            // Skip if we just tried to connect to this address recently
            if (recentConnections.getIfPresent(address) != null) {
                continue;
            }

            // Skip if already connected by exact address match
            if (isConnected(address)) {
                continue;
            }

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


    /**
     * Check if we already have an active connection to the target address.
     * <p>
     * This method performs a comprehensive check to prevent duplicate connection attempts:
     * 1. Checks if there's an exact address match in channels Map
     * 2. Checks all connectedNodeIds to see if any Channel is connected to the same IP:Port
     * 3. For local testing (loopback addresses), checks if we have any active connection to the same IP,
     *    since inbound connections use different ports than the target listening port
     * 4. Verifies that the Channel's underlying Netty channel is actually active
     * <p>
     * This is particularly useful when both nodes have each other in their whitelist,
     * preventing unnecessary reconnection attempts after recentConnections cache expires.
     *
     * @param targetAddress the address we want to connect to
     * @return true if we already have an active connection to this address
     */
    private boolean hasActiveConnectionTo(InetSocketAddress targetAddress) {
        if (targetAddress == null) {
            return false;
        }

        // Fast path: Check if we have a channel with this exact address
        Channel existingChannel = channels.get(targetAddress);
        if (existingChannel != null) {
            // Verify the channel's Netty channel is actually active
            if (existingChannel.getCtx() != null &&
                existingChannel.getCtx().channel() != null &&
                existingChannel.getCtx().channel().isActive()) {
                log.debug("hasActiveConnectionTo({}) = TRUE (exact match in channels map)", targetAddress);
                return true;
            } else {
                log.debug("hasActiveConnectionTo({}) - exact match found but channel inactive (ctx={}, netty={})",
                        targetAddress,
                        existingChannel.getCtx() != null,
                        existingChannel.getCtx() != null && existingChannel.getCtx().channel() != null ?
                            existingChannel.getCtx().channel().isActive() : "null");
            }
        }

        // Additional check: Look through all connected nodes to see if any has
        // a remoteAddress matching our target (handles outbound connections)
        InetAddress targetHost = targetAddress.getAddress();
        int targetPort = targetAddress.getPort();

        // DIAGNOSTIC: Log the current state of connectedNodeIds
        if (log.isDebugEnabled()) {
            log.debug("hasActiveConnectionTo({}) - checking connectedNodeIds (size={})", targetAddress, connectedNodeIds.size());
            for (Map.Entry<String, Channel> entry : connectedNodeIds.entrySet()) {
                Channel ch = entry.getValue();
                InetSocketAddress addr = ch.getRemoteAddress();
                boolean isActive = ch.getCtx() != null && ch.getCtx().channel() != null && ch.getCtx().channel().isActive();
                log.debug("  connectedNodeIds[{}]: remoteAddr={}, nodeId={}, isActive={}",
                        entry.getKey().substring(0, Math.min(8, entry.getKey().length())) + "...",
                        addr, ch.getNodeId(), isActive);
            }
        }

        for (Channel channel : connectedNodeIds.values()) {
            InetSocketAddress remoteAddr = channel.getRemoteAddress();
            if (remoteAddr != null) {
                // Exact match (works for outbound connections where remoteAddress = target)
                if (remoteAddr.getAddress().equals(targetHost) &&
                    remoteAddr.getPort() == targetPort) {
                    // Verify the channel is actually active
                    if (channel.getCtx() != null &&
                        channel.getCtx().channel() != null &&
                        channel.getCtx().channel().isActive()) {
                        log.debug("hasActiveConnectionTo({}) = TRUE (exact match in connectedNodeIds)", targetAddress);
                        return true;
                    }
                }

                // For loopback/local addresses: if we have any active connection from the same IP,
                // and that connection has a nodeId, assume it's the same node
                // This handles the case where node2 connects to node1 (inbound), then node1 tries to connect to node2
                if (targetHost.isLoopbackAddress() &&
                    remoteAddr.getAddress().equals(targetHost)) {
                    if (channel.getNodeId() != null && !channel.getNodeId().isEmpty()) {
                        // We have an active connection from this loopback IP with a known nodeId
                        // Very likely the same node, skip reconnection attempt
                        if (channel.getCtx() != null &&
                            channel.getCtx().channel() != null &&
                            channel.getCtx().channel().isActive()) {
                            log.debug("hasActiveConnectionTo({}) = TRUE (loopback match: same IP {}, has nodeId {})",
                                    targetAddress, remoteAddr, channel.getNodeId());
                            return true;
                        } else {
                            log.debug("hasActiveConnectionTo({}) - loopback match but channel inactive (remoteAddr={}, nodeId={}, ctx={}, netty={})",
                                    targetAddress, remoteAddr, channel.getNodeId(),
                                    channel.getCtx() != null,
                                    channel.getCtx() != null && channel.getCtx().channel() != null ?
                                        channel.getCtx().channel().isActive() : "null");
                        }
                    } else {
                        log.debug("hasActiveConnectionTo({}) - loopback IP match but no nodeId (remoteAddr={})",
                                targetAddress, remoteAddr);
                    }
                }
            }
        }

        log.debug("hasActiveConnectionTo({}) = FALSE (no matching active connection found)", targetAddress);
        return false;
    }

    private void checkConnections() {
        if (activePeers.size() < config.getMaxConnections()) {
            return;
        }

        List<Channel> peersToDisconnect = new ArrayList<>(activePeers);
        peersToDisconnect.removeIf(p -> !p.isActive() || p.isTrustPeer());

        if (!peersToDisconnect.isEmpty()) {
            Channel peerToDisconnect = peersToDisconnect.get(new Random().nextInt(peersToDisconnect.size()));
            log.info("Max connection limit reached. Disconnecting a random peer without penalty: {}", peerToDisconnect.getRemoteAddress());
            peerToDisconnect.closeWithoutBan();
        }
    }


    public ChannelFuture connectAsync(Node node, boolean isDiscovery) {
        InetSocketAddress address = node.getPreferInetSocketAddress();

        // CRITICAL: Check if we already have an active connection to this address
        // This prevents wasting resources on duplicate handshakes
        if (hasActiveConnectionTo(address)) {
            log.debug("Skipped connection to {} - already have active connection", address);
            return null;  // Don't even attempt the connection
        }

        if (address != null) {
            recentConnections.put(address, System.currentTimeMillis());
        }
        return peerClient.connect(node, future -> {
            if (!future.isSuccess()) {
                log.warn("Connect to peer {} fail, cause:{}", node.getPreferInetSocketAddress(),
                        future.cause() != null ? future.cause().getMessage() : "unknown");
            }
        });
    }

    public void onChannelActive(Channel channel) {
        String nodeId = channel.getNodeId();

        // If nodeId is null or empty, we cannot deduplicate by NodeId
        // Only add to channels map (for backward compatibility), but not to connectedNodeIds
        if (nodeId == null || nodeId.isEmpty()) {
            log.warn("Channel {} has no NodeId, cannot deduplicate. This may cause duplicate connections.",
                     channel.getRemoteAddress());
            addChannelToMaps(channel, null);
            return;
        }

        // Thread-safe deduplication using compute
        // This ensures atomic check-and-update on connectedNodeIds
        Channel[] result = new Channel[1]; // To hold the result from compute
        boolean[] shouldClose = new boolean[1];

        connectedNodeIds.compute(nodeId, (key, existingChannel) -> {
            if (existingChannel == null) {
                // No existing connection, accept the new one
                result[0] = channel;
                return channel;
            }

            // Check if existing channel is actually active
            if (isNettyChannelActive(existingChannel)) {
                // DUPLICATE DETECTION: Both channels are active
                // Use deterministic tie-breaking to ensure both sides make the same decision
                //
                // Algorithm:
                // - Compare local NodeId with remote NodeId
                // - Node with smaller NodeId prefers OUTBOUND (isActive=true) connections
                // - Node with larger NodeId prefers INBOUND (isActive=false) connections
                //
                // Example (Node1=ABC, Node2=XYZ, ABC < XYZ):
                // - Node1 sees duplicate: localId(ABC) < remoteId(XYZ) → prefers outbound → keeps its outbound
                // - Node2 sees duplicate: localId(XYZ) > remoteId(ABC) → prefers inbound → keeps Node1's outbound
                // - Both nodes keep the SAME connection!

                Channel channelToKeep;
                Channel channelToClose;

                if (localNodeId != null) {
                    boolean preferOutbound = localNodeId.compareTo(nodeId) < 0;
                    boolean newIsOutbound = channel.isActive();
                    boolean existingIsOutbound = existingChannel.isActive();

                    log.info("Duplicate connection to NodeId {}. Deterministic resolution: localId={}, remoteId={}, preferOutbound={}",
                             nodeId, localNodeId.substring(0, 8) + "...", nodeId.substring(0, 8) + "...", preferOutbound);
                    log.info("  Existing: {} (outbound={}), New: {} (outbound={})",
                             existingChannel.getRemoteAddress(), existingIsOutbound,
                             channel.getRemoteAddress(), newIsOutbound);

                    if (preferOutbound) {
                        // Local node has smaller ID → prefer outbound connection
                        if (newIsOutbound && !existingIsOutbound) {
                            // New is outbound, existing is inbound → keep new (preferred direction)
                            channelToKeep = channel;
                            channelToClose = existingChannel;
                        } else if (!newIsOutbound && existingIsOutbound) {
                            // New is inbound, existing is outbound → keep existing (preferred direction)
                            channelToKeep = existingChannel;
                            channelToClose = channel;
                        } else if (existingIsOutbound && newIsOutbound) {
                            // BUG-P2P-004 FIX: Both outbound (our preferred direction)
                            // Keep NEW connection (just completed handshake, guaranteed alive)
                            // Existing might be half-open/stale
                            log.info("  Both connections are OUTBOUND (our preferred) - keeping NEW (guaranteed alive)");
                            channelToKeep = channel;
                            channelToClose = existingChannel;
                        } else {
                            // BUG-P2P-004 FIX: Both inbound (NOT our preferred direction)
                            // Keep NEW connection (just completed handshake, guaranteed alive)
                            log.info("  Both connections are INBOUND but we prefer OUTBOUND - keeping NEW (guaranteed alive)");
                            channelToKeep = channel;
                            channelToClose = existingChannel;
                        }
                    } else {
                        // Local node has larger ID → prefer inbound connection
                        if (!newIsOutbound && existingIsOutbound) {
                            // New is inbound, existing is outbound → keep new (preferred direction)
                            channelToKeep = channel;
                            channelToClose = existingChannel;
                        } else if (newIsOutbound && !existingIsOutbound) {
                            // New is outbound, existing is inbound → keep existing (preferred direction)
                            channelToKeep = existingChannel;
                            channelToClose = channel;
                        } else if (!existingIsOutbound && !newIsOutbound) {
                            // BUG-P2P-004 FIX: Both inbound (our preferred direction)
                            // Keep NEW connection (just completed handshake, guaranteed alive)
                            log.info("  Both connections are INBOUND (our preferred) - keeping NEW (guaranteed alive)");
                            channelToKeep = channel;
                            channelToClose = existingChannel;
                        } else {
                            // BUG-P2P-004 FIX: Both outbound (NOT our preferred direction)
                            // Keep NEW connection (just completed handshake, guaranteed alive)
                            log.info("  Both connections are OUTBOUND but we prefer INBOUND - keeping NEW (guaranteed alive)");
                            channelToKeep = channel;
                            channelToClose = existingChannel;
                        }
                    }
                } else {
                    // No local NodeId configured, fall back to first-come-first-served
                    log.warn("No localNodeId configured, using first-come-first-served for duplicate resolution");
                    channelToKeep = existingChannel;
                    channelToClose = channel;
                }

                if (channelToClose == channel) {
                    log.info("  → Closing NEW connection, keeping existing");
                    shouldClose[0] = true;
                    result[0] = existingChannel;
                    return existingChannel;
                } else {
                    log.info("  → Closing EXISTING connection, keeping new");
                    cleanupStaleChannel(existingChannel);
                    result[0] = channel;
                    return channel;
                }
            } else {
                // Existing channel is stale, replace it
                log.info("Replacing stale connection for NodeId {}. Old: {}, New: {}",
                         nodeId, existingChannel.getRemoteAddress(), channel.getRemoteAddress());
                cleanupStaleChannel(existingChannel);
                result[0] = channel;
                return channel; // Replace with new
            }
        });

        // If we should close the new connection, do it outside the compute block
        if (shouldClose[0]) {
            channel.closeWithoutBan();
            return;
        }

        // If the new channel was accepted, add to other tracking structures
        if (result[0] == channel) {
            addChannelToMaps(channel, nodeId);
        }
    }

    /**
     * Check if a channel's underlying Netty channel is actually active and usable.
     *
     * <p>BUG-P2P-003 FIX: Enhanced check to detect dying connections.
     * Previous implementation only checked isActive(), which returns true even for
     * connections that are in the process of closing. This caused the duplicate
     * connection algorithm to keep stale connections and reject new working ones.
     *
     * <p>Now we also check:
     * <ul>
     *   <li>isWritable() - False when connection is congested or closing</li>
     *   <li>isOpen() - False when channel is closed</li>
     * </ul>
     */
    private boolean isNettyChannelActive(Channel channel) {
        if (channel == null || channel.getCtx() == null || channel.getCtx().channel() == null) {
            return false;
        }
        io.netty.channel.Channel nettyChannel = channel.getCtx().channel();
        // isActive alone is not enough - a dying connection may still report isActive=true
        // We also check isOpen and isWritable for more accurate status
        return nettyChannel.isActive() && nettyChannel.isOpen() && nettyChannel.isWritable();
    }

    /**
     * Add channel to tracking maps and notify handlers.
     * This is called after deduplication has passed.
     */
    private void addChannelToMaps(Channel channel, String nodeId) {
        // Add to channels map (keyed by remote address for backward compatibility)
        if (channels.putIfAbsent(channel.getRemoteAddress(), channel) == null) {
            activePeers.add(channel);
            if (channel.isActive()) {
                activePeersCount.incrementAndGet();
            } else {
                passivePeersCount.incrementAndGet();
            }

            log.info("New channel connected: {} (NodeId: {}). Total channels: {}, Unique peers: {}",
                     channel.getRemoteAddress(), nodeId, channels.size(), connectedNodeIds.size());

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
     * Clean up a stale channel from all tracking collections.
     * This is called when replacing a stale connection with a new one from the same NodeId.
     */
    private void cleanupStaleChannel(Channel staleChannel) {
        if (staleChannel == null) {
            return;
        }

        // Remove from channels map
        channels.remove(staleChannel.getRemoteAddress());

        // Remove from activePeers list
        activePeers.remove(staleChannel);

        // Update counters
        if (staleChannel.isActive()) {
            activePeersCount.decrementAndGet();
        } else {
            passivePeersCount.decrementAndGet();
        }

        // Close the stale channel
        try {
            staleChannel.closeWithoutBan();
        } catch (Exception e) {
            log.debug("Error closing stale channel: {}", e.getMessage());
        }

        log.debug("Cleaned up stale channel: {}", staleChannel.getRemoteAddress());
    }

    /**
     * Get unique connected channels, deduplicated by Node ID.
     * This should be used for broadcasting messages to avoid sending to the same peer multiple times.
     *
     * @return list of unique channels (one per Node ID)
     */
    public List<Channel> getUniqueConnectedChannels() {
        return new ArrayList<>(connectedNodeIds.values());
    }

    /**
     * Helper to record handshake success for a Netty channel that doesn't yet have a wrapped Channel instance.
     *
     * @param remote the remote address
     * @param ctx the channel handler context
     * @param nodeId the peer's node ID (for duplicate connection detection)
     * @param isOutbound true if this is an outbound connection (we initiated), false if inbound (peer initiated)
     */
    public void markHandshakeSuccess(java.net.InetSocketAddress remote, ChannelHandlerContext ctx, String nodeId, boolean isOutbound) {
        try {
            Channel ch = new Channel(this);
            ch.setP2pConfig(config);
            ch.setChannelHandlerContext(ctx);
            // Set nodeId BEFORE calling onChannelActive() so duplicate detection works
            ch.setNodeId(nodeId);
            ch.setFinishHandshake(true);
            // IMPORTANT: isActive indicates connection direction for duplicate detection
            // true = outbound (we initiated), false = inbound (peer initiated)
            ch.setActive(isOutbound);
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
        // BUG-P2P-003 FIX: Always attempt to clean up from connectedNodeIds,
        // regardless of whether channels.remove() succeeds.
        // This prevents stale entries from blocking new connections.
        String nodeId = channel.getNodeId();
        if (nodeId != null && !nodeId.isEmpty()) {
            // Only remove if the stored channel is THIS channel (not a replacement)
            connectedNodeIds.computeIfPresent(nodeId, (key, storedChannel) -> {
                if (storedChannel == channel) {
                    log.debug("Removing nodeId {} from connectedNodeIds (channel {})", nodeId, channel.getRemoteAddress());
                    return null; // Remove
                }
                // A different channel is stored - this channel was already replaced
                log.debug("NodeId {} has different channel stored, not removing", nodeId);
                return storedChannel; // Keep the other channel
            });
        }

        // BUG-P2P-005 FIX: Only remove from channels map if the stored channel is THIS channel.
        // When a channel is replaced (due to duplicate detection), both old and new channels
        // have the SAME remoteAddress. Without this check, the old channel's onChannelInactive()
        // would remove the entry that now contains the replacement channel!
        final boolean[] wasRemoved = {false};
        channels.computeIfPresent(channel.getRemoteAddress(), (addr, storedChannel) -> {
            if (storedChannel == channel) {
                wasRemoved[0] = true;
                return null; // Remove only if THIS channel
            }
            log.debug("Channel {} has different channel stored (stored={}, disconnecting={}), not removing from channels map",
                    addr, System.identityHashCode(storedChannel), System.identityHashCode(channel));
            return storedChannel; // Keep the replacement channel
        });

        if (wasRemoved[0]) {
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
        } else {
            log.debug("Channel {} disconnect ignored - already replaced or not in channels map", channel.getRemoteAddress());
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
