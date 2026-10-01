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
package io.xdag.p2p;

import io.xdag.p2p.channel.Channel;
import java.util.Set;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;

public abstract class P2pEventHandler {

  @Getter protected Set<Byte> messageTypes;

  public void onConnect(Channel channel) {}

  public void onDisconnect(Channel channel) {}

  /** A message of the application: {@code [code | body]}. */
  public void onMessage(Channel channel, Bytes data) {}

  /**
   * The peer's connection became writable again, or stopped being writable because the peer reads more slowly
   * than we send. A sender of bulk data should pause while {@code writable} is false (see
   * {@link Channel#isWritable()}).
   */
  public void onWritabilityChanged(Channel channel, boolean writable) {}
}
