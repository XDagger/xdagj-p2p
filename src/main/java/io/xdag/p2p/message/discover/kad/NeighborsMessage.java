package io.xdag.p2p.message.discover.kad;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.kad.table.KademliaOptions;
import io.xdag.p2p.message.discover.MessageType;
import io.xdag.p2p.proto.Discover;
import io.xdag.p2p.proto.Discover.Endpoint;
import io.xdag.p2p.proto.Discover.Neighbours;
import io.xdag.p2p.proto.Discover.Neighbours.Builder;
import io.xdag.p2p.utils.NetUtils;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;

public class NeighborsMessage extends KadMessage {

  private final Discover.Neighbours neighbours;

  public NeighborsMessage(P2pConfig p2pConfig, Bytes data) throws Exception {
    super(p2pConfig, MessageType.KAD_NEIGHBORS, data);
    this.neighbours = Discover.Neighbours.parseFrom(data.toArray());
  }

  public NeighborsMessage(P2pConfig p2pConfig, Node from, List<Node> neighbours, long sequence) {
    super(p2pConfig, MessageType.KAD_NEIGHBORS, null);
    Builder builder = Neighbours.newBuilder().setTimestamp(sequence);

    neighbours.forEach(
        neighbour -> {
          Endpoint endpoint = getEndpointFromNode(neighbour);
          builder.addNeighbours(endpoint);
        });

    Endpoint fromEndpoint = getEndpointFromNode(from);

    builder.setFrom(fromEndpoint);

    this.neighbours = builder.build();

    this.data = Bytes.wrap(this.neighbours.toByteArray());
  }

  public List<Node> getNodes() {
    List<Node> nodes = new ArrayList<>();
    neighbours.getNeighboursList().forEach(n -> nodes.add(NetUtils.getNode(p2pConfig, n)));
    return nodes;
  }

  @Override
  public long getTimestamp() {
    return this.neighbours.getTimestamp();
  }

  @Override
  public Node getFrom() {
    return NetUtils.getNode(p2pConfig, neighbours.getFrom());
  }

  @Override
  public String toString() {
    return "[neighbours: " + neighbours;
  }

  @Override
  public boolean valid() {
    if (!NetUtils.validNode(getFrom())) {
      return false;
    }
    if (!getNodes().isEmpty()) {
      if (getNodes().size() > KademliaOptions.BUCKET_SIZE) {
        return false;
      }
      for (Node node : getNodes()) {
        if (!NetUtils.validNode(node)) {
          return false;
        }
      }
    }
    return true;
  }
}
