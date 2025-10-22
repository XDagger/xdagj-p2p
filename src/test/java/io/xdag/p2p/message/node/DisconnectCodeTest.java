package io.xdag.p2p.message.node;

import static org.junit.jupiter.api.Assertions.*;

import io.xdag.p2p.message.ReasonCode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Updated tests for ReasonCode (replacing legacy DisconnectCode). */
public class DisconnectCodeTest {

  @Test
  void testForNumber_KnownCodes() {
    assertEquals(ReasonCode.BAD_NETWORK, ReasonCode.of(0x00));
    assertEquals(ReasonCode.BAD_NETWORK_VERSION, ReasonCode.of(0x01));
    assertEquals(ReasonCode.TOO_MANY_PEERS, ReasonCode.of(0x02));
    assertEquals(ReasonCode.INVALID_HANDSHAKE, ReasonCode.of(0x03));
    assertEquals(ReasonCode.DUPLICATED_PEER_ID, ReasonCode.of(0x04));
    assertEquals(ReasonCode.MESSAGE_QUEUE_FULL, ReasonCode.of(0x05));
    assertEquals(ReasonCode.VALIDATOR_IP_LIMITED, ReasonCode.of(0x06));
    assertEquals(ReasonCode.HANDSHAKE_EXISTS, ReasonCode.of(0x07));
    assertEquals(ReasonCode.BAD_PEER, ReasonCode.of(0x08));
  }

  @Test
  void testGetValueUniqueness() {
    Set<Integer> values = new HashSet<>();
    for (ReasonCode code : ReasonCode.values()) {
      assertTrue(values.add(code.toByte() & 0xFF));
    }
  }

  @Test
  void testToStringNonNull() {
    for (ReasonCode code : ReasonCode.values()) {
      assertNotNull(code.toString());
    }
  }
}
