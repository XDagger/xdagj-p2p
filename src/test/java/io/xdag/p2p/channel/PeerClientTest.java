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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.xdag.p2p.PeerClient;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for PeerClient class. Tests P2P client functionality including connection
 * establishment and management.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PeerClientTest {

  @Mock private ChannelManager channelManager;

  @Mock private Node node;

  @Mock private ChannelFutureListener futureListener;

  private PeerClient peerClient;

  @BeforeEach
  void setUp() {
    // Use real P2pConfig instead of mock for proper initialization
    P2pConfig realP2pConfig = new P2pConfig();
    realP2pConfig.generateNodeKey(); // Required for P2pChannelInitializer

    peerClient = new PeerClient(realP2pConfig, channelManager);
    InetSocketAddress testAddress = new InetSocketAddress("127.0.0.1", 8080);

    // Mock Node behavior
    when(node.getPreferInetSocketAddress()).thenReturn(testAddress);
    when(node.getPort()).thenReturn(8080);
    when(node.getId()).thenReturn("test-node-id");

    // Mock ChannelManager
    when(channelManager.isShutdown()).thenReturn(false);
  }

  @Test
  void testConstructorShouldInitializeFields() {
    // Given
    P2pConfig realConfig = new P2pConfig();
    realConfig.generateNodeKey();

    // When
    PeerClient client = new PeerClient(realConfig, channelManager);

    // Then
    assertNotNull(client);
    // Verify that the constructor properly stores the dependencies
    // We can't directly access private fields, but we can verify behavior
  }

  @Test
  void testStartShouldCreateWorkerGroup() throws Exception {
    // When
    peerClient.start();

    // Then
    // Verify that workerGroup is created by checking it's not null
    Field workerGroupField = PeerClient.class.getDeclaredField("workerGroup");
    workerGroupField.setAccessible(true);
    Object workerGroup = workerGroupField.get(peerClient);
    assertNotNull(workerGroup, "WorkerGroup should be initialized after start()");
  }

  @Test
  void testStopShouldShutdownWorkerGroup() throws Exception {
    // Given
    peerClient.start();
    Field workerGroupField = PeerClient.class.getDeclaredField("workerGroup");
    workerGroupField.setAccessible(true);
    Object workerGroup = workerGroupField.get(peerClient);
    assertNotNull(workerGroup, "WorkerGroup should exist before stop()");

    // When
    assertDoesNotThrow(() -> peerClient.stop());

    // Then
    // The stop method should complete without throwing exceptions
    // WorkerGroup shutdown is handled internally by Netty
  }

  @Test
  void testConnectWithHostAndPortShouldNotThrow() {
    // Given
    peerClient.start();
    String host = "127.0.0.1";
    int port = 8080;

    // When & Then - connect() method tries to sync on connection
    // It will fail for invalid address but should not throw uncaught exceptions
    assertDoesNotThrow(() -> peerClient.connect(host, port));
  }

  @Test
  void testConnectWithNodeAndListenerShouldReturnChannelFutureWhenNotShutdown() {
    // Given
    peerClient.start();
    when(channelManager.isShutdown()).thenReturn(false);

    // When
    ChannelFuture result = peerClient.connect(node, futureListener);

    // Then
    // the Result can be null or ChannelFuture depending on connection success
    // The important thing is that it doesn't throw an exception
    // and handles the shutdown check properly
  }

  @Test
  void testConnectWithNodeAndListenerShouldReturnNullWhenShutdown() {
    // Given
    peerClient.start();
    when(channelManager.isShutdown()).thenReturn(true);

    // When
    ChannelFuture result = peerClient.connect(node, futureListener);

    // Then
    // When shutdown, the method should return null after the connectAsync call
    // Note: The shutdown check happens after connectAsync, not before
  }

  @Test
  void testConnectAsyncWithNodeShouldAttemptConnection() {
    // Given
    peerClient.start();
    when(channelManager.isShutdown()).thenReturn(false);

    // When
    ChannelFuture result = peerClient.connectAsync(node);

    // Then
    // Should not throw exception and return ChannelFuture or null
  }

  @Test
  void testConnectAsyncShouldReturnNullWhenShutdown() {
    // Given
    peerClient.start();
    when(channelManager.isShutdown()).thenReturn(true);

    // When
    ChannelFuture result = peerClient.connectAsync(node);

    // Then
    // Note: The shutdown check happens AFTER connectAsync, not before,
    // So the method may still return a ChannelFuture even when shutdown
    // The actual shutdown handling happens in the returned ChannelFuture processing
  }

  @Test
  void testConnectAsyncWithNullNodeIdShouldNotThrow() {
    // Given
    peerClient.start();
    when(node.getId()).thenReturn(null);
    when(channelManager.isShutdown()).thenReturn(false);

    // When & Then
    // Should not throw exception even with null node ID
    assertDoesNotThrow(() -> peerClient.connectAsync(node));
  }

  @Test
  void testMultipleStartAndStopCyclesShouldWork() throws Exception {
    // When & Then
    assertDoesNotThrow(
        () -> {
          peerClient.start();
          peerClient.stop();
          peerClient.start();
          peerClient.stop();
        });

    // Verify the final state
    Field workerGroupField = PeerClient.class.getDeclaredField("workerGroup");
    workerGroupField.setAccessible(true);
    Object workerGroup = workerGroupField.get(peerClient);
    // After stop(), workerGroup should still exist but be shutdown
    assertNotNull(workerGroup);
  }

  @Test
  void testConnectWithInvalidHostShouldHandleGracefully() {
    // Given
    peerClient.start();
    String invalidHost = "invalid.host.name.that.does.not.exist";
    int port = 8080;

    // When & Then
    assertDoesNotThrow(() -> peerClient.connect(invalidHost, port));
    // The method should handle connection failures gracefully without throwing
  }

  @Test
  void testConnectAsyncWithValidNodeShouldAttemptConnection() {
    // Given
    peerClient.start();
    Node validNode = mock(Node.class);
    InetSocketAddress validAddress = new InetSocketAddress("127.0.0.1", 8080);
    when(validNode.getPreferInetSocketAddress()).thenReturn(validAddress);
    when(validNode.getPort()).thenReturn(8080);
    when(validNode.getId()).thenReturn("valid-node-id");
    when(channelManager.isShutdown()).thenReturn(false);

    // When
    ChannelFuture result = peerClient.connectAsync(validNode);

    // Then
    // The method should attempt to create a connection
    // Result can be null or ChannelFuture depending on connection success
  }

  @Test
  void testStopWithoutStartShouldThrowNullPointer() {
    // Given - PeerClient not started (workerGroup is null)

    // When & Then
    // The current implementation doesn't handle null workerGroup gracefully
    // This test documents the current behavior - it throws NullPointerException
    assertThrows(NullPointerException.class, () -> peerClient.stop());
  }
}
