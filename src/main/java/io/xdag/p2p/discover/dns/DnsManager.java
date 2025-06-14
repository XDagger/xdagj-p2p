package io.xdag.p2p.discover.dns;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.NodeManager;
import io.xdag.p2p.discover.dns.sync.Client;
import io.xdag.p2p.discover.dns.sync.RandomIterator;
import io.xdag.p2p.discover.dns.tree.Tree;
import io.xdag.p2p.discover.dns.update.PublishService;
import io.xdag.p2p.utils.NetUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Manager for DNS-based node discovery functionality. Handles both publishing and synchronizing DNS
 * tree records for peer discovery.
 */
@Slf4j(topic = "net")
public class DnsManager {

  private final P2pConfig p2pConfig;
  private final NodeManager nodeManager;

  /** Service for publishing DNS tree records */
  private PublishService publishService;

  /** Client for synchronizing DNS tree records */
  private Client syncClient;

  /** Iterator for random node selection */
  private RandomIterator randomIterator;

  /** Set of local IP addresses to filter out */
  private Set<String> localIpSet;

  public DnsManager(P2pConfig p2pConfig, NodeManager nodeManager) {
    this.p2pConfig = p2pConfig;
    this.nodeManager = nodeManager;
  }

  /**
   * Initialize the DNS manager and its components. Sets up publishing service, sync client, and
   * random iterator.
   */
  public void init() {
    publishService = new PublishService(p2pConfig, nodeManager);
    syncClient = new Client(p2pConfig);
    publishService.init();
    syncClient.init();
    randomIterator = syncClient.newIterator();
    localIpSet = NetUtils.getAllLocalAddress();
  }

  /** Close the DNS manager and cleanup resources. Shuts down all services and iterators. */
  public void close() {
    if (publishService != null) {
      publishService.close();
    }
    if (syncClient != null) {
      syncClient.close();
    }
    if (randomIterator != null) {
      randomIterator.close();
    }
  }

  /**
   * Get all DNS nodes from synchronized trees. Filters out local addresses and returns only
   * connectable nodes.
   *
   * @return list of connectable DNS nodes
   */
  public List<DnsNode> getDnsNodes() {
    Set<DnsNode> nodes = new HashSet<>();
    for (Map.Entry<String, Tree> entry : syncClient.getTrees().entrySet()) {
      Tree tree = entry.getValue();
      int v4Size = 0, v6Size = 0;
      List<DnsNode> dnsNodes = tree.getDnsNodes();
      List<DnsNode> ipv6Nodes = new ArrayList<>();
      for (DnsNode dnsNode : dnsNodes) {
        // log.debug("DnsNode:{}", dnsNode);
        if (dnsNode.getInetSocketAddressV4() != null) {
          v4Size += 1;
        }
        if (dnsNode.getInetSocketAddressV6() != null) {
          v6Size += 1;
          ipv6Nodes.add(dnsNode);
        }
      }
      List<DnsNode> connectAbleNodes =
          dnsNodes.stream()
              .filter(node -> node.getPreferInetSocketAddress() != null)
              .filter(
                  node ->
                      !localIpSet.contains(
                          node.getPreferInetSocketAddress().getAddress().getHostAddress()))
              .toList();
      log.debug(
          "Tree {} node size:{}, v4 node size:{}, v6 node size:{}, connectable size:{}",
          entry.getKey(),
          dnsNodes.size(),
          v4Size,
          v6Size,
          connectAbleNodes.size());
      nodes.addAll(connectAbleNodes);
    }
    return new ArrayList<>(nodes);
  }

  /**
   * Get a random node from the DNS trees.
   *
   * @return a randomly selected node
   */
  public Node getRandomNodes() {
    return randomIterator.next();
  }
}
