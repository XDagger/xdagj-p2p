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
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.StringUtils;

@Getter
@Slf4j(topic = "net")
public class KadService implements DiscoverService {
    private static final int MAX_NODES = 2000;
    private static final int NODES_TRIM_THRESHOLD = 3000;

    @Getter
    @Setter
    private static long pingTimeout = 30_000;
    private final List<Node> bootNodes = new ArrayList<>();
    private volatile boolean inited = false;
    private final Map<InetSocketAddress, NodeHandler> nodeHandlerMap = new ConcurrentHashMap<>();
    private Consumer<UdpEvent> messageSender;
    private NodeTable table;
    private Node homeNode;
    private ScheduledExecutorService pongTimer;
    private DiscoverTask discoverTask;
    private final P2pConfig p2pConfig;
    private ReputationManager reputationManager;

    public KadService(P2pConfig p2pConfig) {
        this.p2pConfig = p2pConfig;
    }

    public void init() {
        for (InetSocketAddress address : p2pConfig.getSeedNodes()) {
            Node seed = new Node(null, address);
            seed.setNetworkId(p2pConfig.getNetworkId());
            seed.setNetworkVersion(p2pConfig.getNetworkVersion());
            bootNodes.add(seed);
        }
        for (InetSocketAddress address : p2pConfig.getActiveNodes()) {
            Node active = new Node(null, address);
            active.setNetworkId(p2pConfig.getNetworkId());
            active.setNetworkVersion(p2pConfig.getNetworkVersion());
            bootNodes.add(active);
        }
        this.pongTimer =
                Executors.newSingleThreadScheduledExecutor(
                        BasicThreadFactory.builder().namingPattern("pongTimer").build());
        this.homeNode =
                new Node(
                        null,
                        p2pConfig.getIpV4(),
                        p2pConfig.getIpV6(),
                        p2pConfig.getPort());
        
        // Use public key as node ID for better security and identity verification
        // nodeKey MUST be configured before initialization
        if (p2pConfig.getNodeKey() == null) {
            throw new IllegalStateException(
                "NodeKey is not configured! Please set P2pConfig.nodeKey before calling init(). " +
                "Node ID is derived from the public key to ensure node identity verification and " +
                "prevent Sybil attacks. Generate a key pair using ECKeyPair.generate() or load " +
                "from a persistent key file."
            );
        }
        
        // Derive node ID from XDAG address (20 bytes, 160 bits)
        // This is the standard Kademlia node ID length (same as BitTorrent DHT)
        this.homeNode.setId(
            p2pConfig.getNodeKey()
                     .toAddress()              // Generate XDAG address (20 bytes)
                     .toHexString()            // Convert to hex string (0x + 40 chars)
        );
        log.info("Node ID (XDAG address): {}", this.homeNode.getId());
        
        this.homeNode.setNetworkId(p2pConfig.getNetworkId());
        this.homeNode.setNetworkVersion(p2pConfig.getNetworkVersion());
        this.table = new NodeTable(homeNode);

        // Initialize reputation manager
        String reputationDir = p2pConfig.getDataDir() != null
            ? p2pConfig.getDataDir() + "/reputation"
            : "data/reputation";
        this.reputationManager = new ReputationManager(reputationDir);
        log.info("ReputationManager initialized with data directory: {}", reputationDir);

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

    public List<Node> getConnectableNodes() {
        List<Node> nodes = new ArrayList<>();
        // 1) Prefer discovered nodes
        for (NodeHandler nh : nodeHandlerMap.values()) {
            Node n = nh.getNode();
            if (n.getPreferInetSocketAddress() != null) {
                nodes.add(n);
            }
        }
        // 2) Fallback to boot nodes (direct TCP connect to seeds/active)
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
        log.debug(
                "KadService channelActivated called, inited: {}, bootNodes size: {}",
                inited,
                bootNodes.size());
        if (!inited) {
            inited = true;

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
        log.debug("KadService.handleEvent type={} from {}", m.getCode(), sender);

        Node n = new Node(null, sender);

        switch (m.getCode()) {
            case KAD_PING:
                KadPingMessage kadPingMessage = (KadPingMessage) m;
                Node fromNode = kadPingMessage.getFrom();
                if (fromNode != null && fromNode.getId() != null) {
                    n.setId(fromNode.getId());
                    n.updateHostV4(fromNode.getHostV4());
                    n.updateHostV6(fromNode.getHostV6());
                }
                NodeHandler nodeHandler = getNodeHandler(n);
                nodeHandler.getNode().touch();
                nodeHandler.handlePing(kadPingMessage);
                break;
            case KAD_PONG:
                KadPongMessage kadPongMessage = (KadPongMessage) m;
                Node pongFromNode = kadPongMessage.getFrom();
                if (pongFromNode != null && pongFromNode.getId() != null) {
                    n.setId(pongFromNode.getId());
                    n.updateHostV4(pongFromNode.getHostV4());
                    n.updateHostV6(pongFromNode.getHostV6());
                }
                NodeHandler pongHandler = getNodeHandler(n);
                pongHandler.getNode().touch();
                pongHandler.handlePong(kadPongMessage);
                break;
            case KAD_FIND_NODE:
                KadFindNodeMessage findMessage = (KadFindNodeMessage) m;
                Node findFromNode = findMessage.getFrom();
                if (findFromNode != null && findFromNode.getId() != null) {
                    n.setId(findFromNode.getId());
                    n.updateHostV4(findFromNode.getHostV4());
                    n.updateHostV6(findFromNode.getHostV6());
                }
                NodeHandler findHandler = getNodeHandler(n);
                findHandler.getNode().touch();
                findHandler.handleFindNode(findMessage);
                break;
            case KAD_NEIGHBORS:
                KadNeighborsMessage kadNeighborsMessage = (KadNeighborsMessage) m;
                NodeHandler neighborHandler = getNodeHandler(n);
                neighborHandler.getNode().touch();
                neighborHandler.handleNeighbours(kadNeighborsMessage);
                break;
            default:
                break;
        }
    }

    public NodeHandler getNodeHandler(Node n) {
        InetSocketAddress inet4 = n.getInetSocketAddressV4();
        InetSocketAddress inet6 = n.getInetSocketAddressV6();
        NodeHandler existing = null;
        if (inet4 != null) {
            existing = nodeHandlerMap.get(inet4);
        }
        if (existing == null && inet6 != null) {
            existing = nodeHandlerMap.get(inet6);
        }

        if (existing == null) {
            trimTable();
            NodeHandler created = new NodeHandler(n, this);
            InetSocketAddress prefer = created.getNode().getPreferInetSocketAddress();
            if (prefer != null) {
                nodeHandlerMap.put(prefer, created);
            }
            return created;
        }

        // keep Node reference updated
        Node tracked = existing.getNode();
        InetSocketAddress oldPrefer = tracked.getPreferInetSocketAddress();
        if (StringUtils.isNotEmpty(n.getHostV4())) {
            tracked.setHostV4(n.getHostV4());
        }
        if (StringUtils.isNotEmpty(n.getHostV6())) {
            tracked.setHostV6(n.getHostV6());
        }
        InetSocketAddress newPrefer = tracked.getPreferInetSocketAddress();
        if (newPrefer != null && !newPrefer.equals(oldPrefer)) {
            if (oldPrefer != null) {
                nodeHandlerMap.remove(oldPrefer, existing);
            }
            nodeHandlerMap.put(newPrefer, existing);
        }
        return existing;
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
                    .filter(entry -> !entry.getValue().getNode().isConnectible(p2pConfig.getNetworkId()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            staleKeys.forEach(nodeHandlerMap::remove);
        }

        if (nodeHandlerMap.size() > NODES_TRIM_THRESHOLD) {
            List<Map.Entry<InetSocketAddress, NodeHandler>> sorted = new ArrayList<>(nodeHandlerMap.entrySet());
            sorted.sort(Comparator.comparingLong(entry -> entry.getValue().getNode().getUpdateTime()));
            for (Map.Entry<InetSocketAddress, NodeHandler> entry : sorted) {
                nodeHandlerMap.remove(entry.getKey(), entry.getValue());
                if (nodeHandlerMap.size() <= MAX_NODES) {
                    break;
                }
            }
        }
    }
}
