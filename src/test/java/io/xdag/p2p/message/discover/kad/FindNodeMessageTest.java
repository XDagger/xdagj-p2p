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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.discover.MessageType;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FindNodeMessageTest {

  private P2pConfig p2pConfig;
  private Node from;
  private Bytes targetId;

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
    p2pConfig.setNetworkId(1);
    from = new Node(p2pConfig, Bytes.random(64), "127.0.0.1", null, 30303, 30303);
    targetId = Bytes.random(64);
  }

  @Test
  void testCreateFindNodeMessage() {
    FindNodeMessage findNodeMessage = new FindNodeMessage(p2pConfig, from, targetId);
    assertEquals(MessageType.KAD_FIND_NODE, findNodeMessage.getType());
    assertEquals(from, findNodeMessage.getFrom());
    assertEquals(targetId, findNodeMessage.getTargetId());
    assertTrue(findNodeMessage.getTimestamp() > 0);
    assertNotNull(findNodeMessage.getData());
    assertTrue(findNodeMessage.valid());
  }

  @Test
  void testParseFindNodeMessage() throws Exception {
    FindNodeMessage originalFindNodeMessage = new FindNodeMessage(p2pConfig, from, targetId);
    Bytes encoded = originalFindNodeMessage.getData();

    FindNodeMessage parsedFindNodeMessage = new FindNodeMessage(p2pConfig, encoded);
    assertEquals(originalFindNodeMessage.getType(), parsedFindNodeMessage.getType());
    assertEquals(originalFindNodeMessage.getFrom(), parsedFindNodeMessage.getFrom());
    assertEquals(originalFindNodeMessage.getTargetId(), parsedFindNodeMessage.getTargetId());
    assertEquals(originalFindNodeMessage.getTimestamp(), parsedFindNodeMessage.getTimestamp());
    assertTrue(parsedFindNodeMessage.valid());
  }

  @Test
  void testToString() {
    FindNodeMessage findNodeMessage = new FindNodeMessage(p2pConfig, from, targetId);
    String str = findNodeMessage.toString();
    assertNotNull(str);
    assertTrue(str.contains("findNeighbours"));
  }
}
