package io.xdag.p2p.message.discover.kad;

import com.google.protobuf.ByteString;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.discover.Message;
import io.xdag.p2p.message.discover.MessageType;
import io.xdag.p2p.proto.Discover.Endpoint;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

public abstract class KadMessage extends Message {

  protected KadMessage(P2pConfig p2pConfig, MessageType type, Bytes data) {
    super(p2pConfig, type, data);
  }

  public abstract Node getFrom();

  public abstract long getTimestamp();

  public static Endpoint getEndpointFromNode(Node node) {
    Endpoint.Builder builder = Endpoint.newBuilder().setPort(node.getPort());
    if (node.getId() != null) {
      builder.setNodeId(ByteString.copyFrom(node.getId().toArray()));
    }
    if (StringUtils.isNotEmpty(node.getHostV4())) {
      builder.setAddress(ByteString.copyFrom(Objects.requireNonNull(fromString(node.getHostV4()))));
    }
    if (StringUtils.isNotEmpty(node.getHostV6())) {
      builder.setAddressIpv6(
          ByteString.copyFrom(Objects.requireNonNull(fromString(node.getHostV6()))));
    }
    return builder.build();
  }

  public static byte[] fromString(String s) {
    return StringUtils.isBlank(s) ? null : s.getBytes(StandardCharsets.UTF_8);
  }
}
