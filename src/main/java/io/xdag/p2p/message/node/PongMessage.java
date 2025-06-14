package io.xdag.p2p.message.node;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.proto.Connect;
import io.xdag.p2p.utils.BytesUtils;
import org.apache.tuweni.bytes.Bytes;

public class PongMessage extends Message {

  private final Connect.KeepAliveMessage keepAliveMessage;

  public PongMessage(P2pConfig p2pConfig, Bytes data) throws Exception {
    super(p2pConfig, MessageType.PONG, data);
    this.keepAliveMessage = Connect.KeepAliveMessage.parseFrom(data.toArray());
  }

  public PongMessage(P2pConfig p2pConfig) {
    super(p2pConfig, MessageType.PONG, null);
    this.keepAliveMessage =
        Connect.KeepAliveMessage.newBuilder().setTimestamp(System.currentTimeMillis()).build();
    this.data = BytesUtils.wrap(this.keepAliveMessage.toByteArray());
  }

  public long getTimeStamp() {
    return this.keepAliveMessage.getTimestamp();
  }

  @Override
  public boolean valid() {
    return getTimeStamp() > 0
        && getTimeStamp() <= System.currentTimeMillis() + P2pConstant.NETWORK_TIME_DIFF;
  }
}
