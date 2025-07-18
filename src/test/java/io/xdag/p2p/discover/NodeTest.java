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
import org.apache.tuweni.bytes.Bytes;
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
    p2pConfig.setIpV4("127.0.0.1");
    p2pConfig.setIpV6(null);

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
    p2pConfig.setIpV4(null);
    p2pConfig.setIpV6("fe80:0:0:0:204:61ff:fe9d:f157");

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
    p2pConfig.setIpV4("127.0.0.1");
    p2pConfig.setIpV6("fe80:0:0:0:204:61ff:fe9d:f157");

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

  @Test
  public void testUpdateHostV4() {
    Node node = new Node(p2pConfig, NetUtils.getNodeId(), null, "2001:db8::1", 10001);
    
    // Should update when hostV4 is empty
    node.updateHostV4("192.168.1.1");
    assertEquals("192.168.1.1", node.getHostV4());
    
    // Should not update when hostV4 is already set
    node.updateHostV4("10.0.0.1");
    assertEquals("192.168.1.1", node.getHostV4()); // Should remain unchanged
    
    // Should not update when new value is null or empty
    Node node2 = new Node(p2pConfig, NetUtils.getNodeId(), null, "2001:db8::1", 10002);
    node2.updateHostV4(null);
    assertNull(node2.getHostV4());
    
    node2.updateHostV4("");
    assertNull(node2.getHostV4());
  }

  @Test
  public void testUpdateHostV6() {
    Node node = new Node(p2pConfig, NetUtils.getNodeId(), "192.168.1.1", null, 10001);
    
    // Should update when hostV6 is empty
    node.updateHostV6("2001:db8::1");
    assertEquals("2001:db8::1", node.getHostV6());
    
    // Should not update when hostV6 is already set
    node.updateHostV6("2001:db8::2");
    assertEquals("2001:db8::1", node.getHostV6()); // Should remain unchanged
    
    // Should not update when new value is null or empty
    Node node2 = new Node(p2pConfig, NetUtils.getNodeId(), "192.168.1.1", null, 10002);
    node2.updateHostV6(null);
    assertNull(node2.getHostV6());
    
    node2.updateHostV6("");
    assertNull(node2.getHostV6());
  }

  @Test
  public void testGetHexId() {
    Bytes nodeId = Bytes.fromHexString("0x1234567890abcdef");
    Node node = new Node(p2pConfig, nodeId, "127.0.0.1", null, 10001);
    
    String hexId = node.getHexId();
    assertEquals("1234567890abcdef", hexId);
    
    // Test with null id
    Node nodeWithNullId = new Node(p2pConfig, null, "127.0.0.1", null, 10001);
    assertNull(nodeWithNullId.getHexId());
  }

  @Test
  public void testGetIdString() {
    String testString = "test-node-id";
    Bytes nodeId = Bytes.wrap(testString.getBytes());
    Node node = new Node(p2pConfig, nodeId, "127.0.0.1", null, 10001);
    
    String idString = node.getIdString();
    assertEquals(testString, idString);
    
    // Test with null id
    Node nodeWithNullId = new Node(p2pConfig, null, "127.0.0.1", null, 10001);
    assertNull(nodeWithNullId.getIdString());
  }

  @Test
  public void testGetHostKey() {
    // Test with valid IPv4
    Node node1 = new Node(p2pConfig, NetUtils.getNodeId(), "127.0.0.1", null, 10001);
    assertEquals("127.0.0.1", node1.getHostKey());
    
    // Test with valid IPv6
    Node node2 = new Node(p2pConfig, NetUtils.getNodeId(), null, "::1", 10001);
    assertEquals("0:0:0:0:0:0:0:1", node2.getHostKey());
    
    // Test with no valid address
    Node node3 = new Node(p2pConfig, NetUtils.getNodeId(), null, null, 10001);
    assertNull(node3.getHostKey());
  }

  @Test
  public void testGetInetSocketAddressV4() {
    Node node = new Node(p2pConfig, NetUtils.getNodeId(), "127.0.0.1", null, 10001);
    InetSocketAddress address = node.getInetSocketAddressV4();
    
    assertNotNull(address);
    assertEquals("127.0.0.1", address.getAddress().getHostAddress());
    assertEquals(10001, address.getPort());
  }

  @Test
  public void testGetInetSocketAddressV6() {
    Node node = new Node(p2pConfig, NetUtils.getNodeId(), null, "::1", 10001);
    InetSocketAddress address = node.getInetSocketAddressV6();
    
    assertNotNull(address);
    assertEquals("0:0:0:0:0:0:0:1", address.getAddress().getHostAddress());
    assertEquals(10001, address.getPort());
  }

  @Test
  public void testClone() {
    Bytes nodeId = NetUtils.getNodeId();
    Node original = new Node(p2pConfig, nodeId, "127.0.0.1", "::1", 10001, 10002);
    original.setP2pVersion(12345);
    
    Node cloned = (Node) original.clone();
    
    // Verify cloned node has same values
    assertEquals(original.getId(), cloned.getId());
    assertEquals(original.getHostV4(), cloned.getHostV4());
    assertEquals(original.getHostV6(), cloned.getHostV6());
    assertEquals(original.getPort(), cloned.getPort());
    assertEquals(original.getBindPort(), cloned.getBindPort());
    assertEquals(original.getP2pVersion(), cloned.getP2pVersion());
    
    // Verify it's a different object
    assertNotEquals(System.identityHashCode(original), System.identityHashCode(cloned));
  }

  @Test
  public void testEqualsAndHashCode() {
    // Use string-based IDs for proper equals comparison
    String idString1 = "test-node-1";
    String idString2 = "test-node-1"; // Same as 1
    String idString3 = "test-node-2"; // Different
    
    Bytes nodeId1 = Bytes.wrap(idString1.getBytes());
    Bytes nodeId2 = Bytes.wrap(idString2.getBytes());
    Bytes nodeId3 = Bytes.wrap(idString3.getBytes());
    
    Node node1 = new Node(p2pConfig, nodeId1, "127.0.0.1", null, 10001);
    Node node2 = new Node(p2pConfig, nodeId2, "127.0.0.1", null, 10001);
    Node node3 = new Node(p2pConfig, nodeId3, "127.0.0.1", null, 10001);
    Node node4 = new Node(p2pConfig, nodeId1, "192.168.1.1", null, 10002);
    
    // Test equals - Node.equals() compares based on getIdString() which is UTF-8 string representation
    assertEquals(node1, node1); // Same reference
    assertEquals(node1, node2); // Same ID string
    assertNotEquals(node1, node3); // Different ID string
    assertEquals(node1, node4); // Same ID string, equals ignores host/port differences
    assertNotEquals(null, node1); // Null comparison
    assertNotEquals("not a node", node1); // Different type
    
    // Test hashCode consistency - hashCode is based on format() which includes host/port
    assertEquals(node1.hashCode(), node1.hashCode()); // Same object
    assertNotEquals(node1.hashCode(), node4.hashCode()); // Same ID but different host/port
  }

  @Test
  public void testToString() {
    Bytes nodeId = Bytes.fromHexString("0x1234567890abcdef");
    Node node = new Node(p2pConfig, nodeId, "127.0.0.1", "0:0:0:0:0:0:0:1", 10001);
    
    String toString = node.toString();
    
    assertTrue(toString.contains("hostV4='127.0.0.1'"));
    assertTrue(toString.contains("hostV6='0:0:0:0:0:0:0:1'"));
    assertTrue(toString.contains("port=10001"));
    assertTrue(toString.contains("id="));
    
    // Test with null ID
    Node nodeWithNullId = new Node(p2pConfig, null, "127.0.0.1", null, 10001);
    String toStringWithNullId = nodeWithNullId.toString();
    assertTrue(toStringWithNullId.contains("id='null'"));
  }

  @Test
  public void testFormat() {
    Node node = new Node(p2pConfig, NetUtils.getNodeId(), "127.0.0.1", "0:0:0:0:0:0:0:1", 10001);
    
    String format = node.format();
    
    assertTrue(format.contains("hostV4='127.0.0.1'"));
    assertTrue(format.contains("hostV6='0:0:0:0:0:0:0:1'"));
    assertTrue(format.contains("port=10001"));
  }

  @Test
  public void testNodeConstructorWithInetSocketAddress() {
    // Test with resolved address
    InetSocketAddress address = new InetSocketAddress("127.0.0.1", 10001);
    Node node = new Node(p2pConfig, address);
    
    assertEquals("127.0.0.1", node.getHostV4());
    assertEquals(10001, node.getPort());
    assertEquals(10001, node.getBindPort());
    assertNotNull(node.getId());
    assertTrue(node.getUpdateTime() > 0);
    
    // Test constructor behavior with hostname that might not resolve
    // This is harder to test deterministically as it depends on DNS resolution
  }
}
