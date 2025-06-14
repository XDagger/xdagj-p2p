package io.xdag.p2p.message.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for MessageType enum. Tests type mapping, uniqueness, and conversion from byte. */
public class MessageTypeTest {

  @Test
  void testFromByte_KnownTypes() {
    assertEquals(MessageType.PING, MessageType.fromByte((byte) 0xFF), "0xFF should map to PING");
    assertEquals(MessageType.PONG, MessageType.fromByte((byte) 0xFE), "0xFE should map to PONG");
    assertEquals(
        MessageType.HANDSHAKE_HELLO,
        MessageType.fromByte((byte) 0xFD),
        "0xFD should map to HANDSHAKE_HELLO");
    assertEquals(
        MessageType.STATUS, MessageType.fromByte((byte) 0xFC), "0xFC should map to STATUS");
    assertEquals(
        MessageType.DISCONNECT, MessageType.fromByte((byte) 0xFB), "0xFB should map to DISCONNECT");
    assertEquals(
        MessageType.UNKNOWN, MessageType.fromByte((byte) 0x80), "0x80 should map to UNKNOWN");
  }

  @Test
  void testFromByte_UnknownType() {
    assertEquals(
        MessageType.UNKNOWN, MessageType.fromByte((byte) 0x00), "0x00 should map to UNKNOWN");
    assertEquals(
        MessageType.UNKNOWN, MessageType.fromByte((byte) 0xAA), "0xAA should map to UNKNOWN");
    assertEquals(
        MessageType.UNKNOWN,
        MessageType.fromByte((byte) -127),
        "An arbitrary unknown byte should map to UNKNOWN");
  }

  @Test
  void testGetType() {
    assertEquals((byte) 0xFF, MessageType.PING.getType(), "PING type should be 0xFF");
    assertEquals((byte) 0xFE, MessageType.PONG.getType(), "PONG type should be 0xFE");
    assertEquals(
        (byte) 0xFD, MessageType.HANDSHAKE_HELLO.getType(), "HANDSHAKE_HELLO type should be 0xFD");
    assertEquals((byte) 0xFC, MessageType.STATUS.getType(), "STATUS type should be 0xFC");
    assertEquals((byte) 0xFB, MessageType.DISCONNECT.getType(), "DISCONNECT type should be 0xFB");
    assertEquals((byte) 0x80, MessageType.UNKNOWN.getType(), "UNKNOWN type should be 0x80");
  }

  @Test
  void testAllTypesCoveredInFromByte() {
    for (MessageType type : MessageType.values()) {
      // UNKNOWN is the default, so it won't map back from its own type if another type uses 0x80
      if (type != MessageType.UNKNOWN) {
        assertEquals(
            type,
            MessageType.fromByte(type.getType()),
            "Type " + type.name() + " should be retrievable from its byte value");
      }
    }
  }

  @Test
  void testEnumValues() {
    assertNotNull(MessageType.values(), "Should be able to get all enum values");
    assertTrue(MessageType.values().length > 0, "There should be at least one MessageType");
  }

  @Test
  void testUniquenessOfTypes() {
    Set<Byte> typeCodes = new HashSet<>();
    for (MessageType type : MessageType.values()) {
      assertTrue(
          typeCodes.add(type.getType()),
          "Type code " + type.getType() + " for " + type.name() + " should be unique");
    }
  }

  @Test
  void testToString() {
    for (MessageType type : MessageType.values()) {
      assertNotNull(type.toString(), "toString() should not be null for " + type.name());
      assertEquals(type.name(), type.toString(), "toString() should return the enum's name");
    }
  }
}
