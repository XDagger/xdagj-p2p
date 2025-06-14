package io.xdag.p2p.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.xdag.p2p.P2pException;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.UpgradeController;
import io.xdag.p2p.message.node.P2pDisconnectMessage;
import io.xdag.p2p.message.node.StatusMessage;
import io.xdag.p2p.proto.Connect.DisconnectReason;
import io.xdag.p2p.utils.BytesUtils;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

@Slf4j(topic = "net")
public class MessageHandler extends ByteToMessageDecoder {

  private final P2pConfig p2pConfig;
  private final ChannelManager channelManager;

  private final Channel channel;

  public MessageHandler(P2pConfig p2pConfig, ChannelManager channelManager, Channel channel) {
    this.p2pConfig = p2pConfig;
    this.channelManager = channelManager;
    this.channel = channel;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {}

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    log.debug("Channel active, {}", ctx.channel().remoteAddress());
    channel.setChannelHandlerContext(ctx);
    if (channel.isActive()) {
      if (channel.isDiscoveryMode()) {
        channel.send(new StatusMessage(p2pConfig, channelManager));
      } else {
        channelManager.getHandshakeHandler().startHandshake(channel);
      }
    }
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) {
    Bytes data = BytesUtils.extractBytesFromByteBuf(buffer);

    try {
      if (channel.isFinishHandshake()) {
        data = UpgradeController.decodeReceiveData(channel.getVersion(), data);
      }
      channelManager.processMessage(channel, data);
    } catch (Exception e) {
      if (e instanceof P2pException pe) {
        DisconnectReason disconnectReason =
            switch (pe.getType()) {
              case EMPTY_MESSAGE -> DisconnectReason.EMPTY_MESSAGE;
              case BAD_PROTOCOL -> DisconnectReason.BAD_PROTOCOL;
              case NO_SUCH_MESSAGE -> DisconnectReason.NO_SUCH_MESSAGE;
              case BAD_MESSAGE,
                  PARSE_MESSAGE_FAILED,
                  MESSAGE_WITH_WRONG_LENGTH,
                  TYPE_ALREADY_REGISTERED ->
                  DisconnectReason.BAD_MESSAGE;
              default -> DisconnectReason.UNKNOWN;
            };
        channel.send(new P2pDisconnectMessage(p2pConfig, disconnectReason));
      }
      channel.processException(e);
    } catch (Throwable t) {
      log.error(
          "Decode message from {} failed, message:{}",
          channel.getInetSocketAddress(),
          data.toHexString());
      throw t;
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    channel.processException(cause);
  }
}
