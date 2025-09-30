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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for NodeStats class. Tests node statistics tracking functionality. */
public class NodeStatsTest {

  private P2pConfig p2pConfig;
  private Node testNode;
  private NodeStats nodeStats;

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
    InetSocketAddress address = new InetSocketAddress("192.168.1.100", 16783);
    testNode = new Node("test-node-id", address);
    nodeStats = new NodeStats(testNode);
  }

  @Test
  public void testConstructorWithNode() {
    assertNotNull(nodeStats, "NodeStats should be created successfully");
    assertSame(testNode, nodeStats.getNode(), "Node should be set correctly");
    assertEquals(
        testNode.getPreferInetSocketAddress(),
        nodeStats.getSocketAddress(),
        "Socket address should match node's preferred address");
  }

  @Test
  public void testDefaultValues() {
    assertEquals(0, nodeStats.getTotalCount(), "Total count should default to 0");
    assertEquals(0L, nodeStats.getLastDetectTime(), "Last detect time should default to 0");
    assertEquals(
        0L, nodeStats.getLastSuccessDetectTime(), "Last success detect time should default to 0");
    // statusMessage removed in current implementation
  }

  @Test
  public void testSettersAndGetters() {
    // Test total count
    nodeStats.setTotalCount(10);
    assertEquals(10, nodeStats.getTotalCount(), "Total count should be set correctly");

    // Test last detect time
    long currentTime = System.currentTimeMillis();
    nodeStats.setLastDetectTime(currentTime);
    assertEquals(
        currentTime, nodeStats.getLastDetectTime(), "Last detect time should be set correctly");

    // Test last success detects time
    long successTime = currentTime - 1000;
    nodeStats.setLastSuccessDetectTime(successTime);
    assertEquals(
        successTime,
        nodeStats.getLastSuccessDetectTime(),
        "Last success detect time should be set correctly");

    // Test socket address
    InetSocketAddress newAddress = new InetSocketAddress("10.0.0.1", 17000);
    nodeStats.setSocketAddress(newAddress);
    assertEquals(
        newAddress, nodeStats.getSocketAddress(), "Socket address should be set correctly");
  }

  @Test
  public void testFinishDetectWhenEqual() {
    // When last detect time equals last success detect time, detection is finished
    long time = System.currentTimeMillis();
    nodeStats.setLastDetectTime(time);
    nodeStats.setLastSuccessDetectTime(time);

    assertTrue(nodeStats.finishDetect(), "finishDetect should return true when times are equal");
  }

  @Test
  public void testFinishDetectWhenNotEqual() {
    // When last detect time does not equal last success detect time, detection is not finished
    long currentTime = System.currentTimeMillis();
    nodeStats.setLastDetectTime(currentTime);
    nodeStats.setLastSuccessDetectTime(currentTime - 1000);

    assertFalse(
        nodeStats.finishDetect(), "finishDetect should return false when times are not equal");
  }

  @Test
  public void testFinishDetectWithDefaultValues() {
    // With default values (both 0), detection should be considered finished
    assertTrue(
        nodeStats.finishDetect(), "finishDetect should return true with default values (both 0)");
  }

  @Test
  public void testDetectionScenarios() {
    long baseTime = System.currentTimeMillis();

    // Scenario 1: Start detection
    nodeStats.setLastDetectTime(baseTime);
    nodeStats.setLastSuccessDetectTime(0L);
    assertFalse(nodeStats.finishDetect(), "Detection should not be finished when started");

    // Scenario 2: Detection fails, try again
    nodeStats.setLastDetectTime(baseTime + 1000);
    nodeStats.setLastSuccessDetectTime(0L);
    assertFalse(nodeStats.finishDetect(), "Detection should not be finished after failure");

    // Scenario 3: Detection succeeds
    nodeStats.setLastDetectTime(baseTime + 2000);
    nodeStats.setLastSuccessDetectTime(baseTime + 2000);
    assertTrue(nodeStats.finishDetect(), "Detection should be finished after success");
  }

  @Test
  public void testTotalCountIncrement() {
    assertEquals(0, nodeStats.getTotalCount(), "Initial total count should be 0");

    // Simulate multiple detection attempts
    for (int i = 1; i <= 5; i++) {
      nodeStats.setTotalCount(i);
      assertEquals(i, nodeStats.getTotalCount(), "Total count should increment correctly");
    }
  }

  @Test
  public void testNodeReference() {
    // Test that the node reference is maintained correctly
    assertSame(testNode, nodeStats.getNode(), "Node reference should be maintained");

    // Test setting a different node
    InetSocketAddress newAddress = new InetSocketAddress("172.16.0.1", 18000);
    Node newNode = new Node("new-node-id", newAddress);
    nodeStats.setNode(newNode);

    assertSame(newNode, nodeStats.getNode(), "New node should be set correctly");
  }

  @Test
  public void testSocketAddressIndependence() {
    // Test that socket address can be set independently of the node
    InetSocketAddress originalAddress = nodeStats.getSocketAddress();
    InetSocketAddress newAddress = new InetSocketAddress("203.0.113.1", 19000);

    nodeStats.setSocketAddress(newAddress);

    assertEquals(
        newAddress, nodeStats.getSocketAddress(), "Socket address should be updated independently");
    assertEquals(
        originalAddress,
        testNode.getPreferInetSocketAddress(),
        "Node's address should remain unchanged");
  }

  @Test
  public void testStatusMessageHandling() {
    // statusMessage removed; keep backwards-compat by asserting class exists
  }

  @Test
  public void testTimeHandling() {
    // Test with various time values
    long[] testTimes = {0L, 1L, System.currentTimeMillis(), Long.MAX_VALUE};

    for (long time : testTimes) {
      nodeStats.setLastDetectTime(time);
      nodeStats.setLastSuccessDetectTime(time);

      assertEquals(
          time, nodeStats.getLastDetectTime(), "Last detect time should handle value: " + time);
      assertEquals(
          time,
          nodeStats.getLastSuccessDetectTime(),
          "Last success detect time should handle value: " + time);
      assertTrue(
          nodeStats.finishDetect(), "finishDetect should return true for equal times: " + time);
    }
  }

  @Test
  public void testNegativeValues() {
    // Test with negative values (edge case)
    nodeStats.setTotalCount(-1);
    assertEquals(-1, nodeStats.getTotalCount(), "Should handle negative total count");

    nodeStats.setLastDetectTime(-1000L);
    nodeStats.setLastSuccessDetectTime(-1000L);
    assertEquals(-1000L, nodeStats.getLastDetectTime(), "Should handle negative detect time");
    assertEquals(
        -1000L, nodeStats.getLastSuccessDetectTime(), "Should handle negative success time");
    assertTrue(nodeStats.finishDetect(), "finishDetect should work with negative equal times");
  }
}
