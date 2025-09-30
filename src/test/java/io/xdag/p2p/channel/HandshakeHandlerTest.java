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


        clientConfig = mock(P2pConfig.class);
        when(clientConfig.getNetHandshakeExpiry()).thenReturn(5000L);
        when(clientConfig.getNetworkId()).thenReturn((byte)1);
        when(clientConfig.getNetworkVersion()).thenReturn((short)1);


        serverConfig = mock(P2pConfig.class);
        when(serverConfig.getNetHandshakeExpiry()).thenReturn(5000L);
        when(serverConfig.getNetworkId()).thenReturn((byte)1);
        when(serverConfig.getNetworkVersion()).thenReturn((short)1);

    }

    @Test
    void testOutboundHandshake() {
        HandshakeHandler handler = new HandshakeHandler(clientConfig, clientKey, true);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // Client initiates handshake
        ch.pipeline().fireChannelActive();

        XdagFrame frame = ch.readOutbound();
        assertNotNull(frame);
        assertEquals(MessageCode.HANDSHAKE_INIT.toByte(), frame.getPacketType());
    }

    @Test
    void testInboundHandshake() {
        HandshakeHandler handler = new HandshakeHandler(serverConfig, serverKey, false);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // Server receives Init
        InitMessage init = new InitMessage(new byte[32], System.currentTimeMillis());
        XdagFrame initFrame = new XdagFrame((byte)0, (byte)0, init.getCode().toByte(), 0, init.getBody().length, init.getBody().length, init.getBody());
        ch.writeInbound(initFrame);

        XdagFrame helloFrame = ch.readOutbound();
        assertNotNull(helloFrame);
        assertEquals(MessageCode.HANDSHAKE_HELLO.toByte(), helloFrame.getPacketType());
    }

    @Test
    void testFullHandshake() {
        // Setup client channel
        HandshakeHandler clientHandshakeHandler = new HandshakeHandler(clientConfig, clientKey, true);
        EmbeddedChannel clientChannel = new EmbeddedChannel(clientHandshakeHandler);

        // Setup server channel
        HandshakeHandler serverHandshakeHandler = new HandshakeHandler(serverConfig, serverKey, false);
        EmbeddedChannel serverChannel = new EmbeddedChannel(serverHandshakeHandler);

        // 1. Client sends Init
        clientChannel.pipeline().fireChannelActive();
        XdagFrame initFrame = clientChannel.readOutbound();

        // 2. Server receives Init, sends Hello
        serverChannel.writeInbound(initFrame);
        XdagFrame helloFrame = serverChannel.readOutbound();
        HelloMessage helloMessage = new HelloMessage(helloFrame.getBody());
        assertTrue(helloMessage.validate(clientConfig));

        // 3. Client receives Hello, sends World
        clientChannel.writeInbound(helloFrame);
        XdagFrame worldFrame = clientChannel.readOutbound();
        WorldMessage worldMessage = new WorldMessage(worldFrame.getBody());
        assertTrue(worldMessage.validate(serverConfig));

        // 4. Server receives World
        serverChannel.writeInbound(worldFrame);

        // Verify both handlers are removed from pipelines
        assertNull(clientChannel.pipeline().get(HandshakeHandler.class));
        assertNull(serverChannel.pipeline().get(HandshakeHandler.class));
    }
}
