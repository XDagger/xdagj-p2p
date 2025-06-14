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

class PingMessageTest {

  private P2pConfig p2pConfig;
  private Node from;
  private Node to;

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
    p2pConfig.setNetworkId(1);
    from = new Node(p2pConfig, Bytes.random(64), "127.0.0.1", null, 30303, 30303);
    to = new Node(p2pConfig, Bytes.random(64), "127.0.0.2", null, 30304, 30304);
  }

  @Test
  void testCreatePingMessage() {
    PingMessage pingMessage = new PingMessage(p2pConfig, from, to);
    assertEquals(MessageType.KAD_PING, pingMessage.getType());
    assertEquals(p2pConfig.getNetworkId(), pingMessage.getNetworkId());
    assertEquals(from, pingMessage.getFrom());
    assertEquals(to, pingMessage.getTo());
    assertTrue(pingMessage.getTimestamp() > 0);
    assertNotNull(pingMessage.getData());
    assertTrue(pingMessage.valid());
  }

  @Test
  void testParsePingMessage() throws Exception {
    PingMessage originalPingMessage = new PingMessage(p2pConfig, from, to);
    Bytes encoded = originalPingMessage.getData();

    PingMessage parsedPingMessage = new PingMessage(p2pConfig, encoded);
    assertEquals(originalPingMessage.getType(), parsedPingMessage.getType());
    assertEquals(originalPingMessage.getNetworkId(), parsedPingMessage.getNetworkId());
    assertEquals(originalPingMessage.getFrom(), parsedPingMessage.getFrom());
    assertEquals(originalPingMessage.getTo(), parsedPingMessage.getTo());
    assertEquals(originalPingMessage.getTimestamp(), parsedPingMessage.getTimestamp());
    assertTrue(parsedPingMessage.valid());
  }

  @Test
  void testToString() {
    PingMessage pingMessage = new PingMessage(p2pConfig, from, to);
    String str = pingMessage.toString();
    assertNotNull(str);
    assertTrue(str.contains("pingMessage"));
  }
}
