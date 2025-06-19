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
package io.xdag.p2p.discover.kad.table;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.utils.NetUtils;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

public class TimeComparatorTest {

  private final P2pConfig p2pConfig = new P2pConfig();

  @Test
  public void test() throws InterruptedException {

    Node node1 = new Node(p2pConfig, new InetSocketAddress("127.0.0.1", 10001));
    NodeEntry ne1 = new NodeEntry(NetUtils.getNodeId(), node1);
    Thread.sleep(1);
    Node node2 = new Node(p2pConfig, new InetSocketAddress("127.0.0.1", 10002));
    NodeEntry ne2 = new NodeEntry(NetUtils.getNodeId(), node2);
    TimeComparator tc = new TimeComparator();
    int result = tc.compare(ne1, ne2);
    assertEquals(1, result);
  }
}
