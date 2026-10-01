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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.PeerServer;
import io.xdag.p2p.config.P2pConfig;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PeerServer class. Tests P2P server functionality including server startup and
 * shutdown. Note: These are lightweight unit tests that verify basic construction and lifecycle
 * methods. Full integration tests that bind to ports are in separate integration test suites.
 */
class PeerServerTest {

  private P2pConfig p2pConfig;
  private ChannelManager channelManager;
  private PeerServer peerServer;

  @BeforeEach
  void setUp() {
    // Use real P2pConfig instead of mock for proper initialization
    p2pConfig = new P2pConfig();
    p2pConfig.generateNodeKey(); // Required for P2pChannelInitializer

    // Mock ChannelManager since we don't need real channel management in unit tests
    channelManager = org.mockito.Mockito.mock(ChannelManager.class);

    peerServer = new PeerServer(p2pConfig, channelManager);
  }

  @Test
  void testConstructor() {
    // Given & When
    PeerServer server = new PeerServer(p2pConfig, channelManager);

    // Then
    assertNotNull(server, "PeerServer should be constructed successfully");
  }

  /** A port on the loopback interface that nobody listens on at this moment. */
  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }

  private void listenOnLoopback(int port) {
    p2pConfig.setBindIp("127.0.0.1");
    p2pConfig.setPort(port);
  }

  @Test
  void testStartWithValidPort() throws IOException {
    // Given
    listenOnLoopback(freePort());

    // When & Then
    assertDoesNotThrow(() -> peerServer.start(), "start() should not throw");
    assertTrue(peerServer.isListening(), "the listener is bound when start() returns");

    // Clean up - stop the server
    peerServer.stop();
  }

  @Test
  void startReturnsWhenTheListenerIsBound() throws IOException {
    int port = freePort();
    listenOnLoopback(port);

    peerServer.start();
    // no waiting, no retrying: a peer that dials right after start() is accepted
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
      assertTrue(socket.isConnected());
    } finally {
      peerServer.stop();
    }
  }

  /**
   * Whether the port can be bound within a moment. (A closed channel gives its port back when its selector has
   * taken note, which is a few milliseconds after the close is reported - not never, which is what this is about.)
   */
  private static boolean becomesFree(int port) throws InterruptedException {
    for (int attempt = 0; attempt < 150; attempt++) {
      try (ServerSocket socket = new ServerSocket()) {
        socket.bind(new InetSocketAddress("127.0.0.1", port));
        return true;
      } catch (IOException e) {
        Thread.sleep(20);
      }
    }
    return false;
  }

  @Test
  void stopRightAfterStartFreesThePort() throws Exception {
    int port = freePort();
    listenOnLoopback(port);

    // (stop() used to find a listener that was still coming up on its own thread, do nothing - and the
    // listener stayed for the rest of the process)
    peerServer.start();
    peerServer.stop();

    assertFalse(peerServer.isListening());
    assertTrue(becomesFree(port), "the port is free again");
  }

  @Test
  void aPortThatIsTakenIsReportedAndDoesNotHoldStartUp() throws IOException {
    try (ServerSocket taken = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      listenOnLoopback(taken.getLocalPort());

      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> peerServer.start());
      assertFalse(peerServer.isListening(), "the node goes on without a listener, and says so");
      assertDoesNotThrow(() -> peerServer.stop());
    }
  }

  @Test
  void testStartWithZeroPort() {
    // Given - port 0 means do not start server
    p2pConfig.setPort(0);

    // When & Then - should not start any thread
    assertDoesNotThrow(() -> peerServer.start(),
        "start() with port 0 should not throw");
  }

  @Test
  void testStartWithNegativePort() {
    // Given - negative port means do not start server
    p2pConfig.setPort(-1);

    // When & Then - should not start any thread
    assertDoesNotThrow(() -> peerServer.start(),
        "start() with negative port should not throw");
  }

  @Test
  void testStopWithoutStart() {
    // When & Then - stop() should handle case where server was never started
    assertDoesNotThrow(() -> peerServer.stop(),
        "stop() should handle case where start() was never called");
  }

  @Test
  void testStopAfterStart() {
    // Given
    p2pConfig.setPort(0); // Use ephemeral port to avoid conflicts

    // When
    assertDoesNotThrow(() -> {
      peerServer.start();
      Thread.sleep(50); // Give server time to start
      peerServer.stop();
    }, "stop() after start() should not throw");
  }

  @Test
  void testMultipleStops() {
    // When & Then - multiple stop() calls should be idempotent
    assertDoesNotThrow(() -> {
      peerServer.stop();
      peerServer.stop();
      peerServer.stop();
    }, "Multiple stop() calls should be idempotent");
  }

  @Test
  void testStartMethodWithPort() {
    // This tests the blocking start(int port) method indirectly
    // We can't easily test it directly as it blocks, so we test via the public start() method

    // Given
    p2pConfig.setPort(0); // ephemeral port

    // When & Then
    assertDoesNotThrow(() -> peerServer.start(),
        "start() method should invoke start(int port) without throwing");

    try {
      Thread.sleep(50);
      peerServer.stop();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
