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
import java.net.InetSocketAddress;
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
            if (!isOutbound) {
                // An accepted connection is admitted (or not) before a single handler sees a byte of it.
                // Outbound connections are admitted when they are dialled.
                InetSocketAddress remote = ch.remoteAddress();
                ChannelManager.Refusal refusal = channelManager == null ? null
                        : channelManager.admit(ch, remote == null ? null : remote.getAddress(), true);
                if (refusal != null) {
                    log.debug("Refusing connection from {}: {}", remote, refusal);
                    ch.close();
                    return;
                }
            }

            // Optimize network buffer sizes for better performance
            ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(256 * 1024));
            ch.config().setOption(ChannelOption.SO_RCVBUF, 256 * 1024);
            ch.config().setOption(ChannelOption.SO_SNDBUF, 256 * 1024);
            // Stop reading from the socket while our outbound buffer to this peer is full instead of buffering
            // without bound; the high-water mark is where Channel.isWritable() turns false.
            ch.config().setWriteBufferHighWaterMark(Math.max(64 * 1024, config.getMaxOutboundQueueBytes() / 4));
            ch.config().setWriteBufferLowWaterMark(Math.max(32 * 1024, config.getMaxOutboundQueueBytes() / 8));

            ch.pipeline().addLast("inboundGuard", new InboundTrafficGuard(config));
            ch.pipeline().addLast("readTimeoutHandler", new ReadTimeoutHandler(60, TimeUnit.SECONDS));

            // Add frame codec and handshake handler first; message handler will be added after handshake
            ch.pipeline().addLast("xdagFrameCodec", new XdagFrameCodec(config));
            ch.pipeline().addLast("handshakeHandler", new HandshakeHandler(config, channelManager, myKey, isOutbound));

        } catch (Exception e) {
            log.error("Unexpected initChannel error", e);
            ch.close();
        }
    }
}
