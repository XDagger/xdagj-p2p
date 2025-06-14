package io.xdag.p2p.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.message.node.P2pDisconnectMessage;
import io.xdag.p2p.proto.Connect.DisconnectReason;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "net")
public class P2pProtobufVarint32FrameDecoder extends ByteToMessageDecoder {

  private final P2pConfig p2pConfig;
  private final Channel channel;

  public P2pProtobufVarint32FrameDecoder(P2pConfig p2pConfig, Channel channel) {
    this.p2pConfig = p2pConfig;
    this.channel = channel;
  }

  private static int readRawVarint32(ByteBuf buffer) {
    if (!buffer.isReadable()) {
      return 0;
    }
    buffer.markReaderIndex();
    byte tmp = buffer.readByte();
    if (tmp >= 0) {
      return tmp;
    } else {
      int result = tmp & 127;
      if (!buffer.isReadable()) {
        buffer.resetReaderIndex();
        return 0;
      }
      if ((tmp = buffer.readByte()) >= 0) {
        result |= tmp << 7;
      } else {
        result |= (tmp & 127) << 7;
        if (!buffer.isReadable()) {
          buffer.resetReaderIndex();
          return 0;
        }
        if ((tmp = buffer.readByte()) >= 0) {
          result |= tmp << 14;
        } else {
          result |= (tmp & 127) << 14;
          if (!buffer.isReadable()) {
            buffer.resetReaderIndex();
            return 0;
          }
          if ((tmp = buffer.readByte()) >= 0) {
            result |= tmp << 21;
          } else {
            result |= (tmp & 127) << 21;
            if (!buffer.isReadable()) {
              buffer.resetReaderIndex();
              return 0;
            }
            result |= (tmp = buffer.readByte()) << 28;
            if (tmp < 0) {
              throw new CorruptedFrameException("malformed varint.");
            }
          }
        }
      }
      return result;
    }
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    in.markReaderIndex();
    int preIndex = in.readerIndex();
    int length = readRawVarint32(in);
    if (length >= P2pConstant.MAX_MESSAGE_LENGTH) {
      log.warn(
          "Receive a big msg or not encoded msg, host : {}, msg length is : {}",
          ctx.channel().remoteAddress(),
          length);
      in.clear();
      channel.send(new P2pDisconnectMessage(p2pConfig, DisconnectReason.BAD_MESSAGE));
      channel.close();
      return;
    }
    if (preIndex == in.readerIndex()) {
      return;
    }
    if (length < 0) {
      throw new CorruptedFrameException("negative length: " + length);
    }

    if (in.readableBytes() < length) {
      in.resetReaderIndex();
    } else {
      out.add(in.readRetainedSlice(length));
    }
  }
}
