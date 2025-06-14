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
