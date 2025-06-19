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
package io.xdag.p2p.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.node.PingMessage;
import io.xdag.p2p.proto.Connect;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class ProtoUtilsTest {
  private final P2pConfig p2pConfig = new P2pConfig();

  @Test
  public void testCompressMessage() throws Exception {
    PingMessage p1 = new PingMessage(p2pConfig);

    Connect.CompressMessage message = ProtoUtils.compressMessageBytes(p1.getData());

    Bytes d1 = ProtoUtils.uncompressMessageBytes(message);

    PingMessage p2 = new PingMessage(p2pConfig, d1);

    assertEquals(p1.getTimeStamp(), p2.getTimeStamp());

    Connect.CompressMessage m2 = ProtoUtils.compressMessageBytes(Bytes.wrap(new byte[1000]));

    Bytes d2 = ProtoUtils.uncompressMessageBytes(m2);

    assertEquals(1000, d2.size());
    assertEquals(0, d2.get(0));
  }
}
