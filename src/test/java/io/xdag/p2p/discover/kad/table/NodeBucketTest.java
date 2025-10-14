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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.xdag.p2p.discover.Node;
import java.security.SecureRandom;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NodeBucketTest {

    private NodeBucket bucket;
    private Bytes ownerId;
    private int ipCounter = 1;

    private final SecureRandom random = new SecureRandom();

    @BeforeEach
    public void setUp() {
        bucket = new NodeBucket(0);
        ownerId = Bytes.random(20);
    }

    private NodeEntry createUniqueNodeEntry() {
        String ip = "127.0.0." + ipCounter++;
        Node node = new Node(Bytes.random(20).toUnprefixedHexString(), ip, null, 30303, 30303);
        return new NodeEntry(ownerId, node);
    }

    @Test
    public void testAddNodeNotFull() {
        NodeEntry entry1 = createUniqueNodeEntry();
        assertNull(bucket.addNode(entry1));
        assertEquals(1, bucket.getNodesCount());
        assertEquals(entry1, bucket.getNodes().getFirst());
    }

    @Test
    public void testAddNodeDuplicate() {
        NodeEntry entry1 = createUniqueNodeEntry();
        bucket.addNode(entry1);

        assertNull(bucket.addNode(entry1));
        assertEquals(1, bucket.getNodesCount());
    }

    @Test
    public void testAddNodeFull() {
        for (int i = 0; i < KademliaOptions.BUCKET_SIZE; i++) {
            assertNull(bucket.addNode(createUniqueNodeEntry()));
        }
        assertEquals(KademliaOptions.BUCKET_SIZE, bucket.getNodesCount());

        NodeEntry newEntry = createUniqueNodeEntry();
        NodeEntry evicted = bucket.addNode(newEntry);

        assertNotNull(evicted);
        assertEquals(KademliaOptions.BUCKET_SIZE, bucket.getNodesCount());
    }

    @Test
    public void testDropNode() {
        NodeEntry entry1 = createUniqueNodeEntry();
        NodeEntry entry2 = createUniqueNodeEntry();

        bucket.addNode(entry1);
        bucket.addNode(entry2);
        assertEquals(2, bucket.getNodesCount());

        bucket.dropNode(entry1);
        assertEquals(1, bucket.getNodesCount());
        assertEquals(entry2, bucket.getNodes().getFirst());

        bucket.dropNode(entry1);
        assertEquals(1, bucket.getNodesCount());
    }
}
