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
import io.xdag.p2p.P2pException;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.node.HelloMessage;
import io.xdag.p2p.message.node.Message;
import java.io.IOException;
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

  @Mock private Node node;

  @Mock private HelloMessage handshakeMessage;

  @Mock private Message message;

  private Channel channel;
  private InetSocketAddress testAddress;

  @BeforeEach
  void setUp() {
    channel = new Channel(p2pConfig, channelManager);
    testAddress = new InetSocketAddress("127.0.0.1", 8080);

    // Mock basic netty channel behavior
    when(ctx.channel()).thenReturn(nettyChannel);
    when(nettyChannel.remoteAddress()).thenReturn(testAddress);
    when(nettyChannel.writeAndFlush(any())).thenReturn(channelFuture);
    when(nettyChannel.close()).thenReturn(channelFuture);
    when(ctx.writeAndFlush(any())).thenReturn(channelFuture);
    when(channelFuture.addListener(any())).thenReturn(channelFuture);

    // Mock P2pConfig
    when(p2pConfig.getTrustNodes()).thenReturn(new ArrayList<>());

    // Mock message
    when(message.needToLog()).thenReturn(false);
    when(message.getSendData()).thenReturn(Bytes.wrap("test".getBytes()));
  }

  @Test
  void testConstructor() {
    // Given & When
    Channel newChannel = new Channel(p2pConfig, channelManager);

    // Then
    assertNotNull(newChannel);
    assertEquals(p2pConfig, newChannel.getP2pConfig());
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
  void testSetHandshakeMessage() {
    // Given
    when(handshakeMessage.getFrom()).thenReturn(node);
    when(handshakeMessage.getVersion()).thenReturn(1);
    when(node.getHexId()).thenReturn("test-node-id");

    // When
    channel.setHandshakeMessage(handshakeMessage);

    // Then
    assertEquals(handshakeMessage, channel.getHandshakeMessage());
    assertEquals(node, channel.getNode());
    assertEquals("test-node-id", channel.getNodeId());
    assertEquals(1, channel.getVersion());
  }

  @Test
  void testSendMessage() {
    // Given
    channel.setChannelHandlerContext(ctx);

    // When
    channel.send(message);

    // Then
    verify(ctx).writeAndFlush(any());
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
    verify(ctx).writeAndFlush(any());
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

    // Then - should not send when disconnected
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
  void testProcessExceptionWithIOException() {
    // Given
    channel.setChannelHandlerContext(ctx);
    IOException exception = new IOException("Connection lost");

    // When
    channel.processException(exception);

    // Then
    assertTrue(channel.isDisconnect());
    verify(ctx).close();
  }

  @Test
  void testProcessExceptionWithP2pException() {
    // Given
    channel.setChannelHandlerContext(ctx);
    P2pException exception = new P2pException(P2pException.TypeEnum.BAD_MESSAGE, "Bad message");

    // When
    channel.processException(exception);

    // Then
    assertTrue(channel.isDisconnect());
    verify(ctx).close();
  }

  @Test
  void testProcessExceptionWithGenericException() {
    // Given
    channel.setChannelHandlerContext(ctx);
    RuntimeException exception = new RuntimeException("Generic error");

    // When
    channel.processException(exception);

    // Then
    assertTrue(channel.isDisconnect());
    verify(ctx).close();
  }

  @Test
  void testUpdateAvgLatency() {
    // Given
    long latency1 = 100L;
    long latency2 = 200L;

    // When
    channel.updateAvgLatency(latency1);
    channel.updateAvgLatency(latency2);

    // Then
    assertEquals(150L, channel.getAvgLatency()); // (100 + 200) / 2
    assertEquals(2L, channel.getCount());
  }

  @Test
  void testEqualsAndHashCode() {
    // Given
    Channel channel1 = new Channel(p2pConfig, channelManager);
    Channel channel2 = new Channel(p2pConfig, channelManager);

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
    // Given - need to call init to set isActive properly
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
  void testProcessExceptionWithReadTimeoutException() {
    // Given
    channel.setChannelHandlerContext(ctx);
    when(ctx.close()).thenReturn(channelFuture);
    io.netty.handler.timeout.ReadTimeoutException timeoutException = 
        io.netty.handler.timeout.ReadTimeoutException.INSTANCE;

    // When
    channel.processException(timeoutException);

    // Then
    verify(ctx).close();
    assertTrue(channel.isDisconnect());
  }

  @Test
  void testProcessExceptionWithCorruptedFrameException() {
    // Given
    channel.setChannelHandlerContext(ctx);
    when(ctx.close()).thenReturn(channelFuture);
    io.netty.handler.codec.CorruptedFrameException frameException = 
        new io.netty.handler.codec.CorruptedFrameException("Corrupted frame");

    // When
    channel.processException(frameException);

    // Then
    verify(ctx).close();
    assertTrue(channel.isDisconnect());
  }

  @Test
  void testProcessExceptionWithIllegalArgumentInCausalChain() {
    // Given
    channel.setChannelHandlerContext(ctx);
    when(ctx.close()).thenReturn(channelFuture);
    RuntimeException cause = new RuntimeException("Root cause");
    IllegalArgumentException wrapper = new IllegalArgumentException("Loop detected", cause);

    // When
    channel.processException(wrapper);

    // Then
    verify(ctx).close();
    assertTrue(channel.isDisconnect());
  }

  @Test
  void testSendWithFinishedHandshakeAndVersionUpgrade() {
    // Given
    channel.setChannelHandlerContext(ctx);
    channel.setFinishHandshake(true);
    channel.setVersion(2);
    Bytes testData = Bytes.wrap(new byte[]{1, 2, 3, 4});

    // When
    channel.send(testData);

    // Then
    verify(ctx).writeAndFlush(any());
    assertTrue(channel.getLastSendTime() > 0);
  }

  @Test
  void testSendMessageWithLogging() {
    // Given
    channel.setChannelHandlerContext(ctx);
    when(message.needToLog()).thenReturn(true);

    // When
    channel.send(message);

    // Then
    verify(ctx).writeAndFlush(any());
    verify(message).needToLog();
    verify(message).getSendData();
  }

  @Test
  void testSendWithException() {
    // Given
    channel.setChannelHandlerContext(ctx);
    when(ctx.writeAndFlush(any())).thenThrow(new RuntimeException("Send failed"));
    when(nettyChannel.close()).thenReturn(channelFuture);
    Bytes testData = Bytes.wrap("test".getBytes());

    // When
    channel.send(testData);

    // Then
    verify(nettyChannel).close();
  }

  @Test
  void testMultipleLatencyUpdates() {
    // When
    channel.updateAvgLatency(100);
    channel.updateAvgLatency(200);
    channel.updateAvgLatency(300);

    // Then
    assertEquals(200, channel.getAvgLatency()); // (100 + 200 + 300) / 3
    assertEquals(3, channel.getCount());
  }

  @Test
  void testEqualsWithDifferentClass() {
    // Given
    channel.setChannelHandlerContext(ctx);
    String notAChannel = "not a channel";

    // When & Then
    assertFalse(channel.equals(notAChannel));
  }

  @Test
  void testEqualsWithNull() {
    // Given
    channel.setChannelHandlerContext(ctx);

    // When & Then
    assertFalse(channel.equals(null));
  }

  @Test
  void testEqualsSameInstance() {
    // Given
    channel.setChannelHandlerContext(ctx);

    // When & Then
    assertTrue(channel.equals(channel));
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
    channel.setWaitForPong(true);
    assertTrue(channel.isWaitForPong());

    long pingTime = System.currentTimeMillis();
    channel.setPingSent(pingTime);
    assertEquals(pingTime, channel.getPingSent());

    channel.setVersion(5);
    assertEquals(5, channel.getVersion());

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
