package io.xdag.p2p.stats;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;

public class TrafficStats {
  @Getter private static final TrafficStatHandler tcp = new TrafficStatHandler();

  @Getter private static final TrafficStatHandler udp = new TrafficStatHandler();

  @Getter
  @ChannelHandler.Sharable
  static class TrafficStatHandler extends ChannelDuplexHandler {

    private final AtomicLong outSize = new AtomicLong();
    private final AtomicLong inSize = new AtomicLong();
    private final AtomicLong outPackets = new AtomicLong();
    private final AtomicLong inPackets = new AtomicLong();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      inPackets.incrementAndGet();
      if (msg instanceof ByteBuf) {
        inSize.addAndGet(((ByteBuf) msg).readableBytes());
      } else if (msg instanceof DatagramPacket) {
        inSize.addAndGet(((DatagramPacket) msg).content().readableBytes());
      }
      super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
        throws Exception {
      outPackets.incrementAndGet();
      if (msg instanceof ByteBuf) {
        outSize.addAndGet(((ByteBuf) msg).readableBytes());
      } else if (msg instanceof DatagramPacket) {
        outSize.addAndGet(((DatagramPacket) msg).content().readableBytes());
      }
      super.write(ctx, msg, promise);
    }
  }
}
