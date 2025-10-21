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
 * Unit tests for KadPingMessage class.
 * Tests encoding/decoding, field validation, and message creation.
 */
public class KadPingMessageTest {

    private Node fromNode;
    private Node toNode;

    @BeforeEach
    public void setUp() {
        // Create two test nodes with valid IDs
        String fromId = Bytes.random(20).toHexString();
        String toId = Bytes.random(20).toHexString();
        
        fromNode = new Node(fromId, new InetSocketAddress("127.0.0.1", 10001));
        fromNode.setNetworkId((byte) P2pConstant.MAINNET_ID);
        fromNode.setNetworkVersion(P2pConstant.MAINNET_VERSION);
        
        toNode = new Node(toId, new InetSocketAddress("192.168.1.100", 10002));
        toNode.setNetworkId((byte) P2pConstant.MAINNET_ID);
        toNode.setNetworkVersion(P2pConstant.MAINNET_VERSION);
    }

    @Test
    public void testMessageCreation() {
        // Test creating a new KadPingMessage
        KadPingMessage msg = new KadPingMessage(fromNode, toNode);
        
        assertNotNull(msg);
        assertEquals(MessageCode.KAD_PING, msg.getCode());
        assertEquals(MessageCode.KAD_PING, msg.getType());
        assertEquals(KadPongMessage.class, msg.getResponseMessageClass());
        assertNotNull(msg.getFrom());
        assertNotNull(msg.getTo());
        assertTrue(msg.getTimestamp() > 0);
        assertNotNull(msg.getBody());
        assertTrue(msg.getBody().length > 0);
    }

    @Test
    public void testNetworkIdAndVersion() {
        // Test that network ID and version are correctly set from the from node
        KadPingMessage msg = new KadPingMessage(fromNode, toNode);
        
        assertEquals(fromNode.getNetworkId(), msg.getNetworkId());
        assertEquals(fromNode.getNetworkVersion(), msg.getNetworkVersion());
        assertEquals((byte) P2pConstant.MAINNET_ID, msg.getNetworkId());
        assertEquals(P2pConstant.MAINNET_VERSION, msg.getNetworkVersion());
    }

    @Test
    public void testEncodeDecodeRoundTrip() {
        // Test that encoding and then decoding produces equivalent message
        KadPingMessage original = new KadPingMessage(fromNode, toNode);
        
        // Get the encoded body
        byte[] encoded = original.getBody();
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
        
        // Decode back to a new message
        KadPingMessage decoded = new KadPingMessage(encoded);
        
        // Verify all fields match
        assertEquals(original.getNetworkId(), decoded.getNetworkId());
        assertEquals(original.getNetworkVersion(), decoded.getNetworkVersion());
        assertEquals(original.getTimestamp(), decoded.getTimestamp());
        
        // Verify node fields
        assertNotNull(decoded.getFrom());
        assertNotNull(decoded.getTo());
        assertEquals(original.getFrom().getId(), decoded.getFrom().getId());
        assertEquals(original.getTo().getId(), decoded.getTo().getId());
        assertEquals(original.getFrom().getPort(), decoded.getFrom().getPort());
        assertEquals(original.getTo().getPort(), decoded.getTo().getPort());
    }

    @Test
    public void testEncodeMethod() {
        // Test the encode() method specifically
        KadPingMessage msg = new KadPingMessage(fromNode, toNode);
        
        SimpleEncoder enc = new SimpleEncoder();
        msg.encode(enc);
        byte[] encoded = enc.toBytes();
        
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
        
        // Decode and verify
        SimpleDecoder dec = new SimpleDecoder(encoded);
        assertEquals(msg.getNetworkId(), dec.readByte());
        assertEquals(msg.getNetworkVersion(), dec.readShort());
        assertEquals(msg.getTimestamp(), dec.readLong());
    }

    @Test
    public void testTimestampIsSet() {
        // Test that timestamp is automatically set on creation
        long beforeCreation = System.currentTimeMillis();
        KadPingMessage msg = new KadPingMessage(fromNode, toNode);
        long afterCreation = System.currentTimeMillis();
        
        assertTrue(msg.getTimestamp() >= beforeCreation);
        assertTrue(msg.getTimestamp() <= afterCreation);
    }

