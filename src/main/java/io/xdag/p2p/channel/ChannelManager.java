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
import io.xdag.p2p.message.node.HandshakeMessage;
import io.xdag.p2p.utils.NetUtils;
import io.netty.util.AttributeKey;
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

    // ---- admission control: every TCP connection, from the moment it is accepted or dialled ----
    /** Marks a connection that holds a slot in the counters below. */
    static final AttributeKey<ConnectionSlot> SLOT = AttributeKey.valueOf("p2p.slot");
    private final Object admissionLock = new Object();
    private final Map<InetAddress, Integer> connectionsPerIp = new java.util.HashMap<>();
    private final Map<String, Integer> connectionsPerSubnet = new java.util.HashMap<>();
    private int inboundConnections;
    private int pendingInbound;
    private int totalConnections;
    /** Addresses that turned out to be this node itself; never dialled again. */
    private final Set<InetSocketAddress> selfAddresses = ConcurrentHashMap.newKeySet();
    /** Upper bound on remembered bans: the map must not grow with the number of addresses that misbehave. */
    private static final int MAX_BAN_ENTRIES = 50_000;
    private static final long MAX_BAN_TIME_MS = 30L * 24 * 60 * 60 * 1000;

    /** One admitted TCP connection. */
    static final class ConnectionSlot {
        final InetAddress address;
        final boolean inbound;
        boolean pending;

        ConnectionSlot(InetAddress address, boolean inbound) {
            this.address = address;
            this.inbound = inbound;
            this.pending = inbound;
        }
    }

    /** Why a connection was not admitted (null: it was). */
    public enum Refusal {
        BANNED, NOT_A_CONFIGURED_PEER, TOO_MANY_CONNECTIONS, TOO_MANY_PENDING, TOO_MANY_FROM_IP, TOO_MANY_FROM_SUBNET,
        SHUTDOWN
    }

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
            log.debug("Attempted to ban whitelisted node {}, ignoring", inetAddress);
            return;
        }

        long now = System.currentTimeMillis();
        long banExpiry = now + banTimeMs;

        // Track ban count for this IP
        AtomicInteger count = banCounts.computeIfAbsent(inetAddress, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();

        // Apply graduated ban duration for repeat offenders
        long adjustedBanTime = Math.min(banTimeMs, MAX_BAN_TIME_MS);
        if (currentCount > 1) {
            // Double ban time for each repeat offense, up to 30 days max. Computed by shifting with a bound:
            // the former banTimeMs * (long) Math.pow(2, count - 1) overflowed after some 48 offences, the result
            // went negative, Math.min() picked it, and the ban expired before it began.
            int doublings = Math.min(currentCount - 1, 40);
            adjustedBanTime = adjustedBanTime >= (MAX_BAN_TIME_MS >> doublings)
                    ? MAX_BAN_TIME_MS
                    : adjustedBanTime << doublings;
            banExpiry = now + adjustedBanTime;
            log.debug("Repeat offender {} (count: {}), increasing ban duration to {}ms",
                     inetAddress, currentCount, adjustedBanTime);
        }
        pruneBans(now);

        BanInfo banInfo = new BanInfo(inetAddress, banExpiry, currentCount);
        bannedNodes.put(inetAddress, banInfo);

        log.debug("Banned node {} - count: {}, duration: {}, expires: {}",
                 inetAddress, currentCount, formatDuration(adjustedBanTime), banExpiry);

        // Close any existing connections from this IP - optimized to avoid stream
        for (Channel ch : channels.values()) {
            if (ch.getInetAddress() != null && ch.getInetAddress().equals(inetAddress)) {
                log.debug("Closing existing connection from banned node: {}", ch.getRemoteAddress());
                ch.closeWithoutBan(); // Use closeWithoutBan() to prevent infinite recursion
            }
        }
    }

    /** Forget expired bans (and, if there are still too many, the offence counters of addresses not banned now). */
    private void pruneBans(long now) {
        if (bannedNodes.size() < MAX_BAN_ENTRIES && banCounts.size() < MAX_BAN_ENTRIES) {
            return;
        }
        bannedNodes.values().removeIf(info -> info.banExpiryTimestamp() <= now);
        banCounts.keySet().removeIf(address -> !bannedNodes.containsKey(address));
    }

    // =====================================================================================================
    // Admission control
    // =====================================================================================================

    /**
     * Decide whether a new TCP connection may exist, and if so reserve its slot. Called for every accepted
     * connection before any handler is installed, and for every connection this node dials.
     *
     * <p>Limits: bans; in a closed network only configured peers; total and inbound connections; inbound
     * connections that have not finished the handshake; connections per IP address and per network. Configured
     * peers are exempt from the per-address limits (the operator chose them) but not from the totals.
     *
     * @return null if admitted, otherwise the reason
     */
    public Refusal admit(io.netty.channel.Channel nettyChannel, InetAddress address, boolean inbound) {
        if (address == null) {
            return Refusal.NOT_A_CONFIGURED_PEER;
        }
        boolean configured = config.isConfiguredPeer(address);
        if (isBanned(address)) {
            return Refusal.BANNED;
        }
        if (!config.isPermissionless() && !configured) {
            return Refusal.NOT_A_CONFIGURED_PEER;
        }
        String subnet = NetUtils.subnetKey(address);
        synchronized (admissionLock) {
            if (totalConnections >= config.getMaxConnections() + config.getMaxPendingHandshakes()) {
                return Refusal.TOO_MANY_CONNECTIONS;
            }
            if (inbound) {
                if (inboundConnections - pendingInbound >= config.getMaxInboundConnections() && !configured) {
                    return Refusal.TOO_MANY_CONNECTIONS;
                }
                if (pendingInbound >= config.getMaxPendingHandshakes()) {
                    return Refusal.TOO_MANY_PENDING;
                }
            }
            if (!configured) {
                if (connectionsPerIp.getOrDefault(address, 0) >= config.getMaxConnectionsPerIp()) {
                    return Refusal.TOO_MANY_FROM_IP;
                }
                if (connectionsPerSubnet.getOrDefault(subnet, 0) >= config.getMaxConnectionsPerSubnet()) {
                    return Refusal.TOO_MANY_FROM_SUBNET;
                }
            }
            ConnectionSlot slot = new ConnectionSlot(address, inbound);
            totalConnections++;
            connectionsPerIp.merge(address, 1, Integer::sum);
            connectionsPerSubnet.merge(subnet, 1, Integer::sum);
            if (inbound) {
                inboundConnections++;
                pendingInbound++;
            }
            nettyChannel.attr(SLOT).set(slot);
        }
        nettyChannel.closeFuture().addListener(f -> release(nettyChannel));
        return null;
    }

    private void release(io.netty.channel.Channel nettyChannel) {
        ConnectionSlot slot = nettyChannel.attr(SLOT).getAndSet(null);
        if (slot == null) {
            return;
        }
        synchronized (admissionLock) {
            totalConnections--;
            connectionsPerIp.computeIfPresent(slot.address, (k, v) -> v <= 1 ? null : v - 1);
            connectionsPerSubnet.computeIfPresent(NetUtils.subnetKey(slot.address), (k, v) -> v <= 1 ? null : v - 1);
            if (slot.inbound) {
                inboundConnections--;
                if (slot.pending) {
                    pendingInbound--;
                }
            }
        }
    }

    /** The handshake of an inbound connection is done: it no longer counts as pending. */
    private void handshakeFinished(io.netty.channel.Channel nettyChannel) {
        ConnectionSlot slot = nettyChannel.attr(SLOT).get();
        if (slot == null) {
            return;
        }
        synchronized (admissionLock) {
            if (slot.pending) {
                slot.pending = false;
                pendingInbound--;
            }
        }
    }

    /** Whether one more connection to this address would be admitted right now (checked before dialling). */
    private boolean mayDial(InetAddress address) {
        if (address == null || isBanned(address)) {
            return false;
        }
        boolean configured = config.isConfiguredPeer(address);
        if (!config.isPermissionless() && !configured) {
            return false;
        }
        if (!configured && !config.isAllowPrivateAddresses() && !NetUtils.isPublicAddress(address)) {
            return false;
        }
        synchronized (admissionLock) {
            return configured
                    || (connectionsPerIp.getOrDefault(address, 0) < config.getMaxConnectionsPerIp()
                    && connectionsPerSubnet.getOrDefault(NetUtils.subnetKey(address), 0) < config.getMaxConnectionsPerSubnet());
        }
    }

    /** Number of TCP connections that currently hold a slot (handshake done or not). */
    public int getTotalConnections() {
        synchronized (admissionLock) {
            return totalConnections;
        }
    }

    public int getInboundConnections() {
        synchronized (admissionLock) {
            return inboundConnections;
        }
    }

    public int getPendingInboundConnections() {
        synchronized (admissionLock) {
            return pendingInbound;
        }
    }

    /**
     * The handshake showed that the other end is this very node (it dialled one of its own addresses, e.g.
     * because another node announced it). The address is not dialled again.
     */
    public void onSelfConnection(InetSocketAddress remote, boolean outbound) {
        if (outbound && remote != null) {
            selfAddresses.add(remote);
        }
    }

    /**
     * Drop every connection that is not with a configured peer. Called when a network is closed at run time
     * ({@link P2pConfig#setPermissionless} false).
     */
    public void closeUnconfiguredPeers() {
        for (Channel ch : new ArrayList<>(channels.values())) {
            if (ch.getInetAddress() != null && !config.isConfiguredPeer(ch.getInetAddress())) {
                ch.closeWithoutBan();
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
        try {
            doConnectLoop();
        } catch (Throwable t) {
            // an exception that escapes would silently cancel the periodic task
            log.warn("connect loop failed: {}", t.toString());
        }
    }

    private void doConnectLoop() {
        if (activePeers.size() >= config.getMaxConnections()) {
            return;
        }

        // Nodes the operator listed to stay connected to ("active nodes") are dialled whenever they are missing,
        // on top of the minimum number of connections. In a closed network they and the seeds are all there is.
        List<Node> candidates = new ArrayList<>();
        int wanted = 0;
        for (InetSocketAddress active : config.getActiveNodes()) {
            if (active.getAddress() != null && !hasActiveConnectionTo(active)) {
                candidates.add(new Node(null, active));
                wanted++;
            }
        }

        int desiredConnections = Math.max(0, config.getMinConnections() - activePeers.size());
        if (desiredConnections > 0) {
            List<Node> connectableNodes = new ArrayList<>();
            if (config.isPermissionless()) {
                connectableNodes.addAll(nodeManager.getConnectableNodes());
            }
            // The seeds are always candidates: a node whose discovered neighbours all went away must be able to
            // find its way back, and in a closed network there are no discovered neighbours at all.
            try {
                connectableNodes.addAll(nodeManager.getBootNodes());
            } catch (Throwable ignore) {
                // ignore if not available
            }
            Collections.shuffle(connectableNodes);
            candidates.addAll(connectableNodes);
        }
        wanted += desiredConnections;
        if (wanted <= 0) {
            return;
        }
        log.debug("Pool before-connect: active={}, min={}, wanted={}", activePeers.size(), config.getMinConnections(), wanted);

        // Get home node's port to avoid self-connection
        int homePort = config.getPort();

        int connectCount = 0;
        for (Node node : candidates) {
            if (connectCount >= wanted) {
                break;
            }

            InetSocketAddress address = node.getPreferInetSocketAddress();
            if (address == null || address.getAddress() == null) {
                continue;
            }

            // Skip self-connection attempts (comparing port since in local testing all nodes use 127.0.0.1)
            if (address.getPort() == homePort &&
                (address.getAddress().isLoopbackAddress() || address.getAddress().isAnyLocalAddress())) {
                log.debug("Skipping self-connection to {}", address);
                continue;
            }
            if (selfAddresses.contains(address)) {
                continue;
            }

            // Skip if already connected to this node. The discovery layer names a node by the hex form of its
            // address, the handshake by the Base58 form: compare like with like.
            if (node.getId() != null && connectedNodeIds.containsKey(toPeerId(node.getId()))) {
                log.debug("Skipping connection to {} - already connected to Node ID {}", address, node.getId());
                continue;
            }

            if (!mayDial(address.getAddress())) {
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

            log.debug("Attempting to connect to {}", address);
            connectAsync(node, false);
            connectCount++;
        }

    }

    /**
     * The handshake form (Base58Check of the 20-byte address) of a node id given in the discovery form
     * ("0x" + hex). Anything else is returned unchanged.
     */
    static String toPeerId(String nodeId) {
        if (!Node.isValidId(nodeId)) {
            return nodeId;
        }
        try {
            return Base58.encodeCheck(org.apache.tuweni.bytes.Bytes.fromHexString(nodeId));
        } catch (RuntimeException e) {
            return nodeId;
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

        // A node that dialled us is connected from an ephemeral port; what identifies it is the address it
        // listens on, which it announced in the handshake. (This used to be guessed: on the loopback interface
        // any connection from the same IP counted as "this node", so a node on a single machine never connected
        // to a second neighbour.)
        for (Channel channel : connectedNodeIds.values()) {
            if (!isNettyChannelOpen(channel)) {
                continue;
            }
            InetSocketAddress remoteAddr = channel.getRemoteAddress();
            if (targetAddress.equals(remoteAddr) || targetAddress.equals(channel.getListenAddress())) {
                log.debug("hasActiveConnectionTo({}) = TRUE (connected node {})", targetAddress, channel.getNodeId());
                return true;
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
            log.debug("Max connection limit reached. Disconnecting a random peer without penalty: {}", peerToDisconnect.getRemoteAddress());
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

        if (address == null || address.getAddress() == null || peerClient == null) {
            return null;
        }
        recentConnections.put(address, System.currentTimeMillis());
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

                    log.debug("Duplicate connection to NodeId {}. Deterministic resolution: localId={}, remoteId={}, preferOutbound={}",
                             nodeId, localNodeId.substring(0, 8) + "...", nodeId.substring(0, 8) + "...", preferOutbound);
                    log.debug("  Existing: {} (outbound={}), New: {} (outbound={})",
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
                            log.debug("  Both connections are OUTBOUND (our preferred) - keeping NEW (guaranteed alive)");
                            channelToKeep = channel;
                            channelToClose = existingChannel;
                        } else {
                            // BUG-P2P-004 FIX: Both inbound (NOT our preferred direction)
                            // Keep NEW connection (just completed handshake, guaranteed alive)
                            log.debug("  Both connections are INBOUND but we prefer OUTBOUND - keeping NEW (guaranteed alive)");
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
                            log.debug("  Both connections are INBOUND (our preferred) - keeping NEW (guaranteed alive)");
                            channelToKeep = channel;
                            channelToClose = existingChannel;
                        } else {
                            // BUG-P2P-004 FIX: Both outbound (NOT our preferred direction)
                            // Keep NEW connection (just completed handshake, guaranteed alive)
                            log.debug("  Both connections are OUTBOUND but we prefer INBOUND - keeping NEW (guaranteed alive)");
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
                    log.debug("  → Closing NEW connection, keeping existing");
                    shouldClose[0] = true;
                    result[0] = existingChannel;
                    return existingChannel;
                } else {
                    log.debug("  → Closing EXISTING connection, keeping new");
                    cleanupStaleChannel(existingChannel);
                    result[0] = channel;
                    return channel;
                }
            } else {
                // Existing channel is stale, replace it
                log.debug("Replacing stale connection for NodeId {}. Old: {}, New: {}",
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
    private boolean isNettyChannelOpen(Channel channel) {
        return channel != null && channel.getCtx() != null && channel.getCtx().channel() != null
                && channel.getCtx().channel().isActive();
    }

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

            log.debug("New channel connected: {} (NodeId: {}). Total channels: {}, Unique peers: {}",
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
    public void markHandshakeSuccess(ChannelHandlerContext ctx, HandshakeMessage handshake, boolean isOutbound) {
        InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
        Channel ch = register(remote, ctx, handshake.getPeerId(), isOutbound, handshake);
        if (ch == null) {
            throw new IllegalStateException("connection could not be registered");
        }
    }

    public void markHandshakeSuccess(java.net.InetSocketAddress remote, ChannelHandlerContext ctx, String nodeId, boolean isOutbound) {
        register(remote, ctx, nodeId, isOutbound, null);
    }

    private Channel register(java.net.InetSocketAddress remote, ChannelHandlerContext ctx, String nodeId, boolean isOutbound,
            HandshakeMessage handshake) {
        try {
            Channel ch = new Channel(this);
            ch.setP2pConfig(config);
            ch.setChannelHandlerContext(ctx);
            if (handshake != null && remote != null) {
                ch.setPeer(handshake.getPeer(remote.getAddress().getHostAddress()));
                if (remote.getAddress() != null && handshake.getPort() > 0 && handshake.getPort() <= 65535) {
                    ch.setListenAddress(new InetSocketAddress(remote.getAddress(), handshake.getPort()));
                }
            }
            handshakeFinished(ctx.channel());
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
            return ch;
        } catch (Exception e) {
            log.warn("Failed to mark handshake success for {}: {}", remote, e.getMessage());
            return null;
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

            log.debug("Channel disconnected: {}. Total channels: {}", channel.getRemoteAddress(), channels.size());
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
