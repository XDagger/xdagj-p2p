package io.xdag.p2p.message.discover.kad;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.discover.MessageType;
import io.xdag.p2p.proto.Discover;
import io.xdag.p2p.proto.Discover.Endpoint;
import io.xdag.p2p.utils.NetUtils;
import org.apache.tuweni.bytes.Bytes;

public class PongMessage extends KadMessage {

  private final Discover.PongMessage pongMessage;

  public PongMessage(P2pConfig p2pConfig, Bytes data) throws Exception {
    super(p2pConfig, MessageType.KAD_PONG, data);
    this.pongMessage = Discover.PongMessage.parseFrom(data.toArray());
  }

  public PongMessage(P2pConfig p2pConfig, Node from) {
    super(p2pConfig, MessageType.KAD_PONG, null);
    Endpoint toEndpoint = getEndpointFromNode(from);
    this.pongMessage =
        Discover.PongMessage.newBuilder()
            .setFrom(toEndpoint)
            .setEcho(p2pConfig.getNetworkId())
            .setTimestamp(System.currentTimeMillis())
            .build();
    this.data = Bytes.wrap(this.pongMessage.toByteArray());
  }

  public int getNetworkId() {
    return this.pongMessage.getEcho();
  }

  @Override
  public long getTimestamp() {
    return this.pongMessage.getTimestamp();
  }

  @Override
  public Node getFrom() {
    return NetUtils.getNode(p2pConfig, pongMessage.getFrom());
  }

  @Override
  public String toString() {
    return "[pongMessage: " + pongMessage;
  }

  @Override
  public boolean valid() {
    return NetUtils.validNode(getFrom());
  }
}
