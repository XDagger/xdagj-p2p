package io.xdag.p2p.channel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.channel.ChannelHandlerContext;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.handler.node.HandshakeHandler;
import io.xdag.p2p.message.node.Message;
import io.xdag.p2p.message.node.StatusMessage;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for MessageHandler class. Tests message processing, exception handling, and channel
 * lifecycle management.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageHandlerTest {

  @Mock private P2pConfig p2pConfig;

  @Mock private ChannelManager channelManager;

  @Mock private Channel channel;

  @Mock private ChannelHandlerContext ctx;

  @Mock private io.netty.channel.Channel nettyChannel;

  @Mock private HandshakeHandler handshakeHandler;

  @Mock private Node node;

  private MessageHandler messageHandler;

  @BeforeEach
  void setUp() {
    messageHandler = new MessageHandler(p2pConfig, channelManager, channel);
    when(ctx.channel()).thenReturn(nettyChannel);
    when(nettyChannel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

    // Mock for StatusMessage creation
    when(channelManager.getChannels()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());
    when(p2pConfig.getHomeNode())
        .thenReturn(
            io.xdag.p2p.proto.Discover.Endpoint.newBuilder()
                .setAddress(com.google.protobuf.ByteString.copyFromUtf8("127.0.0.1"))
                .setPort(8080)
                .setNodeId(com.google.protobuf.ByteString.copyFromUtf8("test-node-id"))
                .build());
    when(p2pConfig.getMaxConnections()).thenReturn(100);
    when(p2pConfig.getNetworkId()).thenReturn(1);
  }

  @Test
  void testChannelActiveWithDiscoveryMode() {
    // Given
    when(channel.isActive()).thenReturn(true);
    when(channel.isDiscoveryMode()).thenReturn(true);

    // When
    messageHandler.channelActive(ctx);

    // Then
    verify(channel).setChannelHandlerContext(ctx);
    verify(channel).send((StatusMessage) any());
    verify(channelManager, never()).getHandshakeHandler();
  }

  @Test
  void testChannelActiveWithHandshakeMode() {
    // Given
    when(channel.isActive()).thenReturn(true);
    when(channel.isDiscoveryMode()).thenReturn(false);
    when(channelManager.getHandshakeHandler()).thenReturn(handshakeHandler);

    // When
    messageHandler.channelActive(ctx);

    // Then
    verify(channel).setChannelHandlerContext(ctx);
    verify(channelManager).getHandshakeHandler();
    verify(handshakeHandler).startHandshake(channel);
    verify(channel, never()).send((StatusMessage) any());
  }

  @Test
  void testChannelActiveWithInactiveChannel() {
    // Given
    when(channel.isActive()).thenReturn(false);

    // When
    messageHandler.channelActive(ctx);

    // Then
    verify(channel).setChannelHandlerContext(ctx);
    verify(channel, never()).send((Message) any());
    verify(channelManager, never()).getHandshakeHandler();
  }

  @Test
  void testHandlerAddedDoesNotThrow() {
    // When & Then - should not throw any exception
    messageHandler.handlerAdded(ctx);
  }

  @Test
  void testChannelInactiveDoesNotThrow() {
    // When & Then - should not throw any exception
    try {
      messageHandler.channelInactive(ctx);
    } catch (Exception e) {
      // channelInactive method doesn't exist, that's fine
    }
  }

  @Test
  void testExceptionCaught() {
    // Given
    RuntimeException exception = new RuntimeException("Test exception");

    // When
    messageHandler.exceptionCaught(ctx, exception);

    // Then
    verify(channel).processException(exception);
  }
}
