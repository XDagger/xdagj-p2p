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
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.IdleStateHandler;
import io.xdag.crypto.encoding.Base58;
import io.xdag.crypto.hash.HashUtils;
import io.xdag.crypto.keys.AddressUtils;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.handler.node.KeepAliveHandler;
import io.xdag.p2p.handler.node.XdagBusinessHandler;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.node.HandshakeMessage;
import io.xdag.p2p.message.node.HelloMessage;
import io.xdag.p2p.message.node.InitMessage;
import io.xdag.p2p.message.node.WorldMessage;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

/**
 * Mutual authentication of the two ends of a TCP connection.
 *
 * <pre>
 *   dialling side (A)                         accepting side (B)
 *   INIT(nonceA)                  ------&gt;
 *                                 &lt;------     INIT(nonceB)
 *                                 &lt;------     HELLO(.., secret = nonceA, signed by B)
 *   WORLD(.., secret = H(nonceB | id of B), signed by A)   ------&gt;
 * </pre>
 *
 * <p>Each side signs a value the <em>other</em> side chose for this connection, so a recorded message is of no use
 * later. (Until frame version 2 only the dialling side chose a nonce and signed it itself in WORLD; anyone who
 * had once been dialled by a node could replay that node's WORLD for the five minutes its timestamp was
 * accepted and pass as that node.) A's signature also covers the identity of the node it believes it is talking
 * to, so a third node cannot hand B a WORLD that A produced for a connection to somebody else.
 *
 * <p>What the handshake does not do is protect the connection afterwards: messages are neither encrypted nor
 * authenticated. Everything the application receives has to be verified on its own merits.
 */
@Slf4j
public class HandshakeHandler extends ChannelInboundHandlerAdapter {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final P2pConfig config;
    private final ChannelManager channelManager;
    private final ECKeyPair myKey;
    private final boolean isOutbound;
    private final AtomicBoolean isHandshakeDone = new AtomicBoolean(false);

