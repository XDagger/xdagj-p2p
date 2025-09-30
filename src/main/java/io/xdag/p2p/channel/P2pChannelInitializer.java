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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.stats.TrafficStats;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "net")
public class P2pChannelInitializer extends ChannelInitializer<NioSocketChannel> {

    private final P2pConfig config;
    private final ChannelManager channelManager;
    private final ECKeyPair myKey;
    private final boolean isOutbound;

    public P2pChannelInitializer(
            P2pConfig config,
            ChannelManager channelManager,
            ECKeyPair myKey,
            boolean isOutbound) {
        this.config = config;
        this.channelManager = channelManager;
        this.myKey = myKey;
        this.isOutbound = isOutbound;
    }

    @Override
    public void initChannel(NioSocketChannel ch) {
        try {
            // Optimize network buffer sizes for better performance
            ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(256 * 1024));
            ch.config().setOption(ChannelOption.SO_RCVBUF, 256 * 1024);
            ch.config().setOption(ChannelOption.SO_SNDBUF, 256 * 1024);
            ch.config().setOption(ChannelOption.SO_BACKLOG, 1024);

            // Add basic handlers
            ch.pipeline().addLast("readTimeoutHandler", new ReadTimeoutHandler(60, TimeUnit.SECONDS));
            ch.pipeline().addLast(TrafficStats.getTcp());

            // Add frame codec and handshake handler first; message handler will be added after handshake
            ch.pipeline().addLast("xdagFrameCodec", new XdagFrameCodec(config));
            ch.pipeline().addLast("handshakeHandler", new HandshakeHandler(config, channelManager, myKey, isOutbound));


            // MessageHandler will be added dynamically after handshake is complete
            // This can be done by listening to a custom user event fired by HandshakeHandler

        } catch (Exception e) {
            log.error("Unexpected initChannel error", e);
            ch.close();
        }
    }
}
