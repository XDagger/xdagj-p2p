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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.xdag.p2p.config.P2pConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * Bounds what one connection may send us: a token bucket of {@code maxInboundBytesPerSecond} with a burst of
 * {@code maxInboundBurstBytes}. A peer that exceeds it is disconnected. This sits at the head of the pipeline,
 * before any decoding, so the bound holds whatever the bytes are.
 */
@Slf4j(topic = "net")
public class InboundTrafficGuard extends ChannelInboundHandlerAdapter {

    private final double rate;
    private final double burst;
    private double tokens;
    private long last = System.nanoTime();

    public InboundTrafficGuard(P2pConfig config) {
        this.rate = Math.max(1, config.getMaxInboundBytesPerSecond());
        this.burst = Math.max(this.rate, config.getMaxInboundBurstBytes());
        this.tokens = this.burst;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf buf) {
            long now = System.nanoTime();
            tokens = Math.min(burst, tokens + (now - last) / 1e9 * rate);
            last = now;
            tokens -= buf.readableBytes();
            if (tokens < 0) {
                log.debug("Peer {} sends faster than allowed, disconnecting", ctx.channel().remoteAddress());
                buf.release();
                ctx.close();
                return;
            }
        }
        super.channelRead(ctx, msg);
    }
}
