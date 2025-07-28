package io.xdag.p2p.handler.node;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.channel.ChannelHandlerContext;
import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.channel.PeerClient;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.NodeManager;
import io.xdag.p2p.message.node.StatusMessage;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for NodeDetectHandler class. Tests node detection logic, message handling, and
 * connection management.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NodeDetectHandlerTest {

  private P2pConfig p2pConfig;

  @Mock private ChannelManager channelManager;

  @Mock private NodeManager nodeManager;

  @Mock private PeerClient peerClient;

  @Mock private Channel channel;

  @Mock private ChannelHandlerContext ctx;

  @Mock private StatusMessage statusMessage;

  private NodeDetectHandler nodeDetectHandler;

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
    p2pConfig.setNodeDetectEnable(true);
    nodeDetectHandler = new NodeDetectHandler(p2pConfig, channelManager, nodeManager);
  }

  @Test
  void testConstructor() {
    // Given & When
    NodeDetectHandler handler = new NodeDetectHandler(p2pConfig, channelManager, nodeManager);

    // Then
    assertNotNull(handler);
  }

  @Test
  void testInitWithNodeDetectDisabled() {
    // Given
    P2pConfig disabledConfig = new P2pConfig();
    disabledConfig.setNodeDetectEnable(false);
    NodeDetectHandler handler = new NodeDetectHandler(disabledConfig, channelManager, nodeManager);

    // When
    handler.init(peerClient);

    // Then - Should not initialize when node detect is disabled,
    // No exception should be thrown
  }

  @Test
  void testInitWithNodeDetectEnabled() {
    // Given
    p2pConfig.setNodeDetectEnable(true);

    // When
    nodeDetectHandler.init(peerClient);

    // Then
    // Should initialize successfully without throwing exception
  }

  @Test
  void testClose() {
    // Given
    nodeDetectHandler.init(peerClient);

    // When
    nodeDetectHandler.close();

    // Then
    // Should close successfully without throwing exception
  }

  @Test
  void testOnMessageWithInactiveChannel() {
    // Given
    when(channel.isActive()).thenReturn(false);
    when(channel.getCtx()).thenReturn(ctx);
    doNothing().when(channel).setDiscoveryMode(anyBoolean());
    doNothing().when(channel).send(any(StatusMessage.class));
    when(ctx.close()).thenReturn(null);

    // When
    nodeDetectHandler.onMessage(channel, statusMessage);

    // Then
    verify(channel).setDiscoveryMode(true);
    verify(channel).send(any(StatusMessage.class));
    verify(ctx).close();
  }

  @Test
  void testOnMessageWithActiveChannelButNoNodeStats() {
    // Given
    when(channel.isActive()).thenReturn(true);
    String testIp = "192.168.1.100";
    int testPort = 8080;
    when(channel.getInetSocketAddress()).thenReturn(new InetSocketAddress(testIp, testPort));

    // When
    nodeDetectHandler.onMessage(channel, statusMessage);

    // Then
    // Should return early when no nodeStats found
    verify(channel, never()).getCtx();
  }

  @Test
  void testNotifyDisconnectWithInactiveChannel() {
    // Given
    when(channel.isActive()).thenReturn(false);

    // When
    nodeDetectHandler.notifyDisconnect(channel);

    // Then
    // Should return early for the inactive channel
    verify(channel, never()).getInetSocketAddress();
  }

  @Test
  void testNotifyDisconnectWithNullSocketAddress() {
    // Given
    when(channel.isActive()).thenReturn(true);
    when(channel.getInetSocketAddress()).thenReturn(null);

    // When
    nodeDetectHandler.notifyDisconnect(channel);

    // Then
    // Should return early when socket address is null
    // No exception should be thrown
  }

  @Test
  void testGetConnectableNodes() {
    // Given & When
    List<Node> result = nodeDetectHandler.getConnectableNodes();

    // Then
    assertNotNull(result);
    // The actual implementation would return nodes based on internal logic
  }

  @Test
  void testOnConnect() {
    // Given & When
    nodeDetectHandler.onConnect(channel);

    // Then
    // Should complete without throwing exception,
    // This is a no-op method in the current implementation
  }

  @Test
  void testOnDisconnect() {
    // Given & When
    nodeDetectHandler.onDisconnect(channel);

    // Then
    // Should complete without throwing exception
    // This method calls notifyDisconnect internally
  }

  @Test
  void testWorkWithEmptyNodeManager() {
    // Given
    when(nodeManager.getConnectableNodes()).thenReturn(new ArrayList<>());

    // When
    nodeDetectHandler.work();

    // Then
    // Should attempt to load nodes when nodeStatMap is empty
    verify(nodeManager).getConnectableNodes();
  }

  @Test
  void testTrimNodeMapBasicFunctionality() {
    // Given & When
    nodeDetectHandler.trimNodeMap();

    // Then
    // Should complete without throwing exception,
    // This method cleans up timed out nodes
  }

  @Test
  void testBadNodesCacheAccess() {
    // Given & When
    var badNodesCache = NodeDetectHandler.getBadNodesCache();

    // Then
    assertNotNull(badNodesCache);
    // Should be able to access the static bad nodes cache
  }
}
