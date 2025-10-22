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

import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.node.*;
import io.xdag.p2p.message.discover.*;
import io.xdag.p2p.utils.SimpleEncoder;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageFactoryTest {

    private MessageFactory factory;
    private P2pConfig config;

    @BeforeEach
    void setUp() {
        factory = new MessageFactory();
        config = new P2pConfig();
    }

    // ==================== create() Tests ====================

    @Test
    void testCreateInitMessage() throws MessageException {
        // Given - encoded INIT message
        byte[] secret = new byte[32];
        long timestamp = System.currentTimeMillis();
        InitMessage originalMsg = new InitMessage(secret, timestamp);
        SimpleEncoder enc = new SimpleEncoder();
        originalMsg.encode(enc);
        byte[] body = enc.toBytes();

        // When
        Message decoded = factory.create(MessageCode.HANDSHAKE_INIT.toByte(), body);

        // Then
        assertNotNull(decoded);
        assertInstanceOf(InitMessage.class, decoded);
        assertEquals(MessageCode.HANDSHAKE_INIT, decoded.getCode());
    }

    @Test
    void testCreateHelloMessage() throws MessageException {
        // Given - properly encoded HELLO message with all required fields
        byte networkId = (byte) 1;
        short networkVersion = (short) 1;
        String peerId = "test-peer-id";
        int port = 8080;
        String clientId = "test-client";
        String[] capabilities = new String[]{"xdag/1"};
        long latestBlockNumber = 100L;
        byte[] secret = new byte[32];
        ECKeyPair keyPair = ECKeyPair.generate();
        boolean isServer = false;
        String tag = "test-tag";

        HelloMessage originalMsg = new HelloMessage(
            networkId, networkVersion, peerId, port, clientId,
            capabilities, latestBlockNumber, secret, keyPair, isServer, tag
        );
        byte[] body = originalMsg.getBody();

        // When
        Message decoded = factory.create(MessageCode.HANDSHAKE_HELLO.toByte(), body);

        // Then
        assertNotNull(decoded);
        assertInstanceOf(HelloMessage.class, decoded);
        assertEquals(MessageCode.HANDSHAKE_HELLO, decoded.getCode());
    }

    @Test
    void testCreateWorldMessage() throws MessageException {
        // Given - properly encoded WORLD message with all required fields
        byte networkId = (byte) 1;
        short networkVersion = (short) 1;
        String peerId = "test-peer-id-2";
        int port = 8081;
        String clientId = "test-client-2";
        String[] capabilities = new String[]{"xdag/1"};
        long latestBlockNumber = 200L;
        byte[] secret = new byte[32];
        ECKeyPair keyPair = ECKeyPair.generate();
        boolean isServer = true;
        String tag = "test-tag-2";

        WorldMessage originalMsg = new WorldMessage(
            networkId, networkVersion, peerId, port, clientId,
            capabilities, latestBlockNumber, secret, keyPair, isServer, tag
        );
        byte[] body = originalMsg.getBody();

        // When
        Message decoded = factory.create(MessageCode.HANDSHAKE_WORLD.toByte(), body);

        // Then
        assertNotNull(decoded);
        assertInstanceOf(WorldMessage.class, decoded);
        assertEquals(MessageCode.HANDSHAKE_WORLD, decoded.getCode());
    }

    @Test
    void testCreateDisconnectMessage() throws MessageException {
        // Given - encoded DISCONNECT message
        DisconnectMessage originalMsg = new DisconnectMessage(ReasonCode.BAD_NETWORK);
        SimpleEncoder enc = new SimpleEncoder();
        originalMsg.encode(enc);
        byte[] body = enc.toBytes();

        // When
        Message decoded = factory.create(MessageCode.DISCONNECT.toByte(), body);

        // Then
        assertNotNull(decoded);
        assertInstanceOf(DisconnectMessage.class, decoded);
        assertEquals(MessageCode.DISCONNECT, decoded.getCode());
    }

    @Test
    void testCreatePingMessage() throws MessageException {
        // Given - encoded PING message
        PingMessage originalMsg = new PingMessage(new byte[10]);
        SimpleEncoder enc = new SimpleEncoder();
        originalMsg.encode(enc);
        byte[] body = enc.toBytes();

        // When
        Message decoded = factory.create(MessageCode.PING.toByte(), body);

        // Then
        assertNotNull(decoded);
        assertInstanceOf(PingMessage.class, decoded);
        assertEquals(MessageCode.PING, decoded.getCode());
    }

    @Test
    void testCreatePongMessage() throws MessageException {
        // Given - encoded PONG message
        PongMessage originalMsg = new PongMessage(new byte[10]);
        SimpleEncoder enc = new SimpleEncoder();
        originalMsg.encode(enc);
        byte[] body = enc.toBytes();

        // When
        Message decoded = factory.create(MessageCode.PONG.toByte(), body);

        // Then
        assertNotNull(decoded);
        assertInstanceOf(PongMessage.class, decoded);
        assertEquals(MessageCode.PONG, decoded.getCode());
    }

    @Test
    void testCreateKadPingMessage() throws MessageException {
        // Given - encoded KAD_PING message
        String fromId = org.apache.tuweni.bytes.Bytes.random(20).toHexString();
        String toId = org.apache.tuweni.bytes.Bytes.random(20).toHexString();
        io.xdag.p2p.discover.Node fromNode = new io.xdag.p2p.discover.Node(
            fromId, new java.net.InetSocketAddress("127.0.0.1", 8001)
        );
        io.xdag.p2p.discover.Node toNode = new io.xdag.p2p.discover.Node(
            toId, new java.net.InetSocketAddress("127.0.0.1", 8002)
        );
        KadPingMessage originalMsg = new KadPingMessage(fromNode, toNode);
        SimpleEncoder enc = new SimpleEncoder();
        originalMsg.encode(enc);
        byte[] body = enc.toBytes();

        // When
        Message decoded = factory.create(MessageCode.KAD_PING.toByte(), body);

        // Then
        assertNotNull(decoded);
        assertInstanceOf(KadPingMessage.class, decoded);
        assertEquals(MessageCode.KAD_PING, decoded.getCode());
    }

    @Test
    void testCreateKadPongMessage() throws MessageException {
        // Given - encoded KAD_PONG message
        String fromId = org.apache.tuweni.bytes.Bytes.random(20).toHexString();
        io.xdag.p2p.discover.Node fromNode = new io.xdag.p2p.discover.Node(
            fromId, new java.net.InetSocketAddress("127.0.0.1", 8003)
        );
        KadPongMessage originalMsg = new KadPongMessage(fromNode);
        SimpleEncoder enc = new SimpleEncoder();
        originalMsg.encode(enc);
        byte[] body = enc.toBytes();

        // When
        Message decoded = factory.create(MessageCode.KAD_PONG.toByte(), body);

        // Then
        assertNotNull(decoded);
        assertInstanceOf(KadPongMessage.class, decoded);
        assertEquals(MessageCode.KAD_PONG, decoded.getCode());
    }

    @Test
    void testCreateKadFindNodeMessage() throws MessageException {
        // Given - encoded KAD_FIND_NODE message with Node and target
        String fromId = org.apache.tuweni.bytes.Bytes.random(20).toHexString();
        io.xdag.p2p.discover.Node fromNode = new io.xdag.p2p.discover.Node(
            fromId, new java.net.InetSocketAddress("127.0.0.1", 9001)
        );
        Bytes targetId = Bytes.random(64);
        KadFindNodeMessage originalMsg = new KadFindNodeMessage(fromNode, targetId);
        SimpleEncoder enc = new SimpleEncoder();
        originalMsg.encode(enc);
        byte[] body = enc.toBytes();

        // When
        Message decoded = factory.create(MessageCode.KAD_FIND_NODE.toByte(), body);

        // Then
        assertNotNull(decoded);
        assertInstanceOf(KadFindNodeMessage.class, decoded);
        assertEquals(MessageCode.KAD_FIND_NODE, decoded.getCode());
    }

    @Test
    void testCreateKadNeighborsMessage() throws MessageException {
        // Given - encoded KAD_NEIGHBORS message
        String fromId = org.apache.tuweni.bytes.Bytes.random(20).toHexString();
        io.xdag.p2p.discover.Node fromNode = new io.xdag.p2p.discover.Node(
            fromId, new java.net.InetSocketAddress("127.0.0.1", 8005)
        );
        java.util.List<io.xdag.p2p.discover.Node> nodes = new java.util.ArrayList<>();
        KadNeighborsMessage originalMsg = new KadNeighborsMessage(fromNode, nodes);
        SimpleEncoder enc = new SimpleEncoder();
        originalMsg.encode(enc);
        byte[] body = enc.toBytes();

        // When
        Message decoded = factory.create(MessageCode.KAD_NEIGHBORS.toByte(), body);

        // Then
        assertNotNull(decoded);
        assertInstanceOf(KadNeighborsMessage.class, decoded);
        assertEquals(MessageCode.KAD_NEIGHBORS, decoded.getCode());
    }

    @Test
    void testCreateWithInvalidMessageCode() throws MessageException {
        // Given - invalid message code
        byte invalidCode = (byte) 0xFF;

        // When
        Message result = factory.create(invalidCode, new byte[0]);

        // Then - should return null for unknown code
        assertNull(result);
    }

    @Test
    void testCreateWithNullBody() {
        // Given - null body
        byte validCode = MessageCode.PING.toByte();

        // When/Then - should throw MessageException
        MessageException exception = assertThrows(MessageException.class, () -> {
            factory.create(validCode, null);
        });

        assertTrue(exception.getMessage().contains("Message body is null"));
    }

    @Test
    void testCreateWithMalformedBody() {
        // Given - malformed body (too short for expected format)
        byte[] malformedBody = new byte[]{1}; // Too short for most messages

        // When/Then - should throw MessageException
        assertThrows(MessageException.class, () -> {
            factory.create(MessageCode.HANDSHAKE_INIT.toByte(), malformedBody);
        });
    }

    // ==================== parse() Tests ====================

    @Test
    void testParseValidMessage() throws MessageException {
        // Given - valid encoded message with code prefix
        PingMessage originalMsg = new PingMessage(new byte[10]);
        Bytes encoded = originalMsg.getSendData();

        // When
        Message parsed = MessageFactory.parse(config, encoded);

        // Then
        assertNotNull(parsed);
        assertInstanceOf(PingMessage.class, parsed);
        assertEquals(MessageCode.PING, parsed.getCode());
    }

    @Test
    void testParseWithEmptyBytes() {
        // Given - empty bytes
        Bytes empty = Bytes.EMPTY;

        // When/Then - should throw MessageException
        MessageException exception = assertThrows(MessageException.class, () -> {
            MessageFactory.parse(config, empty);
        });

        assertEquals("Empty UDP packet", exception.getMessage());
    }

    @Test
    void testParseWithNullBytes() {
        // Given - null bytes
        Bytes nullBytes = null;

        // When/Then - should throw MessageException
        MessageException exception = assertThrows(MessageException.class, () -> {
            MessageFactory.parse(config, nullBytes);
        });

        assertEquals("Empty UDP packet", exception.getMessage());
    }

    @Test
    void testParseWithOnlyMessageCode() throws MessageException {
        // Given - only message code, no body
        Bytes onlyCode = Bytes.of(MessageCode.PING.toByte());

        // When
        Message parsed = MessageFactory.parse(config, onlyCode);

        // Then - should create message with empty body
        assertNotNull(parsed);
        assertEquals(MessageCode.PING, parsed.getCode());
    }

    @Test
    void testParseDisconnectMessage() throws MessageException {
        // Given - encoded DISCONNECT message
        DisconnectMessage originalMsg = new DisconnectMessage(ReasonCode.BAD_PEER);
        Bytes encoded = originalMsg.getSendData();

        // When
        Message parsed = MessageFactory.parse(config, encoded);

        // Then
        assertNotNull(parsed);
        assertInstanceOf(DisconnectMessage.class, parsed);
        assertEquals(MessageCode.DISCONNECT, parsed.getCode());
    }

    @Test
    void testParseWorldMessage() throws MessageException {
        // Given - encoded WORLD message
        byte networkId = (byte) 1;
        short networkVersion = (short) 1;
        String peerId = "test-peer-world";
        int port = 8085;
        String clientId = "test-client-world";
        String[] capabilities = new String[]{"xdag/1"};
        long latestBlockNumber = 500L;
        byte[] secret = new byte[32];
        ECKeyPair keyPair = ECKeyPair.generate();
        boolean isServer = true;
        String tag = "test-tag-world";

        WorldMessage originalMsg = new WorldMessage(
            networkId, networkVersion, peerId, port, clientId,
            capabilities, latestBlockNumber, secret, keyPair, isServer, tag
        );
        Bytes encoded = originalMsg.getSendData();

        // When
        Message parsed = MessageFactory.parse(config, encoded);

        // Then
        assertNotNull(parsed);
        assertInstanceOf(WorldMessage.class, parsed);
        assertEquals(MessageCode.HANDSHAKE_WORLD, parsed.getCode());
    }

    @Test
    void testParseHelloMessage() throws MessageException {
        // Given - encoded HELLO message
        byte networkId = (byte) 1;
        short networkVersion = (short) 1;
        String peerId = "test-peer-hello";
        int port = 8086;
        String clientId = "test-client-hello";
        String[] capabilities = new String[]{"xdag/1"};
        long latestBlockNumber = 600L;
        byte[] secret = new byte[32];
        ECKeyPair keyPair = ECKeyPair.generate();
        boolean isServer = false;
        String tag = "test-tag-hello";

        HelloMessage originalMsg = new HelloMessage(
            networkId, networkVersion, peerId, port, clientId,
            capabilities, latestBlockNumber, secret, keyPair, isServer, tag
        );
        Bytes encoded = originalMsg.getSendData();

        // When
        Message parsed = MessageFactory.parse(config, encoded);

        // Then
        assertNotNull(parsed);
        assertInstanceOf(HelloMessage.class, parsed);
        assertEquals(MessageCode.HANDSHAKE_HELLO, parsed.getCode());
    }
}
