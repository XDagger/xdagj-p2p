package io.xdag.p2p.message.discover.kad;

import com.google.protobuf.ByteString;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.discover.MessageType;
import io.xdag.p2p.proto.Discover;
import io.xdag.p2p.proto.Discover.Endpoint;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.NetUtils;
import org.apache.tuweni.bytes.Bytes;

public class FindNodeMessage extends KadMessage {

  private final Discover.FindNeighbours findNeighbours;

  public FindNodeMessage(P2pConfig p2pConfig, Bytes data) throws Exception {
    super(p2pConfig, MessageType.KAD_FIND_NODE, data);
    this.findNeighbours = Discover.FindNeighbours.parseFrom(data.toArray());
  }

  public FindNodeMessage(P2pConfig p2pConfig, Node from, Bytes targetId) {
    super(p2pConfig, MessageType.KAD_FIND_NODE, null);
    Endpoint fromEndpoint = getEndpointFromNode(from);
    this.findNeighbours =
        Discover.FindNeighbours.newBuilder()
            .setFrom(fromEndpoint)
            .setTargetId(ByteString.copyFrom(targetId.toArray()))
            .setTimestamp(System.currentTimeMillis())
            .build();
    this.data = BytesUtils.wrap(this.findNeighbours.toByteArray());
  }

  /** Get target ID as Tuweni Bytes */
  public Bytes getTargetId() {
    return BytesUtils.wrap(this.findNeighbours.getTargetId().toByteArray());
  }

  @Override
  public long getTimestamp() {
    return this.findNeighbours.getTimestamp();
  }

  @Override
  public Node getFrom() {
    return NetUtils.getNode(p2pConfig, findNeighbours.getFrom());
  }

  @Override
  public String toString() {
    return "[findNeighbours: " + findNeighbours;
  }

  @Override
  public boolean valid() {
    return NetUtils.validNode(getFrom()) && getTargetId().size() == P2pConstant.NODE_ID_LEN;
  }
}
