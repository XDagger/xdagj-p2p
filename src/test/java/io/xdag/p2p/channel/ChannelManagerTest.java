package io.xdag.p2p.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.NodeManager;
import io.xdag.p2p.discover.dns.DnsManager;
import io.xdag.p2p.message.node.DisconnectCode;
import java.net.InetSocketAddress;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ChannelManagerTest {

  private P2pConfig p2pConfig;
  private ChannelManager channelManager;

  @Mock private Channel c1, c2, c3;
  @Mock private NodeManager nodeManager;
  @Mock private DnsManager dnsManager;

  private InetSocketAddress a1 = new InetSocketAddress("100.1.1.1", 100);
  private InetSocketAddress a2 = new InetSocketAddress("100.1.1.2", 100);
  private InetSocketAddress a3 = new InetSocketAddress("100.1.1.2", 99);

  @BeforeEach
  public void beforeEach() {
    p2pConfig = new P2pConfig();
    channelManager = new ChannelManager(p2pConfig, nodeManager, dnsManager);
    channelManager.getBannedNodes().cleanUp();
    clearChannels();
  }

  @Test
  public void testGetConnectionNum() {
    when(c1.getInetAddress()).thenReturn(a1.getAddress());
    when(c2.getInetAddress()).thenReturn(a2.getAddress());
    when(c3.getInetAddress()).thenReturn(a3.getAddress());

    assertEquals(0, channelManager.getConnectionNum(a1.getAddress()));

    channelManager.getChannels().put(a1, c1);
    assertEquals(1, channelManager.getConnectionNum(a1.getAddress()));

    channelManager.getChannels().put(a2, c2);
    assertEquals(1, channelManager.getConnectionNum(a2.getAddress()));

    channelManager.getChannels().put(a3, c3);
    assertEquals(2, channelManager.getConnectionNum(a3.getAddress()));
  }

  @Test
  public void testNotifyDisconnect() {
    when(c1.getInetSocketAddress()).thenReturn(a1);
    when(c1.getInetAddress()).thenReturn(a1.getAddress());
    channelManager.getChannels().put(a1, c1);

    assertNull(channelManager.getBannedNodes().getIfPresent(a1.getAddress()));
    assertEquals(1, channelManager.getChannels().size());

    channelManager.notifyDisconnect(c1);

    assertNotNull(channelManager.getBannedNodes().getIfPresent(a1.getAddress()));
    assertEquals(0, channelManager.getChannels().size());
  }

  @Test
  public void testProcessPeerNormal() {
    when(c1.getInetSocketAddress()).thenReturn(a1);
    when(c1.getInetAddress()).thenReturn(a1.getAddress());
    assertEquals(DisconnectCode.NORMAL, channelManager.processPeer(c1));
    assertEquals(1, channelManager.getChannels().size());
  }

  @Test
  public void testProcessPeerTooManyPeers() {
    p2pConfig.setMaxConnections(1);
    when(c1.getInetSocketAddress()).thenReturn(a1);
    channelManager.getChannels().put(a1, c1);

    when(c2.getInetSocketAddress()).thenReturn(a2);
    when(c2.getInetAddress()).thenReturn(a2.getAddress());

    assertEquals(DisconnectCode.TOO_MANY_PEERS, channelManager.processPeer(c2));
  }

  @Test
  public void testProcessPeerMaxConnectionWithSameIp() {
    p2pConfig.setMaxConnectionsWithSameIp(1);
    lenient().when(c1.getInetSocketAddress()).thenReturn(a1);
    lenient().when(c1.getInetAddress()).thenReturn(a1.getAddress());
    channelManager.getChannels().put(a1, c1);

    lenient().when(c2.getInetSocketAddress()).thenReturn(a2);
    lenient().when(c2.getInetAddress()).thenReturn(a1.getAddress());

    assertEquals(DisconnectCode.MAX_CONNECTION_WITH_SAME_IP, channelManager.processPeer(c2));
  }

  @Test
  public void testProcessPeerDuplicatePeer() {
    // Setup first peer
    Bytes nodeId = Bytes.random(64);
    Node node1 = new Node(p2pConfig, nodeId, "127.0.0.1", null, 30301, 30301);
    when(c1.getNode()).thenReturn(node1);
    when(c1.getNodeId()).thenReturn(node1.getHexId());
    when(c1.getInetSocketAddress()).thenReturn(a1);
    when(c1.getInetAddress()).thenReturn(a1.getAddress());
    when(c1.getStartTime()).thenReturn(100L);
    channelManager.getChannels().put(a1, c1);

    // Setup second peer with the same nodeId
    Node node2 = new Node(p2pConfig, nodeId, "127.0.0.1", null, 30302, 30302);
    when(c2.getNode()).thenReturn(node2);
    when(c2.getNodeId()).thenReturn(node2.getHexId()); // Same nodeId as c1
    when(c2.getInetSocketAddress()).thenReturn(a2);
    when(c2.getInetAddress()).thenReturn(a2.getAddress());
    when(c2.getStartTime()).thenReturn(200L);

    assertEquals(DisconnectCode.DUPLICATE_PEER, channelManager.processPeer(c2));
  }

  @Test
  public void testProcessPeerTimeBanned() {
    channelManager.getBannedNodes().put(a1.getAddress(), System.currentTimeMillis() + 10000);
    when(c1.getInetSocketAddress()).thenReturn(a1);
    when(c1.getInetAddress()).thenReturn(a1.getAddress());
    when(c1.isActive()).thenReturn(false);
    when(c1.isTrustPeer()).thenReturn(false);

    assertEquals(DisconnectCode.TIME_BANNED, channelManager.processPeer(c1));
  }

  private void clearChannels() {
    channelManager.getChannels().clear();
    channelManager.getBannedNodes().invalidateAll();
  }
}
