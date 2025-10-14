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
package io.xdag.p2p.message.discover;

import static org.junit.jupiter.api.Assertions.*;

import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.utils.SimpleDecoder;
import io.xdag.p2p.utils.SimpleEncoder;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for KadNeighborsMessage class.
 * Tests encoding/decoding, neighbor list handling, and message creation.
 */
public class KadNeighborsMessageTest {

    private Node fromNode;
    private List<Node> neighbors;

    @BeforeEach
    public void setUp() {
        // Create a from node
        String fromId = Bytes.random(20).toHexString();
        fromNode = new Node(fromId, new InetSocketAddress("127.0.0.1", 10001));
        fromNode.setNetworkId((byte) P2pConstant.MAINNET_ID);
        fromNode.setNetworkVersion((short) P2pConstant.MAINNET_VERSION);
        
        // Create neighbor nodes
        neighbors = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String neighborId = Bytes.random(20).toHexString();
            Node neighbor = new Node(neighborId, new InetSocketAddress("192.168.1." + (100 + i), 10002 + i));
            neighbor.setNetworkId((byte) P2pConstant.MAINNET_ID);
            neighbor.setNetworkVersion((short) P2pConstant.MAINNET_VERSION);
            neighbors.add(neighbor);
        }
    }

    @Test
    public void testMessageCreation() {
        // Test creating a new KadNeighborsMessage
        KadNeighborsMessage msg = new KadNeighborsMessage(fromNode, neighbors);
        
        assertNotNull(msg);
        assertEquals(MessageCode.KAD_NEIGHBORS, msg.getCode());
        assertEquals(MessageCode.KAD_NEIGHBORS, msg.getType());
        assertNull(msg.getResponseMessageClass());
        assertNotNull(msg.getFrom());
        assertNotNull(msg.getNeighbors());
        assertEquals(5, msg.getNeighbors().size());
        assertTrue(msg.getTimestamp() > 0);
        assertNotNull(msg.getBody());
        assertTrue(msg.getBody().length > 0);
    }

    @Test
    public void testFromNodeIsSet() {
        // Test that from node is correctly set
        KadNeighborsMessage msg = new KadNeighborsMessage(fromNode, neighbors);
        
        assertNotNull(msg.getFrom());
        assertEquals(fromNode.getId(), msg.getFrom().getId());
        assertEquals(fromNode.getHostV4(), msg.getFrom().getHostV4());
        assertEquals(fromNode.getPort(), msg.getFrom().getPort());
    }

    @Test
    public void testNeighborsListIsSet() {
        // Test that neighbors list is correctly set
        KadNeighborsMessage msg = new KadNeighborsMessage(fromNode, neighbors);
        
        assertNotNull(msg.getNeighbors());
        assertEquals(5, msg.getNeighbors().size());
        
        for (int i = 0; i < neighbors.size(); i++) {
            assertEquals(neighbors.get(i).getId(), msg.getNeighbors().get(i).getId());
        }
    }

    @Test
    public void testTimestampIsSet() {
        // Test that timestamp is automatically set on creation
        long beforeCreation = System.currentTimeMillis();
        KadNeighborsMessage msg = new KadNeighborsMessage(fromNode, neighbors);
        long afterCreation = System.currentTimeMillis();
        
        assertTrue(msg.getTimestamp() >= beforeCreation);
        assertTrue(msg.getTimestamp() <= afterCreation);
    }

    @Test
    public void testEncodeDecodeRoundTrip() {
        // Test that encoding and then decoding produces equivalent message
        KadNeighborsMessage original = new KadNeighborsMessage(fromNode, neighbors);
        
        // Get the encoded body
        byte[] encoded = original.getBody();
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
        
        // Decode back to a new message
        KadNeighborsMessage decoded = new KadNeighborsMessage(encoded);
        
        // Verify all fields match
        assertEquals(original.getTimestamp(), decoded.getTimestamp());
        assertEquals(original.getNeighbors().size(), decoded.getNeighbors().size());
        
        // Verify from node
        assertNotNull(decoded.getFrom());
        assertEquals(original.getFrom().getId(), decoded.getFrom().getId());
        assertEquals(original.getFrom().getHostV4(), decoded.getFrom().getHostV4());
        assertEquals(original.getFrom().getPort(), decoded.getFrom().getPort());
        
        // Verify neighbors
        for (int i = 0; i < original.getNeighbors().size(); i++) {
            assertEquals(original.getNeighbors().get(i).getId(), 
                        decoded.getNeighbors().get(i).getId());
            assertEquals(original.getNeighbors().get(i).getPort(), 
                        decoded.getNeighbors().get(i).getPort());
        }
    }

    @Test
    public void testEncodeMethod() {
        // Test the encode() method specifically
        KadNeighborsMessage msg = new KadNeighborsMessage(fromNode, neighbors);
        
        SimpleEncoder enc = new SimpleEncoder();
        msg.encode(enc);
        byte[] encoded = enc.toBytes();
        
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
        
        // Decode and verify structure
        SimpleDecoder dec = new SimpleDecoder(encoded);
        byte[] fromNodeBytes = dec.readBytes();
        assertNotNull(fromNodeBytes);
        int neighborsCount = dec.readInt();
        assertEquals(5, neighborsCount);
    }

    @Test
    public void testEmptyNeighborsList() {
        // Test with empty neighbors list
        List<Node> emptyList = Collections.emptyList();
        KadNeighborsMessage msg = new KadNeighborsMessage(fromNode, emptyList);
        
        assertNotNull(msg.getNeighbors());
        assertEquals(0, msg.getNeighbors().size());
        
        // Verify it can be decoded
        KadNeighborsMessage decoded = new KadNeighborsMessage(msg.getBody());
        assertEquals(0, decoded.getNeighbors().size());
    }

    @Test
    public void testSingleNeighbor() {
        // Test with a single neighbor
        List<Node> singleNeighbor = new ArrayList<>();
        singleNeighbor.add(neighbors.get(0));
        
        KadNeighborsMessage msg = new KadNeighborsMessage(fromNode, singleNeighbor);
        
        assertEquals(1, msg.getNeighbors().size());
        
        // Verify round-trip
        KadNeighborsMessage decoded = new KadNeighborsMessage(msg.getBody());
        assertEquals(1, decoded.getNeighbors().size());
        assertEquals(neighbors.get(0).getId(), decoded.getNeighbors().get(0).getId());
    }

    @Test
    public void testManyNeighbors() {
        // Test with many neighbors (simulate a full bucket)
        List<Node> manyNeighbors = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            String neighborId = Bytes.random(20).toHexString();
            Node neighbor = new Node(neighborId, new InetSocketAddress("10.0.0." + i, 30000 + i));
            neighbor.setNetworkId((byte) 1);
            neighbor.setNetworkVersion((short) 1);
            manyNeighbors.add(neighbor);
        }
        
        KadNeighborsMessage msg = new KadNeighborsMessage(fromNode, manyNeighbors);
        
        assertEquals(16, msg.getNeighbors().size());
        
        // Verify round-trip
        KadNeighborsMessage decoded = new KadNeighborsMessage(msg.getBody());
        assertEquals(16, decoded.getNeighbors().size());
    }

    @Test
    public void testGetSendData() {
        // Test getSendData() method which should include message code prefix
        KadNeighborsMessage msg = new KadNeighborsMessage(fromNode, neighbors);
        Bytes sendData = msg.getSendData();
        
        assertNotNull(sendData);
        assertTrue(sendData.size() > 0);
        
        // First byte should be the message code
        assertEquals(MessageCode.KAD_NEIGHBORS.toByte(), sendData.get(0));
    }

    @Test
    public void testMultipleEncodingsProduceSameResult() {
        // Test that encoding the same message multiple times produces the same result
        KadNeighborsMessage msg = new KadNeighborsMessage(fromNode, neighbors);
        
        SimpleEncoder enc1 = new SimpleEncoder();
        msg.encode(enc1);
        byte[] encoded1 = enc1.toBytes();
        
        SimpleEncoder enc2 = new SimpleEncoder();
        msg.encode(enc2);
        byte[] encoded2 = enc2.toBytes();
        
        assertArrayEquals(encoded1, encoded2);
    }

    @Test
    public void testFromNodeSerialization() {
        // Test that from node is correctly serialized
        KadNeighborsMessage original = new KadNeighborsMessage(fromNode, neighbors);
        KadNeighborsMessage decoded = new KadNeighborsMessage(original.getBody());
        
        // Verify all from node fields
        assertEquals(fromNode.getId(), decoded.getFrom().getId());
        assertEquals(fromNode.getHostV4(), decoded.getFrom().getHostV4());
        assertEquals(fromNode.getPort(), decoded.getFrom().getPort());
        assertEquals(fromNode.getNetworkId(), decoded.getFrom().getNetworkId());
        assertEquals(fromNode.getNetworkVersion(), decoded.getFrom().getNetworkVersion());
    }

    @Test
    public void testNeighborsSerialization() {
        // Test that all neighbors are correctly serialized
        KadNeighborsMessage original = new KadNeighborsMessage(fromNode, neighbors);
        KadNeighborsMessage decoded = new KadNeighborsMessage(original.getBody());
        
        assertEquals(neighbors.size(), decoded.getNeighbors().size());
        
        for (int i = 0; i < neighbors.size(); i++) {
            Node originalNeighbor = neighbors.get(i);
            Node decodedNeighbor = decoded.getNeighbors().get(i);
            
            assertEquals(originalNeighbor.getId(), decodedNeighbor.getId());
            assertEquals(originalNeighbor.getHostV4(), decodedNeighbor.getHostV4());
            assertEquals(originalNeighbor.getPort(), decodedNeighbor.getPort());
        }
    }

    @Test
    public void testMessageCodeIsCorrect() {
        // Test that message code is KAD_NEIGHBORS
        KadNeighborsMessage msg = new KadNeighborsMessage(fromNode, neighbors);
        
        assertEquals(MessageCode.KAD_NEIGHBORS, msg.getCode());
        assertEquals(MessageCode.KAD_NEIGHBORS, msg.getType());
    }

    @Test
    public void testResponseMessageClass() {
        // Test that response message class is null (KadNeighbors has no response)
        KadNeighborsMessage msg = new KadNeighborsMessage(fromNode, neighbors);
        
        assertNull(msg.getResponseMessageClass());
    }
}
