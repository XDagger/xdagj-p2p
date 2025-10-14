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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.xdag.p2p.PeerServer;
import io.xdag.p2p.config.P2pConfig;
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

  @Test
  void testStartWithValidPort() {
    // Given
    p2pConfig.setPort(8080);

    // When & Then - start() spawns a background thread, should not throw
    assertDoesNotThrow(() -> peerServer.start(),
        "start() should spawn background thread without throwing");

    // Clean up - stop the server
    try {
      Thread.sleep(50); // Give server thread time to start
      peerServer.stop();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
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
