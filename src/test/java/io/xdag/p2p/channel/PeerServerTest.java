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
import static org.mockito.Mockito.when;

import io.xdag.p2p.config.P2pConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for PeerServer class. Tests P2P server functionality including server startup and
 * shutdown.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PeerServerTest {

  @Mock private P2pConfig p2pConfig;

  @Mock private ChannelManager channelManager;

  private PeerServer peerServer;

  @BeforeEach
  void setUp() {
    peerServer = new PeerServer(p2pConfig, channelManager);
  }

  @Test
  void testConstructor() {
    // Given & When
    PeerServer server = new PeerServer(p2pConfig, channelManager);

    // Then
    assertNotNull(server);
  }

  @Test
  void testInitWithValidPort() {
    // Given
    when(p2pConfig.getPort()).thenReturn(8080);

    // When
    peerServer.init();

    // Then - should not throw any exception
    // The init method starts a new thread for the server
    // We need to give it a moment to start
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void testInitWithZeroPort() {
    // Given
    when(p2pConfig.getPort()).thenReturn(0);

    // When
    peerServer.init();

    // Then - should not throw any exception
    // When port is 0 or negative, no server thread is started
  }

  @Test
  void testInitWithNegativePort() {
    // Given
    when(p2pConfig.getPort()).thenReturn(-1);

    // When
    peerServer.init();

    // Then - should not throw any exception
    // When port is 0 or negative, no server thread is started
  }

  @Test
  void testClose() {
    // When
    peerServer.close();

    // Then - should not throw any exception
    // Close should handle the case where server is not listening gracefully
  }

  @Test
  void testCloseAfterInit() {
    // Given
    when(p2pConfig.getPort()).thenReturn(8080);
    peerServer.init();

    // Give the server thread time to start
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // When
    peerServer.close();

    // Then - should not throw any exception
    // Close should properly shut down the server
  }

  @Test
  void testStartWithValidPort() {
    // Given
    int port = 0; // Use port 0 to let the system choose an available port

    // When
    // We run this in a separate thread since start() is blocking
    Thread serverThread = new Thread(() -> peerServer.start(port));
    serverThread.setDaemon(true);
    serverThread.start();

    // Give the server time to start
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Then - should not throw any exception
    // The server should start successfully on an available port
  }

  @Test
  void testStartWithInvalidPort() {
    // Given
    int invalidPort = -1;

    // When
    // We run this in a separate thread since start() is blocking
    Thread serverThread = new Thread(() -> peerServer.start(invalidPort));
    serverThread.setDaemon(true);
    serverThread.start();

    // Give the server time to attempt start
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Then - should not throw any exception
    // The server should handle invalid ports gracefully
  }

  @Test
  void testMultipleInitAndClose() {
    // Given
    when(p2pConfig.getPort()).thenReturn(8080);

    // When
    peerServer.init();
    peerServer.close();
    peerServer.init();
    peerServer.close();

    // Then - should not throw any exception
    // Multiple init/close cycles should be handled gracefully
  }

  @Test
  void testCloseWithoutInit() {
    // When
    peerServer.close();

    // Then - should not throw any exception
    // Close should handle the case where init was never called
  }
}
