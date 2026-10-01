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
package io.xdag.p2p.handler.discover;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.discover.KadPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

/**
 * Datagram -&gt; {@link UdpEvent}. A datagram that is not a well-formed, correctly signed discovery message of
 * this network is dropped without a word at any level above debug: the sender of such a datagram is not
 * somebody to talk to, and an address on a datagram is not proof of who sent it.
 */
@Slf4j(topic = "net")
public class P2pPacketDecoder extends MessageToMessageDecoder<DatagramPacket> {

  public static final int MAXSIZE = KadPacket.MAX_LENGTH;

  private final P2pConfig p2pConfig;
  /**
   * Datagrams accepted per source address, counted before the signature is checked: verifying a signature
   * costs far more than sending a datagram, so a flood must be cut off before that.
   */
  private final Cache<InetAddress, TokenBucket> limits =
      CacheBuilder.newBuilder().maximumSize(20_000).expireAfterAccess(2, TimeUnit.MINUTES).build();

  public P2pPacketDecoder(P2pConfig p2pConfig) {
    this.p2pConfig = p2pConfig;
  }

  private boolean allowed(InetSocketAddress sender) {
    if (sender == null || sender.getAddress() == null) {
      return false;
    }
    try {
      return limits.get(sender.getAddress(), () -> new TokenBucket(p2pConfig.getMaxDiscoveryBurst(),
          p2pConfig.getMaxDiscoveryPacketsPerSecond())).tryAcquire();
    } catch (ExecutionException e) {
      return true;
    }
  }

  @Override
  public void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) {
    ByteBuf buf = packet.content();
    int length = buf.readableBytes();
    if (length <= KadPacket.HEADER_LENGTH || length > MAXSIZE) {
      log.debug("UDP rcv bad packet, from {} length = {}", packet.sender(), length);
      return;
    }
    if (!allowed(packet.sender())) {
      log.trace("UDP rate limit, dropping packet from {}", packet.sender());
      return;
    }

    byte[] encoded = new byte[length];
    buf.readBytes(encoded);

    try {
      KadPacket kadPacket = KadPacket.decode(Bytes.wrap(encoded), p2pConfig.getNetworkId());
      out.add(new UdpEvent(kadPacket.getMessage(), packet.sender(), kadPacket.getNodeId(), kadPacket.getHash()));
    } catch (Exception e) {
      log.debug("Dropping UDP packet from {} (type {}, len {}): {}", packet.sender(), encoded[0], encoded.length,
          e.getMessage());
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    // an error while reading one datagram must not close the discovery socket
    log.debug("UDP decoder exception: {}", String.valueOf(cause));
  }

  /** A token bucket: {@code rate} tokens per second, at most {@code burst} saved up. */
  static final class TokenBucket {
    private final double burst;
    private final double rate;
    private double tokens;
    private long last = System.nanoTime();

    TokenBucket(double burst, double rate) {
      this.burst = Math.max(1, burst);
      this.rate = Math.max(0, rate);
      this.tokens = this.burst;
    }

    synchronized boolean tryAcquire() {
      long now = System.nanoTime();
      tokens = Math.min(burst, tokens + (now - last) / 1e9 * rate);
      last = now;
      if (tokens >= 1) {
        tokens -= 1;
        return true;
      }
      return false;
    }
  }
}
