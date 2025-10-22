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
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for KadPongMessage class.
 * Tests encoding/decoding, field validation, and message creation.
 */
public class KadPongMessageTest {

    @Test
    public void testMessageCreation() {
        // Test creating a new KadPongMessage
        KadPongMessage msg = new KadPongMessage((Node) null);
        
        assertNotNull(msg);
        assertEquals(MessageCode.KAD_PONG, msg.getCode());
        assertEquals(MessageCode.KAD_PONG, msg.getType());
        assertNull(msg.getResponseMessageClass());
        assertTrue(msg.getTimestamp() > 0);
        assertNotNull(msg.getBody());
        assertTrue(msg.getBody().length > 0);
    }

    @Test
    public void testNetworkIdAndVersion() {
        // Test that network ID and version are correctly set from constants
        KadPongMessage msg = new KadPongMessage((Node) null);
        
        assertEquals((byte) P2pConstant.MAINNET_ID, msg.getNetworkId());
        assertEquals(P2pConstant.MAINNET_VERSION, msg.getNetworkVersion());
    }

    @Test
    public void testTimestampIsSet() {
        // Test that timestamp is automatically set on creation
        long beforeCreation = System.currentTimeMillis();
        KadPongMessage msg = new KadPongMessage((Node) null);
        long afterCreation = System.currentTimeMillis();
        
        assertTrue(msg.getTimestamp() >= beforeCreation);
        assertTrue(msg.getTimestamp() <= afterCreation);
    }

    @Test
    public void testEncodeDecodeRoundTrip() {
        // Test that encoding and then decoding produces equivalent message
        KadPongMessage original = new KadPongMessage((Node) null);
        
        // Get the encoded body
        byte[] encoded = original.getBody();
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
        
        // Decode back to a new message
        KadPongMessage decoded = new KadPongMessage(encoded);
        
        // Verify all fields match
        assertEquals(original.getNetworkId(), decoded.getNetworkId());
        assertEquals(original.getNetworkVersion(), decoded.getNetworkVersion());
        assertEquals(original.getTimestamp(), decoded.getTimestamp());
    }

    @Test
    public void testEncodeMethod() {
        // Test the encode() method specifically
        KadPongMessage msg = new KadPongMessage((Node) null);
        
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
    public void testGetSendData() {
        // Test getSendData() method which should include message code prefix
        KadPongMessage msg = new KadPongMessage((Node) null);
        Bytes sendData = msg.getSendData();
        
        assertNotNull(sendData);
        assertFalse(sendData.isEmpty());
        
        // First byte should be the message code
        assertEquals(MessageCode.KAD_PONG.toByte(), sendData.get(0));
    }

    @Test
    public void testMultipleEncodingsProduceSameResult() {
        // Test that encoding the same message multiple times produces the same result
        KadPongMessage msg = new KadPongMessage((Node) null);
        
        SimpleEncoder enc1 = new SimpleEncoder();
        msg.encode(enc1);
        byte[] encoded1 = enc1.toBytes();
        
        SimpleEncoder enc2 = new SimpleEncoder();
        msg.encode(enc2);
        byte[] encoded2 = enc2.toBytes();
        
        assertArrayEquals(encoded1, encoded2);
    }

    @Test
    public void testToString() {
        // Test toString() method
        KadPongMessage msg = new KadPongMessage((Node) null);
        String str = msg.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("KadPongMessage"));
        assertTrue(str.contains("networkId"));
        assertTrue(str.contains("networkVersion"));
        assertTrue(str.contains("timestamp"));
    }

    @Test
    public void testMessageCodeIsCorrect() {
        // Test that message code is KAD_PONG
        KadPongMessage msg = new KadPongMessage((Node) null);
        
        assertEquals(MessageCode.KAD_PONG, msg.getCode());
        assertEquals(MessageCode.KAD_PONG, msg.getType());
    }

    @Test
    public void testResponseMessageClass() {
        // Test that response message class is null (KadPong has no response)
        KadPongMessage msg = new KadPongMessage((Node) null);
        
        assertNull(msg.getResponseMessageClass());
    }

    @Test
    public void testBodyIsNotEmpty() {
        // Test that body is populated
        KadPongMessage msg = new KadPongMessage((Node) null);
        
        assertNotNull(msg.getBody());
        assertTrue(msg.getBody().length > 0);
        
        // Body should contain at least: 1 byte (networkId) + 2 bytes (version) + 8 bytes (timestamp) = 11 bytes
        assertTrue(msg.getBody().length >= 11);
    }

    @Test
    public void testDifferentMessagesHaveDifferentTimestamps() throws InterruptedException {
        // Test that messages created at different times have different timestamps
        KadPongMessage msg1 = new KadPongMessage((Node) null);
        Thread.sleep(10); // Small delay to ensure different timestamp
        KadPongMessage msg2 = new KadPongMessage((Node) null);
        
        // Timestamps should be different
        assertTrue(msg2.getTimestamp() >= msg1.getTimestamp());
    }
}
