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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.handler.node.NodeDetectHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Unit tests for P2pChannelInitializer class. Tests P2P channel initialization functionality. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class P2pChannelInitializerTest {

  @Mock private P2pConfig p2pConfig;

  @Mock private ChannelManager channelManager;

  @Mock private NioSocketChannel nioSocketChannel;

  @Mock private ChannelPipeline channelPipeline;

  @Mock private SocketChannelConfig channelConfig;

  @Mock private ChannelFuture channelFuture;

  @Mock private NodeDetectHandler nodeDetectHandler;

  private P2pChannelInitializer channelInitializer;

  @BeforeEach
  void setUp() {
    // Setup mocks
    when(nioSocketChannel.pipeline()).thenReturn(channelPipeline);
    when(nioSocketChannel.config()).thenReturn(channelConfig);
    when(nioSocketChannel.closeFuture()).thenReturn(channelFuture);
    when(channelManager.getNodeDetectHandler()).thenReturn(nodeDetectHandler);

    // Create the initializer
    channelInitializer =
        new P2pChannelInitializer(p2pConfig, channelManager, "test-remote-id", false, true);
  }

  @Test
  void testConstructor() {
    // Given & When
    P2pChannelInitializer initializer =
        new P2pChannelInitializer(p2pConfig, channelManager, "remote-id", false, true);

    // Then
    assertNotNull(initializer);
  }

  @Test
  void testConstructorWithDiscoveryMode() {
    // Given & When
    P2pChannelInitializer initializer =
        new P2pChannelInitializer(p2pConfig, channelManager, "remote-id", true, false);

    // Then
    assertNotNull(initializer);
  }

  @Test
  void testInitChannel() {
    // When
    channelInitializer.initChannel(nioSocketChannel);

    // Then
    verify(nioSocketChannel).pipeline();
    verify(nioSocketChannel, atLeastOnce()).config();
    verify(nioSocketChannel).closeFuture();
    verify(channelConfig, atLeastOnce()).setRecvByteBufAllocator(any());
    verify(channelConfig, atLeastOnce()).setOption(any(), any());
    verify(channelFuture).addListener(any());
  }

  @Test
  void testInitChannelWithDiscoveryMode() {
    // Given
    P2pChannelInitializer discoveryInitializer =
        new P2pChannelInitializer(p2pConfig, channelManager, "remote-id", true, false);

    // When
    discoveryInitializer.initChannel(nioSocketChannel);

    // Then
    verify(nioSocketChannel).pipeline();
    verify(nioSocketChannel, atLeastOnce()).config();
    verify(nioSocketChannel).closeFuture();
    verify(channelConfig, atLeastOnce()).setRecvByteBufAllocator(any());
    verify(channelConfig, atLeastOnce()).setOption(any(), any());
    verify(channelFuture).addListener(any());
  }

  @Test
  void testInitChannelWithEmptyRemoteId() {
    // Given
    P2pChannelInitializer emptyIdInitializer =
        new P2pChannelInitializer(p2pConfig, channelManager, "", false, true);

    // When
    emptyIdInitializer.initChannel(nioSocketChannel);

    // Then
    verify(nioSocketChannel).pipeline();
    verify(nioSocketChannel, atLeastOnce()).config();
    verify(nioSocketChannel).closeFuture();
    verify(channelConfig, atLeastOnce()).setRecvByteBufAllocator(any());
    verify(channelConfig, atLeastOnce()).setOption(any(), any());
    verify(channelFuture).addListener(any());
  }

  @Test
  void testInitChannelWithNullRemoteId() {
    // Given
    P2pChannelInitializer nullIdInitializer =
        new P2pChannelInitializer(p2pConfig, channelManager, null, false, true);

    // When
    nullIdInitializer.initChannel(nioSocketChannel);

    // Then
    verify(nioSocketChannel).pipeline();
    verify(nioSocketChannel, atLeastOnce()).config();
    verify(nioSocketChannel).closeFuture();
    verify(channelConfig, atLeastOnce()).setRecvByteBufAllocator(any());
    verify(channelConfig, atLeastOnce()).setOption(any(), any());
    verify(channelFuture).addListener(any());
  }

  @Test
  void testInitChannelWithTriggerDisabled() {
    // Given
    P2pChannelInitializer noTriggerInitializer =
        new P2pChannelInitializer(p2pConfig, channelManager, "remote-id", false, false);

    // When
    noTriggerInitializer.initChannel(nioSocketChannel);

    // Then
    verify(nioSocketChannel).pipeline();
    verify(nioSocketChannel, atLeastOnce()).config();
    verify(nioSocketChannel).closeFuture();
    verify(channelConfig, atLeastOnce()).setRecvByteBufAllocator(any());
    verify(channelConfig, atLeastOnce()).setOption(any(), any());
    verify(channelFuture).addListener(any());
  }

  @Test
  void testInitChannelWithAllParametersCombinations() {
    // Test all combinations of boolean parameters
    boolean[] discoveryModes = {true, false};
    boolean[] triggers = {true, false};
    String[] remoteIds = {"test-id", "", null};

    for (boolean discoveryMode : discoveryModes) {
      for (boolean trigger : triggers) {
        for (String remoteId : remoteIds) {
          // Given
          P2pChannelInitializer testInitializer =
              new P2pChannelInitializer(
                  p2pConfig, channelManager, remoteId, discoveryMode, trigger);

          // When
          testInitializer.initChannel(nioSocketChannel);

          // Then - should not throw any exception
          // All combinations should be handled gracefully
        }
      }
    }
  }

  @Test
  void testInitChannelHandlesExceptions() {
    // Given
    when(nioSocketChannel.pipeline()).thenThrow(new RuntimeException("Test exception"));

    // When
    channelInitializer.initChannel(nioSocketChannel);

    // Then - should not throw any exception,
    // The method should handle exceptions gracefully and log them
  }
}
