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

import com.google.protobuf.ByteString;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.discover.MessageType;
import io.xdag.p2p.proto.Discover;
import io.xdag.p2p.proto.Discover.Peer;
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
    Peer fromPeer = getPeerFromNode(from);
    this.findNeighbours =
        Discover.FindNeighbours.newBuilder()
            .setFrom(fromPeer)
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
