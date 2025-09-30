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
package io.xdag.p2p.handler.node;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.xdag.p2p.channel.XdagFrame;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.node.PingMessage;
import io.xdag.p2p.message.node.PongMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KeepAliveHandler extends ChannelDuplexHandler {

    private long lastPingTimestamp;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.WRITER_IDLE) {
                log.trace("Writer idle, sending Ping");
                writeMessage(ctx, new PingMessage());
                lastPingTimestamp = System.currentTimeMillis();
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Let frames pass to message codec first; keepalive reacts at Message layer via BusinessHandler if needed
        ctx.fireChannelRead(msg);
    }

    private void writeMessage(ChannelHandlerContext ctx, io.xdag.p2p.message.Message msg) {
        XdagFrame frame = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, msg.getCode().toByte(), 0, msg.getBody().length, msg.getBody().length, msg.getBody());
        ctx.writeAndFlush(frame);
    }
}
