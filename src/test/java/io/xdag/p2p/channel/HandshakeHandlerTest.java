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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.channel.embedded.EmbeddedChannel;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.node.HelloMessage;
import io.xdag.p2p.message.node.InitMessage;
import io.xdag.p2p.message.node.WorldMessage;
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
}
