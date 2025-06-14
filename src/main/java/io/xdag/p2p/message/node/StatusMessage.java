package io.xdag.p2p.message.node;

import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.proto.Connect;
import io.xdag.p2p.proto.Discover;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.NetUtils;
import org.apache.tuweni.bytes.Bytes;

public class StatusMessage extends Message {
  private final Connect.StatusMessage statusMessage;

  public StatusMessage(P2pConfig p2pConfig, Bytes data) throws Exception {
    super(p2pConfig, MessageType.STATUS, data);
    this.statusMessage = Connect.StatusMessage.parseFrom(data.toArray());
  }

  public StatusMessage(P2pConfig p2pConfig, ChannelManager channelManager) {
    super(p2pConfig, MessageType.STATUS, null);
    Discover.Endpoint endpoint = p2pConfig.getHomeNode();
    this.statusMessage =
        Connect.StatusMessage.newBuilder()
            .setFrom(endpoint)
            .setMaxConnections(p2pConfig.getMaxConnections())
            .setCurrentConnections(channelManager.getChannels().size())
            .setNetworkId(p2pConfig.getNetworkId())
            .setTimestamp(System.currentTimeMillis())
            .build();
    this.data = BytesUtils.wrap(statusMessage.toByteArray());
  }

  public int getNetworkId() {
    return this.statusMessage.getNetworkId();
  }

  public int getVersion() {
    return this.statusMessage.getVersion();
  }

  public int getRemainConnections() {
    return this.statusMessage.getMaxConnections() - this.statusMessage.getCurrentConnections();
  }

  public long getTimestamp() {
    return this.statusMessage.getTimestamp();
  }

  public Node getFrom() {
    return NetUtils.getNode(p2pConfig, statusMessage.getFrom());
  }

  @Override
  public String toString() {
    return "[StatusMessage: " + statusMessage;
  }

  @Override
  public boolean valid() {
    return NetUtils.validNode(getFrom());
  }
}
