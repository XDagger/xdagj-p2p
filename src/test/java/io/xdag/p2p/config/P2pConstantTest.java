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
package io.xdag.p2p.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for P2pConstant class. Tests all constant values and their validity. */
public class P2pConstantTest {

  @Test
  public void testNodeIdLength() {
    assertEquals(64, P2pConstant.NODE_ID_LEN);
    assertTrue(P2pConstant.NODE_ID_LEN > 0);
  }

  @Test
  public void testIpV4Urls() {
    assertNotNull(P2pConstant.ipV4Urls);
    assertFalse(P2pConstant.ipV4Urls.isEmpty());
    assertEquals(3, P2pConstant.ipV4Urls.size());

    // Verify all URLs are valid strings
    for (String url : P2pConstant.ipV4Urls) {
      assertNotNull(url);
      assertFalse(url.trim().isEmpty());
      assertTrue(url.startsWith("http"));
    }

    // Verify specific URLs
    assertTrue(P2pConstant.ipV4Urls.contains("http://checkip.amazonaws.com"));
    assertTrue(P2pConstant.ipV4Urls.contains("https://ifconfig.me/"));
    assertTrue(P2pConstant.ipV4Urls.contains("https://4.ipw.cn/"));
  }

  @Test
  public void testIpV6Urls() {
    assertNotNull(P2pConstant.ipV6Urls);
    assertFalse(P2pConstant.ipV6Urls.isEmpty());
    assertEquals(2, P2pConstant.ipV6Urls.size());

    // Verify all URLs are valid strings
    for (String url : P2pConstant.ipV6Urls) {
      assertNotNull(url);
      assertFalse(url.trim().isEmpty());
      assertTrue(url.startsWith("http"));
    }

    // Verify specific URLs
    assertTrue(P2pConstant.ipV6Urls.contains("https://v6.ident.me"));
    assertTrue(P2pConstant.ipV6Urls.contains("http://6.ipw.cn/"));
  }

  @Test
  public void testIpHexValues() {
    assertNotNull(P2pConstant.ipV4Hex);
    assertEquals("00000000", P2pConstant.ipV4Hex);
    assertEquals(8, P2pConstant.ipV4Hex.length()); // 32 bits = 8 hex chars

    assertNotNull(P2pConstant.ipV6Hex);
    assertEquals("00000000000000000000000000000000", P2pConstant.ipV6Hex);
    assertEquals(32, P2pConstant.ipV6Hex.length()); // 128 bits = 32 hex chars
  }

  @Test
  public void testVersionValue() {
    assertEquals(1, P2pConstant.version);
    assertTrue(P2pConstant.version > 0);
  }

  @Test
  public void testNettyThreadNumbers() {
    assertEquals(0, P2pConstant.TCP_NETTY_WORK_THREAD_NUM);
    assertEquals(1, P2pConstant.UDP_NETTY_WORK_THREAD_NUM);
    assertTrue(P2pConstant.UDP_NETTY_WORK_THREAD_NUM >= 0);
  }

  @Test
  public void testTimeoutValues() {
    assertEquals(2000, P2pConstant.NODE_CONNECTION_TIMEOUT);
    assertEquals(20_000, P2pConstant.KEEP_ALIVE_TIMEOUT);
    assertEquals(20_000, P2pConstant.PING_TIMEOUT);
    assertEquals(1000, P2pConstant.NETWORK_TIME_DIFF);
    assertEquals(60_000, P2pConstant.DEFAULT_BAN_TIME);

    // All timeout values should be positive
    assertTrue(P2pConstant.NODE_CONNECTION_TIMEOUT > 0);
    assertTrue(P2pConstant.KEEP_ALIVE_TIMEOUT > 0);
    assertTrue(P2pConstant.PING_TIMEOUT > 0);
    assertTrue(P2pConstant.NETWORK_TIME_DIFF > 0);
    assertTrue(P2pConstant.DEFAULT_BAN_TIME > 0);
  }

  @Test
  public void testMessageLength() {
    assertEquals(5 * 1024 * 1024, P2pConstant.MAX_MESSAGE_LENGTH);
    assertTrue(P2pConstant.MAX_MESSAGE_LENGTH > 0);
    assertEquals(5242880, P2pConstant.MAX_MESSAGE_LENGTH); // 5MB
  }

  @Test
  public void testNodeDetectionValues() {
    assertEquals(5 * 60 * 1000, P2pConstant.NODE_DETECT_THRESHOLD);
    assertEquals(30 * 1000, P2pConstant.NODE_DETECT_MIN_THRESHOLD);
    assertEquals(2 * 1000, P2pConstant.NODE_DETECT_TIMEOUT);

    // All detection values should be positive
    assertTrue(P2pConstant.NODE_DETECT_THRESHOLD > 0);
    assertTrue(P2pConstant.NODE_DETECT_MIN_THRESHOLD > 0);
    assertTrue(P2pConstant.NODE_DETECT_TIMEOUT > 0);

    // Logical relationships
    assertTrue(P2pConstant.NODE_DETECT_THRESHOLD > P2pConstant.NODE_DETECT_MIN_THRESHOLD);
  }

  @Test
  public void testMaxNodeDetectionValues() {
    assertEquals(3, P2pConstant.MAX_NODE_SLOW_DETECT);
    assertEquals(10, P2pConstant.MAX_NODE_NORMAL_DETECT);
    assertEquals(100, P2pConstant.MAX_NODE_FAST_DETECT);

    // All should be positive
    assertTrue(P2pConstant.MAX_NODE_SLOW_DETECT > 0);
    assertTrue(P2pConstant.MAX_NODE_NORMAL_DETECT > 0);
    assertTrue(P2pConstant.MAX_NODE_FAST_DETECT > 0);

    // Logical relationships
    assertTrue(P2pConstant.MAX_NODE_FAST_DETECT > P2pConstant.MAX_NODE_NORMAL_DETECT);
    assertTrue(P2pConstant.MAX_NODE_NORMAL_DETECT > P2pConstant.MAX_NODE_SLOW_DETECT);
  }

  @Test
  public void testNodeLimits() {
    assertEquals(300, P2pConstant.MAX_NODES);
    assertEquals(200, P2pConstant.MIN_NODES);

    // Both should be positive
    assertTrue(P2pConstant.MAX_NODES > 0);
    assertTrue(P2pConstant.MIN_NODES > 0);

    // Logical relationship
    assertTrue(P2pConstant.MAX_NODES > P2pConstant.MIN_NODES);
  }
}
