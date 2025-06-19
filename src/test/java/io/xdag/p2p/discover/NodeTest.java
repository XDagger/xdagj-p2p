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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.utils.NetUtils;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NodeTest {

  private P2pConfig p2pConfig;

  @BeforeEach
  public void init() {
    p2pConfig = new P2pConfig();
    p2pConfig = new P2pConfig();
  }

  @Test
  public void nodeTest() throws InterruptedException {
    Node node1 = new Node(p2pConfig, new InetSocketAddress("127.0.0.1", 10001));
    assertEquals(64, node1.getId().size());

    Node node2 = new Node(p2pConfig, NetUtils.getNodeId(), "127.0.0.1", null, 10002);
    boolean isDif = node1.equals(node2);
    assertFalse(isDif);

    long lastModifyTime = node1.getUpdateTime();
    Thread.sleep(1);
    node1.touch();
    assertNotEquals(lastModifyTime, node1.getUpdateTime());

    node1.setP2pVersion(11111);
    assertTrue(node1.isConnectible(11111));
    assertFalse(node1.isConnectible(11112));
    Node node3 = new Node(p2pConfig, NetUtils.getNodeId(), "127.0.0.1", null, 10003, 10004);
    node3.setP2pVersion(11111);
    assertFalse(node3.isConnectible(11111));
  }

  @Test
  public void ipV4CompatibleTest() {
    p2pConfig.setIp("127.0.0.1");
    p2pConfig.setIpv6(null);

    Node node1 = new Node(p2pConfig, NetUtils.getNodeId(), "127.0.0.1", null, 10002);
    assertNotNull(node1.getPreferInetSocketAddress());

    // Fallback机制：即使本地配置只有IPv4，但节点有IPv6地址时仍然可以连接
    Node node2 =
        new Node(p2pConfig, NetUtils.getNodeId(), null, "fe80:0:0:0:204:61ff:fe9d:f156", 10002);
    assertNotNull(node2.getPreferInetSocketAddress()); // Fallback返回IPv6地址

    Node node3 =
        new Node(
            p2pConfig, NetUtils.getNodeId(), "127.0.0.1", "fe80:0:0:0:204:61ff:fe9d:f156", 10002);
    assertNotNull(node3.getPreferInetSocketAddress());
  }

  @Test
  public void ipV6CompatibleTest() {
    p2pConfig.setIp(null);
    p2pConfig.setIpv6("fe80:0:0:0:204:61ff:fe9d:f157");

    // Fallback机制：即使本地配置只有IPv6，但节点有IPv4地址时仍然可以连接
    Node node1 = new Node(p2pConfig, NetUtils.getNodeId(), "127.0.0.1", null, 10002);
    assertNotNull(node1.getPreferInetSocketAddress()); // Fallback返回IPv4地址

    Node node2 =
        new Node(p2pConfig, NetUtils.getNodeId(), null, "fe80:0:0:0:204:61ff:fe9d:f156", 10002);
    assertNotNull(node2.getPreferInetSocketAddress());

    Node node3 =
        new Node(
            p2pConfig, NetUtils.getNodeId(), "127.0.0.1", "fe80:0:0:0:204:61ff:fe9d:f156", 10002);
    assertNotNull(node3.getPreferInetSocketAddress());
  }

  @Test
  public void ipCompatibleTest() {
    p2pConfig.setIp("127.0.0.1");
    p2pConfig.setIpv6("fe80:0:0:0:204:61ff:fe9d:f157");

    Node node1 = new Node(p2pConfig, NetUtils.getNodeId(), "127.0.0.1", null, 10002);
    assertNotNull(node1.getPreferInetSocketAddress());

    Node node2 =
        new Node(p2pConfig, NetUtils.getNodeId(), null, "fe80:0:0:0:204:61ff:fe9d:f156", 10002);
    assertNotNull(node2.getPreferInetSocketAddress());

    Node node3 =
        new Node(
            p2pConfig, NetUtils.getNodeId(), "127.0.0.1", "fe80:0:0:0:204:61ff:fe9d:f156", 10002);
    assertNotNull(node3.getPreferInetSocketAddress());

    Node node4 = new Node(p2pConfig, NetUtils.getNodeId(), null, null, 10002);
    assertNull(node4.getPreferInetSocketAddress());
  }
}
