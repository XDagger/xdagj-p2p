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

import io.xdag.crypto.encoding.Base58;
import io.xdag.crypto.keys.AddressUtils;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.config.P2pConfig;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class HandshakeMessageTest {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ECKeyPair keyPair;
    private P2pConfig config;

    @BeforeEach
    void setUp() {
        byte[] privateKeyBytes = new byte[32];
        SECURE_RANDOM.nextBytes(privateKeyBytes);
        keyPair = ECKeyPair.fromHex(Bytes.wrap(privateKeyBytes).toHexString());

        config = new P2pConfig();
        config.setNetworkId((byte) 1);
        config.setNetworkVersion((short) 1);
        config.setNetHandshakeExpiry(5000L);
    }

    private HelloMessage createTestHelloMessage(byte[] secret) {
        String peerId = Base58.encodeCheck(AddressUtils.toBytesAddress(keyPair.getPublicKey()));
        return new HelloMessage(
                config.getNetworkId(),
                config.getNetworkVersion(),
                peerId,
                1001,
                "test-client",
                new String[]{"DISC_V5", "ETH_66"},
                12345L,
                secret,
                keyPair,
                false,
                "test-node"
        );
    }

    @Test
    void testEncodeDecode() {
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        HelloMessage originalMsg = createTestHelloMessage(secret);

        byte[] encodedBody = originalMsg.getBody();
        assertNotNull(encodedBody);

        HelloMessage decodedMsg = new HelloMessage(encodedBody);

        assertEquals(originalMsg.getNetworkId(), decodedMsg.getNetworkId());
        assertEquals(originalMsg.getNetworkVersion(), decodedMsg.getNetworkVersion());
        assertEquals(originalMsg.getPeerId(), decodedMsg.getPeerId());
        assertEquals(originalMsg.getPort(), decodedMsg.getPort());
        assertEquals(originalMsg.getClientId(), decodedMsg.getClientId());
        assertArrayEquals(originalMsg.getCapabilities(), decodedMsg.getCapabilities());
        assertEquals(originalMsg.getLatestBlockNumber(), decodedMsg.getLatestBlockNumber());
        assertArrayEquals(originalMsg.getSecret(), decodedMsg.getSecret());
        assertEquals(originalMsg.getSignature(), decodedMsg.getSignature());
    }

    @Test
    void testValidation_Success() {
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        HelloMessage originalMsg = createTestHelloMessage(secret);
        HelloMessage decodedMsg = new HelloMessage(originalMsg.getBody());
        assertTrue(decodedMsg.validate(config));
        assertNotNull(decodedMsg.getPublicKey());
    }

    @Test
    void testValidation_WrongNetwork() {
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        // Create message with current networkId (default 1)
        HelloMessage msg = createTestHelloMessage(secret);
        // Change config to a different network id and expect validation to fail
        config.setNetworkId((byte) 2);
        assertFalse(msg.validate(config));
    }

    @Test
    void testValidation_ExpiredTimestamp() throws InterruptedException {
        config.setNetHandshakeExpiry(1L);
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        HelloMessage originalMsg = createTestHelloMessage(secret);

        // This is not a reliable way to test time-sensitive logic.
        // In a real-world scenario, a controllable clock should be injected.
        Thread.sleep(10);

        HelloMessage msg = new HelloMessage(originalMsg.getBody());
        assertFalse(msg.validate(config));
    }

    @Test
    void testValidation_InvalidSignature() {
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        HelloMessage originalMsg = createTestHelloMessage(secret);

        byte[] body = originalMsg.getBody();
        // Tamper with a byte that is part of the signed data, not the signature itself
        body[20] ^= 0x01;

        HelloMessage tamperedMsg = new HelloMessage(body);
        assertFalse(tamperedMsg.validate(config));
    }

    @Test
    void testValidation_InvalidSecretLength() {
        byte[] invalidSecret = new byte[31];
        HelloMessage msg = createTestHelloMessage(invalidSecret);
        assertFalse(msg.validate(config));
    }

    @Test
    void testValidation_WrongPeerId() {
        byte[] secret = new byte[32];
        new Random().nextBytes(secret);
        HelloMessage originalMsg = createTestHelloMessage(secret);

        HelloMessage wrongPeerIdMsg = new HelloMessage(
                originalMsg.getNetworkId(), originalMsg.getNetworkVersion(),
                "a_different_peer_id_that_is_not_base58_encoded_or_valid",
                originalMsg.getPort(), originalMsg.getClientId(),
                originalMsg.getCapabilities(), originalMsg.getLatestBlockNumber(),
                originalMsg.getSecret(), keyPair, originalMsg.isGenerateBlock(),
                originalMsg.getNodeTag()
        );
        assertFalse(wrongPeerIdMsg.validate(config));
    }
}
