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
package io.xdag.p2p.message.node;

import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.utils.SimpleEncoder;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

public class HandshakeMessagesTest {

    private ECKeyPair keyPair;
    private byte[] secret;
    private P2pConfig config;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @BeforeEach
    void setUp() {
        // Generate test key pair
        byte[] privateKeyBytes = new byte[32];
        SECURE_RANDOM.nextBytes(privateKeyBytes);
        keyPair = ECKeyPair.fromHex(Bytes.wrap(privateKeyBytes).toHexString());

        // Generate test secret
        secret = new byte[InitMessage.SECRET_LENGTH];
        SECURE_RANDOM.nextBytes(secret);

        // Setup test config
        config = new P2pConfig();
        config.setNetworkId((byte)1);
        config.setNetworkVersion((short)1);
        config.setPort(8080);
        config.setClientId("test-client");
        config.setCapabilities(new String[]{"xdag/1"});
    }

    // ==================== HelloMessage Tests ====================

    @Test
    void testHelloMessageConstructor() {
        // Given/When
        HelloMessage msg = new HelloMessage(
            (byte)1,
            (short)1,
            "testPeer",
            8080,
            "testClient",
            new String[]{"xdag/1"},
            100L,
            secret,
            keyPair,
            false,
            "testTag"
        );

        // Then
        assertNotNull(msg);
        assertEquals(MessageCode.HANDSHAKE_HELLO, msg.getCode());
        assertEquals("testPeer", msg.getPeerId());
        assertEquals(8080, msg.getPort());
        assertEquals("testClient", msg.getClientId());
        assertArrayEquals(new String[]{"xdag/1"}, msg.getCapabilities());
        assertEquals(100L, msg.getLatestBlockNumber());
        assertArrayEquals(secret, msg.getSecret());
        assertFalse(msg.isGenerateBlock());
        assertEquals("testTag", msg.getNodeTag());
    }

    @Test
    void testHelloMessageEncodeDecode() {
        // Given - create HelloMessage with proper peerId from keyPair
        String peerId = io.xdag.crypto.encoding.Base58.encodeCheck(
            io.xdag.crypto.keys.AddressUtils.toBytesAddress(keyPair.getPublicKey())
        );

        HelloMessage original = new HelloMessage(
            (byte)1,
            (short)1,
            peerId,  // Use derived peerId
            8080,
            "testClient",
            new String[]{"xdag/1", "xdag/2"},
            100L,
            secret,
            keyPair,
            true,
            "testTag"
        );

        // When - decode from body (constructor already encoded)
        byte[] body = original.getBody();
        HelloMessage decoded = new HelloMessage(body);

        // Then - verify all fields match
        assertEquals(original.getNetworkId(), decoded.getNetworkId());
        assertEquals(original.getNetworkVersion(), decoded.getNetworkVersion());
        assertEquals(original.getPeerId(), decoded.getPeerId());
        assertEquals(original.getPort(), decoded.getPort());
        assertEquals(original.getClientId(), decoded.getClientId());
        assertArrayEquals(original.getCapabilities(), decoded.getCapabilities());
        assertEquals(original.getLatestBlockNumber(), decoded.getLatestBlockNumber());
        assertArrayEquals(original.getSecret(), decoded.getSecret());
        assertEquals(original.isGenerateBlock(), decoded.isGenerateBlock());
        assertEquals(original.getNodeTag(), decoded.getNodeTag());
    }

    @Test
    void testHelloMessageToString() {
        // Given
        HelloMessage msg = new HelloMessage(
            (byte)1,
            (short)1,
            "testPeer",
            8080,
            "testClient",
            new String[]{"xdag/1"},
            100L,
            secret,
            keyPair,
            false,
            "testTag"
        );

        // When
        String str = msg.toString();

        // Then
        assertNotNull(str);
        assertTrue(str.contains("HelloMessage"));
        assertTrue(str.contains("testPeer"));
        assertTrue(str.contains("8080"));
        assertTrue(str.contains("testClient"));
    }

    @Test
    void testHelloMessageValidation() {
        // Given - HelloMessage with proper peerId derived from keyPair
        String peerId = io.xdag.crypto.encoding.Base58.encodeCheck(
            io.xdag.crypto.keys.AddressUtils.toBytesAddress(keyPair.getPublicKey())
        );

        // Set handshake expiry high enough to pass timestamp validation
        config.setNetHandshakeExpiry(60000L); // 60 seconds

        HelloMessage msg = new HelloMessage(
            config.getNetworkId(),
            config.getNetworkVersion(),
            peerId,  // Use derived peerId
            8080,
            "testClient",
            new String[]{"xdag/1"},
            100L,
            secret,
            keyPair,
            false,
            "testTag"
        );

        // When/Then
        assertTrue(msg.validate(config), "HelloMessage should validate with matching config");
    }

