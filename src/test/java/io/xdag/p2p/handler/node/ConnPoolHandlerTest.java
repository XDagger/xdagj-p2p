package io.xdag.p2p.handler.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.NodeManager;
import io.xdag.p2p.discover.dns.DnsManager;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for ConnPoolHandler class. These are true unit tests that test node selection logic
 * without time-based operations.
 */
@ExtendWith(MockitoExtension.class)
public class ConnPoolHandlerTest {

  private P2pConfig p2pConfig;
  private ConnPoolHandler connPoolHandler;

  @Mock private ChannelManager channelManager;
  @Mock private NodeManager nodeManager;
  @Mock private DnsManager dnsManager;

  private final String localIp = "127.0.0.1";

  @BeforeEach
  public void setUp() {
    p2pConfig = new P2pConfig();
    p2pConfig.setDiscoverEnable(false);
    p2pConfig.setPort(10000);
    p2pConfig.setIpV4(localIp);

    lenient().when(channelManager.getBannedNodes()).thenReturn(CacheBuilder.newBuilder().build());

    connPoolHandler = new ConnPoolHandler(p2pConfig, channelManager, nodeManager, dnsManager);
  }

  @Test
  public void testConnPoolHandlerCreation() {
    assertNotNull(connPoolHandler);
  }

  @Test
  public void testGetNodesChooseHomeNode() {
    Node homeNode = new Node(p2pConfig, new InetSocketAddress(localIp, p2pConfig.getPort()));
    Set<InetSocketAddress> inetInUse = new HashSet<>();
    inetInUse.add(homeNode.getInetSocketAddressV4());
    List<Node> connectableNodes = List.of(homeNode);

    lenient().when(nodeManager.getHomeNode()).thenReturn(homeNode);

    // Should not connect to home node if it's already in use
    List<Node> nodes = connPoolHandler.getNodes(new HashSet<>(), inetInUse, connectableNodes, 1);
    assertEquals(0, nodes.size());

    // Should connect to home node if not in use
    nodes = connPoolHandler.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes, 1);
    assertEquals(1, nodes.size());
  }

  @Test
  public void testGetNodesOrderByUpdateTimeDesc() {
    Node node1 = new Node(p2pConfig, new InetSocketAddress(localIp, 90));
    node1.setUpdateTime(System.currentTimeMillis());
    Node node2 = new Node(p2pConfig, new InetSocketAddress(localIp, 100));
    node2.setUpdateTime(System.currentTimeMillis() + 10);

    assertTrue(node1.getUpdateTime() < node2.getUpdateTime());

    List<Node> connectableNodes = List.of(node1, node2);

    List<Node> nodes =
        connPoolHandler.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes, 2);
    assertEquals(2, nodes.size());
    assertTrue(nodes.get(0).getUpdateTime() > nodes.get(1).getUpdateTime());

    List<Node> nodes2 =
        connPoolHandler.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes, 1);
    assertEquals(1, nodes2.size());
    assertEquals(node2, nodes2.getFirst());
  }

  @Test
  public void testGetNodesBanNodeLogic() {
    InetSocketAddress bannedAddress = new InetSocketAddress(localIp, 90);
    Cache<InetAddress, Long> bannedNodes = CacheBuilder.newBuilder().build();
    bannedNodes.put(
        bannedAddress.getAddress(), System.currentTimeMillis() + 10000); // Banned for 10s

    when(channelManager.getBannedNodes()).thenReturn(bannedNodes);

    Node node = new Node(p2pConfig, bannedAddress);
    List<Node> connectableNodes = List.of(node);

    List<Node> nodes =
        connPoolHandler.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes, 1);
    assertEquals(0, nodes.size());
  }

  @Test
  public void testGetNodesNodeInUse() {
    Node node = new Node(p2pConfig, new InetSocketAddress(localIp, 90));
    List<Node> connectableNodes = List.of(node);

    Set<String> nodesInUse = new HashSet<>();
    nodesInUse.add(node.getHexId());

    List<Node> nodes = connPoolHandler.getNodes(nodesInUse, new HashSet<>(), connectableNodes, 1);
    assertEquals(0, nodes.size());
  }

  @Test
  public void testGetNodesEmptyConnectableNodes() {
    List<Node> nodes =
        connPoolHandler.getNodes(new HashSet<>(), new HashSet<>(), new ArrayList<>(), 1);
    assertEquals(0, nodes.size());
  }

  @Test
  public void testGetNodesLimit() {
    List<Node> connectableNodes = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      connectableNodes.add(new Node(p2pConfig, new InetSocketAddress(localIp, 90 + i)));
    }

    List<Node> nodes =
        connPoolHandler.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes, 3);
    assertEquals(3, nodes.size());
  }
}
