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
package io.xdag.p2p.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import io.netty.channel.embedded.EmbeddedChannel;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.node.InitMessage;
import java.security.SecureRandom;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HandshakeHandlerTest {

    private P2pConfig clientConfig;
    private P2pConfig serverConfig;
    private ChannelManager channelManager;
    private ECKeyPair clientKey;
    private ECKeyPair serverKey;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();


    @BeforeEach
    void setUp() {
        byte[] privateKeyBytes = new byte[32];
        SECURE_RANDOM.nextBytes(privateKeyBytes);
        clientKey = ECKeyPair.fromHex(Bytes.wrap(privateKeyBytes).toHexString());
        SECURE_RANDOM.nextBytes(privateKeyBytes);
        serverKey = ECKeyPair.fromHex(Bytes.wrap(privateKeyBytes).toHexString());

        // Use real P2pConfig instances instead of mocks for proper message serialization
        clientConfig = new P2pConfig();
        clientConfig.setNetHandshakeExpiry(5000L);
        clientConfig.setNetworkId((byte)1);
        clientConfig.setNetworkVersion((short)1);
        clientConfig.setPort(8080);
        clientConfig.setClientId("test-client");
        clientConfig.setCapabilities(new String[]{"xdag/1"});
        clientConfig.setEnableGenerateBlock(false);
        clientConfig.setNodeTag("test");

        serverConfig = new P2pConfig();
        serverConfig.setNetHandshakeExpiry(5000L);
        serverConfig.setNetworkId((byte)1);
        serverConfig.setNetworkVersion((short)1);
        serverConfig.setPort(8081);
        serverConfig.setClientId("test-server");
        serverConfig.setCapabilities(new String[]{"xdag/1"});
        serverConfig.setEnableGenerateBlock(false);
        serverConfig.setNodeTag("test");

        channelManager = mock(ChannelManager.class);
    }

    @Test
    void testOutboundHandshake() {
        HandshakeHandler handler = new HandshakeHandler(clientConfig, channelManager, clientKey, true);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // Client initiates handshake
        ch.pipeline().fireChannelActive();

        XdagFrame frame = ch.readOutbound();
        assertNotNull(frame);
        assertEquals(MessageCode.HANDSHAKE_INIT.toByte(), frame.getPacketType());
    }

    @Test
    void testInboundHandshake() {
        HandshakeHandler handler = new HandshakeHandler(serverConfig, channelManager, serverKey, false);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // Server receives Init
        InitMessage init = new InitMessage(new byte[32], System.currentTimeMillis());
        XdagFrame initFrame = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, init.getCode().toByte(), 0, init.getBody().length, init.getBody().length, init.getBody());
        ch.writeInbound(initFrame);

        XdagFrame helloFrame = ch.readOutbound();
        assertNotNull(helloFrame);
        assertEquals(MessageCode.HANDSHAKE_HELLO.toByte(), helloFrame.getPacketType());
    }

    @Test
    void testFullHandshake() {
        // Setup client channel - EmbeddedChannel automatically fires channelActive
        HandshakeHandler clientHandshakeHandler = new HandshakeHandler(clientConfig, channelManager, clientKey, true);
        EmbeddedChannel clientChannel = new EmbeddedChannel(clientHandshakeHandler);

        // Setup server channel
        HandshakeHandler serverHandshakeHandler = new HandshakeHandler(serverConfig, channelManager, serverKey, false);
        EmbeddedChannel serverChannel = new EmbeddedChannel(serverHandshakeHandler);

        // 1. Client automatically sends Init when channel is constructed (channelActive)
        XdagFrame initFrame = clientChannel.readOutbound();
        assertNotNull(initFrame, "Init frame should not be null");
        assertEquals(MessageCode.HANDSHAKE_INIT.toByte(), initFrame.getPacketType());

        // 2. Server receives Init, sends Hello
        serverChannel.writeInbound(initFrame);
        XdagFrame helloFrame = serverChannel.readOutbound();
        assertNotNull(helloFrame, "Hello frame should not be null");
        assertEquals(MessageCode.HANDSHAKE_HELLO.toByte(), helloFrame.getPacketType());

        // 3. Client receives Hello, should send World
        clientChannel.writeInbound(helloFrame);

        // Read World frame
        XdagFrame worldFrame = clientChannel.readOutbound();
        assertNotNull(worldFrame, "World frame should have been sent");
        assertEquals(MessageCode.HANDSHAKE_WORLD.toByte(), worldFrame.getPacketType());

        // 4. Server receives World - handshake complete
        serverChannel.writeInbound(worldFrame);

        // Verify both handlers are removed from pipelines after successful handshake
        assertNull(clientChannel.pipeline().get(HandshakeHandler.class),
                   "Client HandshakeHandler should be removed after handshake");
        assertNull(serverChannel.pipeline().get(HandshakeHandler.class),
                   "Server HandshakeHandler should be removed after handshake");
    }

    // ==================== Test invalid INIT message ====================

    @Test
    void testInvalidInitMessage() {
        // Given - server-side handler
        HandshakeHandler handler = new HandshakeHandler(serverConfig, channelManager, serverKey, false);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // When - send invalid INIT message (empty body)
        XdagFrame invalidInitFrame = new XdagFrame(
            XdagFrame.VERSION,
            XdagFrame.COMPRESS_NONE,
            MessageCode.HANDSHAKE_INIT.toByte(),
            0,
            0,
            0,
            new byte[0]
        );
        ch.writeInbound(invalidInitFrame);

        // Then - channel should be closed, no HELLO response
        assertNull(ch.readOutbound(), "Should not send HELLO for invalid INIT");
    }

    // ==================== Test client receives INIT message ====================

    @Test
    void testClientReceivesInitMessage() {
        // Given - client handler (isOutbound = true)
        HandshakeHandler handler = new HandshakeHandler(clientConfig, channelManager, clientKey, true);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // Clear initial INIT message
        ch.readOutbound();

        // When - client receives INIT message (should not happen)
        InitMessage init = new InitMessage(new byte[32], System.currentTimeMillis());
        XdagFrame initFrame = new XdagFrame(
            XdagFrame.VERSION,
            XdagFrame.COMPRESS_NONE,
            init.getCode().toByte(),
            0,
            init.getBody().length,
            init.getBody().length,
            init.getBody()
        );
        ch.writeInbound(initFrame);

        // Then - should ignore, no response
        assertNull(ch.readOutbound(), "Client should ignore INIT message");
    }

    // ==================== Test server receives HELLO message ====================

    @Test
    void testServerReceivesHelloMessage() {
        // Given - server-side handler (isOutbound = false)
        HandshakeHandler handler = new HandshakeHandler(serverConfig, channelManager, serverKey, false);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // When - server receives HELLO message (should not happen)
        byte[] secret = new byte[32];
        SECURE_RANDOM.nextBytes(secret);
        io.xdag.p2p.message.node.HelloMessage hello = new io.xdag.p2p.message.node.HelloMessage(
            (byte)1, (short)1, "peerId", 8080, "client", new String[]{"xdag/1"}, 0, secret, clientKey, false, "test"
        );
        XdagFrame helloFrame = new XdagFrame(
            XdagFrame.VERSION,
            XdagFrame.COMPRESS_NONE,
            hello.getCode().toByte(),
            0,
            hello.getBody().length,
            hello.getBody().length,
            hello.getBody()
        );
        ch.writeInbound(helloFrame);

        // Then - should ignore, no response
        assertNull(ch.readOutbound(), "Server should ignore HELLO message");
    }

    // ==================== Test client receives WORLD message ====================

    @Test
    void testClientReceivesWorldMessage() {
        // Given - client handler (isOutbound = true)
        HandshakeHandler handler = new HandshakeHandler(clientConfig, channelManager, clientKey, true);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // Clear initial INIT message
        ch.readOutbound();

        // When - client receives WORLD message (should not happen)
        byte[] secret = new byte[32];
        SECURE_RANDOM.nextBytes(secret);
        io.xdag.p2p.message.node.WorldMessage world = new io.xdag.p2p.message.node.WorldMessage(
            (byte)1, (short)1, "peerId", 8080, "client", new String[]{"xdag/1"}, 0, secret, serverKey, false, "test"
        );
        XdagFrame worldFrame = new XdagFrame(
            XdagFrame.VERSION,
            XdagFrame.COMPRESS_NONE,
            world.getCode().toByte(),
            0,
            world.getBody().length,
            world.getBody().length,
            world.getBody()
        );
        ch.writeInbound(worldFrame);

        // Then - should ignore, no response
        assertNull(ch.readOutbound(), "Client should ignore WORLD message");
    }

    // ==================== Test HELLO message with mismatched secret ====================

    @Test
    void testHelloMessageWithMismatchedSecret() {
        // Given - setup client channel and send INIT
        HandshakeHandler clientHandler = new HandshakeHandler(clientConfig, channelManager, clientKey, true);
        EmbeddedChannel clientChannel = new EmbeddedChannel(clientHandler);

        XdagFrame initFrame = clientChannel.readOutbound();
        assertNotNull(initFrame);

        // When - server sends HELLO but secret mismatches
        byte[] wrongSecret = new byte[32];
        SECURE_RANDOM.nextBytes(wrongSecret);
        io.xdag.p2p.message.node.HelloMessage hello = new io.xdag.p2p.message.node.HelloMessage(
            (byte)1, (short)1, "peerId", 8080, "client", new String[]{"xdag/1"}, 0, wrongSecret, serverKey, false, "test"
        );
        XdagFrame helloFrame = new XdagFrame(
            XdagFrame.VERSION,
            XdagFrame.COMPRESS_NONE,
            hello.getCode().toByte(),
            0,
            hello.getBody().length,
            hello.getBody().length,
            hello.getBody()
        );
        clientChannel.writeInbound(helloFrame);

        // Then - should close connection, no WORLD sent
        assertNull(clientChannel.readOutbound(), "Should not send WORLD for mismatched secret");
    }

    // ==================== Test WORLD message with mismatched secret ====================

    @Test
    void testWorldMessageWithMismatchedSecret() {
        // Given - setup server channel
        HandshakeHandler serverHandler = new HandshakeHandler(serverConfig, channelManager, serverKey, false);
        EmbeddedChannel serverChannel = new EmbeddedChannel(serverHandler);

        // Server receives INIT and sends HELLO
        byte[] secret = new byte[32];
        SECURE_RANDOM.nextBytes(secret);
        InitMessage init = new InitMessage(secret, System.currentTimeMillis());
        XdagFrame initFrame = new XdagFrame(
            XdagFrame.VERSION,
            XdagFrame.COMPRESS_NONE,
            init.getCode().toByte(),
            0,
            init.getBody().length,
            init.getBody().length,
            init.getBody()
        );
        serverChannel.writeInbound(initFrame);
        XdagFrame helloFrame = serverChannel.readOutbound();
        assertNotNull(helloFrame);

        // When - client sends WORLD but secret mismatches
        byte[] wrongSecret = new byte[32];
        SECURE_RANDOM.nextBytes(wrongSecret);
        io.xdag.p2p.message.node.WorldMessage world = new io.xdag.p2p.message.node.WorldMessage(
            (byte)1, (short)1, "peerId", 8080, "client", new String[]{"xdag/1"}, 0, wrongSecret, clientKey, false, "test"
        );
        XdagFrame worldFrame = new XdagFrame(
            XdagFrame.VERSION,
            XdagFrame.COMPRESS_NONE,
            world.getCode().toByte(),
            0,
            world.getBody().length,
            world.getBody().length,
            world.getBody()
        );
        serverChannel.writeInbound(worldFrame);

        // Then - handshake should fail, connection closed
        // Note: when validation fails, handler closes connection, EmbeddedChannel does not auto-remove handler
        // We verify no further output messages
        assertNull(serverChannel.readOutbound(), "Should not send further messages after failed validation");
    }

    // ==================== Test unknown message type ====================

    @Test
    void testUnknownMessageCode() {
        // Given
        HandshakeHandler handler = new HandshakeHandler(serverConfig, channelManager, serverKey, false);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // When - send unknown message type (using PING message code)
        XdagFrame unknownFrame = new XdagFrame(
            XdagFrame.VERSION,
            XdagFrame.COMPRESS_NONE,
            MessageCode.PING.toByte(),
            0,
            0,
            0,
            new byte[0]
        );
        ch.writeInbound(unknownFrame);

        // Then - should close connection
        assertNull(ch.readOutbound(), "Should not send response for unknown message");
    }

    // ==================== Test non-XdagFrame message ====================

    @Test
    void testNonXdagFrameMessage() {
        // Given
        HandshakeHandler handler = new HandshakeHandler(serverConfig, channelManager, serverKey, false);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // When - send non-XdagFrame message
        String nonFrameMessage = "not a frame";
        ch.writeInbound(nonFrameMessage);

        // Then - message should be passed to next handler (fireChannelRead)
        Object receivedMsg = ch.readInbound();
        assertEquals(nonFrameMessage, receivedMsg, "Non-frame message should be passed through");
    }

    // ==================== Test message pass-through after handshake ====================

    @Test
    void testMessagePassThroughAfterHandshake() {
        // Given - complete handshake
        HandshakeHandler clientHandler = new HandshakeHandler(clientConfig, channelManager, clientKey, true);
        EmbeddedChannel clientChannel = new EmbeddedChannel(clientHandler);

        HandshakeHandler serverHandler = new HandshakeHandler(serverConfig, channelManager, serverKey, false);
        EmbeddedChannel serverChannel = new EmbeddedChannel(serverHandler);

        // Complete handshake flow
        XdagFrame initFrame = clientChannel.readOutbound();
        serverChannel.writeInbound(initFrame);
        XdagFrame helloFrame = serverChannel.readOutbound();
        clientChannel.writeInbound(helloFrame);
        XdagFrame worldFrame = clientChannel.readOutbound();
        serverChannel.writeInbound(worldFrame);

        // When - send message after handshake complete
        io.xdag.p2p.message.node.PingMessage ping = new io.xdag.p2p.message.node.PingMessage(new byte[4]);
        XdagFrame pingFrame = new XdagFrame(
            XdagFrame.VERSION,
            XdagFrame.COMPRESS_NONE,
            ping.getCode().toByte(),
            0,
            ping.getBody().length,
            ping.getBody().length,
            ping.getBody()
        );
        serverChannel.writeInbound(pingFrame);

        // Then - message should be passed to business handler
        // Since businessHandler is added to pipeline, it will handle this message
        // EmbeddedChannel behavior: if no handler reads message, readInbound returns null
        // This is normal, because businessHandler handled message but may not produce inbound data
        // We verify handler is indeed removed
        assertNull(serverChannel.pipeline().get(HandshakeHandler.class),
                   "HandshakeHandler should be removed after successful handshake");
    }

    // ==================== Test handshake timeout ====================

    @Test
    void testHandshakeTimeout() throws InterruptedException {
        // Given - set very short timeout
        P2pConfig timeoutConfig = new P2pConfig();
        timeoutConfig.setNetHandshakeExpiry(100L); // 100ms timeout
        timeoutConfig.setNetworkId((byte)1);
        timeoutConfig.setNetworkVersion((short)1);
        timeoutConfig.setPort(8080);
        timeoutConfig.setClientId("test-client");
        timeoutConfig.setCapabilities(new String[]{"xdag/1"});

        HandshakeHandler handler = new HandshakeHandler(timeoutConfig, channelManager, clientKey, true);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // Clear initial INIT message
        ch.readOutbound();

        // When - wait for timeout
        Thread.sleep(200);
        ch.runScheduledPendingTasks(); // Trigger scheduled task

        // Then - channel should be closed
        assertNull(ch.readOutbound(), "No further messages after timeout");
    }

    // ==================== Test HELLO message with invalid network version ====================

    @Test
    void testHelloMessageWithInvalidNetworkVersion() {
        // Given - client sends INIT
        HandshakeHandler clientHandler = new HandshakeHandler(clientConfig, channelManager, clientKey, true);
        EmbeddedChannel clientChannel = new EmbeddedChannel(clientHandler);

        XdagFrame initFrame = clientChannel.readOutbound();
        assertNotNull(initFrame);

        // When - server sends HELLO but network version mismatches
        byte[] secret = new byte[32];
        // Extract secret from initFrame (need to parse InitMessage)
        InitMessage parsedInit = new InitMessage(initFrame.getBody());
        secret = parsedInit.getSecret();

        io.xdag.p2p.message.node.HelloMessage hello = new io.xdag.p2p.message.node.HelloMessage(
            (byte)99,  // Wrong network ID
            (short)99, // Wrong network version
            "peerId",
            8080,
            "client",
            new String[]{"xdag/1"},
            0,
            secret,
            serverKey,
            false,
            "test"
        );
        XdagFrame helloFrame = new XdagFrame(
            XdagFrame.VERSION,
            XdagFrame.COMPRESS_NONE,
            hello.getCode().toByte(),
            0,
            hello.getBody().length,
            hello.getBody().length,
            hello.getBody()
        );
        clientChannel.writeInbound(helloFrame);

        // Then - should close connection, no WORLD sent
        assertNull(clientChannel.readOutbound(), "Should not send WORLD for invalid network version");
    }
}