    @Test
    void testHelloMessageInvalidNetworkId() {
        // Given - HelloMessage with wrong network ID
        HelloMessage msg = new HelloMessage(
            (byte)99,  // Wrong network ID
            config.getNetworkVersion(),
            "testPeer",
            8080,
            "testClient",
            new String[]{"xdag/1"},
            100L,
            secret,
            keyPair,
            false,
            "testTag"
        );

        // When/Then
        assertFalse(msg.validate(config), "HelloMessage should not validate with wrong network ID");
    }

    @Test
    void testHelloMessageInvalidNetworkVersion() {
        // Given - HelloMessage with wrong network version
        HelloMessage msg = new HelloMessage(
            config.getNetworkId(),
            (short)99,  // Wrong network version
            "testPeer",
            8080,
            "testClient",
            new String[]{"xdag/1"},
            100L,
            secret,
            keyPair,
            false,
            "testTag"
        );

        // When/Then
        assertFalse(msg.validate(config), "HelloMessage should not validate with wrong network version");
    }

    // ==================== WorldMessage Tests ====================

    @Test
    void testWorldMessageConstructor() {
        // Given/When
        WorldMessage msg = new WorldMessage(
            (byte)1,
            (short)1,
            "testPeer",
            8080,
            "testClient",
            new String[]{"xdag/1"},
            200L,
            secret,
            keyPair,
            true,
            "testTag"
        );

        // Then
        assertNotNull(msg);
        assertEquals(MessageCode.HANDSHAKE_WORLD, msg.getCode());
        assertEquals("testPeer", msg.getPeerId());
        assertEquals(8080, msg.getPort());
        assertEquals("testClient", msg.getClientId());
        assertArrayEquals(new String[]{"xdag/1"}, msg.getCapabilities());
        assertEquals(200L, msg.getLatestBlockNumber());
        assertArrayEquals(secret, msg.getSecret());
        assertTrue(msg.isGenerateBlock());
        assertEquals("testTag", msg.getNodeTag());
    }

    @Test
    void testWorldMessageEncodeDecode() {
        // Given - create WorldMessage with proper peerId from keyPair
        String peerId = io.xdag.crypto.encoding.Base58.encodeCheck(
            io.xdag.crypto.keys.AddressUtils.toBytesAddress(keyPair.getPublicKey())
        );

        WorldMessage original = new WorldMessage(
            (byte)1,
            (short)1,
            peerId,  // Use derived peerId
            8080,
            "testClient",
            new String[]{"xdag/1", "xdag/2", "xdag/3"},
            200L,
            secret,
            keyPair,
            false,
            "testTag"
        );

        // When - decode from body (constructor already encoded)
        byte[] body = original.getBody();
        WorldMessage decoded = new WorldMessage(body);

        // Then - verify all fields match
        assertEquals(original.getNetworkId(), decoded.getNetworkId());
        assertEquals(original.getNetworkVersion(), decoded.getNetworkVersion());
        assertEquals(original.getPeerId(), decoded.getPeerId());
        assertEquals(original.getPort(), decoded.getPort());
        assertEquals(original.getClientId(), decoded.getClientId());
        assertArrayEquals(original.getCapabilities(), decoded.getCapabilities());
        assertEquals(original.getLatestBlockNumber(), decoded.getLatestBlockNumber());
        assertArrayEquals(original.getSecret(), decoded.getSecret());
        assertEquals(original.isGenerateBlock(), decoded.isGenerateBlock());
        assertEquals(original.getNodeTag(), decoded.getNodeTag());
    }

    @Test
    void testWorldMessageToString() {
        // Given
        WorldMessage msg = new WorldMessage(
            (byte)1,
            (short)1,
            "testPeer",
            8080,
            "testClient",
            new String[]{"xdag/1"},
            200L,
            secret,
            keyPair,
            true,
            "testTag"
        );

        // When
        String str = msg.toString();

        // Then
        assertNotNull(str);
        assertTrue(str.contains("WorldMessage"));
        assertTrue(str.contains("testPeer"));
        assertTrue(str.contains("8080"));
        assertTrue(str.contains("testClient"));
        assertTrue(str.contains("true")); // isGenerateBlock
    }

