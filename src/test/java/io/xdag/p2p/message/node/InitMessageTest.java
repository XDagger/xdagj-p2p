/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
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
package io.xdag.p2p.message.node;

import io.xdag.p2p.message.MessageCode;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class InitMessageTest {

    @Test
    void testConstructorAndGetters() {
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        long timestamp = System.currentTimeMillis();

        InitMessage msg = new InitMessage(secret, timestamp);

        assertArrayEquals(secret, msg.getSecret());
        assertEquals(timestamp, msg.getTimestamp());
        assertEquals(MessageCode.HANDSHAKE_INIT, msg.getCode());
        assertNull(msg.getResponseMessageClass());
    }

    @Test
    void testEncodeDecode() {
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        long timestamp = System.currentTimeMillis();

        InitMessage originalMsg = new InitMessage(secret, timestamp);
        byte[] encodedBody = originalMsg.getBody();

        InitMessage decodedMsg = new InitMessage(encodedBody);

        assertArrayEquals(originalMsg.getSecret(), decodedMsg.getSecret());
        assertEquals(originalMsg.getTimestamp(), decodedMsg.getTimestamp());
        assertArrayEquals(encodedBody, decodedMsg.getBody());
    }

    @Test
    void testValidation() {
        // Valid case
        byte[] validSecret = new byte[32];
        new Random().nextBytes(validSecret);
        InitMessage validMsg = new InitMessage(validSecret, System.currentTimeMillis());
        assertTrue(validMsg.validate());

        // Invalid secret length
        byte[] invalidSecret = new byte[31];
        InitMessage invalidMsg1 = new InitMessage(invalidSecret, System.currentTimeMillis());
        assertFalse(invalidMsg1.validate());

        // Null secret
        InitMessage invalidMsg2 = new InitMessage(null, System.currentTimeMillis());
        assertFalse(invalidMsg2.validate());

        // Invalid timestamp
        InitMessage invalidMsg3 = new InitMessage(validSecret, 0);
        assertFalse(invalidMsg3.validate());
    }

    @Test
    void testToString() {
        // Given
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        long timestamp = System.currentTimeMillis();
        InitMessage msg = new InitMessage(secret, timestamp);

        // When
        String str = msg.toString();

        // Then
        assertNotNull(str, "toString should not be null");
        assertTrue(str.contains("InitMessage"), "toString should contain class name");
        assertTrue(str.contains("secret="), "toString should contain secret field");
        assertTrue(str.contains("timestamp="), "toString should contain timestamp field");
        assertTrue(str.contains(String.valueOf(timestamp)), "toString should contain timestamp value");
    }

    @Test
    void testEncodeMethod() {
        // Given
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        long timestamp = System.currentTimeMillis();
        InitMessage msg = new InitMessage(secret, timestamp);

        // When
        io.xdag.p2p.utils.SimpleEncoder enc = new io.xdag.p2p.utils.SimpleEncoder();
        msg.encode(enc);
        byte[] encoded = enc.toBytes();

        // Then
        assertNotNull(encoded, "Encoded bytes should not be null");
        assertTrue(encoded.length >= 40, "Encoded bytes should contain secret (32 bytes) + timestamp (8 bytes)");

        // Verify decode produces same values
        InitMessage decoded = new InitMessage(encoded);
        assertArrayEquals(secret, decoded.getSecret(), "Decoded secret should match");
        assertEquals(timestamp, decoded.getTimestamp(), "Decoded timestamp should match");
    }

    @Test
    void testBodyNotNull() {
        // Given
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        InitMessage msg = new InitMessage(secret, System.currentTimeMillis());

        // When
        byte[] body = msg.getBody();

        // Then
        assertNotNull(body, "Message body should not be null");
        assertTrue(body.length > 0, "Message body should not be empty");
    }

    @Test
    void testSecretLength() {
        // Verify SECRET_LENGTH constant
        assertEquals(32, InitMessage.SECRET_LENGTH, "SECRET_LENGTH should be 32");
    }

    @Test
    void testMessageCodeType() {
        // Given
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        InitMessage msg = new InitMessage(secret, System.currentTimeMillis());

        // When/Then
        assertEquals(MessageCode.HANDSHAKE_INIT, msg.getType(), "Message type should be HANDSHAKE_INIT");
    }

    @Test
    void testNegativeTimestamp() {
        // Given
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        InitMessage msg = new InitMessage(secret, -1);

        // When/Then
        assertFalse(msg.validate(), "Validation should fail for negative timestamp");
    }

    @Test
    void testEmptySecretArray() {
        // Given
        byte[] emptySecret = new byte[0];
        InitMessage msg = new InitMessage(emptySecret, System.currentTimeMillis());

        // When/Then
        assertFalse(msg.validate(), "Validation should fail for empty secret");
    }

    @Test
    void testLargeTimestamp() {
        // Given
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        long largeTimestamp = Long.MAX_VALUE;
        InitMessage msg = new InitMessage(secret, largeTimestamp);

        // When/Then
        assertTrue(msg.validate(), "Validation should pass for large valid timestamp");
        assertEquals(largeTimestamp, msg.getTimestamp(), "Timestamp should be preserved");
    }
}
