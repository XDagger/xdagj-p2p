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
package io.xdag.p2p.message;

import static org.junit.jupiter.api.Assertions.*;

import io.xdag.p2p.message.node.PingMessage;
import io.xdag.p2p.message.node.DisconnectMessage;
import io.xdag.p2p.utils.SimpleEncoder;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class MessageTest {

    // ==================== needToLog() Tests ====================

    @Test
    public void testNeedToLogForPing() {
        // Given - PING message should NOT be logged (not in the list)
        Message msg = new PingMessage(new byte[0]);

        // When/Then
        assertFalse(msg.needToLog(), "PING should not be logged");
    }

    @Test
    public void testNeedToLogForDisconnect() {
        // Given - DISCONNECT message should be logged
        Message msg = new DisconnectMessage(ReasonCode.BAD_NETWORK);

        // When/Then
        assertTrue(msg.needToLog(), "DISCONNECT should be logged");
    }

    // ==================== getType() Tests ====================

    @Test
    public void testGetTypePing() {
        // Given
        Message msg = new PingMessage(new byte[0]);

        // When
        MessageCode code = msg.getType();

        // Then
        assertEquals(MessageCode.PING, code);
    }

    @Test
    public void testGetTypeDisconnect() {
        // Given
        Message msg = new DisconnectMessage(ReasonCode.BAD_NETWORK);

        // When
        MessageCode code = msg.getType();

        // Then
        assertEquals(MessageCode.DISCONNECT, code);
    }

    // ==================== getSendData() Tests ====================

    @Test
    public void testGetSendDataIncludesMessageCode() {
        // Given - a PING message
        Message msg = new PingMessage(new byte[4]);

        // When - get send data
        Bytes sendData = msg.getSendData();

        // Then - first byte should be message code
        assertNotNull(sendData);
        assertTrue(sendData.size() > 0);
        assertEquals(MessageCode.PING.toByte(), sendData.get(0),
            "First byte should be message code");
    }

    @Test
    public void testGetSendDataWithNullBody() {
        // Given - create a message and ensure body gets generated
        Message msg = new PingMessage(new byte[0]);

        // When - get send data (should generate body if null)
        Bytes sendData = msg.getSendData();

        // Then
        assertNotNull(sendData);
        assertTrue(sendData.size() >= 1, "Should at least have message code byte");
    }

    @Test
    public void testGetSendDataMultipleCalls() {
        // Given
        Message msg = new PingMessage(new byte[10]);

        // When - call getSendData multiple times
        Bytes data1 = msg.getSendData();
        Bytes data2 = msg.getSendData();

        // Then - should return consistent data
        assertEquals(data1, data2, "Multiple calls should return same data");
    }

    // ==================== decode() Tests ====================

    @Test
    public void testDecodePingMessage() throws MessageException {
        // Given - encoded PING message
        PingMessage originalMsg = new PingMessage(new byte[]{1, 2, 3, 4});
        SimpleEncoder enc = new SimpleEncoder();
        originalMsg.encode(enc);
        byte[] body = enc.toBytes();

        // When - decode
        Message decoded = Message.decode(MessageCode.PING, body);

        // Then
        assertNotNull(decoded);
        assertEquals(MessageCode.PING, decoded.getCode());
    }

    @Test
    public void testDecodeDisconnectMessage() throws MessageException {
        // Given - encoded DISCONNECT message
        DisconnectMessage originalMsg = new DisconnectMessage(ReasonCode.BAD_NETWORK);
        SimpleEncoder enc = new SimpleEncoder();
        originalMsg.encode(enc);
        byte[] body = enc.toBytes();

        // When - decode
        Message decoded = Message.decode(MessageCode.DISCONNECT, body);

        // Then
        assertNotNull(decoded);
        assertEquals(MessageCode.DISCONNECT, decoded.getCode());
    }

    @Test
    public void testDecodeWithInvalidCode() {
        // Given - invalid message code
        byte[] body = new byte[]{1, 2, 3};

        // When/Then - should throw exception for null message code
        assertThrows(NullPointerException.class, () -> {
            MessageCode invalidCode = null;
            Message.decode(invalidCode, body);
        });
    }

    // ==================== getCode() Tests ====================

    @Test
    public void testGetCode() {
        // Given
        Message msg = new PingMessage(new byte[0]);

        // When
        MessageCode code = msg.getCode();

        // Then
        assertNotNull(code);
        assertEquals(MessageCode.PING, code);
    }

    // ==================== getResponseMessageClass() Tests ====================

    @Test
    public void testGetResponseMessageClass() {
        // Given - PING expects PONG response
        Message msg = new PingMessage(new byte[0]);

        // When
        Class<?> responseClass = msg.getResponseMessageClass();

        // Then
        assertNotNull(responseClass);
    }

    // ==================== getBody() Tests ====================

    @Test
    public void testGetBody() {
        // Given
        Message msg = new PingMessage(new byte[]{1, 2, 3, 4});

        // When - trigger body generation via getSendData
        msg.getSendData();
        byte[] body = msg.getBody();

        // Then
        assertNotNull(body);
    }

    @Test
    public void testGetBodyNotNull() {
        // Given - create message
        Message msg = new PingMessage(new byte[0]);

        // When - get body directly
        byte[] body = msg.getBody();

        // Then - body should not be null (initialized in constructor)
        assertNotNull(body);
    }

    // ==================== toString() Tests ====================

    @Test
    public void testToString() {
        // Given
        Message msg = new PingMessage(new byte[0]);

        // When
        String str = msg.toString();

        // Then
        assertNotNull(str);
        assertTrue(str.contains("PingMessage"), "toString should contain class name");
    }

    @Test
    public void testToStringDifferentMessages() {
        // Given
        Message ping = new PingMessage(new byte[0]);
        Message disconnect = new DisconnectMessage(ReasonCode.BAD_NETWORK);

        // When
        String pingStr = ping.toString();
        String disconnectStr = disconnect.toString();

        // Then
        assertNotEquals(pingStr, disconnectStr, "Different messages should have different toString");
    }

    // ==================== Constructor Tests ====================

    @Test
    public void testMessageCodeNotNull() {
        // Given/When
        Message msg = new PingMessage(new byte[0]);

        // Then
        assertNotNull(msg.getCode());
    }

    @Test
    public void testInitialBodyNotNull() {
        // Given/When - constructor should initialize body to empty array
        Message msg = new PingMessage(new byte[0]);

        // Then
        assertNotNull(msg.getBody());
    }
}
