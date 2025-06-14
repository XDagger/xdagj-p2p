package io.xdag.p2p.message.node;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.proto.Connect;
import io.xdag.p2p.proto.Connect.DisconnectReason;
import io.xdag.p2p.utils.BytesUtils;
import org.apache.tuweni.bytes.Bytes;

public class P2pDisconnectMessage extends Message {

  private final Connect.P2pDisconnectMessage p2pDisconnectMessage;

  public P2pDisconnectMessage(P2pConfig p2pConfig, Bytes data) throws Exception {
    super(p2pConfig, MessageType.DISCONNECT, data);
    this.p2pDisconnectMessage = Connect.P2pDisconnectMessage.parseFrom(data.toArray());
  }

  public P2pDisconnectMessage(P2pConfig p2pConfig, DisconnectReason disconnectReason) {
    super(p2pConfig, MessageType.DISCONNECT, null);
    this.p2pDisconnectMessage =
        Connect.P2pDisconnectMessage.newBuilder().setReason(disconnectReason).build();
    this.data = BytesUtils.wrap(p2pDisconnectMessage.toByteArray());
  }

  private DisconnectReason getReason() {
    return p2pDisconnectMessage.getReason();
  }

  @Override
  public boolean valid() {
    return true;
  }

  @Override
  public String toString() {
    return super.toString() + "reason: " + getReason();
  }
}
