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
package io.xdag.p2p.discover;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.handler.discover.EventHandler;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The discovery socket: bound when {@code init} returns, gone when {@code close} returns. (It used to come up on
 * its own thread while the caller went on, and a {@code close} that came too early left it open.)
 */
class DiscoverServerTest {

  private P2pConfig config;
  private DiscoverServer server;

  @BeforeEach
  void setUp() {
    config = new P2pConfig();
    config.generateNodeKey();
    config.setBindIp("127.0.0.1");
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.close();
    }
  }

  /** A UDP port on the loopback interface that nobody is bound to at this moment. */
  private static int freePort() throws IOException {
    try (DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }

  private static DatagramSocket bind(int port) throws SocketException {
    return new DatagramSocket(port, InetAddress.getLoopbackAddress());
  }

  /**
   * Whether the port can be bound within a moment. (A closed channel gives its port back when its selector has
   * taken note, which is a few milliseconds after the close is reported - not never, which is what this is about.)
   */
  private static boolean becomesFree(int port) throws InterruptedException {
    for (int attempt = 0; attempt < 150; attempt++) {
      try {
        bind(port).close();
        return true;
      } catch (SocketException e) {
        Thread.sleep(20);
      }
    }
    return false;
  }

  @Test
  void initReturnsWhenTheSocketIsBound() throws IOException {
    int port = freePort();
    config.setPort(port);
    server = new DiscoverServer(config);

    server.init(mock(EventHandler.class));

    assertTrue(server.isListening());
    assertThrows(SocketException.class, () -> bind(port).close(), "the port is taken: by the discovery socket");
  }

  @Test
  void closeRightAfterInitFreesThePort() throws Exception {
    int port = freePort();
    config.setPort(port);
    server = new DiscoverServer(config);

    server.init(mock(EventHandler.class));
    server.close();

    assertFalse(server.isListening());
    assertTrue(becomesFree(port), "the port is free again");
  }

  @Test
  void aPortThatIsTakenIsReportedAndDoesNotHoldStartUp() throws IOException {
    try (DatagramSocket taken = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
      config.setPort(taken.getLocalPort());
      server = new DiscoverServer(config);

      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> server.init(mock(EventHandler.class)));
      assertFalse(server.isListening(), "the node goes on without discovery");
    }
  }
}
