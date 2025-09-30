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

import io.xdag.p2p.Peer;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.utils.NetUtils;
import io.xdag.p2p.utils.SimpleDecoder;
import io.xdag.p2p.utils.SimpleEncoder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KadPongMessage extends Message {

  private final byte networkId;
  private final short networkVersion;
  private final long timestamp;

  public KadPongMessage() {
    super(MessageCode.KAD_PONG, null);

    this.timestamp = System.currentTimeMillis();
    this.networkId = P2pConstant.MAINNET_ID;
    this.networkVersion = P2pConstant.MAINNET_VERSION;

    SimpleEncoder enc = new SimpleEncoder();
    enc.writeByte(networkId);
    enc.writeShort(networkVersion);
    enc.writeLong(timestamp);

    this.body = enc.toBytes();
  }

  public KadPongMessage(byte[] body) {
    super(MessageCode.KAD_PONG, null);

    SimpleDecoder dec = new SimpleDecoder(body);
    this.networkId = dec.readByte();
    this.networkVersion = dec.readShort();
    this.timestamp = dec.readLong();

    this.body = body;
  }

  @Override
  public void encode(SimpleEncoder enc) {
    enc.writeByte(networkId);
    enc.writeShort(networkVersion);
    enc.writeLong(timestamp);
  }

  @Override
  public String toString() {
    return "KadPongMessage ["
        + "networkId=" + networkId +
        ", networkVersion=" + networkVersion +
        ", timestamp=" + timestamp +
        "]";
  }

}
