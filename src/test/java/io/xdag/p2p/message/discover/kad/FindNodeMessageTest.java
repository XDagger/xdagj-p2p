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
