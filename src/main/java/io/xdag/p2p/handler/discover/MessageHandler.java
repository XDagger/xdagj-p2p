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

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.discover.KadPacket;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

@Slf4j(topic = "net")
public class MessageHandler extends SimpleChannelInboundHandler<UdpEvent>
    implements Consumer<UdpEvent> {

  private final Channel channel;

  private final EventHandler eventHandler;

  private final P2pConfig config;

  public MessageHandler(NioDatagramChannel channel, EventHandler eventHandler, P2pConfig config) {
    this.channel = channel;
    this.eventHandler = eventHandler;
    this.config = config;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    log.debug("MessageHandler channelActive called, calling eventHandler.channelActivated()");
    eventHandler.channelActivated();
  }

  @Override
  public void channelRead0(ChannelHandlerContext ctx, UdpEvent udpEvent) {
    log.trace("Rcv udp msg type {} from {} ", udpEvent.getMessage().getCode(), udpEvent.getAddress());
    try {
      eventHandler.handleEvent(udpEvent);
    } catch (RuntimeException e) {
      // one bad packet must not take the discovery socket down with it
      log.debug("Handling UDP message from {} failed: {}", udpEvent.getAddress(), e.toString());
    }
  }

  @Override
  public void accept(UdpEvent udpEvent) {
    InetSocketAddress address = udpEvent.getAddress();
    if (address == null || address.isUnresolved() || config == null || config.getNodeKey() == null) {
      return;
    }
    log.trace("Send udp msg type {} to {} ", udpEvent.getMessage().getCode(), address);
    try {
      Bytes wire = KadPacket.encode(udpEvent.getMessage(), config.getNetworkId(), config.getNodeKey());
      sendPacketFromBytes(wire, address);
    } catch (RuntimeException e) {
      log.debug("Not sending UDP message to {}: {}", address, e.getMessage());
    }
  }

  /** Sends bytes as one datagram. */
  void sendPacketFromBytes(Bytes wireBytes, InetSocketAddress address) {
    DatagramPacket packet =
        new DatagramPacket(Unpooled.wrappedBuffer(wireBytes.toArray()), address);
    channel.write(packet);
    channel.flush();
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) {
    ctx.flush();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.debug("UDP message handler exception: {}", String.valueOf(cause));
  }
}
