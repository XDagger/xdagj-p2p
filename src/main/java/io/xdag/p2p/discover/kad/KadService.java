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
package io.xdag.p2p.discover.kad;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.DiscoverService;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.kad.table.NodeTable;
import io.xdag.p2p.handler.discover.UdpEvent;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.discover.KadFindNodeMessage;
import io.xdag.p2p.message.discover.KadNeighborsMessage;
import io.xdag.p2p.message.discover.KadPingMessage;
import io.xdag.p2p.message.discover.KadPongMessage;
import io.xdag.p2p.utils.NetUtils;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

/**
 * Kademlia node discovery over UDP.
 *
 * <p>What a datagram proves and what it does not: the signature (see
 * {@link io.xdag.p2p.message.discover.KadPacket}) proves which key produced it, nothing else. The address it
 * came from is only believed after that endpoint has answered a ping of ours with a pong that echoes the hash
 * of that ping ("bonding"); until then a node is neither put in the table nor handed to anybody as a peer,
 * and its questions (FIND_NODE) are not answered - otherwise a forged source address would make this node
 * send lists of neighbours to whoever the attacker points it at. Addresses a node claims for itself inside a
 * message are ignored altogether: a node is reached where its datagrams come from. Addresses of third parties
 * (NEIGHBORS) are checked for form and policy, and then verified by a ping like any other.
 *
 * <p>Resources: datagrams are rate limited per source address; the number of nodes that are being verified at
 * a time, the number of nodes kept per address and per network, and the total are bounded.
 */
@Getter
@Slf4j(topic = "net")
public class KadService implements DiscoverService {
    private static final int MAX_NODES = 2000;
    private static final int NODES_TRIM_THRESHOLD = 3000;
    /** How long an answered ping keeps an endpoint verified. */
    static final long BOND_EXPIRATION_MS = 12L * 60 * 60 * 1000;
    /** A datagram whose timestamp is further from now than this is a replay or from a broken clock. */
    static final long MAX_CLOCK_SKEW_MS = 20_000;
    /** Most nodes that are being pinged for the first time at the same time. */
    static final int MAX_UNVERIFIED = 256;
    /** Most nodes remembered per address and per network (IPv4 /24, IPv6 /48); not applied to private ones. */
    static final int MAX_NODES_PER_IP = 8;
    static final int MAX_NODES_PER_SUBNET = 64;

    @Getter
    @Setter
    private static long pingTimeout = 30_000;
    private final List<Node> bootNodes = new ArrayList<>();
    private final AtomicBoolean inited = new AtomicBoolean(false);
    /** Every node this service talks to, by the endpoint it is reached at. */
    private final Map<InetSocketAddress, NodeHandler> nodeHandlerMap = new ConcurrentHashMap<>();
    private Consumer<UdpEvent> messageSender;
    private NodeTable table;
    private Node homeNode;
    private ScheduledExecutorService pongTimer;
    private DiscoverTask discoverTask;
    private final P2pConfig p2pConfig;
    private ReputationManager reputationManager;
    private final Cache<InetAddress, TokenBucket> inboundLimits =
            CacheBuilder.newBuilder().maximumSize(20_000).expireAfterAccess(2, TimeUnit.MINUTES).build();

    public KadService(P2pConfig p2pConfig) {
        this.p2pConfig = p2pConfig;
    }

    public void init() {
        for (InetSocketAddress address : p2pConfig.getSeedNodes()) {
            addBootNode(address, "seed");
        }
        for (InetSocketAddress address : p2pConfig.getActiveNodes()) {
            addBootNode(address, "active");
        }
        log.info("Total boot nodes: {}", bootNodes.size());
        this.pongTimer =
                Executors.newSingleThreadScheduledExecutor(
                        BasicThreadFactory.builder().namingPattern("pongTimer").daemon(true).build());
        this.homeNode =
                new Node(
                        null,
                        p2pConfig.getIpV4(),
                        p2pConfig.getIpV6(),
                        p2pConfig.getPort());

        // The node id is the address of the node key; nothing else identifies a node.
        if (p2pConfig.getNodeKey() == null) {
            throw new IllegalStateException(
                "NodeKey is not configured! Please set P2pConfig.nodeKey before calling init(). " +
                "Node ID is derived from the public key to ensure node identity verification and " +
                "prevent Sybil attacks. Generate a key pair using ECKeyPair.generate() or load " +
                "from a persistent key file."
            );
        }
        this.homeNode.setId(p2pConfig.getNodeKey().toAddress().toHexString());
        log.info("Node ID (XDAG address): {}", this.homeNode.getId());

        this.homeNode.setNetworkId(p2pConfig.getNetworkId());
        this.homeNode.setNetworkVersion(p2pConfig.getNetworkVersion());
        this.table = new NodeTable(homeNode, p2pConfig.isAllowPrivateAddresses());

        String reputationDir = p2pConfig.getDataDir() != null
            ? p2pConfig.getDataDir() + "/reputation"
            : "data/reputation";
        this.reputationManager = new ReputationManager(reputationDir);
        log.debug("ReputationManager initialized with data directory: {}", reputationDir);

        if (p2pConfig.isDiscoverEnable()) {
            discoverTask = new DiscoverTask(this);
            discoverTask.init();
        }

        // Ensure boot nodes are registered even if UDP binding is delayed
        try {
            channelActivated();
        } catch (Throwable t) {
            log.debug("channelActivated bootstrap failed (will be called again on UDP bind)", t);
        }
    }

