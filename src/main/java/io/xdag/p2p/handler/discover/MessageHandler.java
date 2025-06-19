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
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

@Slf4j(topic = "net")
public class MessageHandler extends SimpleChannelInboundHandler<UdpEvent>
    implements Consumer<UdpEvent> {

  private final Channel channel;

  private final EventHandler eventHandler;

  public MessageHandler(NioDatagramChannel channel, EventHandler eventHandler) {
    this.channel = channel;
    this.eventHandler = eventHandler;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    log.debug("MessageHandler channelActive called, calling eventHandler.channelActivated()");
    eventHandler.channelActivated();
  }

  @Override
  public void channelRead0(ChannelHandlerContext ctx, UdpEvent udpEvent) {
    log.debug(
        "Rcv udp msg type {}, len {} from {} ",
        udpEvent.getMessage().getType(),
        udpEvent.getMessage().getSendData().size(),
        udpEvent.getAddress());
    eventHandler.handleEvent(udpEvent);
  }

  @Override
  public void accept(UdpEvent udpEvent) {
    log.debug(
        "Send udp msg type {}, len {} to {} ",
        udpEvent.getMessage().getType(),
        udpEvent.getMessage().getSendData().size(),
        udpEvent.getAddress());
    InetSocketAddress address = udpEvent.getAddress();
    sendPacketFromBytes(udpEvent.getMessage().getSendData(), address);
  }

  /** Alternative method for sending with Tuweni Bytes input */
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
    log.warn("UDP message handler exception", cause);
  }
}
