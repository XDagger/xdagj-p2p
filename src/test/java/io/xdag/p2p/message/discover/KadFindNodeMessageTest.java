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
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for KadFindNodeMessage class.
 * Tests encoding/decoding, target field handling, and message creation.
 */
public class KadFindNodeMessageTest {

    private Node fromNode;
    private Bytes targetId;

    @BeforeEach
    public void setUp() {
        // Create a test node
        String fromId = Bytes.random(20).toHexString();
        fromNode = new Node(fromId, new InetSocketAddress("127.0.0.1", 10001));
        fromNode.setNetworkId((byte) P2pConstant.MAINNET_ID);
        fromNode.setNetworkVersion((short) P2pConstant.MAINNET_VERSION);
        
        // Create a random target ID
        targetId = Bytes.random(20);
    }

    @Test
    public void testMessageCreation() {
        // Test creating a new KadFindNodeMessage
        KadFindNodeMessage msg = new KadFindNodeMessage(fromNode, targetId);
        
        assertNotNull(msg);
        assertEquals(MessageCode.KAD_FIND_NODE, msg.getCode());
        assertEquals(MessageCode.KAD_FIND_NODE, msg.getType());
        assertEquals(KadNeighborsMessage.class, msg.getResponseMessageClass());
        assertNotNull(msg.getFrom());
        assertNotNull(msg.getTarget());
        assertTrue(msg.getTimestamp() > 0);
        assertNotNull(msg.getBody());
        assertTrue(msg.getBody().length > 0);
    }

    @Test
    public void testTargetFieldIsSet() {
        // Test that target field is correctly set
        KadFindNodeMessage msg = new KadFindNodeMessage(fromNode, targetId);
        
        assertEquals(targetId, msg.getTarget());
    }

    @Test
    public void testFromNodeIsSet() {
        // Test that from node is correctly set
        KadFindNodeMessage msg = new KadFindNodeMessage(fromNode, targetId);
        
        assertNotNull(msg.getFrom());
        assertEquals(fromNode.getId(), msg.getFrom().getId());
        assertEquals(fromNode.getHostV4(), msg.getFrom().getHostV4());
        assertEquals(fromNode.getPort(), msg.getFrom().getPort());
    }

    @Test
    public void testTimestampIsSet() {
        // Test that timestamp is automatically set on creation
        long beforeCreation = System.currentTimeMillis();
        KadFindNodeMessage msg = new KadFindNodeMessage(fromNode, targetId);
        long afterCreation = System.currentTimeMillis();
        
        assertTrue(msg.getTimestamp() >= beforeCreation);
        assertTrue(msg.getTimestamp() <= afterCreation);
    }

    @Test
    public void testEncodeDecodeRoundTrip() {
        // Test that encoding and then decoding produces equivalent message
        KadFindNodeMessage original = new KadFindNodeMessage(fromNode, targetId);
        
        // Get the encoded body
        byte[] encoded = original.getBody();
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
        
        // Decode back to a new message
        KadFindNodeMessage decoded = new KadFindNodeMessage(encoded);
        
        // Verify all fields match
        assertEquals(original.getTimestamp(), decoded.getTimestamp());
        assertEquals(original.getTarget(), decoded.getTarget());
        
        // Verify from node
        assertNotNull(decoded.getFrom());
        assertEquals(original.getFrom().getId(), decoded.getFrom().getId());
        assertEquals(original.getFrom().getHostV4(), decoded.getFrom().getHostV4());
        assertEquals(original.getFrom().getPort(), decoded.getFrom().getPort());
    }

    @Test
    public void testEncodeMethod() {
        // Test the encode() method specifically
        KadFindNodeMessage msg = new KadFindNodeMessage(fromNode, targetId);
        
        SimpleEncoder enc = new SimpleEncoder();
        msg.encode(enc);
        byte[] encoded = enc.toBytes();
        
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
        
        // Decode and verify structure
        SimpleDecoder dec = new SimpleDecoder(encoded);
        byte[] fromNodeBytes = dec.readBytes();
        assertNotNull(fromNodeBytes);
        byte[] targetBytes = dec.readBytes();
        assertEquals(targetId, Bytes.wrap(targetBytes));
        long timestamp = dec.readLong();
        assertEquals(msg.getTimestamp(), timestamp);
    }

    @Test
    public void testTargetSerialization() {
        // Test that target ID is correctly serialized and deserialized
        Bytes customTarget = Bytes.fromHexString("0x" + "a".repeat(128)); // 64 bytes
        KadFindNodeMessage original = new KadFindNodeMessage(fromNode, customTarget);
        KadFindNodeMessage decoded = new KadFindNodeMessage(original.getBody());
        
        assertEquals(customTarget, decoded.getTarget());
    }

    @Test
    public void testFromNodeSerialization() {
        // Test that from node is correctly serialized
        KadFindNodeMessage original = new KadFindNodeMessage(fromNode, targetId);
        KadFindNodeMessage decoded = new KadFindNodeMessage(original.getBody());
        
        // Verify all from node fields
        assertEquals(fromNode.getId(), decoded.getFrom().getId());
        assertEquals(fromNode.getHostV4(), decoded.getFrom().getHostV4());
        assertEquals(fromNode.getPort(), decoded.getFrom().getPort());
        assertEquals(fromNode.getNetworkId(), decoded.getFrom().getNetworkId());
        assertEquals(fromNode.getNetworkVersion(), decoded.getFrom().getNetworkVersion());
    }