    private void addBootNode(InetSocketAddress address, String kind) {
        if (address == null || address.getAddress() == null) {
            log.warn("Ignoring {} node without a resolved address: {}", kind, address);
            return;
        }
        Node node = new Node(null, address);
        node.setNetworkId(p2pConfig.getNetworkId());
        node.setNetworkVersion(p2pConfig.getNetworkVersion());
        bootNodes.add(node);
        log.info("Added {} node to boot nodes: {}", kind, address);
    }

    public void close() {
        try {
            if (reputationManager != null) {
                reputationManager.stop();
            }

            if (pongTimer != null) {
                pongTimer.shutdownNow();
            }

            if (discoverTask != null) {
                discoverTask.close();
            }
        } catch (Exception e) {
            log.error("Close nodeManagerTasksTimer or pongTimer failed", e);
            throw e;
        }
    }

    /**
     * Returns whether the KadService has been initialized.
     * @return true if channelActivated() has been successfully called
     */
    public boolean isInited() {
        return inited.get();
    }

    /**
     * Nodes that may be dialled: those that answered a ping of ours at the address we know them by. Never a
     * node that only claimed to exist. Falls back to the boot nodes when nothing has been verified yet.
     */
    public List<Node> getConnectableNodes() {
        List<Node> nodes = new ArrayList<>();
        for (NodeHandler nh : nodeHandlerMap.values()) {
            Node n = nh.getNode();
            if (nh.isVerified() && n.isConnectible(p2pConfig.getNetworkId()) && n.getPreferInetSocketAddress() != null) {
                nodes.add(n);
            }
        }
        if (nodes.isEmpty()) {
            nodes.addAll(bootNodes);
        }
        return nodes;
    }

    public List<Node> getTableNodes() {
        return table.getTableNodes();
    }

    public List<Node> getAllNodes() {
        List<Node> nodeList = new ArrayList<>();
        for (NodeHandler nodeHandler : nodeHandlerMap.values()) {
            nodeList.add(nodeHandler.getNode());
        }
        return nodeList;
    }

