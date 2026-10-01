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
package io.xdag.p2p.handler.node;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.channel.XdagFrameCodec;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.message.IMessageCode;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.node.PongMessage;
import java.net.InetSocketAddress;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

/**
 * Last handler of an established connection: answers the keep-alive, handles disconnects and hands
 * application messages to the registered {@link io.xdag.p2p.P2pEventHandler}s as {@code [code | body]}.
 *
 * <p>After the handshake only PING, PONG, DISCONNECT and application messages (codes from 0x16) are
 * expected. Anything else - a second handshake, a discovery message on TCP - is a protocol violation and
 * closes the connection.
 */
@Slf4j
public class XdagBusinessHandler extends SimpleChannelInboundHandler<Message> {

    private final P2pConfig config;
    private final ChannelManager channelManager;

    public XdagBusinessHandler(P2pConfig config, ChannelManager channelManager) {
        this.config = config;
        this.channelManager = channelManager;
    }

    private Channel channelOf(ChannelHandlerContext ctx) {
        io.netty.util.Attribute<Channel> attribute = ctx.channel() == null ? null
                : ctx.channel().attr(XdagFrameCodec.CHANNEL_ATTRIBUTE);
        Channel ch = attribute == null ? null : attribute.get();
        if (ch == null && channelManager != null && ctx.channel() != null
                && ctx.channel().remoteAddress() instanceof InetSocketAddress remote) {
            Map<InetSocketAddress, Channel> channels = channelManager.getChannels();
            ch = channels == null ? null : channels.get(remote);
        }
        return ch;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        IMessageCode code = msg.getCode();
        byte codeByte = code.toByte();

        if (codeByte == MessageCode.PING.toByte()) {
            log.trace("Received PING from {}, sending PONG", ctx.channel().remoteAddress());
            try {
                ctx.writeAndFlush(new PongMessage());
            } catch (RuntimeException e) {
                log.debug("Failed to send PONG to {}: {}", ctx.channel().remoteAddress(), e.toString());
            }
            return;
        } else if (codeByte == MessageCode.PONG.toByte()) {
            log.trace("Received PONG from {}", ctx.channel().remoteAddress());
            return;
        } else if (codeByte == MessageCode.DISCONNECT.toByte()) {
            log.debug("Peer {} disconnects: {}", ctx.channel().remoteAddress(), msg);
            ctx.close();
            return;
        } else if (code.isFrameworkMessage() && codeByte != MessageCode.APP_TEST.toByte()) {
            log.debug("Unexpected message {} from {} after the handshake, closing", msg, ctx.channel().remoteAddress());
            Channel ch = channelOf(ctx);
            if (ch != null) {
                ch.close(P2pConstant.DEFAULT_BAN_TIME);
            } else {
                ctx.close();
            }
            return;
        }

        Channel ch = channelOf(ctx);
        if (ch == null) {
            return;
        }

        Bytes deliver;
        if (codeByte == MessageCode.APP_TEST.toByte()) {
            // Deliver pure app payload without network code
            deliver = Bytes.wrap(msg.getBody() == null ? new byte[0] : msg.getBody());
        } else {
            // Deliver as [code|body]
            deliver = msg.getSendData();
        }
        for (var h : config.getHandlerList()) {
            try {
                h.onMessage(ch, deliver);
            } catch (Exception e) {
                log.warn("Handler onMessage error for {}: {}", ctx.channel().remoteAddress(), e.toString());
            }
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel ch = channelOf(ctx);
        if (ch != null) {
            boolean writable = ctx.channel().isWritable();
            for (var h : config.getHandlerList()) {
                try {
                    h.onWritabilityChanged(ch, writable);
                } catch (Exception e) {
                    log.warn("Handler onWritabilityChanged error: {}", e.toString());
                }
            }
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel ch = channelOf(ctx);
        if (ch != null && channelManager != null) {
            log.debug("Channel inactive detected: {}", ctx.channel().remoteAddress());
            channelManager.onChannelInactive(ch);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // caused by what the peer sent, or by the connection: not an error of ours
        log.debug("Closing connection with {}: {}", ctx.channel().remoteAddress(), cause.toString());
        Channel ch = channelOf(ctx);
        if (ch != null && cause instanceof DecoderException) {
            // the peer does not speak the protocol: not for a while
            ch.close(P2pConstant.DEFAULT_BAN_TIME);
        } else {
            ctx.close();
        }
    }
}
