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

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

public class NodeTest {

    private final SecureRandom random = new SecureRandom();

    private String getRandomNodeId() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    public void testNodeEqualsAndHashCode() {
        String id1 = getRandomNodeId();
        Node node1 = new Node(id1, "127.0.0.1", null, 10001);
        Node node2 = new Node(id1, "127.0.0.1", null, 10001);
        Node node3 = new Node(getRandomNodeId(), "127.0.0.1", null, 10001);

        assertEquals(node1, node2);
        assertNotEquals(node1, node3);
        assertEquals(node1.hashCode(), node2.hashCode());
    }

    @Test
    public void testTouch() throws InterruptedException {
        Node node = new Node(getRandomNodeId(), "127.0.0.1", null, 10001);
        long lastModifyTime = node.getUpdateTime();
        Thread.sleep(1);
        node.touch();
        assertNotEquals(lastModifyTime, node.getUpdateTime());
    }

    @Test
    public void testIsConnectible() {
        Node node1 = new Node(getRandomNodeId(), "127.0.0.1", null, 10001, 10001);
        node1.setNetworkId((byte)1);
        assertTrue(node1.isConnectible((byte)1));
        assertFalse(node1.isConnectible((byte)2));

        Node node2 = new Node(getRandomNodeId(), "127.0.0.1", null, 10001, 10002);
        node2.setNetworkId((byte)1);
        assertFalse(node2.isConnectible((byte)1));
    }


    @Test
    public void testGetPreferInetSocketAddress() {
        Node node1 = new Node(getRandomNodeId(), "127.0.0.1", null, 10002);
        assertNotNull(node1.getPreferInetSocketAddress());

        Node node2 = new Node(getRandomNodeId(), null, "fe80:0:0:0:204:61ff:fe9d:f156", 10002);
        assertNotNull(node2.getPreferInetSocketAddress());

        Node node3 = new Node(getRandomNodeId(), "127.0.0.1", "fe80:0:0:0:204:61ff:fe9d:f156", 10002);
        assertNotNull(node3.getPreferInetSocketAddress());

        Node node4 = new Node(getRandomNodeId(), null, null, 10002);
        assertNull(node4.getPreferInetSocketAddress());
    }


    @Test
    public void testUpdateHostV4() {
        Node node = new Node(getRandomNodeId(), null, "2001:db8::1", 10001);

        node.updateHostV4("192.168.1.1");
        assertEquals("192.168.1.1", node.getHostV4());

        node.updateHostV4("10.0.0.1");
        assertEquals("192.168.1.1", node.getHostV4());

        Node node2 = new Node(getRandomNodeId(), null, "2001:db8::1", 10002);
        node2.updateHostV4(null);
        assertNull(node2.getHostV4());

        node2.updateHostV4("");
        assertNull(node2.getHostV4());
    }

    @Test
    public void testUpdateHostV6() {
        Node node = new Node(getRandomNodeId(), "192.168.1.1", null, 10001);

        node.updateHostV6("2001:db8::1");
        assertEquals("2001:db8::1", node.getHostV6());

        node.updateHostV6("2001:db8::2");
        assertEquals("2001:db8::1", node.getHostV6());

        Node node2 = new Node(getRandomNodeId(), "192.168.1.1", null, 10002);
        node2.updateHostV6(null);
        assertNull(node2.getHostV6());

        node2.updateHostV6("");
        assertNull(node2.getHostV6());
    }

    @Test
    public void testGetId() {
        String nodeId = getRandomNodeId();
        Node node = new Node(nodeId, "127.0.0.1", null, 10001);
        assertEquals(nodeId, node.getId());

        Node nodeWithNullId = new Node(null, "127.0.0.1", null, 10001);
        assertNull(nodeWithNullId.getId());
    }

    @Test
    public void testClone() throws CloneNotSupportedException {
        Node original = new Node(getRandomNodeId(), "127.0.0.1", "::1", 10001, 10002);
        original.setNetworkVersion((short)12345);

        Node cloned = (Node) original.clone();

        assertEquals(original.getId(), cloned.getId());
        assertEquals(original.getHostV4(), cloned.getHostV4());
        assertEquals(original.getHostV6(), cloned.getHostV6());
        assertEquals(original.getPort(), cloned.getPort());
        assertEquals(original.getBindPort(), cloned.getBindPort());
        assertEquals(original.getNetworkVersion(), cloned.getNetworkVersion());

        assertNotEquals(System.identityHashCode(original), System.identityHashCode(cloned));
    }


    @Test
    public void testNodeConstructorWithInetSocketAddress() {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 10001);
        Node node = new Node(getRandomNodeId(), address);

        assertEquals("127.0.0.1", node.getHostV4());
        assertEquals(10001, node.getPort());
        assertEquals(10001, node.getBindPort());
        assertNotNull(node.getId());
        assertTrue(node.getUpdateTime() > 0);
    }
}
