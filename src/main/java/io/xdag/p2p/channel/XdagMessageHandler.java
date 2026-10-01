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
import io.netty.handler.codec.MessageToMessageCodec;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageException;
import io.xdag.p2p.message.MessageFactory;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.xerial.snappy.Snappy;

/**
 * Messages &lt;-&gt; frames: compression and splitting of a message into frames of at most
 * {@code netMaxFrameBodySize} bytes, and the reverse.
 *
 * <p>The sender writes all frames of a message one after the other, so at any time at most one message is being
 * put together on the receiving side. Anything else is a protocol violation that closes the connection: a frame
 * of another packet in between, a frame whose header disagrees with the first one, an empty frame of a split
 * packet, more bytes than announced. The receiver therefore buffers at most one packet of at most
 * {@code netMaxPacketSize} bytes per connection. (It used to keep a map of half-received packets that a peer
 * could fill with as many packets as it liked, each pinned until completed.)
 */
@Slf4j
public class XdagMessageHandler extends MessageToMessageCodec<XdagFrame, Message> {

    private final P2pConfig config;
    private final MessageFactory messageFactory = new MessageFactory();
    private final AtomicInteger packetCounter = new AtomicInteger(0);

    /** The packet that is being put together, if any. Only touched by the channel's event loop. */
    private Assembly assembly;

