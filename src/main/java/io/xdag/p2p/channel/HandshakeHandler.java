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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.IdleStateHandler;
import io.xdag.crypto.encoding.Base58;
import io.xdag.crypto.keys.AddressUtils;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.handler.node.KeepAliveHandler;
import io.xdag.p2p.handler.node.XdagBusinessHandler;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.node.HandshakeMessage;
import io.xdag.p2p.message.node.HelloMessage;
import io.xdag.p2p.message.node.InitMessage;
import io.xdag.p2p.message.node.WorldMessage;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.InetSocketAddress;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandshakeHandler extends ChannelInboundHandlerAdapter {

    private final P2pConfig config;
    private final ChannelManager channelManager;
    private final ECKeyPair myKey;
    private final boolean isOutbound;
    private final AtomicBoolean isHandshakeDone = new AtomicBoolean(false);

    private ScheduledFuture<?> timeoutFuture;
    private byte[] secret;

    public HandshakeHandler(P2pConfig config, ChannelManager channelManager, ECKeyPair myKey, boolean isOutbound) {
        this.config = config;
        this.channelManager = channelManager;
        this.myKey = myKey;
        this.isOutbound = isOutbound;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("Handshake handler active for channel: {}", ctx.channel().remoteAddress());
        timeoutFuture = ctx.executor().schedule(() -> {
            if (!isHandshakeDone.get()) {
                log.warn("Handshake timeout, disconnecting channel: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
        }, config.getNetHandshakeExpiry(), TimeUnit.MILLISECONDS);

        if (isOutbound) {
            startHandshake(ctx);
        }
        super.channelActive(ctx);
    }

    private void startHandshake(ChannelHandlerContext ctx) {
        this.secret = new byte[InitMessage.SECRET_LENGTH];
        new SecureRandom().nextBytes(secret);

        InitMessage initMessage = new InitMessage(secret, System.currentTimeMillis());
        writeMessage(ctx, initMessage);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (isHandshakeDone.get() || !(msg instanceof XdagFrame)) {
            ctx.fireChannelRead(msg);
            return;
        }

        XdagFrame frame = (XdagFrame) msg;
        MessageCode code = MessageCode.of(frame.getPacketType());

        if (code == null) {
            log.warn("Received unknown message code during handshake: {}", frame.getPacketType());
            ctx.close();
            return;
        }

        switch (code) {
            case HANDSHAKE_INIT -> handleInit(ctx, frame.getBody());
            case HANDSHAKE_HELLO -> handleHello(ctx, frame.getBody());
            case HANDSHAKE_WORLD -> handleWorld(ctx, frame.getBody());
            default -> {
                log.warn("Received unexpected message during handshake: {}", code);
                ctx.close();
            }
        }
    }

    private void handleInit(ChannelHandlerContext ctx, byte[] body) {
        if (isOutbound) {
            log.warn("Received INIT message on an outbound connection. Ignoring.");
            return;
        }

        InitMessage initMessage = new InitMessage(body);
        if (!initMessage.validate()) {
            log.warn("Invalid INIT message received.");
            ctx.close();
            return;
        }

        this.secret = initMessage.getSecret();
        HelloMessage helloMessage = createHelloMessage(secret);
        writeMessage(ctx, helloMessage);
    }

    private void handleHello(ChannelHandlerContext ctx, byte[] body) {
        if (!isOutbound) {
            log.warn("Received HELLO message on an inbound connection. Ignoring.");
            return;
        }

        HelloMessage helloMessage = new HelloMessage(body);
        if (!Arrays.equals(secret, helloMessage.getSecret()) || !helloMessage.validate(config)) {
            log.warn("Invalid HELLO message received.");
            ctx.close();
            return;
        }

        WorldMessage worldMessage = createWorldMessage(secret);
        writeMessage(ctx, worldMessage);

        handshakeComplete(ctx, helloMessage);
    }

    private void handleWorld(ChannelHandlerContext ctx, byte[] body) {
        if (isOutbound) {
            log.warn("Received WORLD message on an outbound connection. Ignoring.");
            return;
        }

        WorldMessage worldMessage = new WorldMessage(body);
        if (!Arrays.equals(secret, worldMessage.getSecret()) || !worldMessage.validate(config)) {
            log.warn("Invalid WORLD message received.");
            ctx.close();
            return;
        }

        handshakeComplete(ctx, worldMessage);
    }

    private void handshakeComplete(ChannelHandlerContext ctx, HandshakeMessage msg) {
        if (isHandshakeDone.compareAndSet(false, true)) {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
            log.info("Handshake successful with peer: {}", msg.getPeerId());

            ChannelPipeline pipeline = ctx.pipeline();
            // Add handlers for post-handshake communication BEFORE registering the channel,
            // so that application onConnect sends will pass through message codec
            pipeline.addLast("idleStateHandler", new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS));
            pipeline.addLast("keepAliveHandler", new KeepAliveHandler());
            pipeline.addLast("xdagMessageHandler", new XdagMessageHandler(config));
            pipeline.addLast("businessHandler", new XdagBusinessHandler(config, channelManager));
            // Register channel to manager to count as active (this triggers app onConnect callbacks)
            // Pass nodeId for duplicate connection detection
            try {
                channelManager.markHandshakeSuccess((java.net.InetSocketAddress) ctx.channel().remoteAddress(), ctx, msg.getPeerId());
            } catch (Exception ignored) {}

            // Remove this handler from the pipeline
            pipeline.remove(this);
        }
    }

    private String getMyPeerId() {
        return Base58.encodeCheck(AddressUtils.toBytesAddress(myKey.getPublicKey()));
    }

    private HelloMessage createHelloMessage(byte[] secret) {
        return new HelloMessage(
                config.getNetworkId(),
                config.getNetworkVersion(),
                getMyPeerId(),
                config.getPort(),
                config.getClientId(),
                config.getCapabilities(),
                0,
                secret,
                myKey,
                config.isEnableGenerateBlock(),
                config.getNodeTag()
        );
    }

    private WorldMessage createWorldMessage(byte[] secret) {
        return new WorldMessage(
                config.getNetworkId(),
                config.getNetworkVersion(),
                getMyPeerId(),
                config.getPort(),
                config.getClientId(),
                config.getCapabilities(),
                0,
                secret,
                myKey,
                config.isEnableGenerateBlock(),
                config.getNodeTag()
        );
    }

    private void writeMessage(ChannelHandlerContext ctx, Message msg) {
        XdagFrame frame = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, msg.getCode().toByte(), 0, msg.getBody().length, msg.getBody().length, msg.getBody());
        ctx.writeAndFlush(frame);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in HandshakeHandler", cause);
        ctx.close();
    }
}
