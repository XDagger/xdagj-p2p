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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import io.netty.channel.embedded.EmbeddedChannel;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.config.P2pConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class P2pChannelInitializerTest {

    private P2pConfig config;
    private ChannelManager channelManager;
    private ECKeyPair keyPair;

    @BeforeEach
    void setUp() {
        config = mock(P2pConfig.class);
        channelManager = mock(ChannelManager.class);
        keyPair = ECKeyPair.create();
    }

    @Test
    void testInitChannel_Outbound() {
        P2pChannelInitializer initializer = new P2pChannelInitializer(config, channelManager, keyPair, true);
        EmbeddedChannel ch = new EmbeddedChannel(initializer);

        assertNotNull(ch.pipeline().get("readTimeoutHandler"));
        assertNotNull(ch.pipeline().get("xdagFrameCodec"));
        assertNotNull(ch.pipeline().get("handshakeHandler"));
    }

    @Test
    void testInitChannel_Inbound() {
        P2pChannelInitializer initializer = new P2pChannelInitializer(config, channelManager, keyPair, false);
        EmbeddedChannel ch = new EmbeddedChannel(initializer);

        assertNotNull(ch.pipeline().get("readTimeoutHandler"));
        assertNotNull(ch.pipeline().get("xdagFrameCodec"));
        assertNotNull(ch.pipeline().get("handshakeHandler"));
    }
}