    private ScheduledFuture<?> timeoutFuture;
    /** The nonce this side chose for the other side to sign. */
    private byte[] secret;
    /** The nonce the other side chose for this side to sign. */
    private byte[] peerSecret;

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
                log.debug("Handshake timeout, disconnecting channel: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
        }, config.getNetHandshakeTimeout(), TimeUnit.MILLISECONDS);

        if (isOutbound) {
            sendInit(ctx);
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
        }
        super.channelInactive(ctx);
    }

    private void sendInit(ChannelHandlerContext ctx) {
        this.secret = new byte[InitMessage.SECRET_LENGTH];
        RANDOM.nextBytes(secret);
        writeMessage(ctx, new InitMessage(secret, System.currentTimeMillis()));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (isHandshakeDone.get() || !(msg instanceof XdagFrame)) {
            ctx.fireChannelRead(msg);
            return;
        }

        XdagFrame frame = (XdagFrame) msg;
        MessageCode code = MessageCode.of(frame.getPacketType());

        // handshake messages are small and never split or compressed
        if (code == null || frame.isChunked() || frame.getCompressType() != XdagFrame.COMPRESS_NONE) {
            fail(ctx, "unexpected frame during handshake: type " + frame.getPacketType());
            return;
        }

        switch (code) {
            case HANDSHAKE_INIT -> handleInit(ctx, frame.getBody());
            case HANDSHAKE_HELLO -> handleHello(ctx, frame.getBody());
            case HANDSHAKE_WORLD -> handleWorld(ctx, frame.getBody());
            case DISCONNECT -> ctx.close();
            default -> fail(ctx, "unexpected message during handshake: " + code);
        }
    }

    private void handleInit(ChannelHandlerContext ctx, byte[] body) {
        if (peerSecret != null) {
            fail(ctx, "second INIT");
            return;
        }
        InitMessage initMessage;
        try {
            initMessage = new InitMessage(body);
        } catch (RuntimeException e) {
            fail(ctx, "malformed INIT");
            return;
        }
        if (!initMessage.validate()
                || Math.abs(System.currentTimeMillis() - initMessage.getTimestamp()) > config.getNetHandshakeExpiry()) {
            fail(ctx, "invalid INIT");
            return;
        }
        this.peerSecret = initMessage.getSecret();

        if (!isOutbound) {
            // accepting side: issue our own challenge, then answer theirs
            sendInit(ctx);
            writeMessage(ctx, createHelloMessage(peerSecret));
        }
        // dialling side: this is the challenge of the accepting side; it is answered in WORLD, after HELLO
    }

    private void handleHello(ChannelHandlerContext ctx, byte[] body) {
        if (!isOutbound) {
            fail(ctx, "HELLO on an inbound connection");
            return;
        }
        if (peerSecret == null) {
            fail(ctx, "HELLO before INIT");
            return;
        }
        HelloMessage helloMessage;
        try {
            helloMessage = new HelloMessage(body);
        } catch (RuntimeException e) {
            fail(ctx, "malformed HELLO");
            return;
        }
        if (!Arrays.equals(secret, helloMessage.getSecret()) || !helloMessage.validate(config)) {
            fail(ctx, "invalid HELLO");
            return;
        }
        if (getMyPeerId().equals(helloMessage.getPeerId())) {
            onSelfConnection(ctx);
            return;
        }

        writeMessage(ctx, createWorldMessage(bind(peerSecret, helloMessage.getPeerId())));
        handshakeComplete(ctx, helloMessage);
    }

    private void handleWorld(ChannelHandlerContext ctx, byte[] body) {
        if (isOutbound) {
            fail(ctx, "WORLD on an outbound connection");
            return;
        }
        if (secret == null) {
            fail(ctx, "WORLD before INIT");
            return;
        }
        WorldMessage worldMessage;
        try {
            worldMessage = new WorldMessage(body);
        } catch (RuntimeException e) {
            fail(ctx, "malformed WORLD");
            return;
        }
        if (!Arrays.equals(bind(secret, getMyPeerId()), worldMessage.getSecret()) || !worldMessage.validate(config)) {
            fail(ctx, "invalid WORLD");
            return;
        }
        if (getMyPeerId().equals(worldMessage.getPeerId())) {
            onSelfConnection(ctx);
            return;
        }

        handshakeComplete(ctx, worldMessage);
    }

    /**
     * What the dialling side signs: the nonce of the accepting side together with the identity it expects there.
     */
    static byte[] bind(byte[] nonce, String peerId) {
        return HashUtils.sha256(Bytes.concatenate(Bytes.wrap(nonce), Bytes.wrap(peerId.getBytes(StandardCharsets.UTF_8))))
                .toArray();
    }

    private void onSelfConnection(ChannelHandlerContext ctx) {
        log.debug("Connected to ourselves at {}, closing", ctx.channel().remoteAddress());
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress remote) {
            channelManager.onSelfConnection(remote, isOutbound);
        }
        ctx.close();
    }

    /** The other side does not speak the protocol: close, and do not talk to that address again for a while. */
    private void fail(ChannelHandlerContext ctx, String why) {
        log.debug("Handshake with {} failed: {}", ctx.channel().remoteAddress(), why);
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress remote) {
            channelManager.banNode(remote.getAddress(), P2pConstant.DEFAULT_BAN_TIME);
        }
        ctx.close();
    }

    private void handshakeComplete(ChannelHandlerContext ctx, HandshakeMessage msg) {
        if (isHandshakeDone.compareAndSet(false, true)) {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
            log.debug("Handshake successful with peer: {}", msg.getPeerId());

            ChannelPipeline pipeline = ctx.pipeline();
            // Add handlers for post-handshake communication BEFORE registering the channel,
            // so that application onConnect sends will pass through message codec
            pipeline.addLast("idleStateHandler", new IdleStateHandler(30, 30, 0, TimeUnit.SECONDS));
            pipeline.addLast("keepAliveHandler", new KeepAliveHandler());
            pipeline.addLast("xdagMessageHandler", new XdagMessageHandler(config));
            pipeline.addLast("businessHandler", new XdagBusinessHandler(config, channelManager));
            // Register channel to manager to count as active (this triggers app onConnect callbacks)
            try {
                channelManager.markHandshakeSuccess(ctx, msg, isOutbound);
            } catch (Exception e) {
                log.warn("Registering the connection with {} failed: {}", ctx.channel().remoteAddress(), e.getMessage());
                ctx.close();
                return;
            }

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
                latestBlockNumber(),
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
                latestBlockNumber(),
                secret,
                myKey,
                config.isEnableGenerateBlock(),
                config.getNodeTag()
        );
    }

    private long latestBlockNumber() {
        try {
            return Math.max(0, config.getLatestBlockNumberSupplier().getAsLong());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private void writeMessage(ChannelHandlerContext ctx, Message msg) {
        XdagFrame frame = new XdagFrame(XdagFrame.VERSION, XdagFrame.COMPRESS_NONE, msg.getCode().toByte(), 0, msg.getBody().length, msg.getBody().length, msg.getBody());
        ctx.writeAndFlush(frame);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("Exception in HandshakeHandler for {}: {}", ctx.channel().remoteAddress(), cause.toString());
        if (cause instanceof DecoderException) {
            fail(ctx, "garbage instead of a handshake");
        } else {
            ctx.close();
        }
    }
}