    @Override
    public void setMessageSender(Consumer<UdpEvent> messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public void channelActivated() {
        log.debug("KadService.channelActivated() called - inited: {}, bootNodes: {}", inited.get(), bootNodes.size());
        if (inited.compareAndSet(false, true)) {
            for (Node node : bootNodes) {
                log.debug("Creating NodeHandler for boot node: {}", node.getPreferInetSocketAddress());
                getNodeHandler(node);
            }
        }
    }

    @Override
    public void handleEvent(UdpEvent udpEvent) {
        Message m = udpEvent.getMessage();
        InetSocketAddress sender = udpEvent.getAddress();
        String signer = udpEvent.getNodeId();
        if (m == null || sender == null || sender.getAddress() == null || !Node.isValidId(signer)) {
            // an event without a verified sender (nothing that came through the packet decoder)
            return;
        }
        if (homeNode != null && signer.equals(homeNode.getId())) {
            return;
        }
        if (!p2pConfig.isPermissionless() && !p2pConfig.isConfiguredPeer(sender.getAddress())) {
            // a closed network: strangers are not answered, not even with a pong
            return;
        }
        if (!withinClock(m)) {
            log.debug("Dropping discovery message from {}: timestamp out of range", sender);
            return;
        }
        if (!inboundAllowed(sender.getAddress())) {
            log.debug("Dropping discovery message from {}: rate limit", sender);
            return;
        }
        log.trace("KadService.handleEvent type={} from {}", m.getCode(), sender);

        Node from = fromOf(m);
        NodeHandler handler = handlerFor(sender, signer, from);
        if (handler == null) {
            return;
        }
        handler.getNode().touch();
        switch (m.getCode().toByte()) {
            case 0x00 -> handler.handlePing((KadPingMessage) m, udpEvent.getHash());
            case 0x01 -> handler.handlePong((KadPongMessage) m);
            case 0x02 -> handler.handleFindNode((KadFindNodeMessage) m);
            case 0x03 -> handler.handleNeighbours((KadNeighborsMessage) m);
            default -> {
                // not a discovery message; the decoder does not produce these
            }
        }
    }

    private static Node fromOf(Message m) {
        if (m instanceof KadPingMessage ping) {
            return ping.getFrom();
        } else if (m instanceof KadPongMessage pong) {
            return pong.getFrom();
        } else if (m instanceof KadFindNodeMessage find) {
            return find.getFrom();
        } else if (m instanceof KadNeighborsMessage neighbors) {
            return neighbors.getFrom();
        }
        return null;
    }

    private static boolean withinClock(Message m) {
        long timestamp;
        if (m instanceof KadPingMessage ping) {
            timestamp = ping.getTimestamp();
        } else if (m instanceof KadPongMessage pong) {
            timestamp = pong.getTimestamp();
        } else if (m instanceof KadFindNodeMessage find) {
            timestamp = find.getTimestamp();
        } else if (m instanceof KadNeighborsMessage neighbors) {
            timestamp = neighbors.getTimestamp();
        } else {
            return false;
        }
        return Math.abs(System.currentTimeMillis() - timestamp) <= MAX_CLOCK_SKEW_MS;
    }

    private boolean inboundAllowed(InetAddress address) {
        try {
            TokenBucket bucket = inboundLimits.get(address, () -> new TokenBucket(
                    p2pConfig.getMaxDiscoveryBurst(), p2pConfig.getMaxDiscoveryPacketsPerSecond()));
            return bucket.tryAcquire(1);
        } catch (ExecutionException e) {
            return true;
        }
    }

    /**
     * The handler for the node that speaks from an endpoint. What the node says about its own address is not
     * used; its announced port only decides whether it is connectible (the port it listens on must be the port
     * it speaks from). A new key at a known endpoint is a new node: the old one is forgotten.
     */
    private NodeHandler handlerFor(InetSocketAddress sender, String signer, Node from) {
        NodeHandler existing = nodeHandlerMap.get(sender);
        if (existing != null && existing.getNode().getId() != null && !signer.equals(existing.getNode().getId())) {
            log.debug("Endpoint {} now speaks with key {} instead of {}", sender, signer, existing.getNode().getId());
            table.dropNode(existing.getNode());
            nodeHandlerMap.remove(sender, existing);
            existing = null;
        }
        if (existing == null) {
            Node n = new Node(signer, sender);
            n.setNetworkId(p2pConfig.getNetworkId());
            n.setNetworkVersion(p2pConfig.getNetworkVersion());
            existing = getNodeHandler(n);
            if (existing == null) {
                return null;
            }
        }
        Node tracked = existing.getNode();
        if (tracked.getId() == null) {
            // a boot node, known by address only until now
            tracked.setId(signer);
        }
        if (from != null) {
            tracked.setBindPort(from.getPort());
            tracked.setNetworkId(from.getNetworkId());
            tracked.setNetworkVersion(from.getNetworkVersion());
        }
        return existing;
    }

    /**
     * Whether a node learnt from somebody else may be contacted at all: a proper description, an address the
     * policy allows, and not this node.
     */
    boolean mayContact(Node n) {
        if (n == null || !n.isWellFormed() || !p2pConfig.isPermissionless()) {
            return false;
        }
        if (homeNode != null && n.getId() != null && n.getId().equals(homeNode.getId())) {
            return false;
        }
        InetSocketAddress address = n.getPreferInetSocketAddress();
        if (address == null || address.getAddress() == null) {
            return false;
        }
        if (address.getPort() == p2pConfig.getPort()
                && (address.getAddress().isLoopbackAddress() || address.getAddress().isAnyLocalAddress())) {
            return false;
        }
        return p2pConfig.isAllowPrivateAddresses() || NetUtils.isPublicAddress(address.getAddress());
    }

    /**
     * The handler for a node, created if there is none and there is room for it. Returns null if the node is
     * not accepted (no address, over a limit).
     */
    public NodeHandler getNodeHandler(Node n) {
        InetSocketAddress prefer = n.getPreferInetSocketAddress();
        if (prefer == null || prefer.getAddress() == null) {
            return null;
        }
        NodeHandler existing = nodeHandlerMap.get(prefer);
        if (existing != null) {
            return existing;
        }
        trimTable();
        if (!hasRoomFor(prefer.getAddress())) {
            log.debug("Not tracking {}: too many nodes from that address or network, or too many unverified", prefer);
            return null;
        }
        NodeHandler created = new NodeHandler(n, this);
        NodeHandler raced = nodeHandlerMap.putIfAbsent(prefer, created);
        if (raced != null) {
            return raced;
        }
        created.start();
        return created;
    }

    private boolean hasRoomFor(InetAddress address) {
        boolean limited = !(p2pConfig.isAllowPrivateAddresses() && !NetUtils.isPublicAddress(address));
        int unverified = 0;
        int sameIp = 0;
        int sameSubnet = 0;
        String subnet = NetUtils.subnetKey(address);
        for (Map.Entry<InetSocketAddress, NodeHandler> entry : nodeHandlerMap.entrySet()) {
            NodeHandler h = entry.getValue();
            if (!h.isVerified() && h.getState() != NodeHandler.State.DEAD) {
                unverified++;
            }
            if (limited) {
                InetAddress other = entry.getKey().getAddress();
                if (address.equals(other)) {
                    sameIp++;
                }
                if (subnet.equals(NetUtils.subnetKey(other))) {
                    sameSubnet++;
                }
            }
        }
        return unverified < MAX_UNVERIFIED && sameIp < MAX_NODES_PER_IP && sameSubnet < MAX_NODES_PER_SUBNET;
    }

    public Node getPublicHomeNode() {
        return homeNode;
    }

    public void sendOutbound(UdpEvent udpEvent) {
        if (p2pConfig.isDiscoverEnable() && messageSender != null) {
            messageSender.accept(udpEvent);
        }
    }

    private void trimTable() {
        if (nodeHandlerMap.size() > NODES_TRIM_THRESHOLD) {
            List<InetSocketAddress> staleKeys = nodeHandlerMap.entrySet().stream()
                    .filter(entry -> entry.getValue().getState() == NodeHandler.State.DEAD
                            || !entry.getValue().getNode().isConnectible(p2pConfig.getNetworkId()))
                    .map(Map.Entry::getKey)
                    .toList();
            staleKeys.forEach(nodeHandlerMap::remove);
        }

        if (nodeHandlerMap.size() > NODES_TRIM_THRESHOLD) {
            List<Map.Entry<InetSocketAddress, NodeHandler>> sorted = new ArrayList<>(nodeHandlerMap.entrySet());
            sorted.sort(Comparator.comparingLong(entry -> entry.getValue().getNode().getUpdateTime()));
            for (Map.Entry<InetSocketAddress, NodeHandler> entry : sorted) {
                nodeHandlerMap.remove(entry.getKey(), entry.getValue());
                table.dropNode(entry.getValue().getNode());
                if (nodeHandlerMap.size() <= MAX_NODES) {
                    break;
                }
            }
        }
    }

    /** Counts of handlers by state, for logs and tests. */
    public Map<NodeHandler.State, Integer> stateCounts() {
        Map<NodeHandler.State, Integer> counts = new HashMap<>();
        for (NodeHandler h : nodeHandlerMap.values()) {
            counts.merge(h.getState(), 1, Integer::sum);
        }
        return counts;
    }

    /** A token bucket: {@code rate} tokens per second, at most {@code burst} saved up. */
    static final class TokenBucket {
        private final double burst;
        private final double rate;
        private double tokens;
        private long last = System.nanoTime();

        TokenBucket(double burst, double rate) {
            this.burst = Math.max(1, burst);
            this.rate = Math.max(0, rate);
            this.tokens = this.burst;
        }

        synchronized boolean tryAcquire(double n) {
            long now = System.nanoTime();
            tokens = Math.min(burst, tokens + (now - last) / 1e9 * rate);
            last = now;
            if (tokens >= n) {
                tokens -= n;
                return true;
            }
            return false;
        }
    }
}
