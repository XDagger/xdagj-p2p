package io.xdag.p2p.message.node;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.xdag.p2p.config.P2pConfig;
import org.junit.jupiter.api.Test;

public class HelloMessageTest {

  @Test
  public void testHelloMessage() throws Exception {
    P2pConfig p2pConfig = new P2pConfig();
    p2pConfig = new P2pConfig();
    HelloMessage m1 = new HelloMessage(p2pConfig, DisconnectCode.NORMAL, 0);
    assertEquals(0, m1.getCode());

    assertEquals(p2pConfig.getNodeID(), m1.getFrom().getId());
    assertEquals(p2pConfig.getPort(), m1.getFrom().getPort());
    assertEquals(p2pConfig.getIp(), m1.getFrom().getHostV4());
    assertEquals(p2pConfig.getNetworkId(), m1.getNetworkId());
    assertEquals(MessageType.HANDSHAKE_HELLO, m1.getType());

    HelloMessage m2 = new HelloMessage(p2pConfig, m1.getData());
    assertEquals(p2pConfig.getNodeID(), m2.getFrom().getId());
    assertEquals(p2pConfig.getPort(), m2.getFrom().getPort());
    assertEquals(p2pConfig.getIp(), m2.getFrom().getHostV4());
    assertEquals(p2pConfig.getNetworkId(), m2.getNetworkId());
    assertEquals(MessageType.HANDSHAKE_HELLO, m2.getType());
  }
}
