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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.UnknownMessage;
import java.net.InetSocketAddress;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XdagBusinessHandler extends SimpleChannelInboundHandler<Message> {

    private final P2pConfig config;
    private final ChannelManager channelManager;

    public XdagBusinessHandler(P2pConfig config, ChannelManager channelManager) {
        this.config = config;
        this.channelManager = channelManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        try {
            MessageCode code = msg.getType();
            InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
            Channel ch = channelManager.getChannels().get(remote);
            if (ch == null) {
                log.debug("No Channel wrapper found for {}. Dropping message {}", remote, code);
                return;
            }
            log.debug("Business inbound: {} from {}, bodyLen={} handlers={}", code, remote,
                    msg.getBody() != null ? msg.getBody().length : 0, config.getHandlerList().size());
            if (code == MessageCode.PING) {
                // Auto-respond to TCP keepalive pings at business layer
                try {
                    ctx.writeAndFlush(new io.xdag.p2p.message.node.PongMessage());
                } catch (Exception e) {
                    log.warn("Failed to send pong response to {}: {}", remote, e.getMessage());
                }
                return;
            } else if (code == MessageCode.PONG) {
                // Latency can be tracked here if needed
                return;
            } else if (code == MessageCode.APP_TEST) {
                // Deliver pure app payload without network code
                org.apache.tuweni.bytes.Bytes appPayload = org.apache.tuweni.bytes.Bytes.wrap(msg.getBody());
                for (var h : config.getHandlerList()) {
                    h.onMessage(ch, appPayload);
                }
            } else {
                // Deliver as [code|body]
                org.apache.tuweni.bytes.Bytes deliver = msg.getSendData();
                for (var h : config.getHandlerList()) {
                    h.onMessage(ch, deliver);
                }
            }
        } catch (Exception e) {
            log.error("Business handler error: {}", e.getMessage(), e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in XdagBusinessHandler", cause);
        ctx.close();
    }
}
