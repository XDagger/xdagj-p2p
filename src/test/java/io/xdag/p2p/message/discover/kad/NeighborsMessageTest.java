package io.xdag.p2p.message.discover.kad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.discover.MessageType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NeighborsMessageTest {

  private P2pConfig p2pConfig;
  private Node from;
  private List<Node> neighbors;
  private long sequence;

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
    p2pConfig.setNetworkId(1);
    from = new Node(p2pConfig, Bytes.random(64), "127.0.0.1", null, 30303, 30303);
    neighbors = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      neighbors.add(new Node(p2pConfig, Bytes.random(64), "127.0.0.1", null, 30304 + i, 30304 + i));
    }
    sequence = System.currentTimeMillis();
  }

  @Test
  void testCreateNeighborsMessage() {
    NeighborsMessage neighborsMessage = new NeighborsMessage(p2pConfig, from, neighbors, sequence);
    assertEquals(MessageType.KAD_NEIGHBORS, neighborsMessage.getType());
    assertEquals(from, neighborsMessage.getFrom());
    assertEquals(sequence, neighborsMessage.getTimestamp());
    assertEquals(neighbors.size(), neighborsMessage.getNodes().size());
    assertEquals(neighbors, neighborsMessage.getNodes());
    assertNotNull(neighborsMessage.getData());
    assertTrue(neighborsMessage.valid());
  }

  @Test
  void testParseNeighborsMessage() throws Exception {
    NeighborsMessage originalMessage = new NeighborsMessage(p2pConfig, from, neighbors, sequence);
    Bytes encoded = originalMessage.getData();

    NeighborsMessage parsedMessage = new NeighborsMessage(p2pConfig, encoded);
    assertEquals(originalMessage.getType(), parsedMessage.getType());
    assertEquals(originalMessage.getFrom(), parsedMessage.getFrom());
    assertEquals(originalMessage.getTimestamp(), parsedMessage.getTimestamp());
    assertEquals(originalMessage.getNodes(), parsedMessage.getNodes());
    assertTrue(parsedMessage.valid());
  }

  @Test
  void testToString() {
    NeighborsMessage neighborsMessage = new NeighborsMessage(p2pConfig, from, neighbors, sequence);
    String str = neighborsMessage.toString();
    assertNotNull(str);
    assertTrue(str.contains("neighbours"));
  }

  @Test
  void testValidation() {
    // Valid message
    NeighborsMessage validMessage = new NeighborsMessage(p2pConfig, from, neighbors, sequence);
    assertTrue(validMessage.valid());

    // Empty neighbors list is valid
    NeighborsMessage emptyNeighborsMessage =
        new NeighborsMessage(p2pConfig, from, Collections.emptyList(), sequence);
    assertTrue(emptyNeighborsMessage.valid());

    // Invalid from node
    Node invalidFrom = new Node(p2pConfig, Bytes.random(10), "127.0.0.1", null, 30303, 30303);
    NeighborsMessage invalidFromMessage =
        new NeighborsMessage(p2pConfig, invalidFrom, neighbors, sequence);
    // The id check in validNode is based on length, so this should be invalid.
    // Based on the code, it uses String length, which can be tricky with byte arrays.
    // Let's create a node that is clearly invalid by having a bad ID length.
    assertFalse(invalidFromMessage.valid());
  }
}
