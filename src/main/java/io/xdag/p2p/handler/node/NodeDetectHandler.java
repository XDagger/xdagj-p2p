package io.xdag.p2p.handler.node;

import static io.xdag.p2p.config.P2pConstant.MAX_NODES;
import static io.xdag.p2p.config.P2pConstant.MAX_NODE_FAST_DETECT;
import static io.xdag.p2p.config.P2pConstant.MAX_NODE_NORMAL_DETECT;
import static io.xdag.p2p.config.P2pConstant.MAX_NODE_SLOW_DETECT;
import static io.xdag.p2p.config.P2pConstant.MIN_NODES;
import static io.xdag.p2p.config.P2pConstant.NODE_DETECT_MIN_THRESHOLD;
import static io.xdag.p2p.config.P2pConstant.NODE_DETECT_THRESHOLD;
import static io.xdag.p2p.config.P2pConstant.NODE_DETECT_TIMEOUT;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.channel.PeerClient;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.NodeManager;
import io.xdag.p2p.message.node.Message;
import io.xdag.p2p.message.node.StatusMessage;
import io.xdag.p2p.stats.NodeStats;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

@Slf4j(topic = "net")
public class NodeDetectHandler implements MessageHandler {

  private final P2pConfig p2pConfig;
  private final ChannelManager channelManager;
  private final NodeManager nodeManager;
  private PeerClient peerClient;

  private final Map<InetSocketAddress, NodeStats> nodeStatMap = new ConcurrentHashMap<>();

  public NodeDetectHandler(
      P2pConfig p2pConfig, ChannelManager channelManager, NodeManager nodeManager) {
    this.p2pConfig = p2pConfig;
    this.channelManager = channelManager;
    this.nodeManager = nodeManager;
  }

