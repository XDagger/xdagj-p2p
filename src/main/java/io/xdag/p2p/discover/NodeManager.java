package io.xdag.p2p.discover;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.kad.KadService;
import java.util.List;

public class NodeManager {

  private final P2pConfig p2pConfig;
  private DiscoverService discoverService;
  private DiscoverServer discoverServer;

  public NodeManager(P2pConfig p2pConfig) {
    this.p2pConfig = p2pConfig;
  }

  public void init() {
    discoverService = new KadService(p2pConfig);
    discoverService.init();
    if (p2pConfig.isDiscoverEnable()) {
      discoverServer = new DiscoverServer(p2pConfig);
      discoverServer.init(discoverService);
    }
  }

  public void close() {
    if (discoverService != null) {
      discoverService.close();
    }
    if (discoverServer != null) {
      discoverServer.close();
    }
  }

  public List<Node> getConnectableNodes() {
    return discoverService.getConnectableNodes();
  }

  public Node getHomeNode() {
    return discoverService.getPublicHomeNode();
  }

  public List<Node> getTableNodes() {
    return discoverService.getTableNodes();
  }

  public List<Node> getAllNodes() {
    return discoverService.getAllNodes();
  }
}
