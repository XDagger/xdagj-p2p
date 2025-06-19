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
