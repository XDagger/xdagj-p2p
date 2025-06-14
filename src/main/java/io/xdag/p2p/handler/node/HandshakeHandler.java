package io.xdag.p2p.handler.node;

import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.node.DisconnectCode;
import io.xdag.p2p.message.node.HelloMessage;
import io.xdag.p2p.message.node.Message;
import io.xdag.p2p.message.node.P2pDisconnectMessage;
import io.xdag.p2p.proto.Connect.DisconnectReason;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "net")
public class HandshakeHandler implements MessageHandler {

  private final P2pConfig p2pConfig;
  private final ChannelManager channelManager;

  private final int networkId;

  public HandshakeHandler(P2pConfig p2pConfig, ChannelManager channelManager) {
    this.p2pConfig = p2pConfig;
    this.channelManager = channelManager;
    this.networkId = this.p2pConfig.getNetworkId();
  }

  public void startHandshake(Channel channel) {
    sendHelloMsg(channel, DisconnectCode.NORMAL, channel.getStartTime());
  }

  @Override
  public void onMessage(Channel channel, Message message) {
    HelloMessage msg = (HelloMessage) message;

    if (channel.isFinishHandshake()) {
      log.warn("Close channel {}, handshake is finished", channel.getInetSocketAddress());
      channel.send(new P2pDisconnectMessage(p2pConfig, DisconnectReason.DUP_HANDSHAKE));
      channel.close();
      return;
    }

    channel.setHandshakeMessage(msg);

    DisconnectCode code = channelManager.processPeer(channel);
    if (code != DisconnectCode.NORMAL) {
      sendHelloMsg(channel, code, msg.getTimestamp());
      channelManager.logDisconnectReason(channel, channelManager.getDisconnectReason(code));
      channel.close();
      return;
    }

    channelManager.updateNodeId(channel, msg.getFrom().getHexId());
    if (channel.isDisconnect()) {
      return;
    }

    if (channel.isActive()) {
      if (msg.getCode() != DisconnectCode.NORMAL.getValue()
          || (msg.getNetworkId() != networkId && msg.getVersion() != networkId)) {
        DisconnectCode disconnectCode = DisconnectCode.forNumber(msg.getCode());
        // v0.1 have version, v0.2 both have version and networkId
        log.info(
            "Handshake failed {}, code: {}, reason: {}, networkId: {}, version: {}",
            channel.getInetSocketAddress(),
            msg.getCode(),
            disconnectCode.name(),
            msg.getNetworkId(),
            msg.getVersion());
        channelManager.logDisconnectReason(
            channel, channelManager.getDisconnectReason(disconnectCode));
        channel.close();
        return;
      }
    } else {

      if (msg.getNetworkId() != networkId) {
        log.info(
            "Peer {} different network id, peer->{}, me->{}",
            channel.getInetSocketAddress(),
            msg.getNetworkId(),
            networkId);
        sendHelloMsg(channel, DisconnectCode.DIFFERENT_VERSION, msg.getTimestamp());
        channelManager.logDisconnectReason(channel, DisconnectReason.DIFFERENT_VERSION);
        channel.close();
        return;
      }
      sendHelloMsg(channel, DisconnectCode.NORMAL, msg.getTimestamp());
    }
    channel.setFinishHandshake(true);
    channel.updateAvgLatency(System.currentTimeMillis() - channel.getStartTime());
    p2pConfig.handlerList.forEach(h -> h.onConnect(channel));
  }

  private void sendHelloMsg(Channel channel, DisconnectCode code, long time) {
    HelloMessage handshakeMessage = new HelloMessage(p2pConfig, code, time);
    channel.send(handshakeMessage);
  }

  @Override
  public void onConnect(Channel channel) {
    // No action needed for handshake handler on connect
  }

  @Override
  public void onDisconnect(Channel channel) {
    // No action needed for handshake handler on disconnect
  }
}
