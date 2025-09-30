package io.xdag.p2p.message.node;

import static org.junit.jupiter.api.Assertions.*;

import io.xdag.p2p.message.MessageCode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Updated tests for MessageCode (replacing legacy MessageType). */
public class MessageTypeTest {

  @Test
  void testOf_KnownTypes() {
    assertEquals(MessageCode.KAD_PING, MessageCode.of(0x00));
    assertEquals(MessageCode.KAD_PONG, MessageCode.of(0x01));
    assertEquals(MessageCode.HANDSHAKE_HELLO, MessageCode.of(0x12));
    assertEquals(MessageCode.DISCONNECT, MessageCode.of(0x10));
    assertEquals(MessageCode.APP_TEST, MessageCode.of(0x20));
  }

  @Test
  void testToByteUniqueness() {
    Set<Integer> codes = new HashSet<>();
    for (MessageCode c : MessageCode.values()) {
      assertTrue(codes.add((int)(c.toByte() & 0xFF)));
    }
  }

  @Test
  void testToStringNonNull() {
    for (MessageCode c : MessageCode.values()) {
      assertNotNull(c.toString());
    }
  }
}
