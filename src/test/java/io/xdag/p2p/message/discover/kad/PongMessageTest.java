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

class PongMessageTest {

  private P2pConfig p2pConfig;
  private Node from;

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
    p2pConfig.setNetworkId(1);
    from = new Node(p2pConfig, Bytes.random(64), "127.0.0.1", null, 30303, 30303);
  }

  @Test
  void testCreatePongMessage() {
    PongMessage pongMessage = new PongMessage(p2pConfig, from);
    assertEquals(MessageType.KAD_PONG, pongMessage.getType());
    assertEquals(p2pConfig.getNetworkId(), pongMessage.getNetworkId());
    assertEquals(from, pongMessage.getFrom());
    assertTrue(pongMessage.getTimestamp() > 0);
    assertNotNull(pongMessage.getData());
    assertTrue(pongMessage.valid());
  }

  @Test
  void testParsePongMessage() throws Exception {
    PongMessage originalPongMessage = new PongMessage(p2pConfig, from);
    Bytes encoded = originalPongMessage.getData();

    PongMessage parsedPongMessage = new PongMessage(p2pConfig, encoded);
    assertEquals(originalPongMessage.getType(), parsedPongMessage.getType());
    assertEquals(originalPongMessage.getNetworkId(), parsedPongMessage.getNetworkId());
    assertEquals(originalPongMessage.getFrom(), parsedPongMessage.getFrom());
    assertEquals(originalPongMessage.getTimestamp(), parsedPongMessage.getTimestamp());
    assertTrue(parsedPongMessage.valid());
  }

  @Test
  void testToString() {
    PongMessage pongMessage = new PongMessage(p2pConfig, from);
    String str = pongMessage.toString();
    assertNotNull(str);
    assertTrue(str.contains("pongMessage"));
  }
}
