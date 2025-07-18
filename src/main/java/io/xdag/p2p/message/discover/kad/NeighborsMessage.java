/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.p2p.message.discover.kad;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.kad.table.KademliaOptions;
import io.xdag.p2p.message.discover.MessageType;
import io.xdag.p2p.proto.Discover;
import io.xdag.p2p.proto.Discover.Peer;
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
          Peer peer = getPeerFromNode(neighbour);
          builder.addNeighbours(peer);
        });

    Peer fromPeer= getPeerFromNode(from);

    builder.setFrom(fromPeer);

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
