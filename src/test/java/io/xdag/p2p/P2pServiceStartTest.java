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
package io.xdag.p2p;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.xdag.p2p.config.P2pConfig;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * What a caller can rely on when {@link P2pService#start()} returns, with real services on the loopback
 * interface: the node can be reached. It used to return while the listener was still coming up on its own
 * thread; a node started right after it dialled too early, was refused, and tried again half a minute later.
 */
class P2pServiceStartTest {

  private final List<P2pService> services = new ArrayList<>();

  @AfterEach
  void tearDown() {
    for (P2pService service : services) {
      service.stop();
    }
  }

  /** A port on the loopback interface that nobody listens on at this moment. */
  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }

  private P2pService service(int port, boolean discovery) {
    P2pConfig config = new P2pConfig();
    config.generateNodeKey();
    config.setBindIp("127.0.0.1");
    config.setIpV4("127.0.0.1");
    config.setPort(port);
    config.setDiscoverEnable(discovery);
    config.setAllowPrivateAddresses(true);
    config.setMinConnections(1);
    P2pService service = new P2pService(config);
    services.add(service);
    return service;
  }

  private static void await(String what, long timeoutMs, BooleanSupplier condition) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() >= deadline) {
        fail("timed out waiting for " + what);
      }
      Thread.sleep(20);
    }
  }

  @Test
  void theNodeCanBeReachedWhenStartReturns() throws IOException {
    int port = freePort();
    P2pService service = service(port, true);

    service.start();

    assertTrue(service.isStarted());
    assertTrue(service.getPeerServer().isListening(), "the TCP listener is bound");
    assertTrue(service.getNodeManager().isDiscoveryListening(), "the discovery socket is bound");
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
      assertTrue(socket.isConnected());
    }
  }

  @Test
  void aNodeStartedRightAfterAnotherConnectsToItAtOnce() throws Exception {
    int portA = freePort();
    P2pService a = service(portA, false);
    P2pService b = service(freePort(), false);
    b.getConfig().getActiveNodes().add(new InetSocketAddress("127.0.0.1", portA));

    a.start();
    b.start();

    // well below the 30 s after which an address that refused is dialled again
    await("the two nodes to connect", 10_000,
        () -> a.getChannelManager().getChannels().size() == 1 && b.getChannelManager().getChannels().size() == 1);
  }

  @Test
  void theModeSetBeforeStartIsTheModeTheServiceStartsIn() throws IOException {
    P2pService service = service(freePort(), false);

    service.setPermissionless(false);
    service.start();
    assertFalse(service.isPermissionless());
    assertTrue(service.getPeerServer().isListening());

    // while it runs, the mode can be changed as before
    service.setPermissionless(true);
    assertTrue(service.isPermissionless());
  }

  @Test
  void startingTwiceStartsOnce() throws IOException {
    int port = freePort();
    P2pService service = service(port, false);

    service.start();
    PeerServer listener = service.getPeerServer();
    service.start();

    assertEquals(listener, service.getPeerServer(), "no second listener");
    assertTrue(listener.isListening());
  }
}
