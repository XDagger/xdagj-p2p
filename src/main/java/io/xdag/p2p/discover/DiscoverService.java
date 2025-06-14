package io.xdag.p2p.discover;

import io.xdag.p2p.handler.discover.EventHandler;
import io.xdag.p2p.handler.discover.UdpEvent;
import java.util.List;

public interface DiscoverService extends EventHandler {

  void init();

  void close();

  List<Node> getConnectableNodes();

  List<Node> getTableNodes();

  List<Node> getAllNodes();

  Node getPublicHomeNode();

  void channelActivated();

  void handleEvent(UdpEvent event);
}
