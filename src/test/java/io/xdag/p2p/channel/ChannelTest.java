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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.Message;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for Channel class. Tests core channel functionality including message sending,
 * connection management, and exception handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChannelTest {

  @Mock private P2pConfig p2pConfig;

  @Mock private ChannelManager channelManager;

  @Mock private ChannelHandlerContext ctx;

  @Mock private io.netty.channel.Channel nettyChannel;

  @Mock private ChannelFuture channelFuture;

  @Mock private ChannelPipeline pipeline;

  @Mock private Message message;

  @Mock private Attribute<Channel> channelAttribute;

  private Channel channel;
  private InetSocketAddress testAddress;

  @BeforeEach
  void setUp() {
    channel = new Channel(channelManager);
    channel.setP2pConfig(p2pConfig);
    testAddress = new InetSocketAddress("127.0.0.1", 8080);

    // Mock basic netty channel behavior
    when(ctx.channel()).thenReturn(nettyChannel);
    when(nettyChannel.remoteAddress()).thenReturn(testAddress);
    when(nettyChannel.writeAndFlush(any())).thenReturn(channelFuture);
    when(nettyChannel.close()).thenReturn(channelFuture);
    when(ctx.writeAndFlush(any())).thenReturn(channelFuture);
    when(channelFuture.addListener(any())).thenReturn(channelFuture);

    // Mock channel attribute for XdagFrameCodec
    when(nettyChannel.attr(any(AttributeKey.class))).thenReturn(channelAttribute);
    when(channelAttribute.get()).thenReturn(channel);

    // Mock P2pConfig
    when(p2pConfig.getTrustNodes()).thenReturn(new ArrayList<>());

    // Mock message
    when(message.needToLog()).thenReturn(false);
    when(message.getSendData()).thenReturn(Bytes.wrap("test".getBytes()));
  }

  @Test
  void testConstructor() {
    // Given & When
    Channel newChannel = new Channel(channelManager);

    // Then
    assertNotNull(newChannel);
    assertEquals(channelManager, newChannel.getChannelManager());
    assertFalse(newChannel.isDisconnect());
    assertFalse(newChannel.isFinishHandshake());
  }

  @Test
  void testSetChannelHandlerContext() {
    // When
    channel.setChannelHandlerContext(ctx);

    // Then
    assertEquals(ctx, channel.getCtx());
    assertEquals(testAddress, channel.getInetSocketAddress());
    assertEquals(testAddress.getAddress(), channel.getInetAddress());
    assertFalse(channel.isTrustPeer()); // empty trust nodes list
  }

  @Test
  void testSetChannelHandlerContextWithTrustedPeer() throws Exception {
    // Given
    List<InetAddress> trustNodes = new ArrayList<>();
    trustNodes.add(InetAddress.getByName("127.0.0.1"));
    when(p2pConfig.getTrustNodes()).thenReturn(trustNodes);

    // When
    channel.setChannelHandlerContext(ctx);

    // Then
    assertTrue(channel.isTrustPeer());
  }

  @Test
  void testSendMessage() {
    // Given
    channel.setChannelHandlerContext(ctx);

    // When
    channel.send(message);

    // Then
    verify(nettyChannel).writeAndFlush(any());
    assertTrue(channel.getLastSendTime() > 0);
  }

  @Test
  void testSendBytes() {
    // Given
    channel.setChannelHandlerContext(ctx);
    Bytes testData = Bytes.wrap("test data".getBytes());

    // When
    channel.send(testData);

    // Then
    verify(nettyChannel).writeAndFlush(any());
    assertTrue(channel.getLastSendTime() > 0);
  }

  @Test
  void testSendWhenDisconnected() {
    // Given
    channel.setChannelHandlerContext(ctx);
    channel.setDisconnect(true);
    Bytes testData = Bytes.wrap("test data".getBytes());

    // When
    channel.send(testData);

    // Then - should not send when disconnected,
    // The method should return early and not call writeAndFlush
  }

  @Test
  void testClose() {
    // Given
    channel.setChannelHandlerContext(ctx);

    // When
    channel.close();

    // Then
    assertTrue(channel.isDisconnect());
    assertTrue(channel.getDisconnectTime() > 0);
    verify(channelManager).banNode(any(InetAddress.class), anyLong());
    verify(ctx).close();
  }

  @Test
  void testCloseWithCustomBanTime() {
    // Given
    channel.setChannelHandlerContext(ctx);
    long customBanTime = 5000L;

    // When
    channel.close(customBanTime);

    // Then
    assertTrue(channel.isDisconnect());
    verify(channelManager).banNode(any(InetAddress.class), eq(customBanTime));
    verify(ctx).close();
  }

  @Test
  void testEqualsAndHashCode() {
    // Given
    Channel channel1 = new Channel(channelManager);
    channel1.setP2pConfig(p2pConfig);
    Channel channel2 = new Channel(channelManager);
    channel2.setP2pConfig(p2pConfig);

    channel1.setChannelHandlerContext(ctx);
    channel2.setChannelHandlerContext(ctx);

    // When & Then
    assertEquals(channel1, channel2);
    assertEquals(channel1.hashCode(), channel2.hashCode());
  }

  @Test
  void testToString() {
    // Given
    channel.setChannelHandlerContext(ctx);
    channel.setNodeId("test-node-id");

    // When
    String result = channel.toString();

    // Then
    assertTrue(result.contains("127.0.0.1:8080"));
    assertTrue(result.contains("test-node-id"));
  }

  @Test
  void testToStringWithNullNodeId() {
    // Given
    channel.setChannelHandlerContext(ctx);

    // When
    String result = channel.toString();

    // Then
    assertTrue(result.contains("127.0.0.1:8080"));
    assertTrue(result.contains("<null>"));
  }

  @Test
  void testIsActiveWithNodeId() {
    // Given - need it to call init to set isActive properly
    channel.init(pipeline, "test-node-id", false);

    // When & Then
    assertTrue(channel.isActive());
  }

  @Test
  void testIsActiveWithoutNodeId() {
    // Given - init with empty nodeId
    channel.init(pipeline, "", false);

    // When & Then
    assertFalse(channel.isActive());
  }

  @Test
  void testInitMethod() {
    // Given
    String nodeId = "test-node-id";
    boolean discoveryMode = true;

    // When
    channel.init(pipeline, nodeId, discoveryMode);

    // Then
    assertEquals(nodeId, channel.getNodeId());
    assertTrue(channel.isDiscoveryMode());
    assertTrue(channel.isActive());
  }

  @Test
  void testInitMethodWithEmptyNodeId() {
    // Given
    String nodeId = "";

    // When
    channel.init(pipeline, nodeId, false);

    // Then
    assertFalse(channel.isDiscoveryMode());
    assertEquals(nodeId, channel.getNodeId());
    assertFalse(channel.isActive());
  }

  @Test
  void testInitMethodWithNullNodeId() {
    // Given
    String nodeId = null;

    // When
    channel.init(pipeline, nodeId, false);

    // Then
    assertFalse(channel.isDiscoveryMode());
    assertEquals(nodeId, channel.getNodeId());
    assertFalse(channel.isActive());
  }


  @Test
  void testSendMessageWithLogging() {
    // Given
    channel.setChannelHandlerContext(ctx);
    when(message.needToLog()).thenReturn(true);

    // When
    channel.send(message);

    // Then
    verify(nettyChannel).writeAndFlush(any());
    verify(message).needToLog();
    // Note: getSendData is not called in Message send path, only in Bytes send path
  }

  @Test
  void testSendWithException() {
    // Given
    channel.setChannelHandlerContext(ctx);
    when(nettyChannel.writeAndFlush(any())).thenThrow(new RuntimeException("Send failed"));
    Bytes testData = Bytes.wrap("test".getBytes());

    // When
    channel.send(testData);

    // Then
    verify(nettyChannel).close();
  }

  @Test
  void testEqualsWithDifferentClass() {
    // Given
    channel.setChannelHandlerContext(ctx);
    String notAChannel = "not a channel";

    // When & Then
    assertNotEquals(notAChannel, channel);
  }

  @Test
  void testEqualsWithNull() {
    // Given
    channel.setChannelHandlerContext(ctx);

    // When & Then
    assertNotEquals(null, channel);
  }

  @Test
  void testEqualsSameInstance() {
    // Given
    channel.setChannelHandlerContext(ctx);

    // When & Then
    assertEquals(channel, channel);
  }

  @Test
  void testHashCodeConsistency() {
    // Given
    channel.setChannelHandlerContext(ctx);
    int firstHash = channel.hashCode();

    // When
    int secondHash = channel.hashCode();

    // Then
    assertEquals(firstHash, secondHash);
  }

  @Test
  void testToStringWithValidNodeId() {
    // Given
    channel.setChannelHandlerContext(ctx);
    channel.setNodeId("valid-node-id");

    // When
    String result = channel.toString();

    // Then
    assertTrue(result.contains("127.0.0.1:8080"));
    assertTrue(result.contains("valid-node-id"));
    assertFalse(result.contains("<null>"));
  }

  @Test
  void testGettersAndSetters() {
    // Test basic getters and setters
    long disconnectTime = System.currentTimeMillis();
    channel.setDisconnectTime(disconnectTime);
    assertEquals(disconnectTime, channel.getDisconnectTime());

    channel.setTrustPeer(true);
    assertTrue(channel.isTrustPeer());

    long lastSendTime = System.currentTimeMillis();
    channel.setLastSendTime(lastSendTime);
    assertEquals(lastSendTime, channel.getLastSendTime());

    // Test that startTime is set in constructor
    assertTrue(channel.getStartTime() > 0);
  }
}
