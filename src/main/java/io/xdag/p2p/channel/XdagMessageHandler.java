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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.xerial.snappy.Snappy;

@Slf4j
public class XdagMessageHandler extends MessageToMessageCodec<XdagFrame, Message> {

    private static final int MAX_INFLIGHT_PACKETS = 64;
    private final Map<Integer, PacketAggregate> inFlight = new ConcurrentHashMap<>();

    private final P2pConfig config;
    private final MessageFactory messageFactory = new MessageFactory();
    private final AtomicInteger packetCounter = new AtomicInteger(0);

    public XdagMessageHandler(P2pConfig config) {
        this.config = config;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) throws Exception {
        // Send raw body; packetType from message code, size is body length
        byte[] data = msg.getBody();
        byte[] compressed = data;
        log.debug("Encode {}: bodyLen={} to {}", msg.getCode(), data != null ? data.length : 0, ctx.channel().remoteAddress());

        if (config.isEnableFrameCompression()) {
            try {
                compressed = Snappy.compress(data);
            } catch (IOException e) {
                log.error("Failed to compress data", e);
                return;
            }
        }

        byte packetType = msg.getCode().toByte();
        if (msg.getCode() == io.xdag.p2p.message.MessageCode.APP_TEST) {
            log.debug("Encode APP_TEST: bodyLen={} remote={}", data != null ? data.length : 0, ctx.channel().remoteAddress());
        }
        int packetId = packetCounter.incrementAndGet();
        int packetSize = compressed.length;

        int maxPacket = config.getNetMaxPacketSize();
        if (data.length > maxPacket || compressed.length > maxPacket) {
            log.error("Invalid packet size, max = {}, actual = {}", maxPacket, packetSize);
            return;
        }

        int limit = config.getNetMaxFrameBodySize();
        if (limit <= 0) {
            log.error("Invalid frame body size limit: {}", limit);
            return;
        }

        int total = (compressed.length - 1) / limit + 1;
        for (int i = 0; i < total; i++) {
            int len = (i < total - 1) ? limit : (compressed.length - i * limit);
            byte[] body = new byte[len];
            System.arraycopy(compressed, i * limit, body, 0, len);
            out.add(new XdagFrame(
                    XdagFrame.VERSION,
                    config.isEnableFrameCompression() ? XdagFrame.COMPRESS_SNAPPY : XdagFrame.COMPRESS_NONE,
                    packetType,
                    packetId,
                    packetSize,
                    body.length,
                    body));
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, XdagFrame frame, List<Object> out) throws Exception {
        if (frame == null) {
            throw new MessageException("Frame cannot be null");
        }
        log.debug("Decode frame: type={}, id={}, bodyLen={}, from {}", frame.getPacketType(), frame.getPacketId(), frame.getBodySize(), ctx.channel().remoteAddress());

        Message decodedMsg;
        if (frame.isChunked()) {
            decodedMsg = onChunked(frame);
        } else {
            decodedMsg = decodeFrames(Collections.singletonList(frame));
        }

        if (decodedMsg != null) {
            log.debug("Decoded Message: {} bodyLen={} from {}", decodedMsg.getCode(), decodedMsg.getBody() != null ? decodedMsg.getBody().length : 0, ctx.channel().remoteAddress());
            out.add(decodedMsg);
        }
    }

    private Message onChunked(XdagFrame frame) throws IOException, MessageException {
        int packetId = frame.getPacketId();
        PacketAggregate agg = inFlight.computeIfAbsent(packetId, k -> new PacketAggregate(frame.getPacketSize()));
        if (agg.expectedSize < 0 || agg.expectedSize > config.getNetMaxPacketSize()) {
            throw new IOException("Invalid packet size: " + agg.expectedSize);
        }
        agg.frames.add(frame);
        int remaining = agg.remaining.addAndGet(-frame.getBodySize());
        if (remaining == 0) {
            try {
                return decodeFrames(agg.frames);
            } finally {
                inFlight.remove(packetId);
                if (inFlight.size() > MAX_INFLIGHT_PACKETS) {
                    // simple back-pressure: clear all to avoid leak in extreme cases
                    inFlight.clear();
                }
            }
        } else if (remaining < 0) {
            throw new IOException("Packet remaining size went negative");
        }
        return null;
    }

    private Message decodeFrames(List<XdagFrame> frames) throws MessageException, IOException {
        if (frames == null || frames.isEmpty()) {
            throw new MessageException("Frames can't be null or empty");
        }
        XdagFrame head = frames.getFirst();
        byte packetType = head.getPacketType();
        int packetSize = head.getPacketSize();
        if (packetSize < 0 || packetSize > config.getNetMaxPacketSize()) {
            throw new MessageException("Invalid packet size: " + packetSize);
        }
        byte[] data = new byte[packetSize];
        int pos = 0;
        for (XdagFrame frame : frames) {
            System.arraycopy(frame.getBody(), 0, data, pos, frame.getBodySize());
            pos += frame.getBodySize();
        }

        switch (head.getCompressType()) {
            case XdagFrame.COMPRESS_SNAPPY -> {
                // validate uncompressed length
                int length = Snappy.uncompressedLength(data);
                if (length > config.getNetMaxPacketSize()) {
                    throw new MessageException("Uncompressed data length too big: " + length);
                }
                data = Snappy.uncompress(data);
            }
            case XdagFrame.COMPRESS_NONE -> {
                // no-op
            }
            default -> throw new MessageException("Unsupported compress type: " + head.getCompressType());
        }

        return messageFactory.create(packetType, data);
    }

    private static final class PacketAggregate {
        final List<XdagFrame> frames = new ArrayList<>();
        final int expectedSize;
        final AtomicInteger remaining;

        PacketAggregate(int size) {
            this.expectedSize = size;
            this.remaining = new AtomicInteger(size);
        }
    }
}


