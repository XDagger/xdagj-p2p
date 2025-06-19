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
import io.xdag.p2p.message.discover.Message;
import io.xdag.p2p.message.discover.MessageType;
import io.xdag.p2p.proto.Discover;
import io.xdag.p2p.proto.Discover.Endpoint;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.NetUtils;
import java.net.InetSocketAddress;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes;

public class PingMessage extends Message {

  private final Discover.PingMessage pingMessage;

  @Setter private InetSocketAddress sourceAddress; // UDP source address for fallback

  public PingMessage(P2pConfig p2pConfig, Bytes data) throws Exception {
    super(p2pConfig, MessageType.KAD_PING, data);
    this.pingMessage = Discover.PingMessage.parseFrom(data.toArray());
  }

  public PingMessage(P2pConfig p2pConfig, Node from, Node to) {
    super(p2pConfig, MessageType.KAD_PING, null);
    Endpoint fromEndpoint = getEndpointFromNode(from);
    Endpoint toEndpoint = getEndpointFromNode(to);
    this.pingMessage =
        Discover.PingMessage.newBuilder()
            .setVersion(p2pConfig.getNetworkId())
            .setFrom(fromEndpoint)
            .setTo(toEndpoint)
            .setTimestamp(System.currentTimeMillis())
            .build();
    this.data = BytesUtils.wrap(this.pingMessage.toByteArray());
  }

  public int getNetworkId() {
    return this.pingMessage.getVersion();
  }

  public Node getTo() {
    return NetUtils.getNode(p2pConfig, this.pingMessage.getTo());
  }

  public long getTimestamp() {
    return this.pingMessage.getTimestamp();
  }

  public Node getFrom() {
    if (sourceAddress != null) {
      return NetUtils.getNodeWithFallback(p2pConfig, pingMessage.getFrom(), sourceAddress);
    } else {
      return NetUtils.getNode(p2pConfig, pingMessage.getFrom());
    }
  }

  private Endpoint getEndpointFromNode(Node node) {
    String address = node.getHostV4() != null ? node.getHostV4() : node.getHostV6();
    return Endpoint.newBuilder()
        .setAddress(com.google.protobuf.ByteString.copyFromUtf8(address != null ? address : ""))
        .setPort(node.getPort())
        .setNodeId(com.google.protobuf.ByteString.copyFrom(node.getId().toArray()))
        .build();
  }

  @Override
  public String toString() {
    return "[pingMessage: " + pingMessage;
  }

  @Override
  public boolean valid() {
    Node from = getFrom();
    return NetUtils.validNode(from);
  }
}