    public XdagMessageHandler(P2pConfig config) {
        this.config = config;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) throws Exception {
        // Send raw body; packetType from message code, size is body length
        byte[] data = msg.getBody();
        if (data == null) {
            data = new byte[0];
        }
        int maxPacket = config.getNetMaxPacketSize();
        if (data.length > maxPacket) {
            throw new MessageException("Packet too large, max = " + maxPacket + ", actual = " + data.length);
        }
        byte[] compressed = data;
        boolean compress = config.isEnableFrameCompression();
        if (compress) {
            compressed = Snappy.compress(data);
            if (compressed.length > maxPacket) {
                throw new MessageException("Packet too large, max = " + maxPacket + ", actual = " + compressed.length);
            }
        }

        byte packetType = msg.getCode().toByte();
        if (log.isDebugEnabled()) {
            log.debug("Encode message: type=0x{}, bodyLen={}, compressedLen={}, to={}",
                    String.format("%02X", packetType), data.length, compressed.length, ctx.channel().remoteAddress());
        }
        int packetId = packetCounter.incrementAndGet();
        int packetSize = compressed.length;

        int limit = config.getNetMaxFrameBodySize();
        if (limit <= 0) {
            throw new MessageException("Invalid frame body size limit: " + limit);
        }

        byte compressType = compress ? XdagFrame.COMPRESS_SNAPPY : XdagFrame.COMPRESS_NONE;
        int total = packetSize == 0 ? 1 : (packetSize - 1) / limit + 1;
        for (int i = 0; i < total; i++) {
            int len = (i < total - 1) ? limit : (packetSize - i * limit);
            byte[] body = new byte[len];
            System.arraycopy(compressed, i * limit, body, 0, len);
            out.add(new XdagFrame(XdagFrame.VERSION, compressType, packetType, packetId, packetSize, len, body));
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, XdagFrame frame, List<Object> out) throws Exception {
        if (frame == null) {
            throw new MessageException("Frame cannot be null");
        }
        if (log.isTraceEnabled()) {
            log.trace("Decode frame: type={}, id={}, bodyLen={}, from {}", frame.getPacketType(), frame.getPacketId(),
                    frame.getBodySize(), ctx.channel().remoteAddress());
        }

        int packetSize = frame.getPacketSize();
        int bodySize = frame.getBodySize();
        if (packetSize < 0 || packetSize > config.getNetMaxPacketSize()) {
            throw new MessageException("Invalid packet size: " + packetSize);
        }
        if (frame.getBody() == null || frame.getBody().length != bodySize || bodySize > packetSize) {
            throw new MessageException("Invalid frame body size: " + bodySize);
        }

        Message decodedMsg;
        if (assembly == null && !frame.isChunked()) {
            decodedMsg = decodePacket(frame.getPacketType(), frame.getCompressType(), frame.getBody());
        } else {
            decodedMsg = onChunk(frame);
        }

        if (decodedMsg != null) {
            out.add(decodedMsg);
        }
    }

    private Message onChunk(XdagFrame frame) throws IOException {
        if (frame.getBodySize() == 0) {
            // a split packet made of empty frames would never complete
            throw new MessageException("Empty frame in a split packet");
        }
        if (assembly == null) {
            assembly = new Assembly(frame);
        } else if (!assembly.matches(frame)) {
            throw new MessageException("Frame of packet " + frame.getPacketId() + " inside packet " + assembly.packetId);
        }
        if (assembly.received + frame.getBodySize() > assembly.data.length) {
            throw new MessageException("Packet " + assembly.packetId + " is longer than announced");
        }
        System.arraycopy(frame.getBody(), 0, assembly.data, assembly.received, frame.getBodySize());
        assembly.received += frame.getBodySize();
        if (assembly.received < assembly.data.length) {
            return null;
        }
        Assembly done = assembly;
        assembly = null;
        return decodePacket(done.packetType, done.compressType, done.data);
    }

    private Message decodePacket(byte packetType, byte compressType, byte[] data) throws IOException {
        switch (compressType) {
            case XdagFrame.COMPRESS_SNAPPY -> {
                // the length is announced in the compressed data itself: check it before allocating
                int length;
                try {
                    length = Snappy.uncompressedLength(data);
                } catch (IOException e) {
                    throw new MessageException("Malformed compressed data", e);
                }
                if (length < 0 || length > config.getNetMaxPacketSize()) {
                    throw new MessageException("Uncompressed data length too big: " + length);
                }
                try {
                    data = Snappy.uncompress(data);
                } catch (IOException e) {
                    throw new MessageException("Malformed compressed data", e);
                }
            }
            case XdagFrame.COMPRESS_NONE -> {
                // no-op
            }
            default -> throw new MessageException("Unsupported compress type: " + compressType);
        }

        // Framework messages (KAD 0x00-0x0F, node protocol 0x10-0x15) are decoded here; everything else belongs
        // to the application and is handed over as it is.
        int codeInt = 0xFF & packetType;
        if (codeInt >= 0x16) {
            return new ApplicationMessage(packetType, data);
        } else {
            return messageFactory.create(packetType, data);
        }
    }

    /** A split packet that is being received. */
    private static final class Assembly {
        final int packetId;
        final byte packetType;
        final byte compressType;
        final byte[] data;
        int received;

        Assembly(XdagFrame first) {
            this.packetId = first.getPacketId();
            this.packetType = first.getPacketType();
            this.compressType = first.getCompressType();
            // bounded by netMaxPacketSize (checked by the caller)
            this.data = new byte[first.getPacketSize()];
        }

        boolean matches(XdagFrame frame) {
            return frame.getPacketId() == packetId
                    && frame.getPacketType() == packetType
                    && frame.getCompressType() == compressType
                    && frame.getPacketSize() == data.length;
        }
    }

    /**
     * Generic message wrapper for application-layer messages (code >= 0x16).
     * These messages bypass MessageFactory and are decoded by application-specific event handlers.
     */
    private static final class ApplicationMessage extends Message {
        private final byte codeValue;

        ApplicationMessage(byte code, byte[] body) {
            super(new ApplicationMessageCode(code), null);
            this.codeValue = code;
            this.body = body;  // Set body directly
        }

        @Override
        public void encode(io.xdag.p2p.utils.SimpleEncoder enc) {
            // Application messages are already encoded - just write body as-is
            if (body != null && body.length > 0) {
                enc.writeBytes(body);
            }
        }

        @Override
        public org.apache.tuweni.bytes.Bytes getSendData() {
            return super.getSendData();
        }

        @Override
        public boolean needToLog() {
            return true;  // Application messages should be logged
        }

        @Override
        public String toString() {
            return String.format("ApplicationMessage{code=0x%02X, bodyLen=%d}",
                    codeValue, body != null ? body.length : 0);
        }
    }

    /**
     * Simple IMessageCode implementation for application-layer messages.
     */
    private static final class ApplicationMessageCode implements io.xdag.p2p.message.IMessageCode {
        private final byte code;

        ApplicationMessageCode(byte code) {
            this.code = code;
        }

        @Override
        public byte toByte() {
            return code;
        }
    }
}