    @Test
    public void testNodeSerialization() {
        // Test that from and to nodes are correctly serialized
        KadPingMessage original = new KadPingMessage(fromNode, toNode);
        KadPingMessage decoded = new KadPingMessage(original.getBody());
        
        // Verify from node
        assertEquals(fromNode.getId(), decoded.getFrom().getId());
        assertEquals(fromNode.getHostV4(), decoded.getFrom().getHostV4());
        assertEquals(fromNode.getPort(), decoded.getFrom().getPort());
        
        // Verify to node
        assertEquals(toNode.getId(), decoded.getTo().getId());
        assertEquals(toNode.getHostV4(), decoded.getTo().getHostV4());
        assertEquals(toNode.getPort(), decoded.getTo().getPort());
    }

    @Test
    public void testDifferentNetworkIds() {
        // Test with different network IDs
        fromNode.setNetworkId((byte) P2pConstant.TESTNET_ID);
        KadPingMessage msg = new KadPingMessage(fromNode, toNode);
        
        assertEquals((byte) P2pConstant.TESTNET_ID, msg.getNetworkId());
        
        // Verify it decodes correctly
        KadPingMessage decoded = new KadPingMessage(msg.getBody());
        assertEquals((byte) P2pConstant.TESTNET_ID, decoded.getNetworkId());
    }

    @Test
    public void testDifferentNetworkVersions() {
        // Test with different network versions
        fromNode.setNetworkVersion((short) 100);
        KadPingMessage msg = new KadPingMessage(fromNode, toNode);
        
        assertEquals((short) 100, msg.getNetworkVersion());
        
        // Verify it decodes correctly
        KadPingMessage decoded = new KadPingMessage(msg.getBody());
        assertEquals((short) 100, decoded.getNetworkVersion());
    }

    @Test
    public void testToString() {
        // Test toString() method
        KadPingMessage msg = new KadPingMessage(fromNode, toNode);
        String str = msg.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("KadPingMessage"));
        assertTrue(str.contains("networkId"));
        assertTrue(str.contains("networkVersion"));
        assertTrue(str.contains("timestamp"));
    }

    @Test
    public void testGetSendData() {
        // Test getSendData() method which should include message code prefix
        KadPingMessage msg = new KadPingMessage(fromNode, toNode);
        Bytes sendData = msg.getSendData();
        
        assertNotNull(sendData);
        assertFalse(sendData.isEmpty());
        
        // First byte should be the message code
        assertEquals(MessageCode.KAD_PING.toByte(), sendData.get(0));
    }

    @Test
    public void testMultipleEncodingsProduceSameResult() {
        // Test that encoding the same message multiple times produces the same result
        KadPingMessage msg = new KadPingMessage(fromNode, toNode);
        
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
        // Test that different nodes produce different messages
        String altFromId = Bytes.random(20).toHexString();
        Node altFromNode = new Node(altFromId, new InetSocketAddress("10.0.0.1", 20001));
        altFromNode.setNetworkId((byte) P2pConstant.MAINNET_ID);
        altFromNode.setNetworkVersion(P2pConstant.MAINNET_VERSION);
        
        KadPingMessage msg1 = new KadPingMessage(fromNode, toNode);
        KadPingMessage msg2 = new KadPingMessage(altFromNode, toNode);
        
        // Messages should have different bodies (different from nodes)
        assertNotEquals(Bytes.wrap(msg1.getBody()), Bytes.wrap(msg2.getBody()));
    }

    @Test
    public void testMessageCodeIsCorrect() {
        // Test that message code is KAD_PING
        KadPingMessage msg = new KadPingMessage(fromNode, toNode);
        
        assertEquals(MessageCode.KAD_PING, msg.getCode());
        assertEquals(MessageCode.KAD_PING, msg.getType());
    }

    @Test
    public void testResponseMessageClass() {
        // Test that response message class is KadPongMessage
        KadPingMessage msg = new KadPingMessage(fromNode, toNode);
        
        assertEquals(KadPongMessage.class, msg.getResponseMessageClass());
    }
}