  @Getter
  private static final Cache<InetAddress, Long> badNodesCache =
      CacheBuilder.newBuilder().maximumSize(5000).expireAfterWrite(1, TimeUnit.HOURS).build();

  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          new BasicThreadFactory.Builder().namingPattern("node-detect-handler").build());

  public void init(PeerClient peerClient) {
    if (!p2pConfig.isNodeDetectEnable()) {
      return;
    }
    this.peerClient = peerClient;
    executor.scheduleWithFixedDelay(
        () -> {
          try {
            work();
          } catch (Exception t) {
            log.warn("Exception in node detect worker, {}", t.getMessage());
          }
        },
        1,
        5,
        TimeUnit.SECONDS);
  }

  public void close() {
    executor.shutdown();
  }

  public void work() {
    trimNodeMap();
    if (nodeStatMap.size() < MIN_NODES) {
      loadNodes();
    }

    List<NodeStats> nodeStats = getSortedNodeStats();
    if (nodeStats.isEmpty()) {
      return;
    }

    NodeStats nodeStat = nodeStats.getFirst();
    if (nodeStat.getLastDetectTime() > System.currentTimeMillis() - NODE_DETECT_MIN_THRESHOLD) {
      return;
    }

    int n = MAX_NODE_NORMAL_DETECT;
    if (nodeStat.getLastDetectTime() > System.currentTimeMillis() - NODE_DETECT_THRESHOLD) {
      n = MAX_NODE_SLOW_DETECT;
    }

    n = Math.min(n, nodeStats.size());

    for (int i = 0; i < n; i++) {
      detect(nodeStats.get(i));
    }
  }

  public void trimNodeMap() {
    long now = System.currentTimeMillis();
    nodeStatMap.forEach(
        (k, v) -> {
          if (!v.finishDetect() && v.getLastDetectTime() < now - NODE_DETECT_TIMEOUT) {
            nodeStatMap.remove(k);
            badNodesCache.put(k.getAddress(), System.currentTimeMillis());
          }
        });
  }

  private void loadNodes() {
    int size = nodeStatMap.size();
    int count = 0;
    List<Node> nodes = nodeManager.getConnectableNodes();
    for (Node node : nodes) {
      InetSocketAddress socketAddress = node.getPreferInetSocketAddress();
      if (socketAddress != null
          && !nodeStatMap.containsKey(socketAddress)
          && badNodesCache.getIfPresent(socketAddress.getAddress()) == null) {
        NodeStats nodeStats = new NodeStats(node);
        nodeStatMap.put(socketAddress, nodeStats);
        detect(nodeStats);
        count++;
        if (count >= MAX_NODE_FAST_DETECT || count + size >= MAX_NODES) {
          break;
        }
      }
    }
  }

  private void detect(NodeStats stat) {
    try {
      stat.setTotalCount(stat.getTotalCount() + 1);
      setLastDetectTime(stat);
      peerClient.connectAsync(stat.getNode(), true);
    } catch (Exception e) {
      log.warn(
          "Detect node {} failed, {}", stat.getNode().getPreferInetSocketAddress(), e.getMessage());
      nodeStatMap.remove(stat.getSocketAddress());
    }
  }

  public synchronized void onMessage(Channel channel, Message message) {
    StatusMessage statusMessage = (StatusMessage) message;

    if (!channel.isActive()) {
      channel.setDiscoveryMode(true);
      channel.send(new StatusMessage(p2pConfig, channelManager));
      channel.getCtx().close();
      return;
    }

    InetSocketAddress socketAddress = channel.getInetSocketAddress();
    NodeStats nodeStats = nodeStatMap.get(socketAddress);
    if (nodeStats == null) {
      return;
    }

    long cost = System.currentTimeMillis() - nodeStats.getLastDetectTime();
    if (cost > NODE_DETECT_TIMEOUT || statusMessage.getRemainConnections() == 0) {
      badNodesCache.put(socketAddress.getAddress(), cost);
      nodeStatMap.remove(socketAddress);
    }

    nodeStats.setLastSuccessDetectTime(nodeStats.getLastDetectTime());
    setStatusMessage(nodeStats, statusMessage);

    channel.getCtx().close();
  }

  public void notifyDisconnect(Channel channel) {

    if (!channel.isActive()) {
      return;
    }

    InetSocketAddress socketAddress = channel.getInetSocketAddress();
    if (socketAddress == null) {
      return;
    }

    NodeStats nodeStats = nodeStatMap.get(socketAddress);
    if (nodeStats == null) {
      return;
    }

    if (nodeStats.getLastDetectTime() != nodeStats.getLastSuccessDetectTime()) {
      badNodesCache.put(socketAddress.getAddress(), System.currentTimeMillis());
      nodeStatMap.remove(socketAddress);
    }
  }

  private synchronized List<NodeStats> getSortedNodeStats() {
    List<NodeStats> nodeStats = new ArrayList<>(nodeStatMap.values());
    nodeStats.sort(Comparator.comparingLong(NodeStats::getLastDetectTime));
    return nodeStats;
  }

  private synchronized void setLastDetectTime(NodeStats nodeStats) {
    nodeStats.setLastDetectTime(System.currentTimeMillis());
  }

  private synchronized void setStatusMessage(NodeStats nodeStats, StatusMessage message) {
    nodeStats.setStatusMessage(message);
  }

  public synchronized List<Node> getConnectableNodes() {
    List<NodeStats> stats = new ArrayList<>();
    List<Node> nodes = new ArrayList<>();
    nodeStatMap
        .values()
        .forEach(
            stat -> {
              if (stat.getStatusMessage() != null) {
                stats.add(stat);
              }
            });

    if (stats.isEmpty()) {
      return nodes;
    }

    stats.sort(Comparator.comparingInt(o -> -o.getStatusMessage().getRemainConnections()));
    stats.forEach(stat -> nodes.add(stat.getNode()));
    return nodes;
  }

  @Override
  public void onConnect(Channel channel) {
    // No action needed for node detect handler on connect
  }

  @Override
  public void onDisconnect(Channel channel) {
    // No action needed for node detect handler on disconnect
  }
}
