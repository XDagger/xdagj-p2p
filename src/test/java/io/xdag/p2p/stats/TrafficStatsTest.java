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
package io.xdag.p2p.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TrafficStats class. Tests traffic statistics collection for TCP and UDP handlers.
 */
public class TrafficStatsTest {

  private TrafficStats.TrafficStatHandler tcpHandler;
  private TrafficStats.TrafficStatHandler udpHandler;

  @BeforeEach
  void setUp() {
    tcpHandler = TrafficStats.getTcp();
    udpHandler = TrafficStats.getUdp();
  }

  @Test
  public void testGetTcpAndUdpHandlers() {
    assertNotNull(TrafficStats.getTcp(), "TCP handler should not be null");
    assertNotNull(TrafficStats.getUdp(), "UDP handler should not be null");

    // Verify singleton behavior
    assertSame(TrafficStats.getTcp(), TrafficStats.getTcp(), "TCP handler should be singleton");
    assertSame(TrafficStats.getUdp(), TrafficStats.getUdp(), "UDP handler should be singleton");
  }

  @Test
  public void testInitialStatistics() {
    // Note: Since these are static singletons, they might have been used by other tests
    // We test that the atomic values are accessible and non-null
    assertNotNull(tcpHandler.getInSize(), "TCP in size should not be null");
    assertNotNull(tcpHandler.getOutSize(), "TCP out size should not be null");
    assertNotNull(tcpHandler.getInPackets(), "TCP in packets should not be null");
    assertNotNull(tcpHandler.getOutPackets(), "TCP out packets should not be null");

    assertNotNull(udpHandler.getInSize(), "UDP in size should not be null");
    assertNotNull(udpHandler.getOutSize(), "UDP out size should not be null");
    assertNotNull(udpHandler.getInPackets(), "UDP in packets should not be null");
    assertNotNull(udpHandler.getOutPackets(), "UDP out packets should not be null");

    // Values should be non-negative
    assertTrue(tcpHandler.getInSize().get() >= 0, "TCP in size should be non-negative");
    assertTrue(tcpHandler.getOutSize().get() >= 0, "TCP out size should be non-negative");
    assertTrue(tcpHandler.getInPackets().get() >= 0, "TCP in packets should be non-negative");
    assertTrue(tcpHandler.getOutPackets().get() >= 0, "TCP out packets should be non-negative");

    assertTrue(udpHandler.getInSize().get() >= 0, "UDP in size should be non-negative");
    assertTrue(udpHandler.getOutSize().get() >= 0, "UDP out size should be non-negative");
    assertTrue(udpHandler.getInPackets().get() >= 0, "UDP in packets should be non-negative");
    assertTrue(udpHandler.getOutPackets().get() >= 0, "UDP out packets should be non-negative");
  }

  @Test
  public void testHandlerAnnotation() {
    // Verify that the handler is marked as @Sharable
    assertTrue(
        tcpHandler.getClass().isAnnotationPresent(io.netty.channel.ChannelHandler.Sharable.class),
        "TrafficStatHandler should be annotated with @Sharable");
  }

  @Test
  public void testAtomicOperations() {
    // Test that atomic operations work correctly
    long initialTcpInPackets = tcpHandler.getInPackets().get();
    long initialTcpOutPackets = tcpHandler.getOutPackets().get();

    // Simulate increment operations
    tcpHandler.getInPackets().incrementAndGet();
    tcpHandler.getOutPackets().incrementAndGet();

    assertEquals(
        initialTcpInPackets + 1,
        tcpHandler.getInPackets().get(),
        "TCP in packets should increment correctly");
    assertEquals(
        initialTcpOutPackets + 1,
        tcpHandler.getOutPackets().get(),
        "TCP out packets should increment correctly");
  }

  @Test
  public void testAtomicAddOperations() {
    // Test that atomic add operations work correctly
    long initialTcpInSize = tcpHandler.getInSize().get();
    long initialTcpOutSize = tcpHandler.getOutSize().get();

    // Simulate add operations
    tcpHandler.getInSize().addAndGet(1024);
    tcpHandler.getOutSize().addAndGet(2048);

    assertEquals(
        initialTcpInSize + 1024, tcpHandler.getInSize().get(), "TCP in size should add correctly");
    assertEquals(
        initialTcpOutSize + 2048,
        tcpHandler.getOutSize().get(),
        "TCP out size should add correctly");
  }

  @Test
  public void testSeparateTcpUdpCounters() {
    // Test that TCP and UDP counters are independent
    long initialTcpInPackets = tcpHandler.getInPackets().get();
    long initialUdpInPackets = udpHandler.getInPackets().get();

    // Increment TCP counter
    tcpHandler.getInPackets().incrementAndGet();

    // Verify TCP changed but UDP didn't
    assertEquals(
        initialTcpInPackets + 1,
        tcpHandler.getInPackets().get(),
        "TCP in packets should increment");
    assertEquals(
        initialUdpInPackets,
        udpHandler.getInPackets().get(),
        "UDP in packets should remain unchanged");
  }

  @Test
  public void testConcurrentAccess() throws InterruptedException {
    // Test that atomic operations work correctly under concurrent access
    int numThreads = 5;
    int operationsPerThread = 10;

    long initialInPackets = tcpHandler.getInPackets().get();

    Thread[] threads = new Thread[numThreads];

    for (int i = 0; i < numThreads; i++) {
      threads[i] =
          new Thread(
              () -> {
                for (int j = 0; j < operationsPerThread; j++) {
                  tcpHandler.getInPackets().incrementAndGet();
                  tcpHandler.getInSize().addAndGet(100);
                }
              });
    }

    // Start all threads
    for (Thread thread : threads) {
      thread.start();
    }

    // Wait for all threads to complete
    for (Thread thread : threads) {
      thread.join();
    }

    // Verify final counts
    long expectedIncrements = numThreads * operationsPerThread;
    assertEquals(
        initialInPackets + expectedIncrements,
        tcpHandler.getInPackets().get(),
        "In packets should increment correctly under concurrent access");
  }

  @Test
  public void testHandlerClassStructure() {
    // Test that the handler class has the expected structure
    assertTrue(
        tcpHandler instanceof io.netty.channel.ChannelDuplexHandler,
        "TrafficStatHandler should extend ChannelDuplexHandler");

    // Test that it's the same class for both TCP and UDP
    assertEquals(
        tcpHandler.getClass(),
        udpHandler.getClass(),
        "TCP and UDP handlers should be of the same class");
  }
}
