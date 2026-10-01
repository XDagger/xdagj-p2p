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
package io.xdag.p2p.message.discover;

import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.utils.SimpleDecoder;
import io.xdag.p2p.utils.SimpleEncoder;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Answer to a ping. It carries the hash of the ping it answers ({@link #getEcho()}): a pong that does not
 * echo a ping we sent to that very address proves nothing about it.
 */
@Getter
public class KadPongMessage extends Message {

  private final byte networkId;
  private final short networkVersion;
  private final long timestamp;
  private final Node from;
  /** Hash of the ping packet this pong answers ({@link KadPacket#getHash()}). */
  private final Bytes32 echo;

  public KadPongMessage(Node from, Bytes32 echo) {
    super(MessageCode.KAD_PONG, null);

    this.timestamp = System.currentTimeMillis();
    this.networkId = from != null ? from.getNetworkId() : (byte) P2pConstant.MAINNET_ID;
    this.networkVersion = from != null ? from.getNetworkVersion() : P2pConstant.MAINNET_VERSION;
    this.from = from;
    this.echo = echo == null ? Bytes32.ZERO : echo;

    SimpleEncoder enc = new SimpleEncoder();
    encode(enc);
    this.body = enc.toBytes();
  }

  /** A pong that answers nothing in particular (kept for callers that only need a well-formed message). */
  public KadPongMessage(Node from) {
    this(from, Bytes32.ZERO);
  }

  public KadPongMessage(byte[] body) {
    super(MessageCode.KAD_PONG, null);

    SimpleDecoder dec = new SimpleDecoder(body);
    this.networkId = dec.readByte();
    this.networkVersion = dec.readShort();
    this.timestamp = dec.readLong();
    byte[] fromBytes = dec.readBytes();
    if (fromBytes == null || fromBytes.length == 0) {
      throw new IllegalArgumentException("Invalid KadPongMessage: 'from' node data is missing");
    }
    this.from = new Node(fromBytes);
    byte[] echoBytes = dec.readBytes();
    if (echoBytes == null || echoBytes.length != Bytes32.SIZE) {
      throw new IllegalArgumentException("Invalid KadPongMessage: echo must be 32 bytes");
    }
    this.echo = Bytes32.wrap(echoBytes);

    this.body = body;
  }

  @Override
  public void encode(SimpleEncoder enc) {
    enc.writeByte(networkId);
    enc.writeShort(networkVersion);
    enc.writeLong(timestamp);
    enc.writeBytes(from != null ? from.toBytes() : new byte[0]);
    enc.writeBytes(echo.toArray());
  }

  @Override
  public String toString() {
    return "KadPongMessage ["
        + "networkId=" + networkId +
        ", networkVersion=" + networkVersion +
        ", timestamp=" + timestamp +
        ", from=" + (from != null ? from.getPreferInetSocketAddress() : "null") +
        "]";
  }

}