    @Test
    public void testDifferentTargets() {
        // Test with different target IDs
        Bytes target1 = Bytes.random(20);
        Bytes target2 = Bytes.random(20);
        
        KadFindNodeMessage msg1 = new KadFindNodeMessage(fromNode, target1);
        KadFindNodeMessage msg2 = new KadFindNodeMessage(fromNode, target2);
        
        assertEquals(target1, msg1.getTarget());
        assertEquals(target2, msg2.getTarget());
        assertNotEquals(target1, target2);
        assertNotEquals(msg1.getTarget(), msg2.getTarget());
    }

    @Test
    public void testEmptyTarget() {
        // Test with an empty target (edge case)
        Bytes emptyTarget = Bytes.EMPTY;
        KadFindNodeMessage msg = new KadFindNodeMessage(fromNode, emptyTarget);
        
        assertEquals(emptyTarget, msg.getTarget());
        
        // Verify it can be decoded
        KadFindNodeMessage decoded = new KadFindNodeMessage(msg.getBody());
        assertEquals(emptyTarget, decoded.getTarget());
    }

    @Test
    public void testSmallTarget() {
        // Test with a small target (less than 64 bytes)
        Bytes smallTarget = Bytes.fromHexString("0x1234567890abcdef");
        KadFindNodeMessage msg = new KadFindNodeMessage(fromNode, smallTarget);
        
        assertEquals(smallTarget, msg.getTarget());
        
        // Verify it can be decoded
        KadFindNodeMessage decoded = new KadFindNodeMessage(msg.getBody());
        assertEquals(smallTarget, decoded.getTarget());
    }

    @Test
    public void testLargeTarget() {
        // Test with a larger target (more than 64 bytes)
        Bytes largeTarget = Bytes.random(128);
        KadFindNodeMessage msg = new KadFindNodeMessage(fromNode, largeTarget);
        
        assertEquals(largeTarget, msg.getTarget());
        
        // Verify it can be decoded
        KadFindNodeMessage decoded = new KadFindNodeMessage(msg.getBody());
        assertEquals(largeTarget, decoded.getTarget());
    }

    @Test
    public void testGetSendData() {
        // Test getSendData() method which should include message code prefix
        KadFindNodeMessage msg = new KadFindNodeMessage(fromNode, targetId);
        Bytes sendData = msg.getSendData();
        
        assertNotNull(sendData);
        assertFalse(sendData.isEmpty());
        
        // First byte should be the message code
        assertEquals(MessageCode.KAD_FIND_NODE.toByte(), sendData.get(0));
    }

    @Test
    public void testMultipleEncodingsProduceSameResult() {
        // Test that encoding the same message multiple times produces the same result
        KadFindNodeMessage msg = new KadFindNodeMessage(fromNode, targetId);
        
        SimpleEncoder enc1 = new SimpleEncoder();
        msg.encode(enc1);
        byte[] encoded1 = enc1.toBytes();
        
        SimpleEncoder enc2 = new SimpleEncoder();
        msg.encode(enc2);
        byte[] encoded2 = enc2.toBytes();
        
        assertArrayEquals(encoded1, encoded2);
    }

    @Test
    public void testDifferentNodesProduceDifferentMessages() {
        // Test that different from nodes produce different messages
        String altFromId = Bytes.random(20).toHexString();
        Node altFromNode = new Node(altFromId, new InetSocketAddress("10.0.0.1", 20001));
        altFromNode.setNetworkId((byte) 1);
        altFromNode.setNetworkVersion((short) 1);
        
        KadFindNodeMessage msg1 = new KadFindNodeMessage(fromNode, targetId);
        KadFindNodeMessage msg2 = new KadFindNodeMessage(altFromNode, targetId);
        
        // Messages should have different bodies (different from nodes)
        assertNotEquals(Bytes.wrap(msg1.getBody()), Bytes.wrap(msg2.getBody()));
    }

    @Test
    public void testMessageCodeIsCorrect() {
        // Test that message code is KAD_FIND_NODE
        KadFindNodeMessage msg = new KadFindNodeMessage(fromNode, targetId);
        
        assertEquals(MessageCode.KAD_FIND_NODE, msg.getCode());
        assertEquals(MessageCode.KAD_FIND_NODE, msg.getType());
    }

    @Test
    public void testResponseMessageClass() {
        // Test that response message class is KadNeighborsMessage
        KadFindNodeMessage msg = new KadFindNodeMessage(fromNode, targetId);
        
        assertEquals(KadNeighborsMessage.class, msg.getResponseMessageClass());
    }

    @Test
    public void testTargetWith128CharHexString() {
        // Test with a typical Kademlia node ID (64 bytes = 128 hex chars)
        String hexId = "0123456789abcdef".repeat(8); // 128 chars
        Bytes target = Bytes.fromHexString("0x" + hexId);
        
        KadFindNodeMessage msg = new KadFindNodeMessage(fromNode, target);
        assertEquals(target, msg.getTarget());
        assertEquals(64, msg.getTarget().size()); // 64 bytes
        
        // Verify round-trip
        KadFindNodeMessage decoded = new KadFindNodeMessage(msg.getBody());
        assertEquals(target, decoded.getTarget());
    }
}
