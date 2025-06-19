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
import io.xdag.p2p.message.discover.Message;
import io.xdag.p2p.message.discover.kad.FindNodeMessage;
import io.xdag.p2p.message.discover.kad.NeighborsMessage;
import io.xdag.p2p.message.discover.kad.PingMessage;
import io.xdag.p2p.message.discover.kad.PongMessage;
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

@Getter
@Slf4j(topic = "net")
public class KadService implements DiscoverService {
  private static final int MAX_NODES = 2000;
  private static final int NODES_TRIM_THRESHOLD = 3000;

  @Getter @Setter private static long pingTimeout = 15_000;
  private final List<Node> bootNodes = new ArrayList<>();
  private volatile boolean inited = false;
  private final Map<InetSocketAddress, NodeHandler> nodeHandlerMap = new ConcurrentHashMap<>();
  private Consumer<UdpEvent> messageSender;
  private NodeTable table;
  private Node homeNode;
  private ScheduledExecutorService pongTimer;
  private DiscoverTask discoverTask;
  private final P2pConfig p2pConfig;

  public KadService(P2pConfig p2pConfig) {
    this.p2pConfig = p2pConfig;
  }

  public void init() {
    for (InetSocketAddress address : p2pConfig.getSeedNodes()) {
      bootNodes.add(new Node(p2pConfig, address));
    }
    for (InetSocketAddress address : p2pConfig.getActiveNodes()) {
      bootNodes.add(new Node(p2pConfig, address));
    }
    this.pongTimer =
        Executors.newSingleThreadScheduledExecutor(
            new BasicThreadFactory.Builder().namingPattern("pongTimer").build());
    this.homeNode =
        new Node(
            p2pConfig,
            p2pConfig.getNodeID(),
            p2pConfig.getIp(),
            p2pConfig.getIpv6(),
            p2pConfig.getPort());
    this.table = new NodeTable(homeNode);

    if (p2pConfig.isDiscoverEnable()) {
      discoverTask = new DiscoverTask(this);
      discoverTask.init();
    }
  }

  public void close() {
    try {
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
    return getAllNodes().stream()
        .filter(node -> node.isConnectible(p2pConfig.getNetworkId()))
        .filter(node -> node.getPreferInetSocketAddress() != null)
        .collect(Collectors.toList());
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

    // Create a simple node from UDP sender address
    Node n = new Node(p2pConfig, sender);

    switch (m.getType()) {
      case KAD_PING:
        PingMessage pingMessage = (PingMessage) m;
        Node fromNode = pingMessage.getFrom();
        if (fromNode != null && fromNode.getId() != null) {
          n.setId(fromNode.getId());
          // Update address information from message if available
          n.updateHostV4(fromNode.getHostV4());
          n.updateHostV6(fromNode.getHostV6());
        }
        NodeHandler nodeHandler = getNodeHandler(n);
        nodeHandler.getNode().touch();
        nodeHandler.handlePing(pingMessage);
        break;
      case KAD_PONG:
        PongMessage pongMessage = (PongMessage) m;
        Node pongFromNode = pongMessage.getFrom();
        if (pongFromNode != null && pongFromNode.getId() != null) {
          n.setId(pongFromNode.getId());
          n.updateHostV4(pongFromNode.getHostV4());
          n.updateHostV6(pongFromNode.getHostV6());
        }
        NodeHandler pongHandler = getNodeHandler(n);
        pongHandler.getNode().touch();
        pongHandler.handlePong(pongMessage);
        break;
      case KAD_FIND_NODE:
        FindNodeMessage findMessage = (FindNodeMessage) m;
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
        NeighborsMessage neighborsMessage = (NeighborsMessage) m;
        Node neighborFromNode = neighborsMessage.getFrom();
        if (neighborFromNode != null && neighborFromNode.getId() != null) {
          n.setId(neighborFromNode.getId());
          n.updateHostV4(neighborFromNode.getHostV4());
          n.updateHostV6(neighborFromNode.getHostV6());
        }
        NodeHandler neighborHandler = getNodeHandler(n);
        neighborHandler.getNode().touch();
        neighborHandler.handleNeighbours(neighborsMessage, sender);
        break;
      default:
        break;
    }
  }

  public NodeHandler getNodeHandler(Node n) {
    NodeHandler ret = null;
    InetSocketAddress inet4 = n.getInetSocketAddressV4();
    InetSocketAddress inet6 = n.getInetSocketAddressV6();
    if (inet4 != null) {
      ret = nodeHandlerMap.get(inet4);
    }
    if (ret == null && inet6 != null) {
      ret = nodeHandlerMap.get(inet6);
    }

    if (ret == null) {
      trimTable();
      ret = new NodeHandler(p2pConfig, n, this);
      if (n.getPreferInetSocketAddress() != null) {
        nodeHandlerMap.put(n.getPreferInetSocketAddress(), ret);
      }
    } else {
      ret.getNode().updateHostV4(n.getHostV4());
      ret.getNode().updateHostV6(n.getHostV6());
    }
    return ret;
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
      nodeHandlerMap
          .values()
          .forEach(
              handler -> {
                if (!handler.getNode().isConnectible(p2pConfig.getNetworkId())) {
                  nodeHandlerMap.values().remove(handler);
                }
              });
    }
    if (nodeHandlerMap.size() > NODES_TRIM_THRESHOLD) {
      List<NodeHandler> sorted = new ArrayList<>(nodeHandlerMap.values());
      sorted.sort(Comparator.comparingLong(o -> o.getNode().getUpdateTime()));
      for (NodeHandler handler : sorted) {
        nodeHandlerMap.values().remove(handler);
        if (nodeHandlerMap.size() <= MAX_NODES) {
          break;
        }
      }
    }
  }
}