    @Test
    void testWorldMessageValidation() {
        // Given - WorldMessage with proper peerId derived from keyPair
        String peerId = io.xdag.crypto.encoding.Base58.encodeCheck(
            io.xdag.crypto.keys.AddressUtils.toBytesAddress(keyPair.getPublicKey())
        );

        // Set handshake expiry high enough to pass timestamp validation
        config.setNetHandshakeExpiry(60000L); // 60 seconds

        WorldMessage msg = new WorldMessage(
            config.getNetworkId(),
            config.getNetworkVersion(),
            peerId,  // Use derived peerId
            8080,
            "testClient",
            new String[]{"xdag/1"},
            200L,
            secret,
            keyPair,
            true,
            "testTag"
        );

        // When/Then
        assertTrue(msg.validate(config), "WorldMessage should validate with matching config");
    }

    @Test
    void testWorldMessageInvalidNetworkId() {
        // Given - WorldMessage with wrong network ID
        WorldMessage msg = new WorldMessage(
            (byte)99,  // Wrong network ID
            config.getNetworkVersion(),
            "testPeer",
            8080,
            "testClient",
            new String[]{"xdag/1"},
            200L,
            secret,
            keyPair,
            true,
            "testTag"
        );

        // When/Then
        assertFalse(msg.validate(config), "WorldMessage should not validate with wrong network ID");
    }

    @Test
    void testWorldMessageInvalidNetworkVersion() {
        // Given - WorldMessage with wrong network version
        WorldMessage msg = new WorldMessage(
            config.getNetworkId(),
            (short)99,  // Wrong network version
            "testPeer",
            8080,
            "testClient",
            new String[]{"xdag/1"},
            200L,
            secret,
            keyPair,
            true,
            "testTag"
        );

        // When/Then
        assertFalse(msg.validate(config), "WorldMessage should not validate with wrong network version");
    }

    // ==================== PongMessage Tests ====================

    @Test
    void testPongMessageDefaultConstructor() {
        // Given/When
        PongMessage msg = new PongMessage();

        // Then
        assertNotNull(msg);
        assertEquals(MessageCode.PONG, msg.getCode());
        assertTrue(msg.getTimestamp() > 0);
        assertNotNull(msg.getBody());
        assertTrue(msg.getBody().length >= 8); // Long timestamp = 8 bytes
    }

    @Test
    void testPongMessageConstructorWithBody() {
        // Given - create PongMessage and get its body
        PongMessage original = new PongMessage();
        byte[] body = original.getBody();

        // When - create new PongMessage from body
        PongMessage decoded = new PongMessage(body);

        // Then
        assertNotNull(decoded);
        assertEquals(original.getTimestamp(), decoded.getTimestamp());
    }

    @Test
    void testPongMessageWithEmptyBody() {
        // Given - empty body
        byte[] emptyBody = new byte[0];

        // When - create PongMessage with empty body
        PongMessage msg = new PongMessage(emptyBody);

        // Then - should fallback to current timestamp
        assertNotNull(msg);
        assertTrue(msg.getTimestamp() > 0);
        assertNotNull(msg.getBody());
        assertTrue(msg.getBody().length >= 8);
    }

    @Test
    void testPongMessageWithInvalidBody() {
        // Given - body with insufficient bytes (< 8 bytes)
        byte[] invalidBody = new byte[]{1, 2, 3, 4};

        // When - create PongMessage with invalid body
        PongMessage msg = new PongMessage(invalidBody);

        // Then - should fallback to current timestamp
        assertNotNull(msg);
        assertTrue(msg.getTimestamp() > 0);
        assertNotNull(msg.getBody());
        assertTrue(msg.getBody().length >= 8);
    }

    @Test
    void testPongMessageWithNullBody() {
        // Given - null body
        byte[] nullBody = null;

        // When - create PongMessage with null body
        PongMessage msg = new PongMessage(nullBody);

        // Then - should fallback to current timestamp
        assertNotNull(msg);
        assertTrue(msg.getTimestamp() > 0);
        assertNotNull(msg.getBody());
        assertTrue(msg.getBody().length >= 8);
    }

    @Test
    void testPongMessageEncode() {
        // Given
        PongMessage msg = new PongMessage();
        long originalTimestamp = msg.getTimestamp();

        // When - encode
        SimpleEncoder enc = new SimpleEncoder();
        msg.encode(enc);
        byte[] encoded = enc.toBytes();

        // Then - should contain timestamp
        assertNotNull(encoded);
        assertTrue(encoded.length >= 8);

        // Verify we can decode the timestamp
        PongMessage decoded = new PongMessage(encoded);
        assertEquals(originalTimestamp, decoded.getTimestamp());
    }

    @Test
    void testPongMessageToString() {
        // Given
        PongMessage msg = new PongMessage();

        // When
        String str = msg.toString();

        // Then
        assertNotNull(str);
        assertTrue(str.contains("PongMessage"));
        assertTrue(str.contains("timestamp"));
        assertTrue(str.contains(String.valueOf(msg.getTimestamp())));
    }
}
