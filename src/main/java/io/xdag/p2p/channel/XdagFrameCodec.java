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
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.AttributeKey;
import io.xdag.p2p.config.P2pConfig;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Frames on the wire: 20-byte header (see {@link XdagFrame}) followed by the body.
 *
 * <p>A TCP stream never loses or reorders bytes, so a header that does not parse means the other side does not
 * speak this protocol (or is feeding us garbage). The connection is closed at the first such header: searching
 * the stream for the next magic number - what this codec used to do - lets a peer keep a connection alive on
 * garbage while every byte of it is scanned again and again and logged.
 */
@Slf4j
public class XdagFrameCodec extends ByteToMessageCodec<XdagFrame> {

    private final P2pConfig config;

    // Attribute key to store/retrieve Channel from ChannelHandlerContext
    public static final AttributeKey<Channel> CHANNEL_ATTRIBUTE = AttributeKey.valueOf("p2p.channel");

    public XdagFrameCodec(P2pConfig config) {
        this.config = config;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, XdagFrame frame, ByteBuf out) {
        if (frame.getVersion() != XdagFrame.VERSION) {
            throw new EncoderException("Invalid frame version: " + frame.getVersion());
        }

        int bodySize = frame.getBody() == null ? 0 : frame.getBody().length;
        if (bodySize != frame.getBodySize() || bodySize > config.getNetMaxFrameBodySize()) {
            throw new EncoderException("Invalid frame body size: " + bodySize);
        }

        frame.writeHeader(out);
        if (bodySize > 0) {
            out.writeBytes(frame.getBody());
        }

        // Track network layer send - record total frame size (header + body)
        int totalFrameSize = XdagFrame.HEADER_SIZE + bodySize;

        // Retrieve Channel from context attributes
        Channel channel = ctx.channel().attr(CHANNEL_ATTRIBUTE).get();
        if (channel != null && channel.getLayeredStats() != null) {
            channel.getLayeredStats().getNetwork().recordMessageSent(totalFrameSize);
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < XdagFrame.HEADER_SIZE) {
            return;
        }
        in.markReaderIndex();

        XdagFrame frame;
        try {
            frame = XdagFrame.readHeader(in);
        } catch (IllegalArgumentException e) {
            throw new CorruptedFrameException(e.getMessage());
        }
        if (frame.getVersion() != XdagFrame.VERSION) {
            throw new CorruptedFrameException("Unsupported frame version: " + frame.getVersion());
        }
        if (frame.getCompressType() != XdagFrame.COMPRESS_NONE && frame.getCompressType() != XdagFrame.COMPRESS_SNAPPY) {
            throw new CorruptedFrameException("Unsupported compress type: " + frame.getCompressType());
        }
        int bodySize = frame.getBodySize();
        int packetSize = frame.getPacketSize();
        if (bodySize < 0 || bodySize > config.getNetMaxFrameBodySize()) {
            throw new CorruptedFrameException("Invalid frame body size: " + bodySize);
        }
        if (packetSize < 0 || packetSize > config.getNetMaxPacketSize() || bodySize > packetSize) {
            throw new CorruptedFrameException("Invalid packet size: " + packetSize);
        }

        // Check if we have enough bytes for the body
        if (in.readableBytes() < bodySize) {
            in.resetReaderIndex();
            return;
        }

        byte[] body = new byte[bodySize];
        in.readBytes(body);
        frame.setBody(body);

        // Track network layer receive - record total frame size (header + body)
        int totalFrameSize = XdagFrame.HEADER_SIZE + bodySize;

        // Retrieve Channel from context attributes
        Channel channel = ctx.channel().attr(CHANNEL_ATTRIBUTE).get();
        if (channel != null && channel.getLayeredStats() != null) {
            channel.getLayeredStats().getNetwork().recordMessageReceived(totalFrameSize);
        }

        out.add(frame);
    }
}
