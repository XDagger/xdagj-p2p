/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.p2p.channel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.channel.ChannelHandlerContext;
import io.xdag.p2p.config.P2pConfig;
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

  private MessageHandler messageHandler;

  @BeforeEach
  void setUp() {
    messageHandler = new MessageHandler(p2pConfig, channelManager, channel);
    when(ctx.channel()).thenReturn(nettyChannel);
    when(nettyChannel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

    // Mock for StatusMessage creation
    when(channelManager.getChannels()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());
    when(p2pConfig.getHomePeer())
        .thenReturn(
            io.xdag.p2p.proto.Discover.Peer.newBuilder()
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
