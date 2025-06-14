package io.xdag.p2p.handler.node;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.node.DisconnectCode;
import io.xdag.p2p.message.node.HelloMessage;
import io.xdag.p2p.message.node.P2pDisconnectMessage;
import io.xdag.p2p.proto.Connect.DisconnectReason;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for HandshakeHandler class. Tests handshake logic, message processing, and connection
 * validation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HandshakeHandlerTest {

  private P2pConfig p2pConfig;

  @Mock private ChannelManager channelManager;

  @Mock private Channel channel;

  @Mock private HelloMessage helloMessage;

  @Mock private Node node;

  private HandshakeHandler handshakeHandler;
  private final int testNetworkId = 1;
  private final String testNodeId = "test-node-id";
  private final long testTimestamp = System.currentTimeMillis();

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
    p2pConfig.setNetworkId(testNetworkId);
    handshakeHandler = new HandshakeHandler(p2pConfig, channelManager);
  }

  @Test
  void testConstructor() {
    // Given & When
    HandshakeHandler handler = new HandshakeHandler(p2pConfig, channelManager);

    // Then
    assertNotNull(handler);
  }

  @Test
  void testStartHandshake() {
    // Given
    when(channel.getStartTime()).thenReturn(testTimestamp);
    doNothing().when(channel).send(any(HelloMessage.class));

    // When
    handshakeHandler.startHandshake(channel);

    // Then
    verify(channel).send(any(HelloMessage.class));
  }

  @Test
  void testOnMessageWithFinishedHandshake() {
    // Given
    when(channel.isFinishHandshake()).thenReturn(true);
    when(channel.getInetSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
    doNothing().when(channel).send(any(P2pDisconnectMessage.class));
    doNothing().when(channel).close();

    // When
    handshakeHandler.onMessage(channel, helloMessage);

    // Then
    verify(channel).send(any(P2pDisconnectMessage.class));
    verify(channel).close();
  }

  @Test
  void testOnMessageWithNormalHandshake() {
    // Given
    setupNormalHandshakeScenario();

    // When
    handshakeHandler.onMessage(channel, helloMessage);

    // Then
    verify(channel).setHandshakeMessage(helloMessage);
    verify(channelManager).processPeer(channel);
    verify(channel).setFinishHandshake(true);
    verify(channel).updateAvgLatency(any(Long.class));
  }

  @Test
  void testOnMessageWithProcessPeerFailure() {
    // Given
    when(channel.isFinishHandshake()).thenReturn(false);
    when(helloMessage.getTimestamp()).thenReturn(testTimestamp);
    doNothing().when(channel).setHandshakeMessage(any(HelloMessage.class));
    when(channelManager.processPeer(channel)).thenReturn(DisconnectCode.TOO_MANY_PEERS);
    doNothing().when(channel).send(any(HelloMessage.class));
    doNothing()
        .when(channelManager)
        .logDisconnectReason(any(Channel.class), any(DisconnectReason.class));
    when(channelManager.getDisconnectReason(any(DisconnectCode.class)))
        .thenReturn(DisconnectReason.TOO_MANY_PEERS);
    doNothing().when(channel).close();

    // When
    handshakeHandler.onMessage(channel, helloMessage);

    // Then
    verify(channel).send(any(HelloMessage.class));
    verify(channelManager).logDisconnectReason(any(Channel.class), any(DisconnectReason.class));
    verify(channel).close();
    verify(channel, never()).setFinishHandshake(true);
  }

  @Test
  void testOnMessageWithDisconnectedChannel() {
    // Given
    when(channel.isFinishHandshake()).thenReturn(false);
    when(helloMessage.getTimestamp()).thenReturn(testTimestamp);
    when(helloMessage.getFrom()).thenReturn(node);
    when(node.getHexId()).thenReturn(testNodeId);
    doNothing().when(channel).setHandshakeMessage(any(HelloMessage.class));
    when(channelManager.processPeer(channel)).thenReturn(DisconnectCode.NORMAL);
    doNothing().when(channelManager).updateNodeId(any(Channel.class), anyString());
    when(channel.isDisconnect()).thenReturn(true);

    // When
    handshakeHandler.onMessage(channel, helloMessage);

    // Then
    verify(channel, never()).setFinishHandshake(true);
  }

  @Test
  void testOnMessageWithActiveChannelAndWrongCode() {
    // Given
    when(channel.isFinishHandshake()).thenReturn(false);
    when(helloMessage.getTimestamp()).thenReturn(testTimestamp);
    when(helloMessage.getFrom()).thenReturn(node);
    when(node.getHexId()).thenReturn(testNodeId);
    doNothing().when(channel).setHandshakeMessage(any(HelloMessage.class));
    when(channelManager.processPeer(channel)).thenReturn(DisconnectCode.NORMAL);
    doNothing().when(channelManager).updateNodeId(any(Channel.class), anyString());
    when(channel.isDisconnect()).thenReturn(false);
    when(channel.isActive()).thenReturn(true);
    when(helloMessage.getCode()).thenReturn(DisconnectCode.DIFFERENT_VERSION.getValue());
    when(helloMessage.getNetworkId()).thenReturn(testNetworkId);
    when(helloMessage.getVersion()).thenReturn(testNetworkId);
    when(channel.getInetSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
    when(channelManager.getDisconnectReason(any(DisconnectCode.class)))
        .thenReturn(DisconnectReason.DIFFERENT_VERSION);
    doNothing()
        .when(channelManager)
        .logDisconnectReason(any(Channel.class), any(DisconnectReason.class));
    doNothing().when(channel).close();

    // When
    handshakeHandler.onMessage(channel, helloMessage);

    // Then
    verify(channelManager).logDisconnectReason(any(Channel.class), any(DisconnectReason.class));
    verify(channel).close();
    verify(channel, never()).setFinishHandshake(true);
  }

  @Test
  void testOnMessageWithActiveChannelAndWrongNetworkId() {
    // Given
    when(channel.isFinishHandshake()).thenReturn(false);
    when(helloMessage.getTimestamp()).thenReturn(testTimestamp);
    when(helloMessage.getFrom()).thenReturn(node);
    when(node.getHexId()).thenReturn(testNodeId);
    doNothing().when(channel).setHandshakeMessage(any(HelloMessage.class));
    when(channelManager.processPeer(channel)).thenReturn(DisconnectCode.NORMAL);
    doNothing().when(channelManager).updateNodeId(any(Channel.class), anyString());
    when(channel.isDisconnect()).thenReturn(false);
    when(channel.isActive()).thenReturn(true);
    when(helloMessage.getCode()).thenReturn(DisconnectCode.NORMAL.getValue());
    when(helloMessage.getNetworkId()).thenReturn(999); // Wrong network ID
    when(helloMessage.getVersion()).thenReturn(888); // Wrong version too
    when(channel.getInetSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
    when(channelManager.getDisconnectReason(any(DisconnectCode.class)))
        .thenReturn(DisconnectReason.DIFFERENT_VERSION);
    doNothing()
        .when(channelManager)
        .logDisconnectReason(any(Channel.class), any(DisconnectReason.class));
    doNothing().when(channel).close();

    // When
    handshakeHandler.onMessage(channel, helloMessage);

    // Then
    // Active channel with wrong network ID AND version should disconnect
    verify(channelManager).logDisconnectReason(any(Channel.class), any(DisconnectReason.class));
    verify(channel).close();
    verify(channel, never()).setFinishHandshake(true);
  }

  @Test
  void testOnMessageWithInactiveChannelAndWrongNetworkId() {
    // Given
    when(channel.isFinishHandshake()).thenReturn(false);
    when(helloMessage.getTimestamp()).thenReturn(testTimestamp);
    when(helloMessage.getFrom()).thenReturn(node);
    when(node.getHexId()).thenReturn(testNodeId);
    doNothing().when(channel).setHandshakeMessage(any(HelloMessage.class));
    when(channelManager.processPeer(channel)).thenReturn(DisconnectCode.NORMAL);
    doNothing().when(channelManager).updateNodeId(any(Channel.class), anyString());
    when(channel.isDisconnect()).thenReturn(false);
    when(channel.isActive()).thenReturn(false);
    when(helloMessage.getNetworkId()).thenReturn(999); // Wrong network ID
    when(channel.getInetSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
    doNothing().when(channel).send(any(HelloMessage.class));
    doNothing()
        .when(channelManager)
        .logDisconnectReason(any(Channel.class), any(DisconnectReason.class));
    doNothing().when(channel).close();

    // When
    handshakeHandler.onMessage(channel, helloMessage);

    // Then
    verify(channel).send(any(HelloMessage.class));
    verify(channelManager).logDisconnectReason(channel, DisconnectReason.DIFFERENT_VERSION);
    verify(channel).close();
    verify(channel, never()).setFinishHandshake(true);
  }

  @Test
  void testOnMessageWithInactiveChannelAndCorrectNetworkId() {
    // Given
    when(channel.isFinishHandshake()).thenReturn(false);
    when(helloMessage.getTimestamp()).thenReturn(testTimestamp);
    when(helloMessage.getFrom()).thenReturn(node);
    when(node.getHexId()).thenReturn(testNodeId);
    doNothing().when(channel).setHandshakeMessage(any(HelloMessage.class));
    when(channelManager.processPeer(channel)).thenReturn(DisconnectCode.NORMAL);
    doNothing().when(channelManager).updateNodeId(any(Channel.class), anyString());
    when(channel.isDisconnect()).thenReturn(false);
    when(channel.isActive()).thenReturn(false);
    when(helloMessage.getNetworkId()).thenReturn(testNetworkId);
    when(channel.getStartTime()).thenReturn(testTimestamp - 1000);
    doNothing().when(channel).send(any(HelloMessage.class));
    doNothing().when(channel).setFinishHandshake(true);
    doNothing().when(channel).updateAvgLatency(any(Long.class));

    // When
    handshakeHandler.onMessage(channel, helloMessage);

    // Then
    verify(channel).send(any(HelloMessage.class));
    verify(channel).setFinishHandshake(true);
    verify(channel).updateAvgLatency(any(Long.class));
  }

  @Test
  void testOnConnect() {
    // Given & When
    handshakeHandler.onConnect(channel);

    // Then
    // Should complete without throwing exception
    // This is a no-op method in the current implementation
  }

  @Test
  void testOnDisconnect() {
    // Given & When
    handshakeHandler.onDisconnect(channel);

    // Then
    // Should complete without throwing exception
    // This is a no-op method in the current implementation
  }

  /** Helper method to setup a normal handshake scenario */
  private void setupNormalHandshakeScenario() {
    when(channel.isFinishHandshake()).thenReturn(false);
    when(helloMessage.getTimestamp()).thenReturn(testTimestamp);
    when(helloMessage.getFrom()).thenReturn(node);
    when(node.getHexId()).thenReturn(testNodeId);
    doNothing().when(channel).setHandshakeMessage(any(HelloMessage.class));
    when(channelManager.processPeer(channel)).thenReturn(DisconnectCode.NORMAL);
    doNothing().when(channelManager).updateNodeId(any(Channel.class), anyString());
    when(channel.isDisconnect()).thenReturn(false);
    when(channel.isActive()).thenReturn(true);
    when(helloMessage.getCode()).thenReturn(DisconnectCode.NORMAL.getValue());
    when(helloMessage.getNetworkId()).thenReturn(testNetworkId);
    when(helloMessage.getVersion()).thenReturn(testNetworkId);
    when(channel.getStartTime()).thenReturn(testTimestamp - 1000);
    doNothing().when(channel).setFinishHandshake(true);
    doNothing().when(channel).updateAvgLatency(any(Long.class));
  }
}
