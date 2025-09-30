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
/*
 * The MIT License (MIT)
 */
package io.xdag.p2p.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.xdag.p2p.config.P2pConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.xerial.snappy.Snappy;

/**
 * Codec that converts raw application payload (Bytes, first byte is app type) <-> XdagFrame.
 * Handles optional Snappy compression and chunk aggregation.
 */
@Slf4j
public class XdagPayloadCodec extends MessageToMessageCodec<XdagFrame, Bytes> {

  private static final int MAX_INFLIGHT_PACKETS = 64;
  private final Map<Integer, PacketAggregate> inFlight = new ConcurrentHashMap<>();

  private final P2pConfig config;
  private final AtomicInteger packetCounter = new AtomicInteger(0);

  public XdagPayloadCodec(P2pConfig config) {
    this.config = config;
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, Bytes payload, List<Object> out) throws Exception {
    if (payload == null || payload.size() < 1) {
      return;
    }
    byte packetType = payload.get(0);
    byte[] body = payload.size() > 1 ? payload.slice(1).toArray() : new byte[0];

    byte[] compressed = body;
    if (config.isEnableFrameCompression()) {
      try {
        compressed = Snappy.compress(body);
      } catch (IOException e) {
        log.error("Failed to compress payload", e);
        return;
      }
    }

    int packetId = packetCounter.incrementAndGet();
    int packetSize = compressed.length;
    if (packetSize > config.getNetMaxPacketSize()) {
      log.error("Invalid packet size, max = {}, actual = {}", config.getNetMaxPacketSize(), packetSize);
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
      byte[] chunk = new byte[len];
      System.arraycopy(compressed, i * limit, chunk, 0, len);
      out.add(new XdagFrame(
          XdagFrame.VERSION,
          config.isEnableFrameCompression() ? XdagFrame.COMPRESS_SNAPPY : XdagFrame.COMPRESS_NONE,
          packetType,
          packetId,
          packetSize,
          chunk.length,
          chunk));
    }
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, XdagFrame frame, List<Object> out) throws Exception {
    if (frame == null) {
      return;
    }

    Bytes decoded;
    if (frame.isChunked()) {
      decoded = onChunked(frame);
    } else {
      decoded = decodeFrames(Collections.singletonList(frame));
    }

    if (decoded != null) {
      out.add(decoded);
    }
  }

  private Bytes onChunked(XdagFrame frame) throws IOException {
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
          inFlight.clear();
        }
      }
    } else if (remaining < 0) {
      throw new IOException("Packet remaining size went negative");
    }
    return null;
  }

  private Bytes decodeFrames(List<XdagFrame> frames) throws IOException {
    if (frames == null || frames.isEmpty()) {
      return null;
    }
    XdagFrame head = frames.getFirst();
    byte packetType = head.getPacketType();
    int packetSize = head.getPacketSize();
    if (packetSize < 0 || packetSize > config.getNetMaxPacketSize()) {
      throw new IOException("Invalid packet size: " + packetSize);
    }
    byte[] data = new byte[packetSize];
    int pos = 0;
    for (XdagFrame frame : frames) {
      System.arraycopy(frame.getBody(), 0, data, pos, frame.getBodySize());
      pos += frame.getBodySize();
    }

    switch (head.getCompressType()) {
      case XdagFrame.COMPRESS_SNAPPY -> data = Snappy.uncompress(data);
      case XdagFrame.COMPRESS_NONE -> {
      }
      default -> throw new IOException("Unsupported compress type: " + head.getCompressType());
    }

    return Bytes.concatenate(Bytes.of(packetType), Bytes.wrap(data));
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


