package io.xdag.p2p.message.node;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.proto.Connect;
import io.xdag.p2p.proto.Discover;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.NetUtils;
import org.apache.tuweni.bytes.Bytes;

public class HelloMessage extends Message {

  private final Connect.HelloMessage helloMessage;

  public HelloMessage(P2pConfig p2pConfig, Bytes data) throws Exception {
    super(p2pConfig, MessageType.HANDSHAKE_HELLO, data);
    this.helloMessage = Connect.HelloMessage.parseFrom(data.toArray());
  }

  public HelloMessage(P2pConfig p2pConfig, DisconnectCode code, long time) {
    super(p2pConfig, MessageType.HANDSHAKE_HELLO, null);
    Discover.Peer peer = p2pConfig.getHomePeer();
    this.helloMessage =
        Connect.HelloMessage.newBuilder()
            .setFrom(peer)
            .setNetworkId(p2pConfig.getNetworkId())
            .setCode(code.getValue())
            .setVersion(P2pConstant.version)
            .setTimestamp(time)
            .build();
    this.data = BytesUtils.wrap(helloMessage.toByteArray());
  }

  public int getNetworkId() {
    return this.helloMessage.getNetworkId();
  }

  public int getVersion() {
    return this.helloMessage.getVersion();
  }

  public int getCode() {
    return this.helloMessage.getCode();
  }

  public long getTimestamp() {
    return this.helloMessage.getTimestamp();
  }

  public Node getFrom() {
    return NetUtils.getNode(p2pConfig, helloMessage.getFrom());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[HelloMessage: {\n");
    sb.append("  from: {\n");

    // Format address
    if (!helloMessage.getFrom().getAddress().isEmpty()) {
      sb.append("    address: \"")
          .append(new String(helloMessage.getFrom().getAddress().toByteArray()))
          .append("\"\n");
    }

    // Format port
    sb.append("    port: ").append(helloMessage.getFrom().getPort()).append("\n");

    // Format nodeId as hex
    if (!helloMessage.getFrom().getNodeId().isEmpty()) {
      sb.append("    nodeId: \"")
          .append(
              BytesUtils.toHexString(Bytes.wrap(helloMessage.getFrom().getNodeId().toByteArray())))
          .append("\"\n");
    }

    sb.append("  }\n");
    sb.append("  network_id: ").append(helloMessage.getNetworkId()).append("\n");
    sb.append("  code: ").append(helloMessage.getCode()).append("\n");
    sb.append("  timestamp: ").append(helloMessage.getTimestamp()).append("\n");
    sb.append("  version: ").append(helloMessage.getVersion()).append("\n");
    sb.append("}]");

    return sb.toString();
  }

  @Override
  public boolean valid() {
    return NetUtils.validNode(getFrom());
  }
}
