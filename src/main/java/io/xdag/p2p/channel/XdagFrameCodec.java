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
        if (in.readableBytes() < XdagFrame.HEADER_SIZE) {
            return;
        }
        in.markReaderIndex();

        XdagFrame frame = XdagFrame.readHeader(in);

        if (frame.getVersion() != XdagFrame.VERSION) {
            in.resetReaderIndex();
            throw new IOException("Invalid frame version: " + frame.getVersion());
        }

        if (frame.getBodySize() > config.getNetMaxFrameBodySize()) {
            in.resetReaderIndex();
            throw new IOException("Frame body too large: " + frame.getBodySize());
        }

        if (in.readableBytes() < frame.getBodySize()) {
            in.resetReaderIndex();
            return;
        }

        byte[] body = new byte[frame.getBodySize()];
        in.readBytes(body);
        frame.setBody(body);

        out.add(frame);
    }
}
