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
package io.xdag.p2p.discover.kad.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.discover.Node;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NodeTableTest {
    private final SecureRandom random = new SecureRandom();
    private Node homeNode;
    private NodeTable nodeTable;
    private List<Node> nodes;

    private Bytes getRandomNodeIdBytes() {
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        return Bytes.wrap(bytes);
    }

    private String getRandomNodeId() {
        return getRandomNodeIdBytes().toUnprefixedHexString();
    }

    @BeforeEach
    public void init() {
        homeNode = new Node(getRandomNodeId(), new InetSocketAddress("127.0.0.1", 10001));
        nodeTable = new NodeTable(homeNode);
        nodes = new ArrayList<>();
        for (int i = 0; i < KademliaOptions.BUCKET_SIZE + 1; i++) {
            nodes.add(new Node(getRandomNodeId(), new InetSocketAddress("127.0.0.1", 10002 + i)));
        }
        // Force all nodes into the same bucket to trigger eviction
        homeNode.setId("0".repeat(128));
        for (int i = 0; i < nodes.size(); i++) {
            nodes.get(i).setId(buildHighBitId(i));
        }
    }

    private String buildHighBitId(int suffix) {
        // Build 64-byte (128 hex chars) id starting with 0x80 to ensure leadingZeroBits = 0
        StringBuilder sb = new StringBuilder(128);
        sb.append("80");
        for (int i = 0; i < 62; i++) {
            sb.append("00");
        }
        sb.append(String.format("%02x", suffix & 0xFF));
        return sb.toString();
    }

    @Test
    public void testAddNode() {
        Node node = nodes.getFirst();
        assertEquals(0, nodeTable.getNodesCount());
        nodeTable.addNode(node);
        assertEquals(1, nodeTable.getNodesCount());
        assertTrue(nodeTable.contains(node));
    }

    @Test
    public void testAddDuplicateNode() throws Exception {
        Node node = nodes.getFirst();
        nodeTable.addNode(node);
        long firstTouchTime = nodeTable.getAllNodes().getFirst().getModified();
        TimeUnit.MILLISECONDS.sleep(20);
        nodeTable.addNode(node);
        long lastTouchTime = nodeTable.getAllNodes().getFirst().getModified();
        assertTrue(lastTouchTime > firstTouchTime);
        assertEquals(1, nodeTable.getNodesCount());
    }

    @Test
    public void testAddNodeBucketFull() throws Exception {
        for (int i = 0; i < KademliaOptions.BUCKET_SIZE; i++) {
            TimeUnit.MILLISECONDS.sleep(10);
            nodeTable.addNode(nodes.get(i));
        }
        Node lastNode = nodes.get(KademliaOptions.BUCKET_SIZE);
        Node evicted = nodeTable.addNode(lastNode);
        assertNotNull(evicted);
    }

    @Test
    public void testDropNode() {
        Node node = nodes.getFirst();
        nodeTable.addNode(node);
        assertTrue(nodeTable.contains(node));
        nodeTable.dropNode(node);
        assertFalse(nodeTable.contains(node));
    }

    @Test
    public void testGetClosestNodes() {
        for (Node node : nodes) {
            nodeTable.addNode(node);
        }
        List<Node> closest = nodeTable.getClosestNodes(Bytes.wrap(homeNode.getId().getBytes()));
        assertEquals(KademliaOptions.BUCKET_SIZE, closest.size());
    }
}
