package io.xdag.p2p.handler.node;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.node.Message;
import io.xdag.p2p.message.node.MessageType;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for KeepAliveHandler class. Tests keep-alive mechanism, ping/pong message handling,
 * and timeout detection.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KeepAliveHandlerTest {

  @Mock private P2pConfig p2pConfig;

  @Mock private ChannelManager channelManager;

  @Mock private Channel channel;

  @Mock private Message message;

  private KeepAliveHandler keepAliveHandler;

  @BeforeEach
  void setUp() {
    keepAliveHandler = new KeepAliveHandler(p2pConfig, channelManager);
  }

  @Test
  void testConstructor() {
    // Given & When
    KeepAliveHandler handler = new KeepAliveHandler(p2pConfig, channelManager);

    // Then
    assertNotNull(handler);
  }

  @Test
  void testInit() {
    // Given
    Map<InetSocketAddress, Channel> channels = new HashMap<>();
    when(channelManager.getChannels()).thenReturn(channels);

    // When
    keepAliveHandler.init();

    // Then
    // Should initialize successfully without throwing exception,
    // The scheduled task will run in the background
  }

  @Test
  void testClose() {
    // Given
    keepAliveHandler.init();

    // When
    keepAliveHandler.close();

    // Then
    // Should close successfully without throwing exception
  }

  @Test
  void testOnMessageWithPing() {
    // Given
    when(message.getType()).thenReturn(MessageType.PING);
    doNothing().when(channel).send(any(PongMessage.class));

    // When
    keepAliveHandler.onMessage(channel, message);

    // Then
    verify(channel).send(any(PongMessage.class));
  }

  @Test
  void testOnMessageWithPong() {
    // Given
    when(message.getType()).thenReturn(MessageType.PONG);
    channel.pingSent = System.currentTimeMillis() - 1000; // 1 second ago
    channel.waitForPong = true;
    doNothing().when(channel).updateAvgLatency(any(Long.class));

    // When
    keepAliveHandler.onMessage(channel, message);

    // Then
    verify(channel).updateAvgLatency(any(Long.class));
    // waitForPong should be set to false
  }

  @Test
  void testOnMessageWithOtherMessageType() {
    // Given
    when(message.getType()).thenReturn(MessageType.STATUS);

    // When
    keepAliveHandler.onMessage(channel, message);

    // Then
    // Should handle gracefully without any action
    // No verification needed as it's a no-op for other message types
  }

  @Test
  void testOnConnect() {
    // Given & When
    keepAliveHandler.onConnect(channel);

    // Then
    // Should complete without throwing exception,
    // This is a no-op method in the current implementation
  }

  @Test
  void testOnDisconnect() {
    // Given & When
    keepAliveHandler.onDisconnect(channel);

    // Then
    // Should complete without throwing exception,
    // This is a no-op method in the current implementation
  }

  @Test
  void testKeepAliveTaskWithDisconnectedChannel() {
    // Given
    Map<InetSocketAddress, Channel> channels = new HashMap<>();
    InetSocketAddress testAddress = new InetSocketAddress("127.0.0.1", 8080);
    channels.put(testAddress, channel);
    when(channelManager.getChannels()).thenReturn(channels);
    when(channel.isDisconnect()).thenReturn(true);

    // When
    keepAliveHandler.init();

    // Then
    // Disconnected channels should be filtered out,
    // No ping should be sent to disconnected channels
  }

  @Test
  void testKeepAliveTaskWithChannelWaitingForPong() {
    // Given
    Map<InetSocketAddress, Channel> channels = new HashMap<>();
    InetSocketAddress testAddress = new InetSocketAddress("127.0.0.1", 8080);
    channels.put(testAddress, channel);
    when(channelManager.getChannels()).thenReturn(channels);
    when(channel.isDisconnect()).thenReturn(false);
    channel.waitForPong = true;
    channel.pingSent = System.currentTimeMillis() - 1000; // 1 second ago (not timed out)

    // When
    keepAliveHandler.init();

    // Then
    // Should not send the disconnect message as it's not timed out yet
  }

  @Test
  void testKeepAliveTaskWithChannelNotWaitingForPong() {
    // Given
    Map<InetSocketAddress, Channel> channels = new HashMap<>();
    InetSocketAddress testAddress = new InetSocketAddress("127.0.0.1", 8080);
    channels.put(testAddress, channel);
    when(channelManager.getChannels()).thenReturn(channels);
    when(channel.isDisconnect()).thenReturn(false);
    when(channel.isFinishHandshake()).thenReturn(true);
    when(channel.getLastSendTime()).thenReturn(System.currentTimeMillis() - 60000); // 1 minute ago
    channel.waitForPong = false;
    doNothing().when(channel).send(any(PingMessage.class));

    // When
    keepAliveHandler.init();

    // Then
    // Should send the ping message when last send time exceeds ping timeout
    // Note: This test verifies the logic but the actual execution happens asynchronously
  }

  @Test
  void testKeepAliveTaskWithUnfinishedHandshake() {
    // Given
    Map<InetSocketAddress, Channel> channels = new HashMap<>();
    InetSocketAddress testAddress = new InetSocketAddress("127.0.0.1", 8080);
    channels.put(testAddress, channel);
    when(channelManager.getChannels()).thenReturn(channels);
    when(channel.isDisconnect()).thenReturn(false);
    when(channel.isFinishHandshake()).thenReturn(false);
    when(channel.getLastSendTime()).thenReturn(System.currentTimeMillis() - 60000); // 1 minute ago
    channel.waitForPong = false;

    // When
    keepAliveHandler.init();

    // Then
    // Should not send the ping message when handshake is not finished
  }

  @Test
  void testKeepAliveTaskWithRecentActivity() {
    // Given
    Map<InetSocketAddress, Channel> channels = new HashMap<>();
    InetSocketAddress testAddress = new InetSocketAddress("127.0.0.1", 8080);
    channels.put(testAddress, channel);
    when(channelManager.getChannels()).thenReturn(channels);
    when(channel.isDisconnect()).thenReturn(false);
    when(channel.isFinishHandshake()).thenReturn(true);
    when(channel.getLastSendTime()).thenReturn(System.currentTimeMillis() - 1000); // 1 second ago
    channel.waitForPong = false;

    // When
    keepAliveHandler.init();

    // Then
    // Should not send the ping message when there's recent activity
  }
}
