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
import io.xdag.p2p.config.P2pConfig;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XdagFrameCodec extends ByteToMessageCodec<XdagFrame> {

    private final P2pConfig config;

    public XdagFrameCodec(P2pConfig config) {
        this.config = config;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, XdagFrame frame, ByteBuf out) {
        if (frame.getVersion() != XdagFrame.VERSION) {
            log.error("Invalid frame version: {}", frame.getVersion());
            return;
        }

        int bodySize = frame.getBody().length;
        if (bodySize > config.getNetMaxFrameBodySize()) {
            log.error("Frame body too large: {}", bodySize);
            return;
        }

        frame.writeHeader(out);
        out.writeBytes(frame.getBody());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws IOException {
        // Need at least 20 bytes for header (with magic)
        if (in.readableBytes() < XdagFrame.HEADER_SIZE) {
            return;
        }
        in.markReaderIndex();

        XdagFrame frame;
        try {
            frame = XdagFrame.readHeader(in);
        } catch (IllegalArgumentException e) {
            // Invalid magic number - try to resync
            in.resetReaderIndex();
            log.warn("Invalid magic number detected, channel={}, readable={}, error={}",
                    ctx.channel(), in.readableBytes(), e.getMessage());

            // Try to resync by searching for magic number
            if (tryResyncByMagic(in)) {
                log.info("Successfully resynced to magic number, channel={}", ctx.channel());
                return;  // Retry decode on next call
            }

            // If resync failed, skip one byte and retry
            in.skipBytes(1);
            return;
        }

        // Validate frame version
        if (frame.getVersion() != XdagFrame.VERSION) {
            in.resetReaderIndex();
            log.warn("Invalid frame version: expected={}, actual={}, channel={}, readable={}",
                    XdagFrame.VERSION, frame.getVersion(), ctx.channel(), in.readableBytes());

            // Try to resync by searching for magic number
            if (tryResyncByMagic(in)) {
                log.info("Successfully resynced after version error, channel={}", ctx.channel());
                return;
            }

            in.skipBytes(1);
            return;
        }

        // Validate body size
        if (frame.getBodySize() > config.getNetMaxFrameBodySize()) {
            in.resetReaderIndex();
            log.warn("Frame body too large: expected <{}, actual={}, channel={}, readable={}",
                    config.getNetMaxFrameBodySize(), frame.getBodySize(), ctx.channel(), in.readableBytes());

            // Try to resync by searching for magic number
            if (tryResyncByMagic(in)) {
                log.info("Successfully resynced after body size error, channel={}", ctx.channel());
                return;
            }

            in.skipBytes(1);
            return;
        }

        // Check if we have enough bytes for the body
        if (in.readableBytes() < frame.getBodySize()) {
            in.resetReaderIndex();
            return;
        }

        byte[] body = new byte[frame.getBodySize()];
        in.readBytes(body);
        frame.setBody(body);

        out.add(frame);
    }

    /**
     * Tries to resync the stream by searching for the magic number
     *
     * @param in ByteBuf to search in
     * @return true if magic number found and reader index repositioned, false otherwise
     */
    private boolean tryResyncByMagic(ByteBuf in) {
        in.resetReaderIndex();
        int maxSearchBytes = Math.min(in.readableBytes(), config.getNetMaxFrameBodySize());

        for (int i = 0; i < maxSearchBytes - 4; i++) {
            in.markReaderIndex();
            int possibleMagic = in.readInt();

            if (possibleMagic == XdagFrame.MAGIC_NUMBER) {
                // Found magic! Reset to just before it so next decode will read it properly
                in.resetReaderIndex();
                return true;
            }

            // Not magic, move one byte forward
            in.resetReaderIndex();
            in.skipBytes(1);
        }

        // Magic not found, reset to beginning
        in.resetReaderIndex();
        return false;
    }
}
